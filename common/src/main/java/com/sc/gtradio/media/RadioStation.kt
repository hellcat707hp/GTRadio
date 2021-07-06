package com.sc.gtradio.media

import android.content.Context
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.net.toUri
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.*
import java.io.File
import java.io.FilenameFilter
import java.util.*

class RadioStation(
    val mediaId: String,
    baseStationPath: String,
    advertsPath: String,
    private val player: GTRadioPlayer,
    private val context: Context,
    var adsEnabled: Boolean,
    private var _weatherChatterEnabled: Boolean) {

    var weatherChatterEnabled: Boolean
        get() = _weatherChatterEnabled
        set(enabled) {
            if (enabled != _weatherChatterEnabled) {
                _weatherChatterEnabled = enabled
                //Reset DJ files when the weather chatter is enabled/disabled
                randomizeDJFiles()
            }
        }

    var stationName: String = ""

    private var advertFiles: Array<String> = emptyArray()
    private var announcerFiles:  Array<String> = emptyArray()
    private var djMorningFiles:  Array<String> = emptyArray()
    private var djEveningFiles:  Array<String> = emptyArray()
    private var djNightFiles: Array<String> = emptyArray()
    private var djOtherFiles: Array<String> = emptyArray()
    private var djWeatherFogFiles: Array<String> = emptyArray()
    private var djWeatherRainFiles: Array<String> = emptyArray()
    private var djWeatherSunFiles: Array<String> = emptyArray()
    private var djWeatherStormFiles: Array<String> = emptyArray()
    private var songFolders: Array<String> = emptyArray()
    private var currentDJFiles: Array<String> = emptyArray()

    private var advertIterator: Iterator<String>? = null
    private var announcerIterator: Iterator<String>? = null
    private var djIterator: Iterator<String>? = null
    private var songIterator: Iterator<String>? = null

    private var _playing = false
    private var lastSegment = RadioSegmentType.None
    private var songsSinceLastBreak = 0
    private var activeListener: Player.Listener? = null
    private val rng = Random()

    private var playFailureCount = 0

    init {
        stationName = getStationName(baseStationPath)
        val dir = File(baseStationPath)
        val subDirs = dir.list(FilenameFilter { file: File, _: String ->
            Boolean
            return@FilenameFilter file.isDirectory
        })
        if (subDirs != null) {
            for (subDir in subDirs) {
                val currentFolder = "$baseStationPath/$subDir"

                if (subDir.isNotBlank()) {
                    when(subDir) {
                        "Announcer" -> { setupAnnouncerFiles(currentFolder) }
                        "DJ Chatter" -> { setupDJFiles(currentFolder) }
                        "Songs" -> { setupSongFolders(currentFolder) }
                    }
                }
            }
        }

        setupAdvertFiles(advertsPath)
        randomizeAll()
    }

    private fun getStationName(stationPath: String): String {
        if (stationPath.isBlank()) return ""
        val split = stationPath.split('\\')
        return split[split.lastIndex]
    }

    private fun setupAnnouncerFiles(folder: String) {
        if (folder.isNotBlank()) {
            announcerFiles = (File(folder).list() ?: emptyArray()).map { return@map "$folder/$it" }.toTypedArray()
        }
    }

    private fun setupAdvertFiles(folder: String) {
        if (folder.isNotBlank()) {
            advertFiles = (File(folder).list() ?: emptyArray()).map { return@map "$folder/$it" }.toTypedArray()
        }
    }

    private fun setupDJFiles(folder: String) {
        if (folder.isNotBlank()) {
            djMorningFiles = (File("$folder/Morning").list() ?: emptyArray()).map { return@map "$folder/Morning/$it" }.toTypedArray()
            djEveningFiles = (File("$folder/Evening").list() ?: emptyArray()).map { return@map "$folder/Evening/$it" }.toTypedArray()
            djNightFiles = (File("$folder/Night").list() ?: emptyArray()).map { return@map "$folder/Night/$it" }.toTypedArray()
            djOtherFiles = (File("$folder/Other").list() ?: emptyArray()).map { return@map "$folder/Other/$it" }.toTypedArray()

            val weatherFolders = (File("$folder/Weather").list() ?: emptyArray()).map { return@map "$folder/Weather/$it" }.toTypedArray()
            if (weatherFolders.isNotEmpty()) {
                djWeatherFogFiles = (File("$folder/Weather/Fog").list() ?: emptyArray()).map { return@map "$folder/Weather/Fog/$it" }.toTypedArray()
                djWeatherRainFiles = (File("$folder/Weather/Rain").list() ?: emptyArray()).map { return@map "$folder/Weather/Rain/$it" }.toTypedArray()
                djWeatherSunFiles = (File("$folder/Weather/Sun").list() ?: emptyArray()).map { return@map "$folder/Weather/Sun/$it" }.toTypedArray()
                djWeatherStormFiles = (File("$folder/Weather/Storm").list() ?: emptyArray()).map { return@map "$folder/Weather/Storm/$it" }.toTypedArray()
            }
        }
    }

    private fun setupSongFolders(folder: String) {
        if (folder.isNotBlank()) {
            songFolders = (File(folder).list(FilenameFilter { file: File, _: String ->
                return@FilenameFilter file.isDirectory
            }) ?: emptyArray()).map {
                return@map "$folder/$it"
            }.toTypedArray()
        }
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
        var arrToConcat: Array<String> = emptyArray()
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
        songFolders.shuffle()
        songIterator = songFolders.iterator()
    }

    private fun getNextAdvertFile(): String {
        if (!advertIterator!!.hasNext()) {
            randomizeAdvertFiles()
        }
        return advertIterator!!.next()
    }

    private fun getNextAnnouncerFile(): String {
        if (!announcerIterator!!.hasNext()) {
            randomizeAnnouncerFiles()
        }
        return announcerIterator!!.next()
    }

    private fun getNextDjFile(): String {
        if (!djIterator!!.hasNext()) {
            randomizeDJFiles()
        }
        return djIterator!!.next()
    }

    private fun getNextSongFolder(): String {
        if (!songIterator!!.hasNext()) {
            randomizeSongFolders()
        }
        return songIterator!!.next()
    }

    fun stop() {
        _playing = false
        player.stop()
    }

    fun play() {
        _playing = true
        player.radioPlaybackState = PlaybackStateCompat.STATE_PLAYING
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

    private fun playSong(songFolder: String, requireDjIntro: Boolean = false, requireDjOutro: Boolean = false)
    {
        val songFiles = (File(songFolder).list()  ?: emptyArray())
        val mainSongPath = songFolder + "/" + songFiles.find { x -> return@find !x.contains("(Intro") && !x.contains("(Outro") }
        //Intros
        val mainIntro = songFolder + "/" + songFiles.find { x -> return@find x.contains("(Intro)") }
        val djIntros = songFiles.filter { x -> return@filter x.contains("(Intro DJ") }.map { x -> return@map "$songFolder/$x" }
        //Outros
        val mainOutro = songFolder + "/" + songFiles.find { x -> return@find x.contains("(Outro)") }
        val djOutros = songFiles.filter { x -> return@filter x.contains("(Outro DJ") }.map { x -> return@map "$songFolder/$x" }

        val introToUse: String
        if (requireDjIntro && djIntros.isNotEmpty()) {
            //Use a random dj intro
            val introIndex = rng.nextInt(djIntros.size)
            introToUse = djIntros[introIndex]
        } else {
            val djOrStandard = rng.nextInt(11)
            if (djOrStandard < 6 && djIntros.isNotEmpty()) {
                //Use a DJ intro
                val introIndex = rng.nextInt(djIntros.size)
                introToUse = djIntros[introIndex]
            } else {
                introToUse = mainIntro
            }
        }

        val outroToUse: String
        if (requireDjOutro && djOutros.isNotEmpty()) {
            //Use a random dj outro
            val outroIndex = rng.nextInt(djOutros.size)
            outroToUse = djOutros[outroIndex]
        } else {
            val djOrStandard = rng.nextInt(11)
            if (djOrStandard < 6 && djOutros.isNotEmpty()) {
                //Use a DJ outro
                val outroIndex = rng.nextInt(djOutros.size)
                outroToUse = djOutros[outroIndex]
            } else {
                outroToUse = mainOutro
            }
        }

        val introFile = File(introToUse)
        val introSrc = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(MediaItem.fromUri(introFile.toUri()))

        val mainFile = File(mainSongPath)
        val mainSrc = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(MediaItem.fromUri(mainFile.toUri()))

        val outroFile = File(outroToUse)
        val outroSrc = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(MediaItem.fromUri(outroFile.toUri()))

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

    private fun playFile(file: String)
    {
        // Set the media item to be played.
        val fileToPlay = File(file)
        val src = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(MediaItem.fromUri(fileToPlay.toUri()))
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


}