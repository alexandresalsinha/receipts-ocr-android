package com.example.receiptsocr.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Vertically glues a sequence of close-up receipt-segment photos into one tall composite JPEG,
 * so a long receipt can be captured in overlapping, legible close-ups instead of one far-away
 * shot. This is simple top-to-bottom stacking (segments are captured in order, in a fixed
 * layout), not feature-matched panorama stitching.
 */
object ReceiptImageGlue {

    const val MAX_SEGMENTS = 10
    private const val TARGET_WIDTH_PX = 1080
    private const val MAX_TOTAL_HEIGHT_PX = 18_000
    private const val JPEG_QUALITY = 92

    /**
     * Layout for stacking [segmentCount] segments of the given (width, height) pairs into one
     * composite [targetWidth] wide, capped at [maxSegments] segments and [maxTotalHeight] tall.
     * Pulled out of the Bitmap-decoding code so it can be unit-tested without android.graphics.
     */
    internal data class CompositeLayout(
        val scaledHeights: List<Int>,
        val totalHeight: Int,
        val usedSegments: Int
    )

    internal fun computeCompositeLayout(
        segmentDimensions: List<Pair<Int, Int>>,
        targetWidth: Int,
        maxSegments: Int,
        maxTotalHeight: Int
    ): CompositeLayout {
        val scaledHeights = mutableListOf<Int>()
        var totalHeight = 0
        var used = 0
        for ((width, height) in segmentDimensions) {
            if (used >= maxSegments) break
            val scaledHeight = if (width <= 0) 0 else ((height.toLong() * targetWidth) / width).toInt().coerceAtLeast(1)
            if (totalHeight + scaledHeight > maxTotalHeight) break
            scaledHeights += scaledHeight
            totalHeight += scaledHeight
            used++
        }
        return CompositeLayout(scaledHeights, totalHeight, used)
    }

    /**
     * Reads [segmentFiles] top-to-bottom, scales each to [TARGET_WIDTH_PX] wide (preserving
     * aspect ratio), and stacks them into one JPEG. Decodes and scales one segment at a time so
     * at most one segment bitmap plus the growing composite are ever resident in memory.
     */
    fun glueSegments(segmentFiles: List<File>): ByteArray {
        if (segmentFiles.isEmpty()) throw IOException("No captured photos to combine.")

        try {
            val bounds = segmentFiles.map { file -> decodeBounds(file) }
            val layout = computeCompositeLayout(bounds, TARGET_WIDTH_PX, MAX_SEGMENTS, MAX_TOTAL_HEIGHT_PX)
            if (layout.totalHeight <= 0) throw IOException("Could not read the captured photos.")

            val composite = Bitmap.createBitmap(TARGET_WIDTH_PX, layout.totalHeight, Bitmap.Config.RGB_565)
            val canvas = Canvas(composite)

            var y = 0f
            for (index in 0 until layout.usedSegments) {
                val scaledHeight = layout.scaledHeights[index]
                val segment = decodeScaled(segmentFiles[index], TARGET_WIDTH_PX, scaledHeight)
                canvas.drawBitmap(segment, 0f, y, null)
                segment.recycle()
                y += scaledHeight
            }

            val output = ByteArrayOutputStream()
            composite.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            composite.recycle()
            return output.toByteArray()
        } catch (e: OutOfMemoryError) {
            throw IOException("Ran out of memory combining the captured photos. Try capturing fewer segments.", e)
        }
    }

    private fun decodeBounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw IOException("Could not read a captured photo: ${file.name}")
        }
        return options.outWidth to options.outHeight
    }

    private fun decodeScaled(file: File, targetWidth: Int, targetHeight: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetWidth)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val sampled = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            ?: throw IOException("Could not decode a captured photo: ${file.name}")

        if (sampled.width == targetWidth && sampled.height == targetHeight) return sampled
        val scaled = Bitmap.createScaledBitmap(sampled, targetWidth, targetHeight, true)
        if (scaled != sampled) sampled.recycle()
        return scaled
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetWidth: Int): Int {
        var sampleSize = 1
        var currentWidth = width
        while (currentWidth / 2 >= targetWidth) {
            currentWidth /= 2
            sampleSize *= 2
        }
        return sampleSize
    }
}
