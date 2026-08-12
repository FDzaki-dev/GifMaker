package com.gifmaker.app

import kotlin.math.max
import kotlin.math.min

/**
 * Quantizer warna offline murni (median-cut) untuk membangun palet global GIF (maks 256 warna)
 * dari kumpulan piksel beberapa frame, lalu memetakan tiap piksel ke indeks palet terdekat.
 * Tidak bergantung pada library eksternal apa pun.
 */
object ColorQuantizer {

    private const val MAX_SAMPLE_PIXELS = 20000

    private data class Box(val pixels: MutableList<IntArray>)

    /**
     * Membangun palet global dari kumpulan piksel ARGB semua frame.
     * @param allPixels daftar array piksel ARGB per frame
     * @param maxColors jumlah warna maksimum pada palet (<= 256)
     */
    fun buildPalette(allPixels: List<IntArray>, maxColors: Int = 256): IntArray {
        val samples = mutableListOf<IntArray>()
        val totalPixels = allPixels.sumOf { it.size }
        if (totalPixels == 0) return intArrayOf(0xFF000000.toInt())

        // Sampling merata dari semua frame supaya palet representatif, dengan ceiling division
        // agar step tidak pernah jatuh ke 0 (mencegah out-of-bounds saat totalPixels sangat kecil).
        val step = max(1, (totalPixels + MAX_SAMPLE_PIXELS - 1) / MAX_SAMPLE_PIXELS)
        var counter = 0
        for (frame in allPixels) {
            for (px in frame) {
                if (counter % step == 0) {
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    samples.add(intArrayOf(r, g, b))
                }
                counter++
            }
        }
        if (samples.isEmpty()) return intArrayOf(0xFF000000.toInt())

        val boxes = mutableListOf(Box(samples))
        while (boxes.size < maxColors) {
            val splitIndex = pickBoxToSplit(boxes) ?: break
            val box = boxes[splitIndex]
            if (box.pixels.size < 2) break
            val (a, b) = splitBox(box)
            boxes.removeAt(splitIndex)
            boxes.add(a)
            boxes.add(b)
        }

        return boxes.map { box -> averageColor(box.pixels) }.toIntArray()
    }

    private fun pickBoxToSplit(boxes: List<Box>): Int? {
        var bestIndex: Int? = null
        var bestRange = -1
        for (i in boxes.indices) {
            if (boxes[i].pixels.size < 2) continue
            val range = channelRange(boxes[i].pixels)
            if (range > bestRange) {
                bestRange = range
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun channelRange(pixels: List<IntArray>): Int {
        var rMin = 255; var rMax = 0
        var gMin = 255; var gMax = 0
        var bMin = 255; var bMax = 0
        for (p in pixels) {
            rMin = min(rMin, p[0]); rMax = max(rMax, p[0])
            gMin = min(gMin, p[1]); gMax = max(gMax, p[1])
            bMin = min(bMin, p[2]); bMax = max(bMax, p[2])
        }
        return max(rMax - rMin, max(gMax - gMin, bMax - bMin))
    }

    private fun splitBox(box: Box): Pair<Box, Box> {
        var rMin = 255; var rMax = 0
        var gMin = 255; var gMax = 0
        var bMin = 255; var bMax = 0
        for (p in box.pixels) {
            rMin = min(rMin, p[0]); rMax = max(rMax, p[0])
            gMin = min(gMin, p[1]); gMax = max(gMax, p[1])
            bMin = min(bMin, p[2]); bMax = max(bMax, p[2])
        }
        val rRange = rMax - rMin
        val gRange = gMax - gMin
        val bRange = bMax - bMin
        val channel = when (max(rRange, max(gRange, bRange))) {
            rRange -> 0
            gRange -> 1
            else -> 2
        }
        val sorted = box.pixels.sortedBy { it[channel] }
        val mid = sorted.size / 2
        return Box(sorted.subList(0, mid).toMutableList()) to
            Box(sorted.subList(mid, sorted.size).toMutableList())
    }

    private fun averageColor(pixels: List<IntArray>): Int {
        var r = 0L; var g = 0L; var b = 0L
        for (p in pixels) { r += p[0]; g += p[1]; b += p[2] }
        val n = pixels.size
        val ar = (r / n).toInt().coerceIn(0, 255)
        val ag = (g / n).toInt().coerceIn(0, 255)
        val ab = (b / n).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ar shl 16) or (ag shl 8) or ab
    }

    /** Memetakan setiap piksel ARGB ke indeks warna terdekat pada [palette] (jarak Euclidean RGB). */
    fun mapToIndices(pixels: IntArray, palette: IntArray): ByteArray {
        val out = ByteArray(pixels.size)
        // Cache hasil pencarian warna -> indeks supaya frame dengan area datar tidak berulang kali
        // menghitung jarak ke seluruh palet.
        val cache = HashMap<Int, Byte>()
        for (i in pixels.indices) {
            val color = pixels[i] and 0x00FFFFFF
            val cached = cache[color]
            if (cached != null) {
                out[i] = cached
            } else {
                val idx = nearestIndex(pixels[i], palette)
                val idxByte = idx.toByte()
                cache[color] = idxByte
                out[i] = idxByte
            }
        }
        return out
    }

    private fun nearestIndex(pixel: Int, palette: IntArray): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        var bestIdx = 0
        var bestDist = Int.MAX_VALUE
        for (i in palette.indices) {
            val pr = (palette[i] shr 16) and 0xFF
            val pg = (palette[i] shr 8) and 0xFF
            val pb = palette[i] and 0xFF
            val dr = r - pr; val dg = g - pg; val db = b - pb
            val dist = dr * dr + dg * dg + db * db
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = i
                if (dist == 0) break
            }
        }
        return bestIdx
    }
}
