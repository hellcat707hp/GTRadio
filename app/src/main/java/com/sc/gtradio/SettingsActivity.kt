package com.sc.gtradio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            updateLibraryPathPreferenceValue()
            val pathPref = findPreference<Preference>(resources.getString(R.string.radio_folders_uri_key))!!
            pathPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                //Open the SelectLibrary activity allowing the user to choose a folder
                val myIntent = Intent(this.activity, SelectLibraryActivity::class.java)
                this.startActivityForResult(myIntent, R.integer.select_library_request)
                true
            }
        }


        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == R.integer.select_library_request) {
                updateLibraryPathPreferenceValue()
            }
        }

        private fun updateLibraryPathPreferenceValue() {
            val pathPref = findPreference<Preference>(resources.getString(R.string.radio_folders_uri_key))!!
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(requireActivity().applicationContext)
            val radioUri = sharedPref?.getString(getString(R.string.radio_folders_uri_key), "")
            pathPref.summary = radioUri
        }
    }
}