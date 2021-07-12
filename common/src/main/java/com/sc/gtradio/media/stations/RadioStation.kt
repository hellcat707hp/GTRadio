package com.sc.gtradio.media.stations

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat

interface RadioStation {
    val mediaId: String
    val mediaItem: MediaBrowserCompat.MediaItem
    val stationGroupId: String

    val metadata: MediaMetadataCompat

    var stationName: String
    var adsEnabled: Boolean
    var weatherChatterEnabled: Boolean

    fun play()
    fun stop()
}