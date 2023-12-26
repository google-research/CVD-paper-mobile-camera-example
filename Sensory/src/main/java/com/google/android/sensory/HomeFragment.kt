/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.sensory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.sensing.upload.SyncUploadState
import com.google.android.sensory.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

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
    setupSyncUploadProgress()
  }

  private fun setUpActionBar() {
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.app_name)
      setDisplayHomeAsUpEnabled(false)
    }
  }

  private fun setupListeners() {
    binding.fab.setOnClickListener { mainActivityViewModel.triggerOneTimeSync() }
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
      .setPositiveButton(R.string.acknowledge) { _, _ ->
        isAcknowledged = true
        goToParticipantRegistration()
      }
      .setCancelable(false)
    val dialog: AlertDialog = builder.create()
    dialog.show()
  }

  private fun setupSyncUploadProgress() {
    lifecycleScope.launch {
      mainActivityViewModel.syncUploadState.collect {
        when (it) {
          is SyncUploadState.Started,
          is SyncUploadState.InProgress -> showSyncBanner(it)
          is SyncUploadState.Completed -> hideSyncBanner(it)
          is SyncUploadState.Failed -> hideSyncBanner(it)
          is SyncUploadState.NoOp -> {
            println("No operation required. Sync is in progress")
          }
        }
      }
    }
  }

  private fun showSyncBanner(syncUploadState: SyncUploadState) {
    val completedRequests: Int
    val totalRequests: Int
    when (syncUploadState) {
      is SyncUploadState.Started -> {
        totalRequests = syncUploadState.initialTotalRequests
        completedRequests = 0
      }
      is SyncUploadState.InProgress -> {
        completedRequests = syncUploadState.completedRequests
        totalRequests = syncUploadState.currentTotalRequests
      }
      is SyncUploadState.Completed -> {
        completedRequests = syncUploadState.totalRequests
        totalRequests = syncUploadState.totalRequests
      }
      else -> {
        completedRequests = 0
        totalRequests = 0
      }
    }
    if (totalRequests == 0) return
    with(binding.uploadLayout) {
      if (linearLayoutUploadStatus.visibility != View.VISIBLE) {
        // may add fade in animation here later
        linearLayoutUploadStatus.visibility = View.VISIBLE
      }
    }
    updateUploadPercent(completedRequests, totalRequests)
  }

  private fun hideSyncBanner(syncUploadState: SyncUploadState) {
    if (syncUploadState is SyncUploadState.Completed) {
      updateUploadPercent(syncUploadState.totalRequests, syncUploadState.totalRequests)
      binding.uploadLayout.uploadPercent.text = "Uploaded"
      binding.uploadLayout.linearLayoutUploadStatus.visibility = View.GONE
    } else if (syncUploadState is SyncUploadState.Failed) {
      binding.uploadLayout.uploadPercent.text = "Failed"
      // we can add some animation here
      binding.uploadLayout.linearLayoutUploadStatus.visibility = View.GONE
      Toast.makeText(requireContext(), "Upload Failed!", Toast.LENGTH_LONG)
    }
  }

  private fun updateUploadPercent(completed: Int, total: Int) {
    binding.uploadLayout.apply {
      val uploadPercentVal = percentOf(completed, total) * 100
      uploadPercent.text = "Uploading $uploadPercentVal %"
      uploadProgress.apply {
        max = total
        progress = completed
      }
    }
  }

  private fun percentOf(value: Number, total: Number) =
    if (total == 0) 0.0 else value.toDouble() / total.toDouble()

  private fun goToParticipantRegistration() {
    findNavController()
      .navigate(HomeFragmentDirections.actionHomeFragmentToAddParticipantFragment())
  }
}
