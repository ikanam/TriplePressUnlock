package com.jarman.triplepressunlock

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlin.math.max
import kotlin.math.roundToInt

internal object LockBackgroundImageLoader {
    private const val TAG = "LockBackground"

    fun load(context: Context, uriValue: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (targetWidth <= 0 || targetHeight <= 0) return null
        val uri = Uri.parse(uriValue)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                loadWithImageDecoder(context, uri, targetWidth, targetHeight)
            } else {
                loadWithBitmapFactory(context, uri, targetWidth, targetHeight)
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to load custom lock background", exception)
            null
        }
    }

    @TargetApi(Build.VERSION_CODES.P)
    @SuppressLint("UseRequiresApi")
    private fun loadWithImageDecoder(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val target = centerCropDecodeSize(
                info.size.width,
                info.size.height,
                targetWidth,
                targetHeight,
            )
            decoder.setTargetSize(target.first, target.second)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private fun loadWithBitmapFactory(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                targetWidth,
                targetHeight,
            )
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    internal fun centerCropDecodeSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Pair<Int, Int> {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return 1 to 1
        }
        val scale = max(
            targetWidth.toFloat() / sourceWidth,
            targetHeight.toFloat() / sourceHeight,
        ).coerceAtMost(1f)
        return max(1, (sourceWidth * scale).roundToInt()) to
            max(1, (sourceHeight * scale).roundToInt())
    }

    internal fun calculateInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return 1
        }
        val target = centerCropDecodeSize(sourceWidth, sourceHeight, targetWidth, targetHeight)
        var sampleSize = 1
        while (sourceWidth / (sampleSize * 2) >= target.first &&
            sourceHeight / (sampleSize * 2) >= target.second
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
