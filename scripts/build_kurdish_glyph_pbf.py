#!/usr/bin/env python3
"""
Build patched OpenFreeMap SDF glyph PBF (64256-64511.pbf, U+FB00..U+FBFF) with
proper HarfBuzz-shaped Kurdish Sorani cursive forms for ێ (U+06CE), ڵ (U+06B5),
and ڕ (U+0695) mapped into assigned Arabic Presentation Forms-A (bidi=AL) slots:

  U+FB5A..U+FB5D -> ێ (isolated, final, initial, medial)
  U+FB5E..U+FB61 -> ڵ (isolated, final, initial, medial)
  U+FB62..U+FB63 -> ڕ (isolated, final)

Matches MapLibre / fontnik exact SDF format:
  font_size = 24px, buffer = 3px, radius = 8px, cutoff = 0.25,
  bitmap size = (width + 6) * (height + 6) uint8 bytes.
"""

from __future__ import annotations

import argparse
import math
import struct
import sys
import urllib.request
from pathlib import Path
from typing import Dict, List, Tuple

import freetype
import uharfbuzz as hb

OPENFREEMAP_BASE_PBF_URL = (
    "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Regular/64256-64511.pbf"
)
FONT_CANDIDATE_URLS = [
    # Latest official Noto Naskh Arabic (exact family used by OpenMapTiles/OpenFreeMap Noto Sans Regular)
    "https://raw.githubusercontent.com/notofonts/notofonts.github.io/main/fonts/NotoNaskhArabic/hinted/ttf/NotoNaskhArabic-Regular.ttf",
    "https://raw.githubusercontent.com/openmaptiles/fonts/master/noto-sans/NotoNaskhArabic-Regular.ttf",
    "https://raw.githubusercontent.com/notofonts/notofonts.github.io/main/fonts/NotoSansArabic/hinted/ttf/NotoSansArabic-Regular.ttf",
]

FONT_SIZE = 24
SDF_BUFFER = 3
SDF_RADIUS = 8.0
SDF_CUTOFF = 0.25
INF = 1e20

# (target_codepoint, shaping_text, target_cluster_index_in_utf16, description)
KURDISH_GLYPH_SPECS: List[Tuple[int, str, int, str]] = [
    (0xFB5A, "\u06CE", 0, "YEH_SMALL_V isolated (ێ)"),
    (0xFB5B, "\u200D\u06CE", 1, "YEH_SMALL_V final (ـێ)"),
    (0xFB5C, "\u06CE\u200D", 0, "YEH_SMALL_V initial (ێـ)"),
    (0xFB5D, "\u200D\u06CE\u200D", 1, "YEH_SMALL_V medial (ـێـ)"),
    (0xFB5E, "\u06B5", 0, "LAM_SMALL_V isolated (ڵ)"),
    (0xFB5F, "\u200D\u06B5", 1, "LAM_SMALL_V final (ـڵ)"),
    (0xFB60, "\u06B5\u200D", 0, "LAM_SMALL_V initial (ڵـ)"),
    (0xFB61, "\u200D\u06B5\u200D", 1, "LAM_SMALL_V medial (ـڵـ)"),
    (0xFB62, "\u0695", 0, "REH_SMALL_V isolated (ڕ)"),
    (0xFB63, "\u200D\u0695", 1, "REH_SMALL_V final (ـڕ)"),
]


def download_bytes(url: str) -> bytes:
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "TerraSync-GlyphBuilder/1.0"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read()


def read_varint(buf: bytes, pos: int) -> Tuple[int, int]:
    res = 0
    shift = 0
    while True:
        b = buf[pos]
        pos += 1
        res |= (b & 0x7F) << shift
        if not (b & 0x80):
            return res, pos
        shift += 7


def encode_varint(val: int) -> bytes:
    if val < 0:
        val &= 0xFFFFFFFFFFFFFFFF
    out = bytearray()
    while True:
        b = val & 0x7F
        val >>= 7
        if val:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def zigzag_encode(n: int) -> int:
    return (n << 1) ^ (n >> 31)


def zigzag_decode(n: int) -> int:
    return (n >> 1) ^ -(n & 1)


def encode_glyph_msg(
    glyph_id: int,
    bitmap: bytes,
    width: int,
    height: int,
    left: int,
    top: int,
    advance: int,
) -> bytes:
    out = bytearray()
    # field 1: uint32 id
    out.append((1 << 3) | 0)
    out.extend(encode_varint(glyph_id))
    # field 2: bytes bitmap
    if bitmap:
        out.append((2 << 3) | 2)
        out.extend(encode_varint(len(bitmap)))
        out.extend(bitmap)
    # field 3: uint32 width
    out.append((3 << 3) | 0)
    out.extend(encode_varint(width))
    # field 4: uint32 height
    out.append((4 << 3) | 0)
    out.extend(encode_varint(height))
    # field 5: sint32 left
    out.append((5 << 3) | 0)
    out.extend(encode_varint(zigzag_encode(left)))
    # field 6: sint32 top
    out.append((6 << 3) | 0)
    out.extend(encode_varint(zigzag_encode(top)))
    # field 7: uint32 advance
    out.append((7 << 3) | 0)
    out.extend(encode_varint(advance))
    return bytes(out)


def parse_glyph_metrics(pbf_bytes: bytes) -> Dict[int, Dict[str, int]]:
    out: Dict[int, Dict[str, int]] = {}
    pos, n = 0, len(pbf_bytes)
    while pos < n:
        tag, pos = read_varint(pbf_bytes, pos)
        fn, wt = tag >> 3, tag & 7
        if wt == 2:
            l, pos = read_varint(pbf_bytes, pos)
            sub = pbf_bytes[pos : pos + l]
            pos += l
            if fn == 1:
                p2, n2 = 0, len(sub)
                while p2 < n2:
                    t2, p2 = read_varint(sub, p2)
                    f2, w2 = t2 >> 3, t2 & 7
                    if w2 == 2:
                        l2, p2 = read_varint(sub, p2)
                        g = sub[p2 : p2 + l2]
                        p2 += l2
                        if f2 == 3:
                            p3, n3 = 0, len(g)
                            m: Dict[str, int] = {}
                            while p3 < n3:
                                t3, p3 = read_varint(g, p3)
                                f3, w3 = t3 >> 3, t3 & 7
                                if w3 == 0:
                                    v3, p3 = read_varint(g, p3)
                                    if f3 == 1:
                                        m["id"] = v3
                                    elif f3 == 3:
                                        m["w"] = v3
                                    elif f3 == 4:
                                        m["h"] = v3
                                    elif f3 == 5:
                                        m["left"] = zigzag_decode(v3)
                                    elif f3 == 6:
                                        m["top"] = zigzag_decode(v3)
                                    elif f3 == 7:
                                        m["adv"] = v3
                                elif w3 == 2:
                                    l3, p3 = read_varint(g, p3)
                                    if f3 == 2:
                                        m["bmp_len"] = l3
                                    p3 += l3
                                elif w3 == 5:
                                    p3 += 4
                                elif w3 == 1:
                                    p3 += 8
                            if "id" in m:
                                out[m["id"]] = m
                    elif w2 == 0:
                        _, p2 = read_varint(sub, p2)
                    elif w2 == 5:
                        p2 += 4
                    elif w2 == 1:
                        p2 += 8
        elif wt == 0:
            _, pos = read_varint(pbf_bytes, pos)
        elif wt == 5:
            pos += 4
        elif wt == 1:
            pos += 8
    return out


def edt_1d(f: List[float], n: int) -> List[float]:
    """Felzenszwalb & Huttenlocher 1D squared Euclidean distance transform."""
    v = [0] * n
    z = [0.0] * (n + 1)
    d = [0.0] * n
    k = 0
    v[0] = 0
    z[0] = -INF
    z[1] = INF
    for q in range(1, n):
        while True:
            vk = v[k]
            s = ((f[q] + q * q) - (f[vk] + vk * vk)) / (2.0 * (q - vk))
            if s <= z[k]:
                k -= 1
            else:
                break
        k += 1
        v[k] = q
        z[k] = s
        z[k + 1] = INF
    k = 0
    for q in range(n):
        while z[k + 1] < q:
            k += 1
        dq = q - v[k]
        d[q] = dq * dq + f[v[k]]
    return d


def edt_2d(grid: List[float], width: int, height: int) -> List[float]:
    # Columns
    for x in range(width):
        col = [grid[y * width + x] for y in range(height)]
        dcol = edt_1d(col, height)
        for y in range(height):
            grid[y * width + x] = dcol[y]
    # Rows
    for y in range(height):
        row = grid[y * width : (y + 1) * width]
        drow = edt_1d(row, width)
        grid[y * width : (y + 1) * width] = drow
    return grid


def raster_to_sdf(alpha: bytes, w: int, h: int, buf: int = SDF_BUFFER) -> bytes:
    """Convert grayscale 8-bit glyph bitmap to MapLibre TinySDF format."""
    sw = w + 2 * buf
    sh = h + 2 * buf
    size = sw * sh
    grid_outer = [INF] * size
    grid_inner = [0.0] * size

    for y in range(h):
        row_off = (y + buf) * sw + buf
        src_off = y * w
        for x in range(w):
            a = alpha[src_off + x] / 255.0
            if a <= 0.0:
                continue
            idx = row_off + x
            if a >= 1.0:
                grid_outer[idx] = 0.0
                grid_inner[idx] = INF
            else:
                d = 0.5 - a
                grid_outer[idx] = d * d if d > 0.0 else 0.0
                grid_inner[idx] = d * d if d < 0.0 else 0.0

    edt_2d(grid_outer, sw, sh)
    edt_2d(grid_inner, sw, sh)

    out = bytearray(size)
    for i in range(size):
        d = math.sqrt(grid_outer[i]) - math.sqrt(grid_inner[i])
        val = round(255.0 - 255.0 * (d / SDF_RADIUS + SDF_CUTOFF))
        if val < 0:
            val = 0
        elif val > 255:
            val = 255
        out[i] = val
    return bytes(out)


def shape_cluster_glyphs(
    hb_font: hb.Font, text: str, target_cluster: int
) -> List[Tuple[int, int, int, int]]:
    """Return all (glyph_id, x_advance, x_offset, y_offset) in HarfBuzz font units for target_cluster."""
    buf = hb.Buffer()
    buf.add_str(text)
    buf.direction = "rtl"
    buf.script = "Arab"
    buf.language = "ckb"
    if hasattr(hb, "BufferFlags") and hasattr(hb.BufferFlags, "REMOVE_DEFAULT_IGNORABLES"):
        buf.flags = hb.BufferFlags.REMOVE_DEFAULT_IGNORABLES
    hb.shape(hb_font, buf)
    zwj_gid = hb_font.get_nominal_glyph(0x200D)
    out: List[Tuple[int, int, int, int]] = []
    for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
        if info.cluster == target_cluster:
            if zwj_gid is not None and info.codepoint == zwj_gid:
                continue
            out.append((info.codepoint, pos.x_advance, pos.x_offset, pos.y_offset))
    if not out:
        raise RuntimeError(f"Could not find cluster {target_cluster} in shaped output for {text!r}")
    return out


def render_glyph_sdf(
    ft_face: freetype.Face,
    hb_font: hb.Font,
    text: str,
    target_cluster: int,
    top_calibration: int,
) -> Tuple[bytes, int, int, int, int, int]:
    """Render and composite all HarfBuzz-shaped glyphs (base + GPOS marks) for target_cluster into TinySDF."""
    shaped = shape_cluster_glyphs(hb_font, text, target_cluster)
    scale = FONT_SIZE / float(ft_face.units_per_EM)

    rendered = []
    pen_x = 0.0
    total_x_adv = 0.0
    min_x = 10**9
    min_y = 10**9
    max_x = -(10**9)
    max_y = -(10**9)

    for gid, x_adv, x_off, y_off in shaped:
        ft_face.load_glyph(gid, freetype.FT_LOAD_RENDER)
        slot = ft_face.glyph
        bmp = slot.bitmap
        gw = int(bmp.width)
        gh = int(bmp.rows)
        gx = int(round(pen_x + x_off * scale)) + int(slot.bitmap_left)
        # y grows downward from baseline (baseline = 0, bitmap_top above baseline -> negative y)
        gy = int(round(-y_off * scale)) - int(slot.bitmap_top)
        raw = bytes(bmp.buffer)
        if bmp.pitch != gw and gw > 0 and gh > 0:
            rows = bytearray()
            for r in range(gh):
                start = r * bmp.pitch
                rows.extend(raw[start : start + gw])
            raw = bytes(rows)
        if gw > 0 and gh > 0:
            rendered.append((gx, gy, gw, gh, raw))
            min_x = min(min_x, gx)
            min_y = min(min_y, gy)
            max_x = max(max_x, gx + gw)
            max_y = max(max_y, gy + gh)
        adv_px = x_adv * scale if x_adv > 0 else (slot.advance.x / 64.0)
        pen_x += adv_px
        total_x_adv += adv_px

    if not rendered:
        return b"", 0, 0, 0, -FONT_SIZE + top_calibration, max(1, int(round(total_x_adv)))

    w = max_x - min_x
    h = max_y - min_y
    canvas = bytearray(w * h)
    for gx, gy, gw, gh, raw in rendered:
        ox = gx - min_x
        oy = gy - min_y
        for r in range(gh):
            dst_row = (oy + r) * w + ox
            src_row = r * gw
            for c in range(gw):
                v = raw[src_row + c]
                if v > canvas[dst_row + c]:
                    canvas[dst_row + c] = v

    left = min_x
    bitmap_top = -min_y
    top = (bitmap_top - FONT_SIZE) + top_calibration
    adv = max(1, int(round(total_x_adv)))
    sdf = raster_to_sdf(bytes(canvas), w, h, SDF_BUFFER)
    return sdf, w, h, left, top, adv


def load_best_font(font_path: Path | None) -> Tuple[bytes, freetype.Face, hb.Font]:
    candidates: List[Tuple[str, bytes]] = []
    if font_path and font_path.exists():
        candidates.append((str(font_path), font_path.read_bytes()))
    else:
        for url in FONT_CANDIDATE_URLS:
            try:
                data = download_bytes(url)
                candidates.append((url, data))
            except Exception as exc:
                print(f"[WARN] Could not fetch {url}: {exc}", file=sys.stderr)

    for label, data in candidates:
        hb_face = hb.Face(data)
        hb_font = hb.Font(hb_face)
        # Verify Kurdish GSUB joining forms exist (ێ initial != isolated and ڵ initial != isolated)
        try:
            yeh_isol = [g[0] for g in shape_cluster_glyphs(hb_font, "\u06CE", 0)]
            yeh_init = [g[0] for g in shape_cluster_glyphs(hb_font, "\u06CE\u200D", 0)]
            yeh_medi = [g[0] for g in shape_cluster_glyphs(hb_font, "\u200D\u06CE\u200D", 1)]
            lam_isol = [g[0] for g in shape_cluster_glyphs(hb_font, "\u06B5", 0)]
            lam_init = [g[0] for g in shape_cluster_glyphs(hb_font, "\u06B5\u200D", 0)]
            reh_isol = [g[0] for g in shape_cluster_glyphs(hb_font, "\u0695", 0)]
            reh_fina = [g[0] for g in shape_cluster_glyphs(hb_font, "\u200D\u0695", 1)]
        except Exception as exc:
            print(f"[WARN] Font {label} failed Kurdish shaping check: {exc}", file=sys.stderr)
            continue

        if (
            yeh_isol[0] != 0
            and yeh_init != yeh_isol
            and yeh_medi != yeh_isol
            and lam_isol[0] != 0
            and lam_init != lam_isol
            and reh_isol[0] != 0
            and reh_fina != reh_isol
        ):
            import io

            ft_face = freetype.Face(io.BytesIO(data))
            ft_face.set_pixel_sizes(0, FONT_SIZE)
            print(
                f"[OK] Selected Kurdish font: {label} "
                f"(ێ gids={yeh_isol}/{yeh_init}/{yeh_medi}, ڵ={lam_isol}/{lam_init}, ڕ={reh_isol}/{reh_fina})"
            )
            return data, ft_face, hb_font
        else:
            print(f"[WARN] Font {label} lacks full Kurdish joining forms, trying next...", file=sys.stderr)

    raise RuntimeError("No Arabic/Kurdish font with full ێ/ڵ/ڕ joining forms could be loaded")


def patch_fontstack_pbf(base_pbf: bytes, replacements: Dict[int, bytes]) -> bytes:
    """Replace or insert glyphs in a MapLibre glyphs PBF while preserving stack metadata."""
    out_root = bytearray()
    pos, n = 0, len(base_pbf)
    while pos < n:
        tag, pos = read_varint(base_pbf, pos)
        fn, wt = tag >> 3, tag & 7
        if wt == 2:
            l, pos = read_varint(base_pbf, pos)
            sub = base_pbf[pos : pos + l]
            pos += l
            if fn == 1:
                # Re-encode fontstack message
                new_stack = bytearray()
                inserted: set[int] = set()
                p2, n2 = 0, len(sub)
                while p2 < n2:
                    field_start = p2
                    t2, p2 = read_varint(sub, p2)
                    f2, w2 = t2 >> 3, t2 & 7
                    if w2 == 2:
                        l2, p2 = read_varint(sub, p2)
                        val_bytes = sub[p2 : p2 + l2]
                        p2 += l2
                        if f2 == 3:
                            # Extract glyph id
                            gid = None
                            p3, n3 = 0, len(val_bytes)
                            while p3 < n3:
                                t3, p3 = read_varint(val_bytes, p3)
                                f3, w3 = t3 >> 3, t3 & 7
                                if w3 == 0:
                                    v3, p3 = read_varint(val_bytes, p3)
                                    if f3 == 1:
                                        gid = v3
                                        break
                                elif w3 == 2:
                                    l3, p3 = read_varint(val_bytes, p3)
                                    p3 += l3
                                elif w3 == 5:
                                    p3 += 4
                                elif w3 == 1:
                                    p3 += 8
                            if gid is not None and gid in replacements:
                                rep = replacements[gid]
                                new_stack.append((3 << 3) | 2)
                                new_stack.extend(encode_varint(len(rep)))
                                new_stack.extend(rep)
                                inserted.add(gid)
                            else:
                                new_stack.extend(sub[field_start:p2])
                        else:
                            new_stack.extend(sub[field_start:p2])
                    elif w2 == 0:
                        _, p2 = read_varint(sub, p2)
                        new_stack.extend(sub[field_start:p2])
                    elif w2 == 5:
                        p2 += 4
                        new_stack.extend(sub[field_start:p2])
                    elif w2 == 1:
                        p2 += 8
                        new_stack.extend(sub[field_start:p2])

                # Append any glyphs that were not present in the original range
                for gid, rep in sorted(replacements.items()):
                    if gid not in inserted:
                        new_stack.append((3 << 3) | 2)
                        new_stack.extend(encode_varint(len(rep)))
                        new_stack.extend(rep)

                out_root.append((1 << 3) | 2)
                out_root.extend(encode_varint(len(new_stack)))
                out_root.extend(new_stack)
            else:
                out_root.extend(encode_varint(tag))
                out_root.extend(encode_varint(len(sub)))
                out_root.extend(sub)
        elif wt == 0:
            v, pos = read_varint(base_pbf, pos)
            out_root.extend(encode_varint(tag))
            out_root.extend(encode_varint(v))
        elif wt == 5:
            out_root.extend(encode_varint(tag))
            out_root.extend(base_pbf[pos : pos + 4])
            pos += 4
        elif wt == 1:
            out_root.extend(encode_varint(tag))
            out_root.extend(base_pbf[pos : pos + 8])
            pos += 8
    return bytes(out_root)


def main() -> int:
    parser = argparse.ArgumentParser(description="Build patched Kurdish SDF glyph PBF (64256-64511.pbf).")
    parser.add_argument(
        "--out",
        type=Path,
        default=Path("app/src/main/assets/fonts/64256-64511.pbf"),
        help="Output PBF path",
    )
    parser.add_argument("--font", type=Path, default=None, help="Optional local TTF path")
    args = parser.parse_args()

    print(f"Downloading OpenFreeMap base range: {OPENFREEMAP_BASE_PBF_URL}")
    base_pbf = download_bytes(OPENFREEMAP_BASE_PBF_URL)
    base_metrics = parse_glyph_metrics(base_pbf)
    if 0xFBFC not in base_metrics:
        raise RuntimeError("Base PBF missing expected reference glyph U+FBFC")

    _, ft_face, hb_font = load_best_font(args.font)

    # Calibrate baseline offset against existing U+FBFC (ی isolated) in OpenFreeMap PBF
    _, _, _, _, raw_ref_top, _ = render_glyph_sdf(ft_face, hb_font, "\u06CC", 0, 0)
    ofm_ref_top = base_metrics[0xFBFC]["top"]
    top_calibration = ofm_ref_top - raw_ref_top
    print(f"Baseline calibration vs U+FBFC: ofm_top={ofm_ref_top}, ft_top={raw_ref_top}, delta={top_calibration}")

    replacements: Dict[int, bytes] = {}
    for target_cp, shaping_text, cluster_idx, desc in KURDISH_GLYPH_SPECS:
        sdf, w, h, left, top, adv = render_glyph_sdf(
            ft_face, hb_font, shaping_text, cluster_idx, top_calibration
        )
        replacements[target_cp] = encode_glyph_msg(target_cp, sdf, w, h, left, top, adv)
        print(
            f"  U+{target_cp:04X} {desc:28s}: w={w:2d} h={h:2d} left={left:3d} top={top:3d} adv={adv:2d} sdf={len(sdf)}B"
        )

    patched = patch_fontstack_pbf(base_pbf, replacements)

    # Self-verify all 10 glyphs in patched PBF
    verified = parse_glyph_metrics(patched)
    for target_cp, _, _, desc in KURDISH_GLYPH_SPECS:
        m = verified.get(target_cp)
        if not m or m.get("adv", 0) <= 0:
            raise RuntimeError(f"Verification failed for U+{target_cp:04X} ({desc}): {m}")
        expected_bmp = (m["w"] + 2 * SDF_BUFFER) * (m["h"] + 2 * SDF_BUFFER)
        if m.get("bmp_len") != expected_bmp:
            raise RuntimeError(
                f"Bitmap length mismatch for U+{target_cp:04X}: got {m.get('bmp_len')}, expected {expected_bmp}"
            )

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(patched)
    print(f"[OK] Wrote {args.out} ({len(patched):,} bytes, {len(verified)} total glyphs)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
