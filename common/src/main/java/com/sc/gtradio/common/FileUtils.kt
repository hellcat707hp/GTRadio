package com.sc.gtradio.common

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.Closeable

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

   // Util method to close a closeable
   private fun closeQuietly(closeable: Closeable?) {
       if (closeable != null) {
           try {
               closeable.close()
           } catch (re: RuntimeException) {
               throw re
           } catch (ignore: Exception) {
               // ignore exception
           }
       }
   }

    /**
     * Returns an array of the [SimpleFileInfo] contained in the directory represented by this file.
     * The [SimpleFileInfo] class is designed to provide properties (primarily .name) that are pre-retrieved for performance benefit.
     *
     * Most of the functionality here is based on TreeDocumentFile from the Android source
     */
    fun DocumentFile.listSimpleFiles(context: Context): Array<SimpleFileInfo> {
        val resolver: ContentResolver = context.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            this.uri,
            DocumentsContract.getDocumentId(this.uri)
        )
        val results: ArrayList<SimpleFileInfo> = ArrayList()
        var c: Cursor? = null
        try {
            c = resolver.query(
                childrenUri, arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ), null, null, null
            )
            while (c!!.moveToNext()) {
                val documentId = c.getString(0)
                val name = c.getString(1)
                val mime = c.getString(2)
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                    this.uri,
                    documentId
                )
                results.add(SimpleFileInfo(documentUri, name, documentId, mime == DocumentsContract.Document.MIME_TYPE_DIR ))
            }
        }  finally {
            closeQuietly(c)
        }
        return results.toTypedArray()
    }
}



/**
 * A class housing core and simplified file properties including uri, name, and document id
 */
class SimpleFileInfo(val uri: Uri, val name: String, val id: String, val isDirectory: Boolean)