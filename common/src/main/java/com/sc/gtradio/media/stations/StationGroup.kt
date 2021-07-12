package com.sc.gtradio.media.stations

import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.documentfile.provider.DocumentFile
import com.sc.gtradio.common.FileUtils
import org.json.JSONObject
import java.io.*


class StationGroup(val folderDoc: DocumentFile, private val context: Context) {

    var mediaItem: MediaBrowserCompat.MediaItem
    var stationList: ArrayList<MediaBrowserCompat.MediaItem>
    var generation: Int = 0
    lateinit var groupName: String

    init {
        setupDataFromJson(folderDoc)
        mediaItem = getMediaItem(folderDoc, groupName)
        stationList = getStationsList(folderDoc)
    }

    private fun getMediaItem(folderDoc: DocumentFile, groupName: String): MediaBrowserCompat.MediaItem {
        val logoUri = folderDoc.listFiles().find { x -> x.isFile && x.name?.contains("logo") == true }?.uri
        val logoBitmap = FileUtils.getBitmapFromUri(context.contentResolver, logoUri)

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, folderDoc.uri.toString())
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, groupName)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, logoBitmap)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, groupName)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "")
            .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, logoBitmap)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, "")
            .build()
        return MediaBrowserCompat.MediaItem(
            metadata.description,
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun setupDataFromJson(folderDoc: DocumentFile) {
        val jsonInfo = folderDoc.findFile("stationGroupInfo.json") ?: return

        var jsonString = ""
        try {
            val stringBuilder = StringBuilder()
            context.contentResolver.openInputStream(jsonInfo.uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line)
                        line = reader.readLine()
                    }
                }
            }
            jsonString = stringBuilder.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val jsonObj = JSONObject(jsonString)
        generation = try {
            jsonObj.getInt("generation")
        } catch (e: Exception) {
            //No valid generation
            0
        }
        groupName = try {
            jsonObj.getString("name")
        } catch (e: Exception) {
            folderDoc.name ?: ""
        }
    }

    private fun getStationsList(folderDoc: DocumentFile): java.util.ArrayList<MediaBrowserCompat.MediaItem> {
        val list = java.util.ArrayList<MediaBrowserCompat.MediaItem>()

        val subDirs = folderDoc.listFiles().filter { x -> x.isDirectory }
        for (subDir in subDirs) {
            //Skip the Adverts folder since its not a standalone station
            if (generation == 2 && subDir.uri.lastPathSegment?.contains("Adverts") == true) { continue }

            val stationName = subDir.name ?: ""
            val logoUri = subDir.listFiles().find { x -> x.isFile && x.name?.contains("logo") == true }?.uri
            val logoBitmap = FileUtils.getBitmapFromUri(context.contentResolver, logoUri)

            if (stationName.isNotBlank()) {
                val metadata = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, subDir.uri.toString())
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, stationName)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, logoBitmap)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, stationName)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "")
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, logoBitmap)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, "")
                    .build()
                val item = MediaBrowserCompat.MediaItem(metadata.description, FLAG_PLAYABLE)
                list.add(item)
            }
        }
        return list
    }

    fun getStationIndex(stationMediaId: String): Int {
        return stationList.indexOfFirst { x -> x.mediaId == stationMediaId }
    }

    fun getPlaybackActionsForStation(stationIndex: Int): Long {
        return getPlaybackActions((stationIndex < stationList.lastIndex), (stationIndex > 0))
    }

    private fun getPlaybackActions(hasNext: Boolean = false, hasPrevious: Boolean = false): Long {
        if (hasNext && hasPrevious) {
            return PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        } else if (hasNext) {
            return PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        } else if (hasPrevious) {
            return PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        }
        return PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
    }
}