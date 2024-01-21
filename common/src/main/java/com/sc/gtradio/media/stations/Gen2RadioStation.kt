package com.sc.gtradio.media.stations

import android.content.Context
import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import com.sc.gtradio.common.FileUtils.listSimpleFiles
import GTRadioPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSourceFactory
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.util.*

@UnstableApi class Gen2RadioStation(
    override val stationGroupId: String,
    override val mediaId: String,
    override val mediaItem: MediaBrowserCompat.MediaItem,
    baseStationFolderDoc: DocumentFile,
    advertsFolderDoc: DocumentFile,
    private val player: GTRadioPlayer,
    private val context: Context,
    override var adsEnabled: Boolean,
    private var _weatherChatterEnabled: Boolean) : RadioStation {

    //News reports are not a feature of Gen2, so mark them disabled
    override var newsReportsEnabled: Boolean = false

    override var weatherChatterEnabled: Boolean
        get() = _weatherChatterEnabled
        set(enabled) {
            if (enabled != _weatherChatterEnabled) {
                _weatherChatterEnabled = enabled
                //Reset DJ files when the weather chatter is enabled/disabled
                randomizeDJFiles()
            }
        }

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

    private var advertFiles: Array<Uri> = emptyArray()
    private var announcerFiles:  Array<Uri> = emptyArray()
    private var djMorningFiles:  Array<Uri> = emptyArray()
    private var djEveningFiles:  Array<Uri> = emptyArray()
    private var djNightFiles: Array<Uri> = emptyArray()
    private var djOtherFiles: Array<Uri> = emptyArray()
    private var djWeatherFogFiles: Array<Uri> = emptyArray()
    private var djWeatherRainFiles: Array<Uri> = emptyArray()
    private var djWeatherSunFiles: Array<Uri> = emptyArray()
    private var djWeatherStormFiles: Array<Uri> = emptyArray()
    private var songs: Array<Song> = emptyArray()
    private var currentDJFiles: Array<Uri> = emptyArray()

    private var advertIterator: Iterator<Uri>? = null
    private var announcerIterator: Iterator<Uri>? = null
    private var djIterator: Iterator<Uri>? = null
    private var songIterator: Iterator<Song>? = null

    private var _playing = false
    private var lastSegment = RadioSegmentType.None
    private var songsSinceLastBreak = 0
    private var activeListener: Player.Listener? = null
    private val rng = Random()

    init {
        stationName = getStationName(baseStationFolderDoc)
        val subDirs = baseStationFolderDoc.listFiles().filter { x -> x.isDirectory }
        for (subDir in subDirs) {
            when(subDir.name?.uppercase()) {
                "ANNOUNCER" -> { setupAnnouncerFiles(subDir) }
                "DJ CHATTER" -> { setupDJFiles(subDir) }
                "SONGS" -> { setupSongs(subDir) }
            }
        }

        setupAdvertFiles(advertsFolderDoc)
        randomizeAll()
    }

    private fun getStationName(stationDoc: DocumentFile): String {
        return stationDoc.name ?: ""
    }

    private fun setupAnnouncerFiles(folder: DocumentFile) {
        announcerFiles = folder.listFiles().map { x -> x.uri }.toTypedArray()
    }

    private fun setupAdvertFiles(folder: DocumentFile) {
        advertFiles = folder.listFiles().map { x -> x.uri }.toTypedArray()
    }

    private fun setupDJFiles(folder: DocumentFile) {
        val djFolders = folder.listFiles()
        djMorningFiles = (djFolders.firstOrNull { x -> x.name?.uppercase() == "MORNING" }?.listFiles() ?: emptyArray()).map { x -> x.uri }.toTypedArray()
        djEveningFiles = (djFolders.firstOrNull { x -> x.name?.uppercase() == "EVENING" }?.listFiles() ?: emptyArray()).map { x -> x.uri }.toTypedArray()
        djNightFiles = (djFolders.firstOrNull { x -> x.name?.uppercase() == "NIGHT" }?.listFiles() ?: emptyArray()).map { x -> x.uri }.toTypedArray()
        djOtherFiles = (djFolders.firstOrNull { x -> x.name?.uppercase() == "OTHER" }?.listFiles() ?: emptyArray()).map { x -> x.uri }.toTypedArray()

        val weatherFolder =  folder.listFiles().firstOrNull { x -> x.name?.uppercase() == "WEATHER" }
        val weatherFolders = weatherFolder?.listFiles()
        if (weatherFolder != null && weatherFolders?.isNotEmpty() == true) {
            djWeatherFogFiles = (weatherFolders.firstOrNull { x -> x.name?.uppercase() == "FOG" }?.listFiles() ?: emptyArray()).map { x -> x.uri }.toTypedArray()
            djWeatherRainFiles = (weatherFolders.firstOrNull { x -> x.name?.uppercase() == "RAIN" }?.listFiles() ?: emptyArray()).map { x -> x.uri }.toTypedArray()
            djWeatherSunFiles = (weatherFolders.firstOrNull { x -> x.name?.uppercase() == "SUN" }?.listFiles() ?: emptyArray()).map { x -> x.uri }.toTypedArray()
            djWeatherStormFiles = (weatherFolders.firstOrNull { x -> x.name?.uppercase() == "STORM" }?.listFiles() ?: emptyArray()).map { x -> x.uri }.toTypedArray()
        }

    }

    private fun setupSongs(folder: DocumentFile) {
            songs = folder.listFiles().map { x -> Song(x) }.toTypedArray()
    }

    private fun randomizeAll() {
        randomizeAnnouncerFiles()
        randomizeAdvertFiles()
        randomizeDJFiles()
        randomizeSongFolders()
    }

    private fun randomizeAnnouncerFiles() {
        announcerFiles.shuffle()
        announcerIterator = announcerFiles.iterator()
    }

    private fun randomizeAdvertFiles() {
        advertFiles.shuffle()
        advertIterator = advertFiles.iterator()
    }

    private fun randomizeDJFiles() {
        //Include the relevant time-of-day DJ chatter based on current time
        //Note: There is no afternoon slot currently based on the anticipated user dataset
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        var arrToConcat: Array<Uri> = emptyArray()
        if (currentHour in 3..11) {
            //Morning (3am-noon) (03:00 to 12:00)
            djMorningFiles.shuffle()
            arrToConcat = djMorningFiles
        } else if(currentHour in 17..22) {
            //Evening (5pm-11pm) (17:00-23:00)
            djEveningFiles.shuffle()
            arrToConcat = djEveningFiles
        } else if(currentHour < 3 || currentHour >= 23) {
            //Night (11pm-3am) (23:00-03:00)
            djNightFiles.shuffle()
            arrToConcat = djNightFiles
        }
        djOtherFiles.shuffle()
        currentDJFiles = djOtherFiles.plus(arrToConcat)

        if (weatherChatterEnabled) {
            //Include the DJ weather chatter
            djWeatherFogFiles.shuffle()
            djWeatherRainFiles.shuffle()
            djWeatherStormFiles.shuffle()
            djWeatherSunFiles.shuffle()
            currentDJFiles = currentDJFiles.plus(djWeatherFogFiles).plus(djWeatherStormFiles).plus(djWeatherRainFiles).plus(djWeatherSunFiles)
        }

        currentDJFiles.shuffle()
        djIterator = currentDJFiles.iterator()
    }

    private fun randomizeSongFolders() {
        songs.shuffle()
        songIterator = songs.iterator()
    }

    private fun getNextAdvertFile(): Uri {
        if (!advertIterator!!.hasNext()) {
            randomizeAdvertFiles()
        }
        return advertIterator!!.next()
    }

    private fun getNextAnnouncerFile(): Uri {
        if (!announcerIterator!!.hasNext()) {
            randomizeAnnouncerFiles()
        }
        return announcerIterator!!.next()
    }

    private fun getNextDjFile(): Uri {
        if (!djIterator!!.hasNext()) {
            randomizeDJFiles()
        }
        return djIterator!!.next()
    }

    private fun getNextSongFolder(): Song {
        if (!songIterator!!.hasNext()) {
            randomizeSongFolders()
        }
        return songIterator!!.next()
    }

    override fun stop() {
        _playing = false
        player.stop()
    }

    override fun play() {
        _playing = true
        player.radioPlaybackState = PlaybackStateCompat.STATE_PLAYING
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.playWhenReady = false
        playNext()
    }

    private fun playNext() {
        if (activeListener != null) {
            //Remove/clear any active exoplayer listeners to prevent incidental or repeated callbacks
            player.removeListener(activeListener!!)
        }

        if (_playing) {
            when (lastSegment) {
                RadioSegmentType.None -> {
                    //We can do anything here
                    val nextThing = rng.nextInt(5)
                    lastSegment = RadioSegmentType.fromInt(nextThing)
                    playNext()
                }
                RadioSegmentType.Commercial -> {
                    val songOrAnnouncer = rng.nextInt(11)
                    if (songOrAnnouncer < 6 || announcerFiles.isEmpty()) {
                        //Song
                        playNextSong(true)
                    } else {
                        playNextAnnouncerSpot()
                    }
                }
                RadioSegmentType.Announcer -> {
                    //Song with/without intro, or DJ chatter
                    val songOrDj = rng.nextInt(11)
                    if (songOrDj < 3 || currentDJFiles.isEmpty()) {
                        //Song
                        playNextSong(true)
                    } else {
                        playNextDjChatter()
                    }
                }
                RadioSegmentType.DJChatter -> {
                    //Commercial or song with/without intro
                    if (songsSinceLastBreak == 0) {
                        //Need to play a song here
                        playNextSong()
                    } else {
                        //We can either do a song or a commercial
                        val numberOfSongsBeforeBreak =
                            rng.nextInt(3) + 3 //gives us a number from 3-6
                        if (adsEnabled && numberOfSongsBeforeBreak <= songsSinceLastBreak) {
                            //Time for a commercial
                            playNextAdvert()
                        } else {
                            //Play another song
                            playNextSong()
                        }
                    }

                }
                RadioSegmentType.Song -> {
                    //Song, commercial, dj chatter, or announcer
                    val numberOfSongsBeforeBreak =
                        rng.nextInt(3) + 3 //gives us a number from 3-6
                    if (adsEnabled && numberOfSongsBeforeBreak <= songsSinceLastBreak) {
                        //Time for a commercial
                        playNextAdvert()
                    } else {
                        val nextThing = if (currentDJFiles.isEmpty() && announcerFiles.isEmpty()) {
                            //No DJ Files or Announcer files, so set our next item to a song
                            0
                        } else if (currentDJFiles.isEmpty()) {
                            //No DJ files so we have to pick between announcer and song
                            rng.nextInt(2)
                        } else if (announcerFiles.isEmpty()) {
                            //No announcer files so we have to pick between DJ and song
                            if (rng.nextInt(2) == 0) 0 else 2
                        } else {
                            //We can choose a song, dj chatter, or announcer
                            rng.nextInt(3)
                        }
                        when (nextThing) {
                            0 -> {
                                //Song again
                                playNextSong()
                            }
                            1 -> {
                                //Announcer
                                playNextAnnouncerSpot()
                            }
                            else -> {
                                //DJ
                                playNextDjChatter()
                            }
                        }
                    }
                }
                else -> {
                    //Bad state, try something new
                    val nextThing = rng.nextInt(5)
                    lastSegment = RadioSegmentType.fromInt(nextThing + 1)
                    playNext()
                }
            }
        }
    }

    private fun playNextAnnouncerSpot() {
        lastSegment = RadioSegmentType.Announcer
        if (announcerFiles.isNotEmpty()) {
            val nextAnn = getNextAnnouncerFile()
            playFile(nextAnn)
        } else {
            playNext()
        }
    }

    private fun playNextDjChatter() {
        lastSegment = RadioSegmentType.DJChatter
        if (currentDJFiles.isNotEmpty()) {
            val nextDj = getNextDjFile()
            playFile(nextDj)
        } else {
            playNext()
        }
    }

    private fun playNextAdvert() {
        lastSegment = RadioSegmentType.Commercial
        songsSinceLastBreak = 0
        if (adsEnabled && advertFiles.isNotEmpty()) {
            val nextAd = getNextAdvertFile()
            playFile(nextAd)
        } else {
            playNext()
        }
    }

    private fun playNextSong(requireDjIntro: Boolean = false, requireDjOutro: Boolean = false) {
        songsSinceLastBreak += 1
        lastSegment = RadioSegmentType.Song
        if (songs.isNotEmpty()) {
            val nextSong = getNextSongFolder()
            playSong(nextSong, requireDjIntro, requireDjOutro)
        } else {
            playNext()
        }
    }

    private fun playSong(song: Song, requireDjIntro: Boolean, requireDjOutro: Boolean)
    {
        if (song.mainIntroUri == null || song.mainSongUri == null || song.mainOutroUri == null) {
            return
        }

        val introToUse = if (requireDjIntro && song.djIntroUris.isNotEmpty()) {
            //Use a random dj intro
            val introIndex = rng.nextInt(song.djIntroUris.size)
            song.djIntroUris[introIndex]
        } else {
            val djOrStandard = rng.nextInt(11)
            if (djOrStandard < 6 && song.djIntroUris.isNotEmpty()) {
                //Use a DJ intro
                val introIndex = rng.nextInt(song.djIntroUris.size)
                song.djIntroUris[introIndex]
            } else {
                song.mainIntroUri
            }
        }

        val outroToUse = if (requireDjOutro && song.djOutroUris.isNotEmpty()) {
            //Use a random dj outro
            val outroIndex = rng.nextInt(song.djOutroUris.size)
            song.djOutroUris[outroIndex]
        } else {
            val djOrStandard = rng.nextInt(11)
            if (djOrStandard < 6 && song.djOutroUris.isNotEmpty()) {
                //Use a DJ outro
                val outroIndex = rng.nextInt(song.djOutroUris.size)
                song.djOutroUris[outroIndex]
            } else {
                song.mainOutroUri
            }
        }

        val introSrc = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(MediaItem.fromUri(introToUse))

        val mainSrc = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(MediaItem.fromUri(song.mainSongUri))

        val outroSrc = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(MediaItem.fromUri(outroToUse))

        // Prepare the player.
        val concatSource = ConcatenatingMediaSource(true)
        concatSource.addMediaSources(listOf(introSrc, mainSrc, outroSrc))
        player.setMediaSource(concatSource)

        activeListener = object : Player.Listener {
            private var alreadyHandled = false
            override fun onPlaybackStateChanged(state: Int) {
                if (!alreadyHandled && state == Player.STATE_ENDED) {
                    alreadyHandled = true
                    player.removeListener(activeListener!!)
                    playNext()
                }
            }
        }
        player.addListener(activeListener!!)
        player.prepare()
        player.play()
    }

    private fun playFile(file: Uri)
    {
        // Set the media item to be played.
        val src = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(MediaItem.fromUri(file))
        // Prepare the player.
        player.setMediaSource(src)

        activeListener = object : Player.Listener {
            private var alreadyHandled = false
            override fun onPlaybackStateChanged(state: Int) {
                if (!alreadyHandled && state == Player.STATE_ENDED) {
                    alreadyHandled = true
                    player.removeListener(activeListener!!)
                    playNext()
                }
            }
        }

        player.addListener(activeListener!!)
        player.prepare()
        player.play()
    }

    private inner class Song(songFolderDoc: DocumentFile?) {
        val songFiles = songFolderDoc?.listSimpleFiles(context.applicationContext)

        val mainSongUri: Uri? = (songFiles ?: emptyArray()).find { x -> !x.name.uppercase().contains("(INTRO") && !x.name.uppercase().contains("(OUTRO") }?.uri
        val mainIntroUri: Uri? = (songFiles ?: emptyArray()).find { x -> x.name.uppercase().contains("(INTRO)") }?.uri
        val mainOutroUri: Uri? = (songFiles ?: emptyArray()).find { x -> x.name.uppercase().contains("(OUTRO)") }?.uri
        val djIntroUris: Array<Uri> = (songFiles ?: emptyArray()).filter { x -> x.name.uppercase().contains("(INTRO DJ") }.map { x -> x.uri }.toTypedArray()
        val djOutroUris: Array<Uri> = (songFiles ?: emptyArray()).filter { x -> x.name.uppercase().contains("(OUTRO DJ") }.map { x -> x.uri }.toTypedArray()
    }
}