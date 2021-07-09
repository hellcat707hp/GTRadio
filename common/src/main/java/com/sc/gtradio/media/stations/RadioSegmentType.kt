package com.sc.gtradio.media.stations

enum class RadioSegmentType(val value: Int) {
    None(0),
    Commercial(1),
    Announcer(2),
    DJChatter(3),
    Song(4);

    companion object {
        fun fromInt(value: Int) = values().first { it.value == value }
    }
}
