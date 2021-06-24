package com.sc.gtradio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.sc.gtradio.databinding.RadioListBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class RadioList : Fragment() {

    private var _binding: RadioListBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        _binding = RadioListBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonSelect.setOnClickListener {
            //Request access and select folder
            // Choose a directory using the system's file picker.
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply { }

            startActivityForResult(intent, 1234)
        }

        val sharedPref =
            this.activity?.getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)
        val radioUri = sharedPref?.getString(getString(R.string.radio_folders_uri_key), "")
        if (radioUri.isNullOrEmpty()) {
            //Set the button to visible
            binding.buttonSelect.visibility = View.VISIBLE
            binding.textviewNofolder.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onActivityResult(
        requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == 1234
            && resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that
            // the user selected.
            resultData?.data?.also { uri ->
                // Perform operations on the document using its URI.
                val sharedPref =
                    this.activity?.getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)
                with (sharedPref?.edit()) {
                    this?.putString(getString(com.sc.gtradio.R.string.radio_folders_uri_key), uri.toString())
                    this?.apply()
                    if (uri.toString().isNotBlank()) {
                        binding.buttonSelect.visibility = View.INVISIBLE
                        binding.textviewNofolder.visibility = View.INVISIBLE
                    }
                }
            }
        }
    }
}