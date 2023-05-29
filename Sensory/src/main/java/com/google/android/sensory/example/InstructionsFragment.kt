package com.google.android.sensory.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.sensory.R

class InstructionsFragment : DialogFragment() {
  private lateinit var btnCancel: Button
  private lateinit var btnNext: Button
  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
  ): View {
    // Inflate the layout for this fragment
    val layoutId = arguments?.getInt(LAYOUT)!!
    return inflater.inflate(layoutId, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    btnCancel = view.findViewById(R.id.btn_cancel)
    btnNext = view.findViewById(R.id.btn_next)
    btnCancel.setOnClickListener {
      setFragmentResult(INSTRUCTION_FRAGMENT_RESULT, bundleOf(INSTRUCTION_UNDERSTOOD to false))
      dismiss()
    }
    btnNext.setOnClickListener {
      setFragmentResult(INSTRUCTION_FRAGMENT_RESULT, bundleOf(INSTRUCTION_UNDERSTOOD to true))
      dismiss()
    }
  }

  companion object {
    const val INSTRUCTION_FRAGMENT_RESULT = "INSTRUCTION_FRAGMENT_RESULT"
    const val INSTRUCTION_UNDERSTOOD = "INSTRUCTION_UNDERSTOOD"
    const val LAYOUT = "LAYOUT"
  }
}