package com.sc.gtradio.media

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaMetadataCompat
import androidx.media.MediaBrowserServiceCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import java.io.File
import java.io.FileFilter
import java.util.ArrayList

open class GTRadioMusicService : MediaBrowserServiceCompat() {
    private var stationsList: ArrayList<MediaItem> = ArrayList()
    private val sharedPrefListener = GTRadioOnSharedPrefChange()

    //Setup a listener for settings changes
    private inner class GTRadioOnSharedPrefChange : SharedPreferences.OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            if (prefs != null) {
                when(key) {
                    resources.getString(R.string.radio_folders_uri_key) -> {
                        //Update stations list
                        this@GTRadioMusicService.stationsList = this@GTRadioMusicService.getStationsList()
                    }
                    resources.getString(R.string.ads_enabled_preference_key)  -> {
                        //Update ads enabled
                        if (this@GTRadioMusicService.activeStation != null) {
                            val newValue = prefs.getBoolean(key, false)
                            this@GTRadioMusicService.activeStation?.adsEnabled = newValue
                        }
                    }
                    resources.getString(R.string.weather_chatter_enabled_preference_key) -> {
                        //Update weather chatter enabled
                        if (this@GTRadioMusicService.activeStation != null) {
                            val newValue = prefs.getBoolean(key, false)
                            this@GTRadioMusicService.activeStation?.weatherChatterEnabled = newValue
                        }
                    }
                }
            }
        }
    }

    private lateinit var session: MediaSessionCompat

    private var isForegroundService = false

    private lateinit var notificationManager: GTRadioNotificationManager
    private val gtrAudioAttributes = AudioAttributes.Builder()
        .setContentType(C.CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()
    private val playerListener = PlayerEventListener()


    //Exoplayer will handle audio focus
    private val exoPlayer: ExoPlayer by lazy {
        SimpleExoPlayer.Builder(this).build().apply {
            setAudioAttributes(gtrAudioAttributes, true)
            setHandleAudioBecomingNoisy(true)
        }
    }

    //Our own custom player will wrap ExoPlayer in order to provide more accurate playback status as well as other fields for our faux-stream use case
    private val radioPlayer: GTRadioPlayer by lazy {
        GTRadioPlayer(exoPlayer).apply {
            addListener(playerListener)
        }
    }

    private var activeStation: RadioStation? = null

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (activeStation == null) {
                //Build the station to play
                val stationToPlay = buildRadioStation()
                if (stationToPlay == null) {
                    //Must be no stations available
                    return
                } else {
                    activeStation = stationToPlay
                }
            }

            //Get and set the playback actions available when on this station
            val stationIndex = getStationIndex(activeStation!!.mediaId)
            radioPlayer.nextEnabled = stationIndex < stationsList.lastIndex
            radioPlayer.previousEnabled = stationIndex != 0
            val playbackActions = getPlaybackActionsForStation(stationIndex)

            activeStation?.play()
            val playbackState = PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1F)
                .setActions(playbackActions)
                .build()
            session.setPlaybackState(playbackState)
            val media = stationsList.find { x -> return@find x.mediaId == activeStation?.mediaId }!!
            val metadata =  MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, media.mediaId)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, media.description.title as String?)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, media.description.iconBitmap)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, media.description.description as String?)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, media.description.iconBitmap)
                .build()
            session.setMetadata(metadata)
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            if (mediaId.isNullOrBlank()) {
                return
            }

            activeStation = buildRadioStation(mediaId)
            if (activeStation == null) {
                //Unable to find the station to play
                return
            }

            //Get and set the playback actions available when on this station
            val stationIndex = getStationIndex(activeStation!!.mediaId)
            radioPlayer.nextEnabled = stationIndex < stationsList.lastIndex
            radioPlayer.previousEnabled = stationIndex != 0
            val playbackActions = getPlaybackActionsForStation(stationIndex)

            activeStation!!.play()
            val playbackState = PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1F)
                .setActions(playbackActions)
                .build()
            session.setPlaybackState(playbackState)
            val media = stationsList.find { x -> return@find x.mediaId == mediaId }!!
            val metadata =  MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, media.mediaId)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, media.description.title as String?)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, media.description.iconBitmap)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, media.description.description as String?)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, media.description.iconBitmap)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, "")
                .build()
            session.setMetadata(metadata)
        }

        override fun onPause() {
            if (activeStation == null) {
                return
            }

            //Get and set the playback actions available when on this station
            val stationIndex = getStationIndex(activeStation!!.mediaId)
            radioPlayer.nextEnabled = stationIndex < stationsList.lastIndex
            radioPlayer.previousEnabled = stationIndex != 0
            val playbackActions = getPlaybackActionsForStation(stationIndex)

            activeStation?.stop()
            val playbackState = PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1F)
                .setActions(playbackActions)
                .build()
            session.setPlaybackState(playbackState)
        }

        override fun onStop() {
            if (activeStation == null) {
                return
            }

            //Get and set the playback actions available when on this station
            val stationIndex = getStationIndex(activeStation!!.mediaId)
            radioPlayer.nextEnabled = stationIndex < stationsList.lastIndex
            radioPlayer.previousEnabled = stationIndex != 0
            val playbackActions = getPlaybackActionsForStation(stationIndex)

            activeStation?.stop()
            val playbackState = PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1F)
                .setActions(playbackActions)
                .build()
            session.setPlaybackState(playbackState)
        }

        override fun onSkipToNext() {
            if (activeStation == null) {
                //No active station, so find the first one to play
                val stationToPlay = buildRadioStation()
                if (stationToPlay == null) {
                    //Must be no stations available
                    return
                } else {
                    activeStation = stationToPlay
                }
            } else {
                //There is an active station, find its index and move to the next one if available
                val currentStationIndex = getStationIndex(activeStation!!.mediaId)
                var nextStationIndex = currentStationIndex + 1
                if (nextStationIndex >= stationsList.size) {
                    //Loop back to start
                    nextStationIndex = 0
                }
                activeStation = buildRadioStation(stationsList[nextStationIndex].mediaId)
            }

            //Get and set the playback actions available when on this station
            val stationIndex = getStationIndex(activeStation!!.mediaId)
            radioPlayer.nextEnabled = stationIndex < stationsList.lastIndex
            radioPlayer.previousEnabled = stationIndex != 0
            val playbackActions = getPlaybackActionsForStation(stationIndex)

            activeStation?.play()
            val playbackState = PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1F)
                .setActions(playbackActions)
                .build()
            session.setPlaybackState(playbackState)
            val media = stationsList.find { x -> return@find x.mediaId == activeStation?.mediaId }!!
            val metadata =  MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, media.mediaId)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, media.description.title as String?)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, media.description.iconBitmap)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, media.description.description as String?)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, media.description.iconBitmap)
                .build()
            session.setMetadata(metadata)
        }

        override fun onSkipToPrevious() {
            if (activeStation == null) {
                //No active station, so find the first one to play
                val stationToPlay = buildRadioStation()
                if (stationToPlay == null) {
                    //Must be no stations available
                    return
                } else {
                    activeStation = stationToPlay
                }
            } else {
                //There is an active station, find its index and move to the previous one if available
                val currentStationIndex = getStationIndex(activeStation!!.mediaId)
                var prevStationIndex = currentStationIndex - 1
                if (prevStationIndex < 0) {
                    //Loop back to end
                    prevStationIndex = stationsList.lastIndex
                }
                activeStation = buildRadioStation(stationsList[prevStationIndex].mediaId)
            }

            //Get and set the playback actions available when on this station
            val stationIndex = getStationIndex(activeStation!!.mediaId)
            radioPlayer.nextEnabled = stationIndex < stationsList.lastIndex
            radioPlayer.previousEnabled = stationIndex != 0
            val playbackActions = getPlaybackActionsForStation(stationIndex)

            activeStation?.play()
            val playbackState = PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1F)
                .setActions(playbackActions)
                .build()
            session.setPlaybackState(playbackState)
            val media = stationsList.find { x -> return@find x.mediaId == activeStation?.mediaId }!!
            val metadata =  MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, media.mediaId)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, media.description.title as String?)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, media.description.iconBitmap)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, media.description.description as String?)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, media.description.iconBitmap)
                .build()
            session.setMetadata(metadata)
        }

    }

    override fun onCreate() {
        super.onCreate()

        //Subscribe to any SharedPref changes for things like the base folder, ads, and weather chatter
        val sharedPref = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)
        sharedPref.registerOnSharedPreferenceChangeListener(sharedPrefListener)

        // Build a PendingIntent that can be used to launch the UI.
        val sessionActivityPendingIntent =
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                PendingIntent.getActivity(this, 0, sessionIntent, 0)
            }

        // Create a new MediaSession.
        session = MediaSessionCompat(this, "MusicService")
            .apply {
                setSessionActivity(sessionActivityPendingIntent)
                val playbackState = PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1F)
                    .setActions(getPlaybackActions())
                    .build()
                setPlaybackState(playbackState)
                isActive = true
            }
        sessionToken = session.sessionToken
        session.setCallback(callback)
        /**
         * The notification manager will use our player and media session to decide when to post
         * notifications. When notifications are posted or removed our listener will be called, this
         * allows us to promote the service to foreground (required so that we're not killed if
         * the main UI is not visible).
         */
        notificationManager = GTRadioNotificationManager(
            this,
            session.sessionToken,
            PlayerNotificationListener()
        )
        notificationManager.showNotificationForPlayer(radioPlayer)

        stationsList = getStationsList()
    }

    private fun getStationsList(): ArrayList<MediaItem> {
        val sharedPref = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)
        val radioPath = sharedPref?.getString(getString(R.string.radio_folders_uri_key), "") ?: return ArrayList()

        val list = ArrayList<MediaItem>()
        val dir = File(radioPath)

        val subDirs = dir.list() ?: return ArrayList()
        for (subDir in subDirs) {
            //Skip the Adverts folder since its not a standalone station
            if (subDir.endsWith("Adverts")) { continue }

            val stationName = subDir
            val logoUri: String? = (File("$radioPath/$subDir").listFiles(FileFilter { file: File ->
                return@FileFilter file.isFile && file.name.contains("logo")
            } )?: emptyArray()).firstOrNull()?.path

            if (stationName.isNotBlank()) {
                val logo = BitmapFactory.decodeFile(logoUri)
                val metadata = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, stationName)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, stationName)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, logo)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, stationName)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "")
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, logo)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, "")
                    .build()
                val item = MediaItem(metadata.description, MediaItem.FLAG_PLAYABLE)
                list.add(item)
            }
        }
        return list
    }

    override fun onDestroy() {
        //Free up our session
        session.run {
            isActive = false
            release()
        }

        // Free ExoPlayer resources.
        radioPlayer.removeListener(playerListener)
        radioPlayer.release()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        return BrowserRoot("root", null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        result.sendResult(stationsList)
    }

    private fun buildRadioStation(mediaId: String? = null): RadioStation? {
        var mediaIdToBuild = mediaId
        if (mediaIdToBuild.isNullOrBlank()) {
            if (stationsList.isEmpty()) {
                return null
            } else {
                mediaIdToBuild = stationsList.first().mediaId
            }
        }

        if (!stationsList.any { x -> x.mediaId == mediaIdToBuild }) {
            //Someone is asking us to play something that doesn't exist
            return null
        }

        val sharedPref = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)
        val radioPath = sharedPref?.getString(getString(R.string.radio_folders_uri_key), "")
        val adsEnabled = sharedPref?.getBoolean(getString(R.string.ads_enabled_preference_key), false) ?: false
        val weatherChatterEnabled = sharedPref?.getBoolean(getString(R.string.weather_chatter_enabled_preference_key), false) ?: false

        return RadioStation(mediaId!!,"$radioPath/$mediaId", "$radioPath/Adverts", radioPlayer, applicationContext, adsEnabled, weatherChatterEnabled)
    }

    private fun getStationIndex(stationMediaId: String): Int {
        return stationsList.indexOfFirst { x -> x.mediaId == stationMediaId }
    }

    private fun getPlaybackActionsForStation(stationIndex: Int): Long {
        return getPlaybackActions((stationIndex < stationsList.lastIndex), (stationIndex > 0))
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

    /**
     * Listen for notification events.
     */
    private inner class PlayerNotificationListener : PlayerNotificationManager.NotificationListener {
        override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
            if (ongoing && !isForegroundService) {
                ContextCompat.startForegroundService(
                    applicationContext,
                    Intent(applicationContext, this@GTRadioMusicService.javaClass)
                )

                startForeground(notificationId, notification)
                isForegroundService = true
            }
        }

        override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
            stopForeground(true)
            isForegroundService = false
            stopSelf()
        }
    }

    /**
     * Listen for events from ExoPlayer.
     */
    private inner class PlayerEventListener : Player.Listener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            notificationManager.updatePlayerButtons(radioPlayer)
            when (val actualPlaybackState = radioPlayer.playbackState) {
                Player.STATE_BUFFERING,
                Player.STATE_READY -> {
                    notificationManager.showNotificationForPlayer(radioPlayer)
                    if (actualPlaybackState == Player.STATE_READY) {
                        if (!playWhenReady) {
                            // If playback is paused we remove the foreground state which allows the
                            // notification to be dismissed. An alternative would be to provide a
                            // "close" button in the notification which stops playback and clears
                            // the notification.
                            stopForeground(false)
                            isForegroundService = false
                        }
                    }
                }
                else -> {
                    notificationManager.hideNotification()
                }
            }
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            var message = R.string.generic_error
            when (error.type) {
                // If the data from MediaSource object could not be loaded the Exoplayer raises
                // a type_source error.
                // An error message is printed to UI via Toast message to inform the user.
                ExoPlaybackException.TYPE_SOURCE -> {
                    message = R.string.error_media_not_found
                    Log.e("", "TYPE_SOURCE: " + error.sourceException.message)
                }
                // If the error occurs in a render component, Exoplayer raises a type_remote error.
                ExoPlaybackException.TYPE_RENDERER -> {
                    Log.e("", "TYPE_RENDERER: " + error.rendererException.message)
                }
                // If occurs an unexpected RuntimeException Exoplayer raises a type_unexpected error.
                ExoPlaybackException.TYPE_UNEXPECTED -> {
                    Log.e("", "TYPE_UNEXPECTED: " + error.unexpectedException.message)
                }

                // If the error occurs in a remote component, Exoplayer raises a type_remote error.
                ExoPlaybackException.TYPE_REMOTE -> {
                    Log.e("", "TYPE_REMOTE: " + error.message)
                }
            }
            Toast.makeText(
                applicationContext,
                message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

}