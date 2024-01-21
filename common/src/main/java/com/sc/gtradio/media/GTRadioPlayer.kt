import android.media.AudioDeviceInfo
import android.os.Looper
import android.support.v4.media.session.PlaybackStateCompat
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.PriorityTaskManager
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Clock
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsCollector
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.TrackSelectionArray
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.exoplayer.video.spherical.CameraMotionListener

/**
 * A custom ExoPlayer container that more accurately represents certain attributes about our faux live-stream behavior
 */
@UnstableApi class GTRadioPlayer(private val exoPlayer: ExoPlayer): ExoPlayer {

    var radioPlaybackState: Int = PlaybackStateCompat.STATE_NONE

    var nextEnabled = false
    var previousEnabled = false

    //region Customized Functionality
    //-----------
    override fun getPlaybackState(): Int {
        if (radioPlaybackState == PlaybackStateCompat.STATE_PLAYING) {
            return Player.STATE_READY
        } else if (radioPlaybackState == PlaybackStateCompat.STATE_STOPPED) {
            return Player.STATE_IDLE
        }
        return exoPlayer.playbackState
    }
    override fun isPlaying(): Boolean {
        if (radioPlaybackState == PlaybackStateCompat.STATE_PLAYING) {
            return true
        }
        return exoPlayer.isPlaying
    }

    override fun play() {
        radioPlaybackState = PlaybackStateCompat.STATE_PLAYING
        exoPlayer.play()
    }

    override fun pause() {
        radioPlaybackState = PlaybackStateCompat.STATE_PAUSED
        exoPlayer.pause()
    }

    override fun hasPrevious(): Boolean {
        return previousEnabled
    }

    override fun hasPreviousWindow(): Boolean {
        return exoPlayer.hasPreviousWindow()
    }

    override fun hasPreviousMediaItem(): Boolean {
        return previousEnabled
    }

    override fun seekToPreviousWindow() {
        return exoPlayer.seekToPreviousWindow()
    }

    override fun seekToPreviousMediaItem() {
        return exoPlayer.seekToPreviousMediaItem()
    }

    override fun getMaxSeekToPreviousPosition(): Long {
        return exoPlayer.maxSeekToPreviousPosition
    }

    override fun seekToPrevious() {
        return exoPlayer.seekToPrevious()
    }

    override fun hasNext(): Boolean {
        return nextEnabled
    }

    override fun hasNextWindow(): Boolean {
        return exoPlayer.hasNextWindow()
    }

    override fun hasNextMediaItem(): Boolean {
        return nextEnabled;
    }

    override fun seekToNextWindow() {
        return exoPlayer.seekToNextWindow()
    }

    override fun seekToNextMediaItem() {
        return exoPlayer.seekToNextMediaItem()
    }

    override fun seekToNext() {
        return exoPlayer.seekToNext()
    }

    override fun getDuration(): Long {
        //For a faux live-stream, no items have a known duration
        return -1L
    }

    fun getCurrentTrackDuration(): Long {
        return exoPlayer.duration
    }

    override fun getCurrentPosition(): Long {
        //For a faux live-stream, we never really know the official "position" we are in
        return -1L
    }

    fun getCurrentTrackPosition(): Long {
        return exoPlayer.currentPosition
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when(command) {
            Player.COMMAND_PLAY_PAUSE -> {
                true
            }
            Player.COMMAND_STOP -> {
                isPlaying
            }
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                hasNext()
            }
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                hasPrevious()
            }
            else -> {
                exoPlayer.isCommandAvailable(command)
            }
        }
    }

    override fun canAdvertiseSession(): Boolean {
        return exoPlayer.canAdvertiseSession()
    }

    override fun getAvailableCommands(): Player.Commands {
        return exoPlayer.availableCommands
    }
    //---------
    //endregion

    override fun previous() {
        exoPlayer.previous()
    }

    override fun next() {
        exoPlayer.next()
    }

    override fun getApplicationLooper(): Looper {
        return exoPlayer.applicationLooper
    }

    override fun addListener(listener: Player.Listener) {
        exoPlayer.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        exoPlayer.removeListener(listener)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        exoPlayer.setMediaItems(mediaItems)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        exoPlayer.setMediaItems(mediaItems)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startWindowIndex: Int, startPositionMs: Long) {
        exoPlayer.setMediaItems(mediaItems, startWindowIndex, startPositionMs)
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        exoPlayer.setMediaItem(mediaItem)
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        exoPlayer.setMediaItem(mediaItem, startPositionMs)
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        exoPlayer.setMediaItem(mediaItem, resetPosition)
    }

    override fun setMediaSource(mediaSource: MediaSource, startPositionMs: Long) {
        exoPlayer.setMediaSource(mediaSource, startPositionMs)
    }

    override fun setMediaSource(mediaSource: MediaSource, resetPosition: Boolean) {
        exoPlayer.setMediaSource(mediaSource, resetPosition)
    }

    override fun addMediaSource(mediaSource: MediaSource) {
        exoPlayer.addMediaSource(mediaSource)
    }

    override fun addMediaSource(index: Int, mediaSource: MediaSource) {
        exoPlayer.addMediaSource(index,mediaSource)
    }

    override fun addMediaSources(mediaSources: MutableList<MediaSource>) {
        exoPlayer.addMediaSources(mediaSources)
    }

    override fun addMediaSources(index: Int, mediaSources: MutableList<MediaSource>) {
        exoPlayer.addMediaSources(index, mediaSources)
    }

    override fun setShuffleOrder(shuffleOrder: ShuffleOrder) {
        exoPlayer.setShuffleOrder(shuffleOrder)
    }

    override fun setAudioSessionId(audioSessionId: Int) {
        TODO("Not yet implemented")
    }

    override fun getAudioSessionId(): Int {
        TODO("Not yet implemented")
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        TODO("Not yet implemented")
    }

    override fun clearAuxEffectInfo() {
        TODO("Not yet implemented")
    }

    override fun setPreferredAudioDevice(audioDeviceInfo: AudioDeviceInfo?) {
        TODO("Not yet implemented")
    }

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        TODO("Not yet implemented")
    }

    override fun getSkipSilenceEnabled(): Boolean {
        TODO("Not yet implemented")
    }

    override fun setVideoEffects(videoEffects: MutableList<Effect>) {
        TODO("Not yet implemented")
    }

    override fun setVideoScalingMode(videoScalingMode: Int) {
        TODO("Not yet implemented")
    }

    override fun getVideoScalingMode(): Int {
        TODO("Not yet implemented")
    }

    override fun setVideoChangeFrameRateStrategy(videoChangeFrameRateStrategy: Int) {
        TODO("Not yet implemented")
    }

    override fun getVideoChangeFrameRateStrategy(): Int {
        TODO("Not yet implemented")
    }

    override fun setVideoFrameMetadataListener(listener: VideoFrameMetadataListener) {
        TODO("Not yet implemented")
    }

    override fun clearVideoFrameMetadataListener(listener: VideoFrameMetadataListener) {
        TODO("Not yet implemented")
    }

    override fun setCameraMotionListener(listener: CameraMotionListener) {
        TODO("Not yet implemented")
    }

    override fun clearCameraMotionListener(listener: CameraMotionListener) {
        TODO("Not yet implemented")
    }

    override fun createMessage(target: PlayerMessage.Target): PlayerMessage {
        return exoPlayer.createMessage(target)
    }

    override fun setSeekParameters(seekParameters: SeekParameters?) {
        exoPlayer.setSeekParameters(seekParameters)
    }

    override fun getSeekParameters(): SeekParameters {
        return exoPlayer.seekParameters
    }

    override fun setForegroundMode(foregroundMode: Boolean) {
        exoPlayer.setForegroundMode(foregroundMode)
    }

    override fun setPauseAtEndOfMediaItems(pauseAtEndOfMediaItems: Boolean) {
        exoPlayer.pauseAtEndOfMediaItems = pauseAtEndOfMediaItems
    }

    override fun getPauseAtEndOfMediaItems(): Boolean {
        return exoPlayer.pauseAtEndOfMediaItems
    }

    override fun getAudioFormat(): Format? {
        TODO("Not yet implemented")
    }

    override fun getVideoFormat(): Format? {
        TODO("Not yet implemented")
    }

    override fun getAudioDecoderCounters(): DecoderCounters? {
        TODO("Not yet implemented")
    }

    override fun getVideoDecoderCounters(): DecoderCounters? {
        TODO("Not yet implemented")
    }

    override fun setHandleAudioBecomingNoisy(handleAudioBecomingNoisy: Boolean) {
        TODO("Not yet implemented")
    }

    override fun setWakeMode(wakeMode: Int) {
        TODO("Not yet implemented")
    }

    override fun setPriorityTaskManager(priorityTaskManager: PriorityTaskManager?) {
        TODO("Not yet implemented")
    }

    override fun isSleepingForOffload(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isTunnelingEnabled(): Boolean {
        TODO("Not yet implemented")
    }

    override fun addMediaItem(mediaItem: MediaItem) {
        exoPlayer.addMediaItem(mediaItem)
    }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) {
        exoPlayer.addMediaItem(index, mediaItem)
    }

    override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
        exoPlayer.addMediaItems(mediaItems)
    }

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        exoPlayer.addMediaItems(index, mediaItems)
    }

    override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
        exoPlayer.moveMediaItem(currentIndex, newIndex)
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        exoPlayer.moveMediaItems(fromIndex, toIndex, newIndex)
    }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
        TODO("Not yet implemented")
    }

    override fun replaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: MutableList<MediaItem>
    ) {
        TODO("Not yet implemented")
    }

    override fun removeMediaItem(index: Int) {
        exoPlayer.removeMediaItem(index)
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        exoPlayer.removeMediaItems(fromIndex, toIndex)
    }

    override fun clearMediaItems() {
        exoPlayer.clearMediaItems()
    }

    override fun prepare(mediaSource: MediaSource) {
        exoPlayer.prepare()
    }

    override fun prepare(mediaSource: MediaSource, resetPosition: Boolean, resetState: Boolean) {
        exoPlayer.prepare(mediaSource, resetPosition, resetState)
    }

    override fun prepare() {
        exoPlayer.prepare()
    }

    override fun getPlaybackSuppressionReason(): Int {
        return exoPlayer.playbackSuppressionReason
    }

    override fun getPlayerError(): ExoPlaybackException? {
        return exoPlayer.playerError
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        exoPlayer.playWhenReady = playWhenReady
    }

    override fun getPlayWhenReady(): Boolean {
        return exoPlayer.playWhenReady
    }

    override fun setRepeatMode(repeatMode: Int) {
        exoPlayer.repeatMode = repeatMode
    }

    override fun getRepeatMode(): Int {
        return exoPlayer.repeatMode
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        exoPlayer.shuffleModeEnabled = shuffleModeEnabled
    }

    override fun getShuffleModeEnabled(): Boolean {
        return exoPlayer.shuffleModeEnabled
    }

    override fun isLoading(): Boolean {
        return exoPlayer.isLoading
    }

    override fun seekToDefaultPosition() {
        exoPlayer.seekToDefaultPosition()
    }

    override fun seekToDefaultPosition(windowIndex: Int) {
        exoPlayer.seekToDefaultPosition(windowIndex)
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    override fun seekTo(windowIndex: Int, positionMs: Long) {
        exoPlayer.seekTo(windowIndex, positionMs)
    }

    override fun getSeekBackIncrement(): Long {
        TODO("Not yet implemented")
    }

    override fun seekBack() {
        TODO("Not yet implemented")
    }

    override fun getSeekForwardIncrement(): Long {
        TODO("Not yet implemented")
    }

    override fun seekForward() {
        TODO("Not yet implemented")
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        exoPlayer.playbackParameters = playbackParameters
    }

    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        return exoPlayer.playbackParameters
    }

    override fun stop() {
        radioPlaybackState = PlaybackStateCompat.STATE_STOPPED
        exoPlayer.stop()
    }

    override fun release() {
        exoPlayer.release()
    }

    override fun getCurrentTracks(): Tracks {
        TODO("Not yet implemented")
    }

    override fun getTrackSelectionParameters(): TrackSelectionParameters {
        TODO("Not yet implemented")
    }

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        TODO("Not yet implemented")
    }

    override fun getMediaMetadata(): MediaMetadata {
        return exoPlayer.mediaMetadata
    }

    override fun getPlaylistMetadata(): MediaMetadata {
        TODO("Not yet implemented")
    }

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
        TODO("Not yet implemented")
    }

    override fun getCurrentManifest(): Any? {
        return exoPlayer.currentManifest
    }

    override fun getCurrentTimeline(): Timeline {
        return exoPlayer.currentTimeline
    }

    override fun getCurrentPeriodIndex(): Int {
        return exoPlayer.currentPeriodIndex
    }

    override fun getCurrentWindowIndex(): Int {
        return exoPlayer.currentWindowIndex
    }

    override fun getCurrentMediaItemIndex(): Int {
        TODO("Not yet implemented")
    }

    override fun getNextWindowIndex(): Int {
        return exoPlayer.nextWindowIndex
    }

    override fun getNextMediaItemIndex(): Int {
        TODO("Not yet implemented")
    }

    override fun getPreviousWindowIndex(): Int {
        return exoPlayer.previousWindowIndex
    }

    override fun getPreviousMediaItemIndex(): Int {
        TODO("Not yet implemented")
    }

    override fun getCurrentMediaItem(): MediaItem? {
        return exoPlayer.currentMediaItem
    }

    override fun getMediaItemCount(): Int {
        return exoPlayer.mediaItemCount
    }

    override fun getMediaItemAt(index: Int): MediaItem {
        return exoPlayer.getMediaItemAt(index)
    }

    override fun getBufferedPosition(): Long {
        return exoPlayer.bufferedPosition
    }

    override fun getBufferedPercentage(): Int {
        return exoPlayer.bufferedPercentage
    }

    override fun getTotalBufferedDuration(): Long {
        return exoPlayer.totalBufferedDuration
    }

    override fun isCurrentWindowDynamic(): Boolean {
        return exoPlayer.isCurrentWindowDynamic
    }

    override fun isCurrentMediaItemDynamic(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isCurrentWindowLive(): Boolean {
        return exoPlayer.isCurrentWindowLive
    }

    override fun isCurrentMediaItemLive(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getCurrentLiveOffset(): Long {
        return exoPlayer.currentLiveOffset
    }

    override fun isCurrentWindowSeekable(): Boolean {
        return exoPlayer.isCurrentWindowSeekable
    }

    override fun isCurrentMediaItemSeekable(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isPlayingAd(): Boolean {
        return exoPlayer.isPlayingAd
    }

    override fun getCurrentAdGroupIndex(): Int {
        return exoPlayer.currentAdGroupIndex
    }

    override fun getCurrentAdIndexInAdGroup(): Int {
        return exoPlayer.currentAdIndexInAdGroup
    }

    override fun getContentDuration(): Long {
        return exoPlayer.contentDuration
    }

    override fun getContentPosition(): Long {
        return exoPlayer.contentPosition
    }

    override fun getContentBufferedPosition(): Long {
        return exoPlayer.contentBufferedPosition
    }

    override fun getAudioAttributes(): AudioAttributes {
        return exoPlayer.audioAttributes
    }

    override fun setVolume(audioVolume: Float) {
        exoPlayer.volume = audioVolume
    }

    override fun getVolume(): Float {
        return exoPlayer.volume
    }

    override fun getCurrentCues(): CueGroup {
        return exoPlayer.currentCues
    }

    override fun getDeviceInfo(): DeviceInfo {
        return exoPlayer.deviceInfo
    }

    override fun getDeviceVolume(): Int {
        return exoPlayer.deviceVolume
    }

    override fun isDeviceMuted(): Boolean {
        return exoPlayer.isDeviceMuted
    }

    override fun setDeviceVolume(volume: Int) {
        exoPlayer.deviceVolume = volume
    }

    override fun setDeviceVolume(volume: Int, flags: Int) {
        TODO("Not yet implemented")
    }

    override fun increaseDeviceVolume() {
        exoPlayer.increaseDeviceVolume()
    }

    override fun increaseDeviceVolume(flags: Int) {
        TODO("Not yet implemented")
    }

    override fun decreaseDeviceVolume() {
        exoPlayer.decreaseDeviceVolume()
    }

    override fun decreaseDeviceVolume(flags: Int) {
        TODO("Not yet implemented")
    }

    override fun setDeviceMuted(muted: Boolean) {
        exoPlayer.isDeviceMuted = muted
    }

    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        TODO("Not yet implemented")
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
        TODO("Not yet implemented")
    }

    override fun getAudioComponent(): ExoPlayer.AudioComponent? {
        return exoPlayer.audioComponent
    }

    override fun getVideoComponent(): ExoPlayer.VideoComponent? {
        return exoPlayer.videoComponent
    }

    override fun getTextComponent(): ExoPlayer.TextComponent? {
        return exoPlayer.textComponent
    }

    override fun getDeviceComponent(): ExoPlayer.DeviceComponent? {
        return exoPlayer.deviceComponent
    }

    override fun addAudioOffloadListener(listener: ExoPlayer.AudioOffloadListener) {
        exoPlayer.addAudioOffloadListener(listener)
    }

    override fun removeAudioOffloadListener(listener: ExoPlayer.AudioOffloadListener) {
        exoPlayer.removeAudioOffloadListener(listener)
    }

    override fun getAnalyticsCollector(): AnalyticsCollector {
        TODO("Not yet implemented")
    }

    override fun addAnalyticsListener(listener: AnalyticsListener) {
        TODO("Not yet implemented")
    }

    override fun removeAnalyticsListener(listener: AnalyticsListener) {
        TODO("Not yet implemented")
    }

    override fun getRendererCount(): Int {
        return exoPlayer.rendererCount
    }

    override fun getRendererType(index: Int): Int {
        return exoPlayer.getRendererType(index)
    }

    override fun getRenderer(index: Int): Renderer {
        TODO("Not yet implemented")
    }

    override fun getTrackSelector(): TrackSelector? {
        return exoPlayer.trackSelector
    }

    override fun getCurrentTrackGroups(): TrackGroupArray {
        TODO("Not yet implemented")
    }

    override fun getCurrentTrackSelections(): TrackSelectionArray {
        TODO("Not yet implemented")
    }

    override fun getPlaybackLooper(): Looper {
        return exoPlayer.playbackLooper
    }

    override fun getClock(): Clock {
        return exoPlayer.clock
    }

    override fun setMediaSources(mediaSources: MutableList<MediaSource>) {
        exoPlayer.setMediaSources(mediaSources)
    }

    override fun setMediaSources(mediaSources: MutableList<MediaSource>, resetPosition: Boolean) {
        exoPlayer.setMediaSources(mediaSources, resetPosition)
    }

    override fun setMediaSources(mediaSources: MutableList<MediaSource>, startWindowIndex: Int, startPositionMs: Long) {
        exoPlayer.setMediaSources(mediaSources, startWindowIndex, startPositionMs)
    }

    override fun setMediaSource(mediaSource: MediaSource) {
        exoPlayer.setMediaSource(mediaSource)
    }

    override fun clearVideoSurface() {
        TODO("Not yet implemented")
    }

    override fun clearVideoSurface(surface: Surface?) {
        TODO("Not yet implemented")
    }

    override fun setVideoSurface(surface: Surface?) {
        TODO("Not yet implemented")
    }

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        TODO("Not yet implemented")
    }

    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        TODO("Not yet implemented")
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        TODO("Not yet implemented")
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        TODO("Not yet implemented")
    }

    override fun setVideoTextureView(textureView: TextureView?) {
        TODO("Not yet implemented")
    }

    override fun clearVideoTextureView(textureView: TextureView?) {
        TODO("Not yet implemented")
    }

    override fun getVideoSize(): VideoSize {
        TODO("Not yet implemented")
    }

    override fun getSurfaceSize(): Size {
        TODO("Not yet implemented")
    }

}