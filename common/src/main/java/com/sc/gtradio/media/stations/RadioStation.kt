package com.sc.gtradio.media.stations

interface RadioStation {
    val mediaId: String
    val stationGroupId: String
    var stationName: String

    var adsEnabled: Boolean
    var weatherChatterEnabled: Boolean

    fun play()
    fun stop()
}