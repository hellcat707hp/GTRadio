package com.sc.gtradio.media.stations

import android.content.Context
import android.icu.util.Calendar
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import androidx.documentfile.provider.DocumentFile
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.sc.gtradio.media.GTRadioPlayer
import java.util.*

class Gen1RadioStation(
    override val stationGroupId: String,
    override val mediaId: String,
    baseStationDoc: DocumentFile,
    private val player: GTRadioPlayer,
    private val context: Context) : RadioStation {

    override var stationName: String = ""

    //These fields are irrelevant to these stations
    override var adsEnabled: Boolean = true
    override var weatherChatterEnabled: Boolean = false

    private var _playing = false
    private var radioFileUri: Uri?

    private var lastStoppedDurationMs: Long = 0L
    private var lastStoppedTime: Calendar? = null

    init {
        stationName = getStationName(baseStationDoc)
        val files = baseStationDoc.listFiles()
        radioFileUri = files.firstOrNull { x -> x.name?.contains("logo") == false }?.uri
    }

    private fun getStationName(stationFolder: DocumentFile): String {
        return stationFolder.name ?: ""
    }

    override fun stop() {
        if (radioFileUri == null) {
            return
        }

        _playing = false
        lastStoppedDurationMs = player.getCurrentTrackPosition()
        lastStoppedTime = Calendar.getInstance()
        player.stop()
    }

    override fun play() {
        if (radioFileUri == null) {
            return
        }

        _playing = true
        player.radioPlaybackState = PlaybackStateCompat.STATE_PLAYING

        // Set the media item to be played.
        val mediaItem = MediaItem.fromUri(radioFileUri!!)
        val src = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(mediaItem)

        // Prepare the player.
        player.setMediaSource(src)
        player.playWhenReady = false
        player.prepare()
        player.seekTo(getSeekPosition())
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.play()
    }

    private fun getSeekPosition(): Long {
        val rng = Random()
        //For now, pick a random number inside 10 minutes
        return (rng.nextInt(61) * 10000).toLong()
    }

}