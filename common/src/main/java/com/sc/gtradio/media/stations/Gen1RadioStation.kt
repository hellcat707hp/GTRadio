package com.sc.gtradio.media.stations

import android.content.Context
import android.icu.util.Calendar
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.documentfile.provider.DocumentFile
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.sc.gtradio.media.GTRadioPlayer
import java.util.*
import kotlin.math.abs

class Gen1RadioStation(
    override val stationGroupId: String,
    override val mediaId: String,
    override val mediaItem: MediaBrowserCompat.MediaItem,
    baseStationDoc: DocumentFile,
    private val player: GTRadioPlayer,
    context: Context) : RadioStation {

    override val metadata: MediaMetadataCompat =
        MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaItem.mediaId)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, mediaItem.description.title as String?)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, mediaItem.description.title as String?)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, mediaItem.description.iconBitmap)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, mediaItem.description.description as String?)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, mediaItem.description.iconBitmap)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, mediaItem.description.subtitle as String?)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, mediaItem.description.subtitle as String?)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, mediaItem.description.subtitle as String?)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, mediaItem.description.subtitle as String?)
            .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, 1)
            .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, 1)
            .putLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER, 1)
            .build()

    override var stationName: String = ""

    //These fields are irrelevant to these stations
    override var adsEnabled: Boolean = true
    override var weatherChatterEnabled: Boolean = false
    override var newsReportsEnabled: Boolean = true

    private var _playing = false

    //Properties used to calculate elapsed time and current location in the track
    private var durationSec: Int = 0
    private var lastStoppedDurationSec: Int = 0
    private var lastStoppedTime: Calendar? = null
    private var activeListener: Player.Listener? = null

    private val mediaSource: MediaSource?

    init {
        stationName = getStationName(baseStationDoc)
        val files = baseStationDoc.listFiles()
        val radioFileUri = files.firstOrNull { x -> x.name?.uppercase()?.contains("LOGO") == false }?.uri
        mediaSource = if (radioFileUri != null) {
            val media = MediaItem.fromUri(radioFileUri)
            ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(media)
        } else {
            null
        }
    }

    private fun getStationName(stationFolder: DocumentFile): String {
        return stationFolder.name ?: ""
    }

    override fun stop() {
        if (mediaSource == null) {
            return
        }

        _playing = false
        lastStoppedDurationSec = (player.getCurrentTrackPosition() / 1000).toInt()
        lastStoppedTime = Calendar.getInstance()
        player.stop()
    }

    override fun play() {
        if (mediaSource == null) {
            return
        }

        _playing = true
        player.radioPlaybackState = PlaybackStateCompat.STATE_PLAYING

        /* Have to listen to some timeline changes just to get track duration.
         * MediaMetadataRetriever is dated and throws exceptions when using Uri, and using MetadataRetriever is also an async operation where
         *      we can save time by just letting the player prepare the source like it has to anyway (instead of us making MetadataRetriever do it also).
         *      Also note, the GTRadioPlayer.currentTrackDuration field only gets filled once the timeline is built which is async and not immediately following .prepare
         */
        if (durationSec == 0) {
            activeListener = object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    if (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
                        if (durationSec != 0) {
                            player.removeListener(activeListener!!)
                            return
                        }
                        val stuff = timeline.getPeriod(0, Timeline.Period())
                        if (stuff.durationMs > 0) {
                            durationSec = (stuff.durationMs / 1000).toInt()
                            player.removeListener(activeListener!!)
                            seekAndPlayFile()
                        }
                    }
                }
            }
            player.addListener(activeListener!!)
        }

        // Prepare the player.
        player.setMediaSource(mediaSource)
        player.playWhenReady = false
        player.prepare()
        if (durationSec != 0) {
            //Already know the duration so just play, otherwise we must wait for the duration to be calculated in the timeline
            seekAndPlayFile()
        }

    }

    private fun seekAndPlayFile() {
        val seekPos = getSeekPosition()
        if (seekPos > 0L) {
            player.seekTo(seekPos)
        }
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.play()
    }

    private fun getSeekPosition(): Long {
        val rng = Random()
        if (lastStoppedTime == null) {
            //Pick a random starting point
            return (rng.nextInt(durationSec) * 1000).toLong()
        } else {
            //Calculate position
            val now = Calendar.getInstance()
            val elapsedTimeSec = ((now.timeInMillis - lastStoppedTime!!.timeInMillis) / 1000).toInt()
            val divisions = (elapsedTimeSec.toFloat() / durationSec)
            val multiplier = abs(divisions - divisions.toInt())
            val initialStartPositionSec = (durationSec * multiplier) + lastStoppedDurationSec
            val finalPosition = if (initialStartPositionSec > durationSec) {
                initialStartPositionSec - durationSec
            } else {
                initialStartPositionSec
            }
            return (finalPosition * 1000).toLong()
        }


    }

}