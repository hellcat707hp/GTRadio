package com.sc.gtradio

import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.sc.gtradio.databinding.FragmentNowplayingBinding
import com.sc.gtradio.media.*
import com.sc.gtradio.utils.InjectorUtils

class NowPlayingFragment : Fragment() {
    private var _binding: FragmentNowplayingBinding? = null

    private var musicServiceConnection: MusicServiceConnection? = null

    private var playbackState: PlaybackStateCompat = EMPTY_PLAYBACK_STATE
    private val playbackStateObserver = Observer<PlaybackStateCompat> {
        playbackState = it ?: EMPTY_PLAYBACK_STATE
        val metadata = musicServiceConnection?.nowPlaying?.value ?: NOTHING_PLAYING
        updateState(playbackState, metadata)
    }

    private val mediaMetadataObserver = Observer<MediaMetadataCompat> {
        updateState(playbackState, it)
    }

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        if (musicServiceConnection == null) {
            musicServiceConnection = InjectorUtils.provideMusicServiceConnection(requireContext()).also {
                it.playbackState.observe(viewLifecycleOwner, playbackStateObserver)
                it.nowPlaying.observe(viewLifecycleOwner, mediaMetadataObserver)
            }
        }

        _binding = FragmentNowplayingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.playStopButton.setOnClickListener {
            if (playbackState.isPlaying) {
                musicServiceConnection?.transportControls?.stop()
            } else {
                musicServiceConnection?.transportControls?.play()
            }
        }
        binding.nextButton.setOnClickListener {
            musicServiceConnection?.transportControls?.skipToNext()
        }
        binding.previousButton.setOnClickListener {
            musicServiceConnection?.transportControls?.skipToPrevious()
        }

        binding.stationTitle.text = getString(R.string.nothing_playing)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateState(playbackState: PlaybackStateCompat, mediaMetadata: MediaMetadataCompat) {

        // Only update media if the item exists
        if (mediaMetadata.description.mediaId?.isNotBlank() == true) {
            binding.stationTitle.text = mediaMetadata.description.title
            binding.albumArt.setImageBitmap(mediaMetadata.description.iconBitmap)
        } else {
            binding.stationTitle.text = getString(R.string.nothing_playing)
        }

        //Update the play buttons
        if (playbackState.isPlaying) {
            binding.playStopButton.setImageResource(R.drawable.ic_baseline_stop)
        } else {
            binding.playStopButton.setImageResource(R.drawable.ic_baseline_play_arrow)
        }

        binding.previousButton.visibility = if (playbackState.isSkipToPreviousEnabled) View.VISIBLE else View.INVISIBLE
        binding.nextButton.visibility = if (playbackState.isSkipToNextEnabled) View.VISIBLE else View.INVISIBLE
    }

}