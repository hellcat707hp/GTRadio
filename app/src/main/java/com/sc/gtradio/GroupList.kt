package com.sc.gtradio

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sc.gtradio.databinding.FragmentGrouplistBinding
import com.sc.gtradio.databinding.FragmentStationgroupitemBinding
import com.sc.gtradio.media.MusicServiceConnection
import com.sc.gtradio.utils.InjectorUtils

class GroupList : Fragment() {
    private var _binding: FragmentGrouplistBinding? = null

    private var musicServiceConnection: MusicServiceConnection? = null

    private val subscriptionCallback = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(parentId: String, children: List<MediaBrowserCompat.MediaItem>) {
            if (_binding != null) {
                val sharedPref = PreferenceManager.getDefaultSharedPreferences(requireActivity().applicationContext)
                val radioUri = sharedPref?.getString(getString(R.string.radio_folders_uri_key), "")

                if (children.isEmpty() && activity?.contentResolver?.persistedUriPermissions?.any { x -> x.uri == Uri.parse(radioUri) && x.isReadPermission } == true) {
                    //No stations listed and we have already selected a library, so show our nothing-found message
                    binding.textviewNogroups.visibility = View.VISIBLE
                    binding.sadFaceIcon.visibility = View.VISIBLE
                } else {
                    binding.textviewNogroups.visibility = View.INVISIBLE
                    binding.sadFaceIcon.visibility = View.INVISIBLE
                }
            }
            listAdapter.submitList(children)
        }
    }

    private val listAdapter = StationGroupMediaItemListAdapter { clickedItem ->
        musicServiceConnection?.sendCommand("setActiveStationGroupId", Bundle().apply { this.putString("stationGroupId", clickedItem.mediaId) })
        //This is real ugly
        (activity as MainActivity).navigateToRadioList(clickedItem.mediaId)
    }

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val contract = object : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            val intent = super.createIntent(context, input)
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            return intent
        }
    }

    private val permissionResultLauncher = registerForActivityResult(contract)
    { result: Uri? ->
        if (result == null) {
            return@registerForActivityResult
        }
        activity?.contentResolver?.takePersistableUriPermission(result, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val sharedPref = activity?.applicationContext?.let {
            PreferenceManager.getDefaultSharedPreferences(
                it
            )
        }
        with (sharedPref?.edit()) {
            this?.putString(getString(R.string.radio_folders_uri_key), result.toString())
            this?.apply()
        }

    }

    //Setup a listener for settings changes
    private val sharedPrefListener = GroupListOnSharedPrefChange()
    private inner class GroupListOnSharedPrefChange : SharedPreferences.OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            if (prefs != null) {
                when(key) {
                    resources.getString(R.string.radio_folders_uri_key) -> {
                        //Update stations list
                        updateLibrarySelectedComponents()
                        if (musicServiceConnection != null) {
                            musicServiceConnection!!.unsubscribe("root", subscriptionCallback)
                            musicServiceConnection!!.subscribe("root", subscriptionCallback)
                        }
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.requireActivity().applicationContext)
        sharedPref.registerOnSharedPreferenceChangeListener(sharedPrefListener)

        if (musicServiceConnection == null) {
            musicServiceConnection = InjectorUtils.provideMusicServiceConnection(requireContext()).also {
                it.subscribe("root", subscriptionCallback)
            }
        }

        _binding = FragmentGrouplistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonSelect.setOnClickListener {
            permissionResultLauncher.launch(null)
        }

        updateLibrarySelectedComponents()

        binding.stationList.layoutManager = LinearLayoutManager(context)
        binding.stationList.adapter = listAdapter
    }

    override fun onResume() {
        super.onResume()
        updateLibrarySelectedComponents()
    }

    private fun updateLibrarySelectedComponents() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.requireActivity().applicationContext)
        val radioUri = sharedPref?.getString(getString(R.string.radio_folders_uri_key), "")
        if (radioUri.isNullOrEmpty() || activity?.contentResolver?.persistedUriPermissions?.any { x -> x.uri == Uri.parse(radioUri) && x.isReadPermission } != true) {
            //User has no library selected, allow them to select something and possibly reset our pref value
            if (!radioUri.isNullOrEmpty()) {
                with (sharedPref?.edit()) {
                    this?.putString(getString(R.string.radio_folders_uri_key), "")
                    this?.apply()
                }
            }
            binding.buttonSelect.visibility = View.VISIBLE
            binding.textviewNofolder.visibility = View.VISIBLE
            binding.textviewNogroups.visibility = View.INVISIBLE
            binding.sadFaceIcon.visibility = View.INVISIBLE
        } else {
            binding.buttonSelect.visibility = View.INVISIBLE
            binding.textviewNofolder.visibility = View.INVISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this.requireActivity().applicationContext)
        sharedPref.unregisterOnSharedPreferenceChangeListener(sharedPrefListener)
        _binding = null
    }
}


class StationGroupMediaItemListAdapter(
    private val itemClickedListener: (MediaBrowserCompat.MediaItem) -> Unit
) : ListAdapter<MediaBrowserCompat.MediaItem, StationGroupViewHolder>(MediaItemDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationGroupViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = FragmentStationgroupitemBinding.inflate(inflater, parent, false)
        return StationGroupViewHolder(binding, itemClickedListener)
    }

    override fun onBindViewHolder(
        holder: StationGroupViewHolder,
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
            holder.groupArt.setImageBitmap(mediaItem.description.iconBitmap)
        }
    }

    override fun onBindViewHolder(holder: StationGroupViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }
}

class StationGroupViewHolder(
    binding: FragmentStationgroupitemBinding,
    itemClickedListener: (MediaBrowserCompat.MediaItem) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    val titleView: TextView = binding.groupTitle
    val groupArt: ImageView = binding.groupArt

    var item: MediaBrowserCompat.MediaItem? = null

    init {
        binding.root.setOnClickListener {
            item?.let { itemClickedListener(it) }
        }
    }
}

