package com.minimal.carlauncher.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

object BitmapHelper {

    fun safeDrawableToImageBitmap(drawable: Drawable?, width: Int, height: Int): ImageBitmap? {
        if (drawable == null || width <= 0 || height <= 0) return null

        return try {
            if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
                drawable.bitmap.asImageBitmap()
            } else {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }
}
