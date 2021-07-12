package com.sc.gtradio.media

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import androidx.preference.PreferenceManager
import android.support.v4.media.MediaBrowserCompat.MediaItem
import androidx.media.MediaBrowserServiceCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.sc.gtradio.media.stations.Gen1RadioStation
import com.sc.gtradio.media.stations.Gen2RadioStation
import com.sc.gtradio.media.stations.RadioStation
import com.sc.gtradio.media.stations.StationGroup
import java.util.ArrayList

open class GTRadioMusicService : MediaBrowserServiceCompat() {
    private var stationGroups: ArrayList<StationGroup> = ArrayList()
    private var fullStationList: ArrayList<MediaItem> = ArrayList()
    private val stationCache: MutableMap<String, RadioStation> = mutableMapOf()
    private val sharedPrefListener = GTRadioOnSharedPrefChange()

    //Setup a listener for settings changes
    private inner class GTRadioOnSharedPrefChange : SharedPreferences.OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            if (prefs != null) {
                when(key) {
                    resources.getString(R.string.radio_folders_uri_key) -> {
                        //Update stations list
                        this@GTRadioMusicService.stationGroups = this@GTRadioMusicService.getStationGroups()
                        this@GTRadioMusicService.fullStationList = this@GTRadioMusicService.getFullStationList()
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
    private var activeStationGroupId: String? = null

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onCommand(command: String?, extras: Bundle?, cb: ResultReceiver?) {
            super.onCommand(command, extras, cb)
            if (command == "setActiveStationGroupId") {
                activeStationGroupId = extras?.getString("stationGroupId")
            }
        }

        override fun onPlay() {
            if (activeStation == null && activeStationGroupId == null) {
                return
            } else if (activeStation == null && activeStationGroupId != null) {
                //Pick up the first station in the group
                val group = getStationGroup(activeStationGroupId!!) ?: return
                val firstStation = group.stationList.firstOrNull() ?: return
                activeStation = getRadioStation(group, firstStation.mediaId!!)
            }

            if (activeStation == null) {
                return
            }

            //Get and set the playback actions available when on this station
            val group = getStationGroup(activeStation!!.stationGroupId) ?: return
            val stationIndex = group.getStationIndex(activeStation!!.mediaId)
            radioPlayer.nextEnabled = stationIndex < group.stationList.lastIndex
            radioPlayer.previousEnabled = stationIndex != 0
            val playbackActions = group.getPlaybackActionsForStation(stationIndex)

            activeStation?.play()
            val playbackState = PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1F)
                .setActions(playbackActions)
                .build()
            session.setPlaybackState(playbackState)
            session.setMetadata(activeStation!!.metadata)
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            if (mediaId.isNullOrBlank()) {
                return
            }

            val group = getStationGroupContainingMedia(mediaId) ?: return
            activeStation = getRadioStation(group, mediaId)
            if (activeStation == null) {
                //Unable to find the station to play
                return
            }

            activeStationGroupId = group.mediaItem.mediaId

            //Get and set the playback actions available when on this station
            val stationIndex = group.getStationIndex(activeStation!!.mediaId)
            radioPlayer.nextEnabled = stationIndex < group.stationList.lastIndex
            radioPlayer.previousEnabled = stationIndex != 0
            val playbackActions = group.getPlaybackActionsForStation(stationIndex)

            activeStation!!.play()
            val playbackState = PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1F)
                .setActions(playbackActions)
                .build()
            session.setPlaybackState(playbackState)
            session.setMetadata(activeStation!!.metadata)
        }

        override fun onPause() {
            if (activeStation == null) {
                return
            }

            //Get and set the playback actions available when on this station
            val group = getStationGroup(activeStation!!.stationGroupId) ?: return
            val stationIndex = group.getStationIndex(activeStation!!.mediaId)
            radioPlayer.nextEnabled = stationIndex < group.stationList.lastIndex
            radioPlayer.previousEnabled = stationIndex != 0
            val playbackActions = group.getPlaybackActionsForStation(stationIndex)

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
            val group = getStationGroup(activeStation!!.stationGroupId) ?: return
            val stationIndex = group.getStationIndex(activeStation!!.mediaId)
            radioPlayer.nextEnabled = stationIndex < group.stationList.lastIndex
            radioPlayer.previousEnabled = stationIndex != 0
            val playbackActions = group.getPlaybackActionsForStation(stationIndex)

            activeStation?.stop()
            val playbackState = PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1F)
                .setActions(playbackActions)
                .build()
            session.setPlaybackState(playbackState)
        }

        override fun onSkipToNext() {
            if (activeStation == null && activeStationGroupId == null) {
                return
            } else if (activeStation == null && activeStationGroupId != null) {
                //Pick up the first station in the group
                val group = getStationGroup(activeStationGroupId!!) ?: return
                val firstStation = group.stationList.firstOrNull() ?: return
                activeStation = getRadioStation(group, firstStation.mediaId!!)
            }

            //There is an active station, find its index and move to the next one if available
            val group = getStationGroup(activeStation!!.stationGroupId) ?: return
            val stationIndex = group.getStationIndex(activeStation!!.mediaId)
            var nextStationIndex = stationIndex + 1
            if (nextStationIndex >= group.stationList.size) {
                //Loop back to start
                nextStationIndex = 0
            }
            activeStation = getRadioStation(group, group.stationList[nextStationIndex].mediaId!!)

            if (activeStation == null) {
                return
            }

            //Get and set the playback actions available when on this station
            radioPlayer.nextEnabled = nextStationIndex < group.stationList.lastIndex
            radioPlayer.previousEnabled = nextStationIndex != 0
            val playbackActions = group.getPlaybackActionsForStation(nextStationIndex)

            activeStation?.play()
            val playbackState = PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1F)
                .setActions(playbackActions)
                .build()
            session.setPlaybackState(playbackState)
            session.setMetadata(activeStation!!.metadata)
        }

        override fun onSkipToPrevious() {
            if (activeStation == null && activeStationGroupId == null) {
                return
            } else if (activeStation == null && activeStationGroupId != null) {
                //Pick up the first station in the group
                val group = getStationGroup(activeStationGroupId!!) ?: return
                val firstStation = group.stationList.firstOrNull() ?: return
                activeStation = getRadioStation(group, firstStation.mediaId!!)
            }

            //There is an active station, find its index and move to the previous one if available
            val group = getStationGroup(activeStation!!.stationGroupId) ?: return
            val stationIndex = group.getStationIndex(activeStation!!.mediaId)
            var prevStationIndex = stationIndex - 1
            if (prevStationIndex < 0) {
                //Loop back to end
                prevStationIndex = group.stationList.lastIndex
            }
            activeStation = getRadioStation(group, group.stationList[prevStationIndex].mediaId!!)

            if (activeStation == null) {
                return
            }

            //Get and set the playback actions available when on this station
            radioPlayer.nextEnabled = prevStationIndex < group.stationList.lastIndex
            radioPlayer.previousEnabled = prevStationIndex != 0
            val playbackActions = group.getPlaybackActionsForStation(prevStationIndex)

            activeStation?.play()
            val playbackState = PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1F)
                .setActions(playbackActions)
                .build()
            session.setPlaybackState(playbackState)
            session.setMetadata(activeStation!!.metadata)
        }

    }

    override fun onCreate() {
        super.onCreate()

        //Subscribe to any SharedPref changes for things like the base folder, ads, and weather chatter
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
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
                    .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH)
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

        stationGroups = getStationGroups()
        fullStationList = getFullStationList()
    }

    private fun getStationGroups(): ArrayList<StationGroup> {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val radioPath = sharedPref?.getString(getString(R.string.radio_folders_uri_key), "") ?: return ArrayList()
        if (radioPath.isBlank()) { return ArrayList() }
        val dir = DocumentFile.fromTreeUri(applicationContext, Uri.parse(radioPath))

        val list = ArrayList<StationGroup>()
        val subDirs = (dir?.listFiles() ?: emptyArray()).filter { x -> x.isDirectory }
        for (subDir in subDirs) {
            list.add(StationGroup(subDir, applicationContext))
        }
        return list
    }

    private fun getFullStationList(): ArrayList<MediaItem> {
        return ArrayList(stationGroups.map { x -> x.stationList }.flatten())
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
        if (parentId.isBlank() || parentId == "root") {
            val groups = ArrayList(stationGroups.map { x -> x.mediaItem })
            result.sendResult(groups)
            return
        }
        val group = stationGroups.find { x -> x.mediaItem.mediaId == parentId }
        val stations = group?.stationList ?: ArrayList()
        result.sendResult(stations)
    }

    private fun getRadioStation(group: StationGroup, mediaId: String): RadioStation? {
        //Get the mediaId of the first station if none is provided
        if (mediaId.isBlank()) {
            return null
        }

        if (!fullStationList.any { x -> x.mediaId == mediaId }) {
            //Someone is asking us to play something that doesn't exist
            return null
        }

        if (mediaId.isBlank()) {
            //Really lost here, nothing has been found for this id
            return null
        }

        //Let's do a lookup and see if this is cached already
        if (stationCache.containsKey(mediaId)) {
            return stationCache[mediaId]
        }

        //Didn't have the station cached yet, so lets build it and cache it
        val station = buildRadioStation(group, mediaId)
        if (station != null) {
            stationCache[station.mediaItem.mediaId!!] = station
        }
        return station
    }

    private fun buildRadioStation(group: StationGroup, mediaId: String): RadioStation? {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val adsEnabled = sharedPref?.getBoolean(getString(R.string.ads_enabled_preference_key), false) ?: false
        val weatherChatterEnabled = sharedPref?.getBoolean(getString(R.string.weather_chatter_enabled_preference_key), false) ?: false

        val stationMedia = group.stationList.find { x -> x.mediaId == mediaId }!!
        val stationDoc = group.folderDoc.listFiles().find { x -> x.isDirectory && x.uri.toString() == stationMedia.mediaId } ?: return null

        if (group.generation == 1) {
            return Gen1RadioStation(group.mediaItem.mediaId!!, stationMedia.mediaId!!, stationMedia, stationDoc,  radioPlayer, applicationContext)
        } else if (group.generation == 2) {
            val advertsDoc = group.folderDoc.listFiles().find { x -> x.name?.contains("Adverts") == true } ?: return null
            return Gen2RadioStation(group.mediaItem.mediaId!!, stationMedia.mediaId!!, stationMedia, stationDoc, advertsDoc, radioPlayer, applicationContext, adsEnabled, weatherChatterEnabled)
        } else {
            return null
        }
    }

    private fun getStationGroupContainingMedia(mediaId: String): StationGroup? {
        return stationGroups.find { x -> x.stationList.any { y -> y.mediaId == mediaId } }
    }

    private fun getStationGroup(stationGroupId: String): StationGroup? {
        return stationGroups.find { x -> x.mediaItem.mediaId == stationGroupId }
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