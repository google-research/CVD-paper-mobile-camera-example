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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.sensing.model.RequestStatus
import com.google.android.sensory.databinding.FragmentHomeBinding
import kotlinx.coroutines.flow.collect
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
    setupUploadProgressBar()
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

  private fun setupUploadProgressBar() {
    lifecycleScope.launch {
      SensingApplication.sensingEngine(requireContext()).apply {
        getUploadRequest(RequestStatus.UPLOADING).collect {
          it.forEach { uploadRequest ->
            /* We assume only 1 UploadRequest has status = UPLOADING.*/
            with(uploadRequest) {
              val uploadPercentVal = fileOffset / fileSize * 100
              binding.uploadLayout.apply {
                uploadPercent.text = "Uploading $uploadPercentVal %"
                uploadProgress.apply {
                  max = fileSize.toInt()
                  progress = fileOffset.toInt()
                }
                linearLayoutUploadStatus.visibility = View.VISIBLE
              }
            }
          }
        }
        getUploadRequest(RequestStatus.UPLOADED).collect {
          it.forEach { _ ->
            binding.uploadLayout.uploadPercent.text = "Uploaded"
            binding.uploadLayout.linearLayoutUploadStatus.visibility = View.GONE
          }
        }
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

  private fun goToParticipantRegistration() {
    findNavController()
      .navigate(HomeFragmentDirections.actionHomeFragmentToAddParticipantFragment())
  }
}
