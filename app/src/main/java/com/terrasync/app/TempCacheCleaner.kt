package com.terrasync.app

import java.io.File

/**
 * Clears regenerable temp data under cacheDir (Terrarium DEM tiles + KML/CSV export staging).
 * Subdirectories themselves are preserved because ContourOverlayManager keeps a handle to its dir.
 */
internal object TempCacheCleaner {
    val SUBDIRS = listOf("terrarium_dem", "exports")

    /** Returns bytes freed. */
    fun purge(cacheDir: File): Long {
        var freed = 0L
        for (name in SUBDIRS) {
            val dir = File(cacheDir, name)
            if (!dir.isDirectory) continue
            dir.walkBottomUp().forEach { f ->
                if (f == dir) return@forEach
                runCatching {
                    val len = if (f.isFile) f.length() else 0L
                    if (f.delete()) freed += len
                }
            }
        }
        return freed
    }
}
