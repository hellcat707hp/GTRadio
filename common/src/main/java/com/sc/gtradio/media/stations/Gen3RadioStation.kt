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
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.util.*
import kotlin.collections.ArrayList

@UnstableApi class Gen3RadioStation(
    override val stationGroupId: String,
    override val mediaId: String,
    override val mediaItem: MediaBrowserCompat.MediaItem,
    baseStationFolderDoc: DocumentFile,
    advertsFolderDoc: DocumentFile,
    newsFolderDoc: DocumentFile,
    weatherFolderDoc: DocumentFile,
    private val player: GTRadioPlayer,
    private val context: Context,
    override var adsEnabled: Boolean,
    override var weatherChatterEnabled: Boolean,
    override var newsReportsEnabled: Boolean
) : RadioStation {

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

    //Ad files
    private var advertFiles: Array<Uri> = emptyArray()
    private var advertIterator: Iterator<Uri>? = null

    //News report files
    private var newsFiles: Array<Uri> = emptyArray()
    private var newsIterator: Iterator<Uri>? = null

    //Currently these weather files are just all weather files, as we have no way to determine device weather
    private var weatherFiles: Array<Uri> = emptyArray()
    private var weatherIterator: Iterator<Uri>? = null

    //Announcer files
    private var announcerFiles:  ArrayList<Uri> = ArrayList()
    private var announcerIterator: Iterator<Uri>? = null

    //DJ File Collections
    private var djMorningFiles:  ArrayList<Uri> = ArrayList()
    private var djAfternoonFiles:  ArrayList<Uri> = ArrayList()
    private var djEveningFiles:  ArrayList<Uri> = ArrayList()
    private var djNightFiles: ArrayList<Uri> = ArrayList()
    private var djGeneralFiles: ArrayList<Uri> = ArrayList()
    private var djSoloFiles: ArrayList<Uri> = ArrayList()
    private var djToAdFiles: ArrayList<Uri> = ArrayList()
    private var djToNewsFiles: ArrayList<Uri> = ArrayList()
    private var djToWeatherFiles: ArrayList<Uri> = ArrayList()
    private var currentDJFiles: Array<Uri> = emptyArray()
    private var djMainIterator: Iterator<Uri>? = null
    private var djSoloIterator: Iterator<Uri>? = null
    private var djToAdIterator: Iterator<Uri>? = null
    private var djToNewsIterator: Iterator<Uri>? = null
    private var djToWeatherIterator: Iterator<Uri>? = null

    //These generic intros and outros really only exist for user-custom stations
    private var genericSongIntros: ArrayList<Uri> = ArrayList()
    private var genericSongOutros: ArrayList<Uri> = ArrayList()
    private var genericSongIntrosIterator: Iterator<Uri>? = null
    private var genericSongOutrosIterator: Iterator<Uri>? = null

    //Primary content collections (songs or talk show episodes)
    private var songs: Array<Song> = emptyArray()
    private var talkShows: Array<Song> = emptyArray()
    private var songIterator: Iterator<Song>? = null
    private var talkShowIterator: Iterator<Song>? = null
    private var isTalkRadio: Boolean = false

    //Status management properties
    private var _playing = false
    private var lastSegment = RadioSegmentType.None
    private var songsSinceLastBreak = 0
    private var activeListener: Player.Listener? = null
    private val rng = Random()

    /** The Gen1 station powering this station in the event that this is a single-looping-file station */
    private val gen1Station: Gen1RadioStation?

    init {
        val subFiles = baseStationFolderDoc.listSimpleFiles(context)

        gen1Station = if (subFiles.size <= 2) {
            //With just a single audio file (and maybe a logo), likely safe to assume this is a Gen1-style Gen3 station
            //Not sure if theres a better way to do this, but I don't want to duplicate all of the Gen1 code in here
            Gen1RadioStation(stationGroupId, mediaId, mediaItem, baseStationFolderDoc, player, context)
        } else {
            null
        }

        if (gen1Station == null) {
            //Don't bother setting things up if this is basically a Gen1 station

            var songFolderName = ""
            for (file in subFiles) {
                val upperFileName = file.name.uppercase()
                if (file.isDirectory) {
                    if (upperFileName.contains("SONGS") || upperFileName.contains("TALK SHOWS")) {
                        songFolderName = file.name
                    }
                    isTalkRadio = isTalkRadio || upperFileName.contains("TALK SHOWS")
                    continue
                }

                when {
                    upperFileName.startsWith("GENERAL_") -> {
                        djGeneralFiles.add(file.uri)
                    }
                    upperFileName.startsWith("SOLO_") -> {
                        djSoloFiles.add(file.uri)
                    }
                    upperFileName.startsWith("MORNING_") -> {
                        djMorningFiles.add(file.uri)
                    }
                    upperFileName.startsWith("AFTERNOON_") -> {
                        djAfternoonFiles.add(file.uri)
                    }
                    upperFileName.startsWith("EVENING_") -> {
                        djEveningFiles.add(file.uri)
                    }
                    upperFileName.startsWith("NIGHT_") -> {
                        djNightFiles.add(file.uri)
                    }
                    upperFileName.startsWith("ID_") -> {
                        announcerFiles.add(file.uri)
                    }
                    upperFileName.startsWith("TO_AD_") -> {
                        djToAdFiles.add(file.uri)
                    }
                    upperFileName.startsWith("TO_NEWS_") -> {
                        djToNewsFiles.add(file.uri)
                    }
                    upperFileName.startsWith("TO_WEATHER_") -> {
                        djToWeatherFiles.add(file.uri)
                    }
                    upperFileName.startsWith("MORNING_") -> {
                        djMorningFiles.add(file.uri)
                    }
                    upperFileName.startsWith("INTRO_") -> {
                        genericSongIntros.add(file.uri)
                    }
                    upperFileName.startsWith("OUTRO_") -> {
                        genericSongOutros.add(file.uri)
                    }
                }
            }

            setupAdvertFiles(advertsFolderDoc)
            setupNewsFiles(newsFolderDoc)
            setupWeatherFiles(weatherFolderDoc)
            if (!isTalkRadio) {
                val songFolder = baseStationFolderDoc.findFile(songFolderName)
                setupSongs(songFolder)
            } else {
                val showFolder = baseStationFolderDoc.findFile(songFolderName)
                setupTalkShows(showFolder)
            }
            randomizeAll()
        }
    }

    private fun setupAdvertFiles(advertsFolderDoc: DocumentFile) {
        advertFiles = advertsFolderDoc.listSimpleFiles(context).map { x -> x.uri }.toTypedArray()
    }

    private fun setupNewsFiles(newsFolderDoc: DocumentFile) {
        newsFiles = newsFolderDoc.listSimpleFiles(context).map { x -> x.uri }.toTypedArray()
        newsIterator = newsFiles.iterator()
    }

    private fun setupWeatherFiles(weatherFolderDoc: DocumentFile) {
        weatherFiles = weatherFolderDoc.listSimpleFiles(context).map { x -> x.uri }.toTypedArray()
    }

    private fun setupSongs(songFolderDoc: DocumentFile?) {
        if (songFolderDoc == null) {
            songs = emptyArray()
            return
        }
        val songFiles = songFolderDoc.listSimpleFiles(context)
        val mainSongFiles = songFiles.filter { x -> !x.name.contains("_") }
        val songsToUse = ArrayList<Song>()
        for (mainSong in mainSongFiles) {
            val songNameWithoutExtension = mainSong.name.split('.')[0]
            val intros = songFiles.filter { x -> x.name.startsWith(songNameWithoutExtension + "_") }.map {x -> x.uri }.toTypedArray()
            songsToUse.add(Song(intros, mainSong.uri))
        }
        songs = songsToUse.toTypedArray()
    }

    private fun setupTalkShows(showsFolderDoc: DocumentFile?) {
        if (showsFolderDoc == null) {
            talkShows = emptyArray()
            return
        }
        talkShows = showsFolderDoc.listSimpleFiles(context).map { x -> Song(emptyArray(), x.uri)}.toTypedArray()
    }

    private fun randomizeAll() {
        randomizeAnnouncerFiles()
        randomizeAdvertFiles()
        randomizeMainDJFiles()
        randomizeSongs()
        randomizeWeatherFiles()
        randomizeToAdDjFiles()
        randomizeToNewsDjFiles()
        randomizeToWeatherDjFiles()
        randomizeSoloDjFiles()
        randomizeGenericSongIntros()
        randomizeGenericSongOutros()
        randomizeTalkShows()
    }

    private fun randomizeAnnouncerFiles() {
        announcerFiles.shuffle()
        announcerIterator = announcerFiles.iterator()
    }

    private fun randomizeAdvertFiles() {
        advertFiles.shuffle()
        advertIterator = advertFiles.iterator()
    }

    private fun randomizeWeatherFiles() {
        weatherFiles.shuffle()
        weatherIterator = weatherFiles.iterator()
    }

    private fun randomizeMainDJFiles() {
        //Include the relevant time-of-day DJ chatter based on current time
        //Note: There is no afternoon slot currently based on the anticipated user dataset
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        var arrToConcat: ArrayList<Uri> = ArrayList()
        if (currentHour in 3..11) {
            //Morning (3am-noon) (03:00 to 12:00)
            djMorningFiles.shuffle()
            arrToConcat = djMorningFiles
        } else if(currentHour in 12..16) {
            //Afternoon (noon-5pm) (12:00-17:00)
            djAfternoonFiles.shuffle()
            arrToConcat = djAfternoonFiles
        } else if(currentHour in 17..22) {
            //Evening (5pm-11pm) (17:00-23:00)
            djEveningFiles.shuffle()
            arrToConcat = djEveningFiles
        } else if(currentHour < 3 || currentHour >= 23) {
            //Night (11pm-3am) (23:00-03:00)
            djNightFiles.shuffle()
            arrToConcat = djNightFiles
        }
        djGeneralFiles.shuffle()
        currentDJFiles = djGeneralFiles.plus(arrToConcat).toTypedArray()

        currentDJFiles.shuffle()
        djMainIterator = currentDJFiles.iterator()
    }

    private fun randomizeSoloDjFiles() {
        djSoloFiles.shuffle()
        djSoloIterator = djSoloFiles.iterator()
    }

    private fun randomizeToAdDjFiles() {
        djToAdFiles.shuffle()
        djToAdIterator = djToAdFiles.iterator()
    }

    private fun randomizeToNewsDjFiles() {
        djToNewsFiles.shuffle()
        djToNewsIterator = djToNewsFiles.iterator()
    }

    private fun randomizeToWeatherDjFiles() {
        djToWeatherFiles.shuffle()
        djToWeatherIterator = djToWeatherFiles.iterator()
    }

    private fun randomizeTalkShows() {
        talkShows.shuffle()
        talkShowIterator = talkShows.iterator()
    }

    private fun randomizeSongs() {
        songs.shuffle()
        songIterator = songs.iterator()
    }

    private fun randomizeGenericSongIntros() {
        genericSongIntros.shuffle()
        genericSongIntrosIterator = genericSongIntros.iterator()
    }

    private fun randomizeGenericSongOutros() {
        genericSongOutros.shuffle()
        genericSongOutrosIterator = genericSongOutros.iterator()
    }

    private fun getNextAdvertFile(): Uri {
        if (!advertIterator!!.hasNext()) {
            randomizeAdvertFiles()
        }
        return advertIterator!!.next()
    }

    private fun getNextNewsFile(): Uri {
        if (!newsIterator!!.hasNext()) {
            newsIterator = newsFiles.iterator()
        }
        return newsIterator!!.next()
    }

    private fun getNextWeatherFile(): Uri {
        if (!weatherIterator!!.hasNext()) {
            randomizeWeatherFiles()
        }
        return weatherIterator!!.next()
    }


    private fun getNextToAdFile(): Uri {
        if (!djToAdIterator!!.hasNext()) {
            randomizeToAdDjFiles()
        }
        return djToAdIterator!!.next()
    }

    private fun getNextToNewsFile(): Uri {
        if (!djToNewsIterator!!.hasNext()) {
            randomizeToNewsDjFiles()
        }
        return djToNewsIterator!!.next()
    }
    private fun getNextToWeatherFile(): Uri {
        if (!djToWeatherIterator!!.hasNext()) {
            randomizeToWeatherDjFiles()
        }
        return djToWeatherIterator!!.next()
    }

    private fun getNextAnnouncerFile(): Uri {
        if (!announcerIterator!!.hasNext()) {
            randomizeAnnouncerFiles()
        }
        return announcerIterator!!.next()
    }

    private fun getNextDjFile(): Uri {
        if (!djMainIterator!!.hasNext()) {
            randomizeMainDJFiles()
        }
        return djMainIterator!!.next()
    }

    private fun getNextSoloDjFile(): Uri {
        if (!djSoloIterator!!.hasNext()) {
            randomizeSoloDjFiles()
        }
        return djSoloIterator!!.next()
    }

    private fun getNextSong(): Song {
        if (!songIterator!!.hasNext()) {
            randomizeSongs()
        }
        return songIterator!!.next()
    }

    private fun getNextGenericSongIntro(): Uri {
        if (!genericSongIntrosIterator!!.hasNext()) {
            randomizeGenericSongIntros()
        }
        return genericSongIntrosIterator!!.next()
    }

    private fun getNextGenericSongOutro(): Uri {
        if (!genericSongOutrosIterator!!.hasNext()) {
            randomizeGenericSongOutros()
        }
        return genericSongOutrosIterator!!.next()
    }

    private fun getNextTalkShow(): Song {
        if (!talkShowIterator!!.hasNext()) {
            talkShowIterator = talkShows.iterator()
        }
        return talkShowIterator!!.next()
    }


    override fun stop() {
        if (gen1Station == null) {
            _playing = false
            player.stop()
        } else {
            gen1Station.stop()
        }
    }

    override fun play() {
        if (gen1Station == null) {
            _playing = true
            player.radioPlaybackState = PlaybackStateCompat.STATE_PLAYING
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.playWhenReady = false
            playNext()
        } else {
            gen1Station.play()
        }
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
                    val nextThing = rng.nextInt(7)
                    lastSegment = RadioSegmentType.fromInt(nextThing)
                    playNext()
                }
                RadioSegmentType.Commercial, RadioSegmentType.NewsReport -> {
                    //Having just heard a commercial (or the news), we'd like to hear a song or an announcer spot
                    val songOrAnnouncer = rng.nextInt(11)
                    if (!isTalkRadio && (songOrAnnouncer < 6 || announcerFiles.isEmpty())) {
                        //Song
                        playNextSong(true)
                    } else {
                        //For talk radio we just always want an announcer spot after a commercial
                        playNextAnnouncerSpotOrDjSolo()
                    }
                }
                RadioSegmentType.Announcer -> {
                    //Having just heard an announcer spot, we'd like to hear a song with/without intro or DJ chatter
                    val songOrDj = rng.nextInt(11)
                    if (songOrDj < 3 || currentDJFiles.isEmpty()) {
                        //Song
                        playNextSong(true)
                    } else {
                        playNextDjOrWeatherReport()
                    }
                }
                RadioSegmentType.DJChatter, RadioSegmentType.WeatherReport -> {
                    //Having just heard the DJ talk (or a weather break), we'd like to hear a commercial or song with/without intro
                    if (songsSinceLastBreak == 0) {
                        //Need to play a song here
                        playNextSong()
                    } else {
                        //We can either do a song or a commercial
                        val numberOfSongsBeforeBreak =
                            rng.nextInt(3) + 3 //gives us a number from 3-6
                        if (adsEnabled && numberOfSongsBeforeBreak <= songsSinceLastBreak) {
                            //Time for a commercial or news
                            playNextAdvertOrNewsReport()
                        } else {
                            //Play another song
                            playNextSong()
                        }
                    }

                }
                RadioSegmentType.Song -> {
                    //Having just heard a song play, we'd like to hear another song, commercial, dj chatter, or announcer
                    val numberOfSongsBeforeBreak = rng.nextInt(3) + 3 //gives us a number from 3-6
                    if (isTalkRadio || (adsEnabled && numberOfSongsBeforeBreak <= songsSinceLastBreak)) {
                        //Time for a commercial or the news
                        //But for talk radio stations, always attempt to play an ad after each talk segment
                        playNextAdvertOrNewsReport()
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
                                playNextAnnouncerSpotOrDjSolo()
                            }
                            else -> {
                                //DJ
                                playNextDjOrWeatherReport()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun playNextSong(requireDjIntro: Boolean = false) {
        songsSinceLastBreak += 1
        lastSegment = RadioSegmentType.Song
        if (isTalkRadio) {
            if (talkShows.isNotEmpty()) {
                //This is talk radio so play a talk show
                val nextShow = getNextTalkShow()
                playSong(nextShow)
            } else {
                playNext()
            }
        } else {
            //Play music since this is a music station
            if (songs.isNotEmpty()) {
                val nextSong = getNextSong()
                playSong(nextSong, requireDjIntro)
            } else {
                playNext()
            }
        }
    }

    private fun playSong(song: Song, requireDjIntro: Boolean = false)
    {
        val introToUse = if (requireDjIntro && song.intros.isNotEmpty()) {
            //Use a random song-specific dj intro
            val introIndex = rng.nextInt(song.intros.size)
            song.intros[introIndex]
        } else if (requireDjIntro && genericSongIntros.isNotEmpty()) {
            //Use a generic intro
            getNextGenericSongIntro()
        } else {
            val djOrNone = rng.nextInt(11)
            if (djOrNone < 6 && song.intros.isNotEmpty()) {
                //Use a song-specific DJ intro
                val introIndex = rng.nextInt(song.intros.size)
                song.intros[introIndex]
            } else if (djOrNone < 6 && genericSongIntros.isNotEmpty()) {
                //Use a generic DJ intro
                getNextGenericSongIntro()
            } else {
                null
            }
        }

        //Decide whether or not to use an outro if we have them
        val outroToUse = if (genericSongOutros.isNotEmpty()) {
            val useOutro = rng.nextInt(2) //50% chance we use an outro
            if (useOutro == 0) {
                getNextGenericSongOutro()
            } else {
                null
            }
        } else {
            null
        }

        val mediaSource = if (introToUse != null || outroToUse != null) {
            //We have an intro and/or an outro, lets construct the concat source
            val sources = ArrayList<MediaSource>()
            if (introToUse != null) {
                val introSrc = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(
                    MediaItem.fromUri(introToUse))
                sources.add(introSrc)
            }
            sources.add(ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(
                MediaItem.fromUri(song.mainSongUri)))
            if (outroToUse != null) {
                val outroSrc = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(
                    MediaItem.fromUri(outroToUse))
                sources.add(outroSrc)
            }

            val concatSource = ConcatenatingMediaSource(true)
            concatSource.addMediaSources(sources.toList())
            concatSource
        } else {
            //No intros or outros, just play the song
            ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(
                MediaItem.fromUri(song.mainSongUri))
        }

        playSource(mediaSource)
    }

    private fun playFile(file: Uri)
    {
        // Set the media item to be played.
        val src = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(
            MediaItem.fromUri(file))
        playSource(src)
    }

    private fun playSource(source: MediaSource) {
        player.setMediaSource(source)

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

    private fun playNextAdvertOrNewsReport() {
        songsSinceLastBreak = 0
        val advertOrNews = rng.nextInt(11)
        if (advertOrNews in 0..7) {
            //70% of the time we want to play an advert
            playNextAdvert()
        } else {
            playNextNewsReport()
        }
    }

    private fun playNextDjOrWeatherReport() {
        val advertOrWeather = rng.nextInt(11)
        if (advertOrWeather in 0..8) {
            //80% of the time we want to play dj chatter
            playNextDjChatter()
        } else {
            playNextWeatherReport()
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

    private fun playNextAnnouncerSpotOrDjSolo() {
        val announcerOrDj = rng.nextInt(2)
        if (isTalkRadio || announcerOrDj  == 0) {
            //Talk show radio should only have announcer spots, but otherwise 50% of the time we want to play an announcer spot
            playNextAnnouncerSpot()
        } else {
            playNextDjSoloSpot()
        }
    }

    private fun playNextAnnouncerSpot() {
        lastSegment = RadioSegmentType.Announcer
        if (announcerFiles.isNotEmpty()) {
            val nextAnn  = getNextAnnouncerFile()
            playFile(nextAnn)
        } else {
            playNext()
        }
    }

    private fun playNextDjSoloSpot() {
        lastSegment = RadioSegmentType.Announcer
        if (djSoloFiles.isNotEmpty()) {
            val nextSolo  = getNextSoloDjFile()
            playFile(nextSolo)
        } else {
            playNext()
        }
    }

    private fun playNextAdvert(forceSkipTransition: Boolean = false) {
        lastSegment = RadioSegmentType.Commercial
        if (!adsEnabled || advertFiles.isEmpty()) {
            playNext()
            return
        }
        val useDjTransition = rng.nextInt(2) == 0 //50/50 shot at using a DJ transition
        val mediaSource = if (!forceSkipTransition && useDjTransition && djToAdFiles.isNotEmpty()) {
            //Use a DJ transition to introduce the ad break
            val toAdFile = getNextToAdFile()
            val adFile = getNextAdvertFile()
            val toAdSrc = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(
                MediaItem.fromUri(toAdFile))
            val adSrc = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(
                MediaItem.fromUri(adFile))
            val concatSource = ConcatenatingMediaSource(true)
            concatSource.addMediaSources(listOf(toAdSrc, adSrc))
            concatSource
        } else {
            //No transition, just play the ad
            val adFile = getNextAdvertFile()
            ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(
                MediaItem.fromUri(adFile))
        }
        playSource(mediaSource)
    }

    private fun playNextNewsReport() {
        lastSegment = RadioSegmentType.NewsReport
        if (!newsReportsEnabled || newsFiles.isEmpty()) {
            playNext()
            return
        }
        val useDjTransition = rng.nextInt(2) == 0 //50/50 shot at using a DJ transition
        val mediaSource = if (useDjTransition && djToNewsFiles.isNotEmpty()) {
            //Use a DJ transition to introduce the news
            val toNewsFile = getNextToNewsFile()
            val newsFile = getNextNewsFile()
            val toNewsSrc = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(
                MediaItem.fromUri(toNewsFile))
            val newsSrc = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(
                MediaItem.fromUri(newsFile))
            val concatSource = ConcatenatingMediaSource(true)
            concatSource.addMediaSources(listOf(toNewsSrc, newsSrc))
            concatSource
        } else {
            //No transition, just play the news
            val newsFile = getNextNewsFile()
            ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(
                MediaItem.fromUri(newsFile))
        }
        playSource(mediaSource)
    }

    private fun playNextWeatherReport() {
        lastSegment = RadioSegmentType.WeatherReport
        if (!weatherChatterEnabled || weatherFiles.isEmpty()) {
            playNext()
            return
        }
        val useDjTransition = rng.nextInt(2) == 0 //50/50 shot at using a DJ transition
        val mediaSource = if (useDjTransition && djToWeatherFiles.isNotEmpty()) {
            //Use a DJ transition to introduce the weather
            val toWeatherFile = getNextToWeatherFile()
            val weatherFile = getNextWeatherFile()
            val toWeatherSrc = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(
                MediaItem.fromUri(toWeatherFile))
            val weatherSrc = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(
                MediaItem.fromUri(weatherFile))
            val concatSource = ConcatenatingMediaSource(true)
            concatSource.addMediaSources(listOf(toWeatherSrc, weatherSrc))
            concatSource
        } else {
            //No transition, just play the weather
            val weatherFile = getNextWeatherFile()
            ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))).createMediaSource(
                MediaItem.fromUri(weatherFile))
        }
        playSource(mediaSource)
    }

    private inner class Song(val intros: Array<Uri>, val mainSongUri: Uri)

}