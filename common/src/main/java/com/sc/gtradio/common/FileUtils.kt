package com.sc.gtradio.common

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

object FileUtils {
    fun getBitmapFromUri(contentResolver: ContentResolver, fileUri: Uri?): Bitmap? {
        if (fileUri == null) {
            return null
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, fileUri))
        } else {
            MediaStore.Images.Media.getBitmap(contentResolver, fileUri)
        }
    }
}