package com.gifmaker.app

import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Penulis file GIF89a animasi 100% offline: header, Netscape looping extension,
 * Graphic Control Extension per frame, dan kompresi LZW manual (varian GIF).
 * Tidak memakai library eksternal — murni manipulasi byte.
 */
class GifEncoder(outputStream: OutputStream) {

    private val out = BufferedOutputStream(outputStream)
    private var started = false
    private var width = 0
    private var height = 0

    @Throws(IOException::class)
    fun start(width: Int, height: Int, globalPalette: IntArray) {
        this.width = width
        this.height = height
        started = true

        out.write('G'.code); out.write('I'.code); out.write('F'.code)
        out.write('8'.code); out.write('9'.code); out.write('a'.code)

        writeShort(width)
        writeShort(height)

        val colorCount = nextPow2(globalPalette.size).coerceIn(2, 256)
        val bitsPerPixel = log2(colorCount)
        // Packed field: global color table flag=1, color resolution=7, sort=0, size=bitsPerPixel-1
        val packed = 0x80 or (0x70) or (bitsPerPixel - 1)
        out.write(packed)
        out.write(0) // background color index
        out.write(0) // pixel aspect ratio

        writeColorTable(globalPalette, colorCount)

        // Netscape looping extension: loop selamanya
        out.write(0x21); out.write(0xFF); out.write(11)
        writeAscii("NETSCAPE2.0")
        out.write(3); out.write(1); writeShort(0); out.write(0)
    }

    @Throws(IOException::class)
    fun writeFrame(indices: ByteArray, palette: IntArray, delayCentiseconds: Int) {
        check(started) { "start() harus dipanggil sebelum writeFrame()" }

        // Graphic Control Extension
        out.write(0x21); out.write(0xF9); out.write(4)
        out.write(0x00) // no transparency, no disposal specified (biar frame sebelumnya tetap sbg background)
        writeShort(delayCentiseconds.coerceIn(1, 65535))
        out.write(0) // transparent color index (tidak dipakai)
        out.write(0) // block terminator

        // Image Descriptor
        out.write(0x2C)
        writeShort(0); writeShort(0) // left, top
        writeShort(width); writeShort(height)
        out.write(0x00) // tanpa local color table -> pakai global

        val colorCount = nextPow2(palette.size).coerceIn(2, 256)
        val minCodeSize = log2(colorCount).coerceAtLeast(2)
        out.write(minCodeSize)

        val compressed = LzwEncoder.encode(indices, minCodeSize)
        writeBlocks(compressed)
    }

    @Throws(IOException::class)
    fun finish() {
        out.write(0x3B) // trailer
        out.flush()
    }

    private fun writeColorTable(palette: IntArray, size: Int) {
        for (i in 0 until size) {
            val color = if (i < palette.size) palette[i] else 0
            out.write((color shr 16) and 0xFF)
            out.write((color shr 8) and 0xFF)
            out.write(color and 0xFF)
        }
    }

    private fun writeBlocks(data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val blockSize = minOf(255, data.size - offset)
            out.write(blockSize)
            out.write(data, offset, blockSize)
            offset += blockSize
        }
        out.write(0) // block terminator
    }

    private fun writeShort(value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private fun writeAscii(s: String) {
        for (c in s) out.write(c.code)
    }

    private fun nextPow2(n: Int): Int {
        var v = 1
        while (v < n) v = v shl 1
        return v
    }

    private fun log2(n: Int): Int {
        var v = n
        var bits = 0
        while (v > 1) { v = v shr 1; bits++ }
        return bits.coerceAtLeast(1)
    }
}

/** Implementasi LZW sesuai varian yang dipakai format GIF (kode clear/end-of-info, lebar kode variabel). */
private object LzwEncoder {

    fun encode(indices: ByteArray, minCodeSize: Int): ByteArray {
        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        var nextCode = endCode + 1
        var codeSize = minCodeSize + 1

        val dict = HashMap<String, Int>()
        fun resetDict() {
            dict.clear()
            for (i in 0 until clearCode) dict[i.toString()] = i
            nextCode = endCode + 1
            codeSize = minCodeSize + 1
        }
        resetDict()

        val bitWriter = BitWriter()
        bitWriter.writeBits(clearCode, codeSize)

        var current = ""
        for (b in indices) {
            val symbol = (b.toInt() and 0xFF).toString()
            val combined = if (current.isEmpty()) symbol else "$current,$symbol"
            if (dict.containsKey(combined)) {
                current = combined
            } else {
                bitWriter.writeBits(dict[current] ?: clearCode, codeSize)
                if (nextCode < 4096) {
                    dict[combined] = nextCode
                    nextCode++
                    if (nextCode > (1 shl codeSize) && codeSize < 12) {
                        codeSize++
                    }
                } else {
                    bitWriter.writeBits(clearCode, codeSize)
                    resetDict()
                }
                current = symbol
            }
        }
        if (current.isNotEmpty()) {
            bitWriter.writeBits(dict[current] ?: clearCode, codeSize)
        }
        bitWriter.writeBits(endCode, codeSize)
        bitWriter.flush()
        return bitWriter.toByteArray()
    }

    /** Dictionary di atas memakai key String "idx,idx,idx" agar sederhana & tanpa bug hashing kompleks. */
    private class BitWriter {
        private val bytes = ArrayList<Byte>()
        private var currentByte = 0
        private var bitCount = 0

        fun writeBits(value: Int, numBits: Int) {
            var v = value
            var remaining = numBits
            while (remaining > 0) {
                val bitsToWrite = minOf(8 - bitCount, remaining)
                val mask = (1 shl bitsToWrite) - 1
                currentByte = currentByte or ((v and mask) shl bitCount)
                bitCount += bitsToWrite
                v = v shr bitsToWrite
                remaining -= bitsToWrite
                if (bitCount == 8) {
                    bytes.add(currentByte.toByte())
                    currentByte = 0
                    bitCount = 0
                }
            }
        }

        fun flush() {
            if (bitCount > 0) {
                bytes.add(currentByte.toByte())
                currentByte = 0
                bitCount = 0
            }
        }

        fun toByteArray(): ByteArray = bytes.toByteArray()
    }
}
