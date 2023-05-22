package com.google.android.sensory.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.sensory.R
import com.google.android.sensory.databinding.FragmentHomeBinding
import com.google.android.sensory.sensing_sdk.capture.model.CaptureSettings
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.SensorType

class HomeFragment: Fragment() {
  private var _binding: FragmentHomeBinding? = null
  private val binding
    get() = _binding!!
  private val mainActivityViewModel: MainActivityViewModel by activityViewModels()

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentHomeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.fab.setOnClickListener {
      mainActivityViewModel.triggerOneTimeSync()
    }
    binding.btnStartNewParticipant.setOnClickListener {
      SensingApplication.sensingEngine(requireContext().applicationContext).captureSensorData(
        context = requireActivity(),
        folderId = "123",
        captureType = CaptureType.VIDEO_PPG,
        captureSettings =  CaptureSettings(fileTypeMap = mapOf(SensorType.CAMERA to "jpeg")),
        captureId = null
      )
    }
  }
}