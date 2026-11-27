#!/usr/bin/env python3
"""
Compile pre-built TerraSync TSR1 offline road graphs from Geofabrik Iraq OSM PBF.

Applies topology-preserving Douglas-Peucker simplification on degree-2 geometry chains
(keeping 100% of intersections, way endpoints, road names, and exact summed haversine
distances) to keep downloads fast (~10-15 MB) and mobile RAM low while preserving full
routing accuracy.

Matches OfflineRoutingEngine.kt's exact TSR1 GZIP binary format:
  - MAGIC = 0x54535231 ("TSR1")
  - regionName (Java Modified UTF-8: uint16 len + bytes)
  - minLat, minLng, maxLat, maxLng (4 x float64 BE)
  - downloadedAtMillis (int64 BE)
  - nodeCount, edgeCount, nameCount (3 x int32 BE)
  - names[0..nameCount-1] (Java Modified UTF-8)
  - nodes[0..nodeCount-1] (lat float32 BE, lon float32 BE)
  - edges[0..edgeCount-1] (from int32, to int32, dist float32, speed float32, flags int8, nameIdx int32)
"""

from __future__ import annotations

import argparse
import gzip
import math
import os
import struct
import sys
import time
import urllib.request
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Set, Tuple

import osmium

GEOFABRIK_IRAQ_PBF_URL = "https://download.geofabrik.de/asia/iraq/iraq-latest.osm.pbf"

MAGIC = 0x54535231  # TSR1
FLAG_CAR = 1
FLAG_BIKE = 2
FLAG_WALK = 4

# (car_ok, bike_ok, walk_ok, car_speed_kmh)
HIGHWAY_PROFILES: Dict[str, Tuple[bool, bool, bool, float]] = {
    "motorway": (True, False, False, 95.0),
    "motorway_link": (True, False, False, 95.0),
    "trunk": (True, True, True, 80.0),
    "trunk_link": (True, True, True, 80.0),
    "primary": (True, True, True, 65.0),
    "primary_link": (True, True, True, 65.0),
    "secondary": (True, True, True, 55.0),
    "secondary_link": (True, True, True, 55.0),
    "tertiary": (True, True, True, 45.0),
    "tertiary_link": (True, True, True, 45.0),
    "unclassified": (True, True, True, 40.0),
    "road": (True, True, True, 40.0),
    "residential": (True, True, True, 30.0),
    "living_street": (True, True, True, 20.0),
    "service": (True, True, True, 20.0),
    "track": (True, True, True, 16.0),
    "cycleway": (False, True, True, 15.0),
    "path": (False, True, True, 10.0),
    "footway": (False, True, True, 10.0),
}

ARTERIAL_HIGHWAYS: Set[str] = {
    "motorway",
    "motorway_link",
    "trunk",
    "trunk_link",
    "primary",
    "primary_link",
    "secondary",
    "secondary_link",
    "tertiary",
    "tertiary_link",
}

CORE_DRIVABLE_HIGHWAYS: Set[str] = ARTERIAL_HIGHWAYS | {
    "unclassified",
    "road",
    "residential",
    "living_street",
    "cycleway",
}

MINOR_ACCESS_HIGHWAYS: Set[str] = {
    "service",
    "track",
    "path",
    "footway",
}

# Populated Kurdistan hubs where service/track/footway detail is kept in full
KURDISTAN_DETAIL_BOXES: Tuple[Tuple[float, float, float, float], ...] = (
    (36.74, 42.50, 37.24, 43.35),  # Duhok, Zakho, Semel
    (36.60, 43.25, 37.15, 44.15),  # Akre, Shekhan, Bardarash, Amadiya
    (36.02, 43.82, 36.38, 44.28),  # Erbil metro & Shaqlawa approach
    (36.55, 44.35, 36.75, 44.65),  # Soran, Rawanduz, Choman
    (35.42, 45.25, 35.70, 45.60),  # Sulaymaniyah metro
    (35.10, 45.85, 35.30, 46.10),  # Halabja & Said Sadiq
    (36.15, 44.75, 36.35, 45.05),  # Ranya & Qaladiza
    (35.35, 44.25, 35.58, 44.52),  # Kirkuk metro
    (34.55, 45.20, 34.72, 45.42),  # Kalar
)


def encode_java_mutf8(text: str) -> bytes:
    """Encode a Python str to Java DataOutputStream.writeUTF Modified UTF-8."""
    out = bytearray()
    for ch in text:
        cp = ord(ch)
        if cp == 0:
            out.extend((0xC0, 0x80))
        elif cp <= 0x7F:
            out.append(cp)
        elif cp <= 0x7FF:
            out.append(0xC0 | ((cp >> 6) & 0x1F))
            out.append(0x80 | (cp & 0x3F))
        elif cp <= 0xFFFF:
            out.append(0xE0 | ((cp >> 12) & 0x0F))
            out.append(0x80 | ((cp >> 6) & 0x3F))
            out.append(0x80 | (cp & 0x3F))
        else:
            cp -= 0x10000
            hi = 0xD800 + (cp >> 10)
            lo = 0xDC00 + (cp & 0x3FF)
            for unit in (hi, lo):
                out.append(0xE0 | ((unit >> 12) & 0x0F))
                out.append(0x80 | ((unit >> 6) & 0x3F))
                out.append(0x80 | (unit & 0x3F))
        if len(out) > 65500:
            break
    return bytes(out)


def write_utf(stream, text: str) -> None:
    encoded = encode_java_mutf8(text)
    stream.write(struct.pack(">H", len(encoded)))
    stream.write(encoded)


def haversine_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371000.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    d_phi = math.radians(lat2 - lat1)
    d_lam = math.radians(lon2 - lon1)
    a = math.sin(d_phi * 0.5) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(d_lam * 0.5) ** 2
    return 2.0 * r * math.atan2(math.sqrt(a), math.sqrt(max(0.0, 1.0 - a)))


def point_segment_distance_m(
    p_lat: float,
    p_lon: float,
    a_lat: float,
    a_lon: float,
    b_lat: float,
    b_lon: float,
    cos_lat: float,
) -> float:
    """Fast local equirectangular point-to-segment distance in meters."""
    px = (p_lon - a_lon) * 111320.0 * cos_lat
    py = (p_lat - a_lat) * 111132.0
    bx = (b_lon - a_lon) * 111320.0 * cos_lat
    by = (b_lat - a_lat) * 111132.0
    len_sq = bx * bx + by * by
    if len_sq <= 1e-6:
        return math.hypot(px, py)
    t = (px * bx + py * by) / len_sq
    if t < 0.0:
        t = 0.0
    elif t > 1.0:
        t = 1.0
    dx = px - t * bx
    dy = py - t * by
    return math.hypot(dx, dy)


def simplify_chain_indices(
    pts: List[Tuple[int, float, float]],
    start_idx: int,
    end_idx: int,
    tolerance_m: float,
) -> List[int]:
    """Iterative Douglas-Peucker returning kept point indices in [start_idx..end_idx]."""
    if end_idx - start_idx <= 1:
        return [start_idx, end_idx]

    keep = [False] * (end_idx - start_idx + 1)
    keep[0] = True
    keep[-1] = True
    if pts[start_idx][0] == pts[end_idx][0] and end_idx - start_idx >= 3:
        mid = (start_idx + end_idx) // 2
        keep[mid - start_idx] = True
        stack = [(start_idx, mid), (mid, end_idx)]
    else:
        stack = [(start_idx, end_idx)]

    while stack:
        lo, hi = stack.pop()
        if hi - lo <= 1:
            continue
        _, a_lat, a_lon = pts[lo]
        _, b_lat, b_lon = pts[hi]
        cos_lat = math.cos(math.radians((a_lat + b_lat) * 0.5))
        max_dist = -1.0
        max_idx = -1
        for k in range(lo + 1, hi):
            _, p_lat, p_lon = pts[k]
            d = point_segment_distance_m(p_lat, p_lon, a_lat, a_lon, b_lat, b_lon, cos_lat)
            if d > max_dist:
                max_dist = d
                max_idx = k
        if max_dist > tolerance_m and max_idx != -1:
            keep[max_idx - start_idx] = True
            stack.append((lo, max_idx))
            stack.append((max_idx, hi))

    return [start_idx + i for i, flag in enumerate(keep) if flag]


@dataclass
class PackBuilder:
    pack_id: str
    region_name: str
    min_lat: float
    min_lng: float
    max_lat: float
    max_lng: float
    core_highways: Set[str]
    minor_highways: Set[str] = field(default_factory=set)
    minor_detail_boxes: Tuple[Tuple[float, float, float, float], ...] = ()
    simplify_tolerance_m: float = 12.0
    pad_deg: float = 0.01

    node_ref_counts: Counter = field(default_factory=Counter)
    node_map: Dict[int, int] = field(default_factory=dict)
    node_coords: List[Tuple[float, float]] = field(default_factory=list)
    name_map: Dict[str, int] = field(default_factory=lambda: {"": 0})
    names: List[str] = field(default_factory=lambda: [""])
    edges: List[Tuple[int, int, float, float, int, int]] = field(default_factory=list)

    def in_bbox(self, lat: float, lon: float) -> bool:
        return (
            (self.min_lat - self.pad_deg) <= lat <= (self.max_lat + self.pad_deg)
            and (self.min_lng - self.pad_deg) <= lon <= (self.max_lng + self.pad_deg)
        )

    def in_minor_detail_box(self, lat: float, lon: float) -> bool:
        for s_lat, w_lng, n_lat, e_lng in self.minor_detail_boxes:
            if s_lat <= lat <= n_lat and w_lng <= lon <= e_lng:
                return True
        return False

    def accepts_way(self, hw: str, road_name: str, pts: List[Tuple[int, float, float]]) -> bool:
        if hw in self.core_highways:
            return any(self.in_bbox(lat, lon) for _, lat, lon in pts)
        if hw in self.minor_highways:
            if road_name:
                return any(self.in_bbox(lat, lon) for _, lat, lon in pts)
            return any(self.in_minor_detail_box(lat, lon) for _, lat, lon in pts)
        return False

    def count_way_nodes(self, hw: str, road_name: str, pts: List[Tuple[int, float, float]]) -> None:
        if not self.accepts_way(hw, road_name, pts):
            return
        refs = self.node_ref_counts
        for osm_id, _, _ in pts:
            refs[osm_id] += 1

    def get_or_add_node(self, osm_id: int, lat: float, lon: float) -> int:
        idx = self.node_map.get(osm_id)
        if idx is not None:
            return idx
        idx = len(self.node_coords)
        self.node_map[osm_id] = idx
        self.node_coords.append((lat, lon))
        return idx

    def get_or_add_name(self, name: str) -> int:
        if not name:
            return 0
        idx = self.name_map.get(name)
        if idx is not None:
            return idx
        idx = len(self.names)
        self.name_map[name] = idx
        self.names.append(name)
        return idx

    def add_way(
        self,
        hw: str,
        road_name: str,
        fwd_flags: int,
        rev_flags: int,
        car_speed: float,
        pts: List[Tuple[int, float, float]],
    ) -> None:
        if not self.accepts_way(hw, road_name, pts):
            return

        # Clip to contiguous sub-runs intersecting the pack bbox
        n = len(pts)
        in_flags = [self.in_bbox(lat, lon) for _, lat, lon in pts]
        refs = self.node_ref_counts
        name_idx: int | None = None

        # Precompute cumulative exact haversine distance along original way geometry
        cum_dist = [0.0] * n
        for i in range(n - 1):
            _, lat1, lon1 = pts[i]
            _, lat2, lon2 = pts[i + 1]
            cum_dist[i + 1] = cum_dist[i] + haversine_meters(lat1, lon1, lat2, lon2)

        # Identify pinned indices: way ends, junctions (refcount >= 2), or bbox boundary crossings
        pinned: List[int] = []
        for i in range(n):
            if (
                i == 0
                or i == n - 1
                or refs.get(pts[i][0], 0) >= 2
                or (i > 0 and in_flags[i] != in_flags[i - 1])
                or (i + 1 < n and in_flags[i] != in_flags[i + 1])
            ):
                pinned.append(i)

        # Simplify each unbranched chain between consecutive pinned nodes
        kept_indices: List[int] = []
        for p_idx in range(len(pinned) - 1):
            lo = pinned[p_idx]
            hi = pinned[p_idx + 1]
            if not (in_flags[lo] or in_flags[hi]):
                continue
            chain = simplify_chain_indices(pts, lo, hi, self.simplify_tolerance_m)
            if kept_indices and kept_indices[-1] == chain[0]:
                kept_indices.extend(chain[1:])
            else:
                kept_indices.extend(chain)

        for k in range(len(kept_indices) - 1):
            i1 = kept_indices[k]
            i2 = kept_indices[k + 1]
            if not (in_flags[i1] or in_flags[i2]):
                continue
            id1, lat1, lon1 = pts[i1]
            id2, lat2, lon2 = pts[i2]
            if id1 == id2:
                continue
            u = self.get_or_add_node(id1, lat1, lon1)
            v = self.get_or_add_node(id2, lat2, lon2)
            if u == v:
                continue
            if name_idx is None:
                name_idx = self.get_or_add_name(road_name)
            dist = max(cum_dist[i2] - cum_dist[i1], 0.5)
            if fwd_flags:
                self.edges.append((u, v, dist, car_speed, fwd_flags, name_idx))
            if rev_flags:
                self.edges.append((v, u, dist, car_speed, rev_flags, name_idx))

    def write_tsr(self, out_dir: Path, now_ms: int) -> Path:
        out_dir.mkdir(parents=True, exist_ok=True)
        out_path = out_dir / f"pack_{self.pack_id}.tsr"
        tmp_path = out_dir / f"pack_{self.pack_id}.tsr.tmp"
        node_count = len(self.node_coords)
        edge_count = len(self.edges)
        name_count = len(self.names)

        with gzip.open(tmp_path, "wb", compresslevel=6) as gz:
            gz.write(struct.pack(">I", MAGIC))
            write_utf(gz, self.region_name)
            gz.write(
                struct.pack(
                    ">ddddqiii",
                    self.min_lat,
                    self.min_lng,
                    self.max_lat,
                    self.max_lng,
                    now_ms,
                    node_count,
                    edge_count,
                    name_count,
                )
            )
            for name in self.names:
                write_utf(gz, name)
            pack_node = struct.Struct(">ff").pack
            node_buf = bytearray()
            for lat, lon in self.node_coords:
                node_buf.extend(pack_node(lat, lon))
                if len(node_buf) >= 262144:
                    gz.write(node_buf)
                    node_buf.clear()
            if node_buf:
                gz.write(node_buf)
                node_buf.clear()

            pack_edge = struct.Struct(">iiffbi").pack
            edge_buf = bytearray()
            for u, v, dist, spd, flg, n_idx in self.edges:
                edge_buf.extend(pack_edge(u, v, dist, spd, flg, n_idx))
                if len(edge_buf) >= 262144:
                    gz.write(edge_buf)
                    edge_buf.clear()
            if edge_buf:
                gz.write(edge_buf)

        os.replace(tmp_path, out_path)
        size_mb = out_path.stat().st_size / (1024.0 * 1024.0)
        print(
            f"[OK] {out_path.name}: {node_count:,} nodes, {edge_count:,} edges, "
            f"{name_count:,} names, {size_mb:.2f} MB"
        )
        return out_path


def _extract_way_info(w: osmium.osm.Way):
    hw = w.tags.get("highway")
    if not hw:
        return None
    profile = HIGHWAY_PROFILES.get(hw)
    if profile is None:
        return None
    access = w.tags.get("access", "")
    if access in ("private", "no"):
        return None

    car_ok, bike_ok, walk_ok, car_speed = profile
    oneway = w.tags.get("oneway", "")
    junction = w.tags.get("junction", "")
    is_oneway_fwd = (
        oneway in ("yes", "true", "1")
        or junction == "roundabout"
        or hw in ("motorway", "motorway_link")
    )
    is_oneway_rev = oneway == "-1"
    fwd_flags = (
        (FLAG_CAR if car_ok and not is_oneway_rev else 0)
        | (FLAG_BIKE if bike_ok and not is_oneway_rev else 0)
        | (FLAG_WALK if walk_ok else 0)
    )
    rev_flags = (
        (FLAG_CAR if car_ok and not is_oneway_fwd else 0)
        | (FLAG_BIKE if bike_ok and not is_oneway_fwd else 0)
        | (FLAG_WALK if walk_ok else 0)
    )
    if fwd_flags == 0 and rev_flags == 0:
        return None

    pts: List[Tuple[int, float, float]] = []
    for n in w.nodes:
        if n.location.valid():
            pts.append((n.ref, n.location.lat, n.location.lon))
    if len(pts) < 2:
        return None

    road_name = (w.tags.get("name") or "").strip()
    return hw, road_name, fwd_flags, rev_flags, car_speed, pts


class NodeRefCountPass(osmium.SimpleHandler):
    def __init__(self, builders: List[PackBuilder]) -> None:
        super().__init__()
        self.builders = builders
        self.ways_seen = 0

    def way(self, w: osmium.osm.Way) -> None:
        info = _extract_way_info(w)
        if info is None:
            return
        hw, road_name, _, _, _, pts = info
        for builder in self.builders:
            builder.count_way_nodes(hw, road_name, pts)
        self.ways_seen += 1


class RoadGraphExtractor(osmium.SimpleHandler):
    def __init__(self, builders: List[PackBuilder]) -> None:
        super().__init__()
        self.builders = builders
        self.ways_seen = 0

    def way(self, w: osmium.osm.Way) -> None:
        info = _extract_way_info(w)
        if info is None:
            return
        hw, road_name, fwd_flags, rev_flags, car_speed, pts = info
        for builder in self.builders:
            builder.add_way(hw, road_name, fwd_flags, rev_flags, car_speed, pts)
        self.ways_seen += 1


def download_pbf(dest: Path, url: str = GEOFABRIK_IRAQ_PBF_URL) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".pbf.part")
    print(f"Downloading {url} -> {dest} ...")
    req = urllib.request.Request(url, headers={"User-Agent": "TerraSync-RoadPackBuilder/1.0"})
    with urllib.request.urlopen(req, timeout=180) as resp, open(tmp, "wb") as out:
        while True:
            chunk = resp.read(1024 * 256)
            if not chunk:
                break
            out.write(chunk)
    os.replace(tmp, dest)
    print(f"Downloaded {dest.stat().st_size / (1024.0 * 1024.0):.2f} MB")


def main() -> int:
    parser = argparse.ArgumentParser(description="Build TerraSync TSR1 offline road packs.")
    parser.add_argument("--pbf", type=Path, default=Path("iraq-latest.osm.pbf"), help="Input OSM PBF path")
    parser.add_argument("--out-dir", type=Path, default=Path("dist"), help="Output directory for .tsr packs")
    args = parser.parse_args()

    if not args.pbf.exists():
        download_pbf(args.pbf)

    builders = [
        PackBuilder(
            pack_id="kurdistan_north",
            region_name="Kurdistan",
            min_lat=34.45,
            min_lng=42.35,
            max_lat=37.38,
            max_lng=46.35,
            core_highways=CORE_DRIVABLE_HIGHWAYS,
            minor_highways=MINOR_ACCESS_HIGHWAYS,
            minor_detail_boxes=KURDISTAN_DETAIL_BOXES,
            simplify_tolerance_m=12.0,
        ),
        PackBuilder(
            pack_id="iraq_south",
            region_name="Southern Iraq",
            min_lat=29.05,
            min_lng=38.75,
            max_lat=34.45,
            max_lng=48.65,
            core_highways=ARTERIAL_HIGHWAYS,
            simplify_tolerance_m=25.0,
        ),
    ]

    t0 = time.monotonic()
    print(f"Pass 1/2: Counting junction nodes from {args.pbf} ...")
    pass1 = NodeRefCountPass(builders)
    pass1.apply_file(str(args.pbf), locations=True, idx="flex_mem")
    print(f"Pass 1 complete ({pass1.ways_seen:,} ways) in {time.monotonic() - t0:.1f}s")

    t1 = time.monotonic()
    print(f"Pass 2/2: Extracting simplified road graphs from {args.pbf} ...")
    pass2 = RoadGraphExtractor(builders)
    pass2.apply_file(str(args.pbf), locations=True, idx="flex_mem")
    print(f"Pass 2 complete ({pass2.ways_seen:,} ways) in {time.monotonic() - t1:.1f}s")

    for b in builders:
        b.node_ref_counts.clear()

    now_ms = int(time.time() * 1000)
    for builder in builders:
        if not builder.node_coords or not builder.edges:
            print(f"[ERROR] Pack {builder.pack_id} produced an empty graph!", file=sys.stderr)
            return 1
        builder.write_tsr(args.out_dir, now_ms)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
