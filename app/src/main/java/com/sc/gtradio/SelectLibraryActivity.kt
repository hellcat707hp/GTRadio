package com.sc.gtradio

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sc.gtradio.databinding.ActivitySelectLibraryBinding
import com.sc.gtradio.databinding.FragmentFolderitemBinding
import java.io.File
import java.lang.reflect.Field


class SelectLibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectLibraryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySelectLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        buildTabs()
    }

    private fun buildTabs() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val currentRadioPath = sharedPref?.getString(getString(R.string.radio_folders_uri_key), "")

        //Use StorageManager to list out the disk volumes
        val sm: StorageManager? = ContextCompat.getSystemService(
            applicationContext.applicationContext,
            StorageManager::class.java
        )
        val f: Field = StorageVolume::class.java.getDeclaredField("mPath")
        f.isAccessible = true

        //Build out the tabs to be placed at the top
        val tabLists = ArrayList<TabList>()
        for (volume in sm!!.storageVolumes) {
            val clickCallback = { clickedItem: FolderItem ->
                //Handle the item click by updating the settings and navigating back
                val sharedPref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                with (sharedPref?.edit()) {
                    this?.putString(getString(R.string.radio_folders_uri_key), clickedItem.fullPath)
                    this?.apply()
                }
                setResult(Activity.RESULT_OK)
                finish()
            }
            val volFile = f.get(volume) as File
            val folders = (volFile.list() ?: emptyArray()).map {
                return@map FolderItem(volFile.absolutePath+"/"+it, it, volFile.absolutePath+"/"+it == currentRadioPath)
            }
            tabLists.add(TabList(volume.getDescription(applicationContext), folders, clickCallback, tabLists.size+1))
        }
        binding.volumeListPager.adapter = ListViewPagerAdapter(supportFragmentManager, tabLists)
        binding.volumeTabs.setupWithViewPager(binding.volumeListPager)
    }


}

class ListViewPagerAdapter(fm: FragmentManager, private val tabs: Collection<TabList>): FragmentStatePagerAdapter(fm) {
    override fun getCount(): Int {
        return tabs.size
    }

    override fun getItem(position: Int): Fragment {
        return LinearLayoutFragment(tabs.elementAt(position))
    }

    override fun getPageTitle(position: Int): CharSequence {
        return tabs.elementAt(position).title
    }

}

class LinearLayoutFragment(private val tab: TabList): Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_folderlist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        //Setup the ListAdapter and touch listener for our RecyclerView
        val listAdapter = FolderItemAdapter(tab.itemClickedListener)
        val recyclerView = view.findViewById<RecyclerView>(R.id.folderList)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = listAdapter
        val touchListener = object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_MOVE)
                    //With a RecyclerView nested inside a ViewPager, we have to stop the ViewPager from swallowing our scroll events
                    recyclerView.parent.requestDisallowInterceptTouchEvent(true)
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) { }
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) { }
        }
        recyclerView.addOnItemTouchListener(touchListener)
        listAdapter.submitList(tab.folders)
    }

}

class TabList(val title: String, val folders: List<FolderItem>, val itemClickedListener: (FolderItem) -> Unit, val id: Int)

class FolderItemAdapter(
    private val itemClickedListener: (FolderItem) -> Unit
) : ListAdapter<FolderItem, FolderViewHolder>(FolderItemDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = FragmentFolderitemBinding.inflate(inflater, parent, false)
        return FolderViewHolder(binding, itemClickedListener)
    }

    override fun onBindViewHolder(
        holder: FolderViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val folderItem = getItem(position)
        val fullRefresh = payloads.isEmpty()

        // Normally we only fully refresh the list item if it's being initially bound, but
        // we might also do it if there was a payload that wasn't understood, just to ensure
        // there isn't a stale item.
        if (fullRefresh) {
            holder.item = folderItem
            holder.titleView.text = folderItem.shortPath
            holder.radio.isChecked = folderItem.selected
        }
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }
}

class FolderViewHolder(
    binding: FragmentFolderitemBinding,
    itemClickedListener: (FolderItem) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    val titleView: TextView = binding.title
    val radio: RadioButton = binding.radioButton
    var item: FolderItem? = null

    init {
        binding.root.setOnClickListener {
            item?.let { itemClickedListener(it) }
        }
        binding.radioButton.isChecked = item?.selected == true
    }
}

class FolderItem(val fullPath: String, val shortPath: String, val selected: Boolean)

val FolderItemDiffCallback =  object : DiffUtil.ItemCallback<FolderItem>() {
    override fun areItemsTheSame(
        oldItem: FolderItem,
        newItem: FolderItem
    ): Boolean =
        oldItem.fullPath == newItem.fullPath

    override fun areContentsTheSame(oldItem: FolderItem, newItem: FolderItem) =
        oldItem.fullPath == newItem.fullPath

    override fun getChangePayload(oldItem: FolderItem, newItem: FolderItem): Nothing? =
        null
}