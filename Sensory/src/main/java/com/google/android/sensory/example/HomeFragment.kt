package com.google.android.sensory.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.sensory.R
import com.google.android.sensory.databinding.FragmentHomeBinding


class HomeFragment : Fragment() {
  private var _binding: FragmentHomeBinding? = null
  private val binding
    get() = _binding!!
  private val mainActivityViewModel: MainActivityViewModel by activityViewModels()

  private var isAcknowledged = false

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentHomeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    setUpActionBar()
    setupListeners()
  }

  private fun setUpActionBar() {
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.app_name)
      setDisplayHomeAsUpEnabled(false)
    }
  }

  private fun setupListeners() {
    binding.fab.setOnClickListener {
      mainActivityViewModel.triggerOneTimeSync()
    }
    binding.btnStartNewParticipant.setOnClickListener {
      if (isAcknowledged) {
        goToParticipantRegistration()
      } else {
        showAcknowledgementDialog()
      }
    }
  }

  private fun showAcknowledgementDialog() {
    val builder: AlertDialog.Builder = AlertDialog.Builder(requireActivity())
    builder
      .setTitle(R.string.acknowledge_dialog_title)
      .setMessage(R.string.acknowledge_dialog_message)
      .setPositiveButton(
        R.string.acknowledge
      ) { _, _ ->
        isAcknowledged = true
        goToParticipantRegistration()
      }
      .setCancelable(false)
    val dialog: AlertDialog = builder.create()
    dialog.show()
  }

  private fun goToParticipantRegistration() {
    findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToAddParticipantFragment())
  }
}