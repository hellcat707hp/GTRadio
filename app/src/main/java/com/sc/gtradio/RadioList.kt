package com.sc.gtradio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sc.gtradio.utils.InjectorUtils
import com.sc.gtradio.databinding.FragmentStationitemBinding
import com.sc.gtradio.databinding.RadioListBinding
import com.sc.gtradio.media.*

class RadioList() : Fragment() {
    private var _binding: RadioListBinding? = null

    private var musicServiceConnection: MusicServiceConnection? = null

    private val subscriptionCallback = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(parentId: String, children: List<MediaBrowserCompat.MediaItem>) {
            listAdapter.submitList(children)
        }
    }

    private val listAdapter = MediaItemListAdapter { clickedItem ->
        val nowPlaying = musicServiceConnection?.nowPlaying?.value
        val transportControls = musicServiceConnection?.transportControls

        if (clickedItem.mediaId == nowPlaying?.description?.mediaId) {
            musicServiceConnection!!.playbackState.value?.let { playbackState ->
                when {
                    playbackState.isPlaying ->
                        transportControls?.stop()
                    playbackState.isPlayEnabled ->
                        transportControls?.play()
                    else -> {
                        transportControls?.play()
                    }
                }
            }
        } else {
            transportControls?.playFromMediaId(clickedItem.mediaId, null)
        }
    }

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        if (musicServiceConnection == null) {
            musicServiceConnection = InjectorUtils.provideMusicServiceConnection(requireContext()).also {
                it.subscribe("root", subscriptionCallback)
            }
        }

        _binding = RadioListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonSelect.setOnClickListener {
            val myIntent = Intent(this.activity, SettingsActivity::class.java)
            this.startActivity(myIntent)
        }

        val sharedPref = this.activity?.getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)
        val radioUri = sharedPref?.getString(getString(R.string.radio_folders_uri_key), "")
        if (radioUri.isNullOrEmpty()) {
            //User has no library selected, allow them to select something
            binding.buttonSelect.visibility = View.VISIBLE
            binding.textviewNofolder.visibility = View.VISIBLE
       } else {
            binding.buttonSelect.visibility = View.INVISIBLE
            binding.textviewNofolder.visibility = View.INVISIBLE
       }

        binding.stationList.layoutManager = LinearLayoutManager(context)
        binding.stationList.adapter = listAdapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onActivityResult(
        requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == R.integer.read_storage_permission_request && resultCode == Activity.RESULT_OK) {
            // The user allowed us to read storage, might want to do something here at some point
        }
    }
}


class MediaItemListAdapter(
    private val itemClickedListener: (MediaBrowserCompat.MediaItem) -> Unit
) : ListAdapter<MediaBrowserCompat.MediaItem, MediaViewHolder>(MediaItemDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = FragmentStationitemBinding.inflate(inflater, parent, false)
        return MediaViewHolder(binding, itemClickedListener)
    }

    override fun onBindViewHolder(
        holder: MediaViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val mediaItem = getItem(position)
        val fullRefresh = payloads.isEmpty()

        // Normally we only fully refresh the list item if it's being initially bound, but
        // we might also do it if there was a payload that wasn't understood, just to ensure
        // there isn't a stale item.
        if (fullRefresh) {
            holder.item = mediaItem
            holder.titleView.text = mediaItem.description.title
            holder.albumArt.setImageBitmap(mediaItem.description.iconBitmap)
        }
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }
}

class MediaViewHolder(
    binding: FragmentStationitemBinding,
    itemClickedListener: (MediaBrowserCompat.MediaItem) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    val titleView: TextView = binding.title
    val albumArt: ImageView = binding.albumArt

    var item: MediaBrowserCompat.MediaItem? = null

    init {
        binding.root.setOnClickListener {
            item?.let { itemClickedListener(it) }
        }
    }
}

val MediaItemDiffCallback =  object : DiffUtil.ItemCallback<MediaBrowserCompat.MediaItem>() {
    override fun areItemsTheSame(
        oldItem: MediaBrowserCompat.MediaItem,
        newItem: MediaBrowserCompat.MediaItem
    ): Boolean =
        oldItem.mediaId == newItem.mediaId

    override fun areContentsTheSame(oldItem: MediaBrowserCompat.MediaItem, newItem: MediaBrowserCompat.MediaItem) =
        oldItem.mediaId == newItem.mediaId

    override fun getChangePayload(oldItem: MediaBrowserCompat.MediaItem, newItem: MediaBrowserCompat.MediaItem): Nothing? =
        null
}
