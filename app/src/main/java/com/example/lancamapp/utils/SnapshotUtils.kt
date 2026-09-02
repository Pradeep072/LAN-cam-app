package com.example.lancamapp.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SnapshotUtils {

    fun captureSurfaceSnapshot(
        surfaceView: SurfaceView,
        context: Context,
        cameraName: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (surfaceView.width <= 0 || surfaceView.height <= 0) {
            onError("Camera feed not ready for snapshot")
            return
        }

        val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(
                surfaceView,
                bitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        val savedFileName = saveBitmapToGallery(context, bitmap, cameraName)
                        if (savedFileName != null) {
                            onSuccess(savedFileName)
                        } else {
                            onError("Failed to save snapshot to storage")
                        }
                    } else {
                        onError("Snapshot capture failed (code $copyResult)")
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            Log.e("SnapshotUtils", "PixelCopy error: $e")
            onError("Snapshot error: ${e.message}")
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, cameraName: String): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanName = cameraName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "LANCAM_${cleanName}_$timeStamp.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LAN-cam")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            fileName
        } catch (e: Exception) {
            Log.e("SnapshotUtils", "Error saving bitmap to gallery: $e")
            null
        }
    }
}
