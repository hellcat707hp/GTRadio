package com.sc.gtradio.media.stations

import android.content.Context
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import androidx.documentfile.provider.DocumentFile
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.sc.gtradio.media.GTRadioPlayer
import java.util.*

class Gen2RadioStation(
    override val stationGroupId: String,
    override val mediaId: String,
    baseStationFolderDoc: DocumentFile,
    advertsFolderDoc: DocumentFile,
    private val player: GTRadioPlayer,
    private val context: Context,
    override var adsEnabled: Boolean,
    private var _weatherChatterEnabled: Boolean) : RadioStation {

    override var weatherChatterEnabled: Boolean
        get() = _weatherChatterEnabled
        set(enabled) {
            if (enabled != _weatherChatterEnabled) {
                _weatherChatterEnabled = enabled
                //Reset DJ files when the weather chatter is enabled/disabled
                randomizeDJFiles()
            }
        }

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
            when(subDir.name) {
                "Announcer" -> { setupAnnouncerFiles(subDir) }
                "DJ Chatter" -> { setupDJFiles(subDir) }
                "Songs" -> { setupSongs(subDir) }
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
        djMorningFiles = (folder.listFiles().firstOrNull { x -> x.name == "Morning" }?.listFiles() ?: emptyArray()).map { x -> x.uri }.toTypedArray()
        djEveningFiles = (folder.listFiles().firstOrNull { x -> x.name == "Evening" }?.listFiles() ?: emptyArray()).map { x -> x.uri }.toTypedArray()
        djNightFiles = (folder.listFiles().firstOrNull { x -> x.name == "Night" }?.listFiles() ?: emptyArray()).map { x -> x.uri }.toTypedArray()
        djOtherFiles = (folder.listFiles().firstOrNull { x -> x.name == "Other" }?.listFiles() ?: emptyArray()).map { x -> x.uri }.toTypedArray()

        val weatherFolders =  folder.listFiles().firstOrNull { x -> x.name == "Weather" }
        if (weatherFolders != null && weatherFolders.listFiles().isNotEmpty()) {
            djWeatherFogFiles = (weatherFolders.listFiles().firstOrNull { x -> x.name == "Fog" }?.listFiles() ?: emptyArray()).map { x -> x.uri }.toTypedArray()
            djWeatherRainFiles = (weatherFolders.listFiles().firstOrNull { x -> x.name == "Rain" }?.listFiles() ?: emptyArray()).map { x -> x.uri }.toTypedArray()
            djWeatherSunFiles = (weatherFolders.listFiles().firstOrNull { x -> x.name == "Sun" }?.listFiles() ?: emptyArray()).map { x -> x.uri }.toTypedArray()
            djWeatherStormFiles = (weatherFolders.listFiles().firstOrNull { x -> x.name == "Storm" }?.listFiles() ?: emptyArray()).map { x -> x.uri }.toTypedArray()
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
        djMorningFiles.shuffle()
        djEveningFiles.shuffle()
        djNightFiles.shuffle()
        djOtherFiles.shuffle()

        //Include the relevant time-of-day DJ chatter based on current time
        //Note: There is no afternoon slot currently based on the anticipated user dataset
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        var arrToConcat: Array<Uri> = emptyArray()
        if (currentHour in 3..12) {
            //Morning (3am-noon) (03:00 to 12:00)
            arrToConcat = djMorningFiles
        } else if(currentHour in 17..22) {
            //Evening (5pm-11pm) (17:00-23:00)
            arrToConcat = djEveningFiles
        } else if(currentHour < 3 || currentHour >= 23) {
            //Night (11pm-3am) (23:00-03:00)
            arrToConcat = djNightFiles
        }
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
                    val nextThing = rng.nextInt(3)
                    lastSegment = RadioSegmentType.fromInt(nextThing + 1)
                    playNext()
                }
                RadioSegmentType.Commercial -> {
                    val songOrAnnouncer = rng.nextInt(11)
                    if (songOrAnnouncer < 6 || announcerFiles.isEmpty()) {
                        //Song
                        songsSinceLastBreak += 1
                        lastSegment = RadioSegmentType.Song
                        val nextSong = getNextSongFolder()
                        playSong(nextSong, true)
                    } else {
                        val nextAnn = getNextAnnouncerFile()
                        lastSegment = RadioSegmentType.Announcer
                        playFile(nextAnn)
                    }
                }
                RadioSegmentType.Announcer -> {
                    //Song with/without intro, or DJ chatter
                    val songOrDj = rng.nextInt(11)
                    if (songOrDj < 3 || currentDJFiles.isEmpty()) {
                        //Song
                        songsSinceLastBreak += 1
                        lastSegment = RadioSegmentType.Song
                        val nextSong = getNextSongFolder()
                        playSong(nextSong, true)
                    } else {
                        lastSegment = RadioSegmentType.DJChatter
                        val nextDj = getNextDjFile()
                        playFile(nextDj)
                    }
                }
                RadioSegmentType.DJChatter -> {
                    //Commercial or song with/without intro
                    if (songsSinceLastBreak == 0) {
                        //Need to play a song here
                        songsSinceLastBreak += 1
                        lastSegment = RadioSegmentType.Song
                        val nextSong = getNextSongFolder()
                        playSong(nextSong)
                    } else {
                        //We can either do a song or a commercial
                        val numberOfSongsBeforeBreak =
                            rng.nextInt(3) + 3 //gives us a number from 3-6
                        if (adsEnabled && numberOfSongsBeforeBreak <= songsSinceLastBreak) {
                            //Time for a commercial
                            lastSegment = RadioSegmentType.Commercial
                            songsSinceLastBreak = 0
                            val nextAd = getNextAdvertFile()
                            playFile(nextAd)
                        } else {
                            //Play another song
                            songsSinceLastBreak += 1
                            lastSegment = RadioSegmentType.Song
                            val nextSong = getNextSongFolder()
                            playSong(nextSong)
                        }
                    }

                }
                RadioSegmentType.Song -> {
                    //Song, commercial, dj chatter, or announcer
                    val numberOfSongsBeforeBreak =
                        rng.nextInt(3) + 3 //gives us a number from 3-6
                    if (adsEnabled && numberOfSongsBeforeBreak <= songsSinceLastBreak) {
                        //Time for a commercial
                        lastSegment = RadioSegmentType.Commercial
                        songsSinceLastBreak = 0
                        val nextAd = getNextAdvertFile()
                        playFile(nextAd)
                    } else {
                        val nextThing = if (currentDJFiles.isEmpty() && announcerFiles.isEmpty()) {
                            //No DJ Files or Announcer files, so set our next item to a song
                            0
                        } else if (currentDJFiles.isNotEmpty()) {
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
                                songsSinceLastBreak += 1
                                lastSegment = RadioSegmentType.Song
                                val nextSong = getNextSongFolder()
                                playSong(nextSong)
                            }
                            1 -> {
                                //Announcer
                                lastSegment = RadioSegmentType.Announcer
                                val nextAnn = getNextAnnouncerFile()
                                playFile(nextAnn)
                            }
                            else -> {
                                //DJ
                                lastSegment = RadioSegmentType.DJChatter
                                val nextDj = getNextDjFile()
                                playFile(nextDj)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun playSong(song: Song, requireDjIntro: Boolean = false, requireDjOutro: Boolean = false)
    {
        if (song.mainIntroUri == null || song.mainSongUri == null || song.mainOutroUri == null) {
            return
        }

        val introToUse: Uri
        if (requireDjIntro && song.djIntroUris.isNotEmpty()) {
            //Use a random dj intro
            val introIndex = rng.nextInt(song.djIntroUris.size)
            introToUse = song.djIntroUris[introIndex]
        } else {
            val djOrStandard = rng.nextInt(11)
            if (djOrStandard < 6 && song.djIntroUris.isNotEmpty()) {
                //Use a DJ intro
                val introIndex = rng.nextInt(song.djIntroUris.size)
                introToUse = song.djIntroUris[introIndex]
            } else {
                introToUse = song.mainIntroUri
            }
        }

        val outroToUse: Uri
        if (requireDjOutro && song.djOutroUris.isNotEmpty()) {
            //Use a random dj outro
            val outroIndex = rng.nextInt(song.djOutroUris.size)
            outroToUse = song.djOutroUris[outroIndex]
        } else {
            val djOrStandard = rng.nextInt(11)
            if (djOrStandard < 6 && song.djOutroUris.isNotEmpty()) {
                //Use a DJ outro
                val outroIndex = rng.nextInt(song.djOutroUris.size)
                outroToUse = song.djOutroUris[outroIndex]
            } else {
                outroToUse = song.mainOutroUri
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
        val mainSongUri: Uri? = (songFolderDoc?.listFiles() ?: emptyArray()).find { x -> x.name?.contains("(Intro") == false && x.name?.contains("(Outro") == false }?.uri
        val mainIntroUri: Uri? = (songFolderDoc?.listFiles() ?: emptyArray()).find { x -> x.name?.contains("(Intro)") == true}?.uri
        val mainOutroUri: Uri? = (songFolderDoc?.listFiles() ?: emptyArray()).find { x -> x.name?.contains("(Outro)") == true}?.uri
        val djIntroUris: Array<Uri> = (songFolderDoc?.listFiles() ?: emptyArray()).filter { x -> x.name?.contains("(Intro DJ") == true }.map { x -> x.uri }.toTypedArray()
        val djOutroUris: Array<Uri> = (songFolderDoc?.listFiles() ?: emptyArray()).filter { x -> x.name?.contains("(Outro DJ") == true }.map { x -> x.uri }.toTypedArray()
    }
}