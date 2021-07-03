package com.sc.gtradio.utils

import android.content.ComponentName
import android.content.Context
import com.sc.gtradio.media.GTRadioMusicService
import com.sc.gtradio.media.MusicServiceConnection

/**
 * Static methods used to inject classes needed for various Activities and Fragments.
 */
object InjectorUtils {
    fun provideMusicServiceConnection(context: Context): MusicServiceConnection {
        return MusicServiceConnection.getInstance(
            context.applicationContext,
            ComponentName(context, GTRadioMusicService::class.java)
        )
    }
}