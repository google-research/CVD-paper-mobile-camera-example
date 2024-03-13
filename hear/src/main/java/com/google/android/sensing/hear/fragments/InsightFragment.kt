/*
 * Copyright 2024 Google LLC
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

package com.google.android.sensing.hear.fragments

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.sensing.hear.R
import com.google.android.sensing.hear.model.Field
import com.google.android.sensing.hear.model.parseStructValue

class InsightFragment : Fragment(R.layout.fragment_insight) {
  private val args: RecordingFragmentArgs by navArgs()

  private lateinit var causesAdapter: CausesListAdapter
  private lateinit var rvCauses: RecyclerView
  private lateinit var restartButton: Button

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    rvCauses = view.findViewById(R.id.causes_list)
    causesAdapter = CausesListAdapter(requireContext())
    restartButton = view.findViewById(R.id.button_re_start)
    restartButton.setOnClickListener {
      findNavController().popBackStack(R.id.recordFragment, false)
    }

    rvCauses.apply {
      layoutManager = LinearLayoutManager(requireContext())
      adapter = causesAdapter
    }
    val causesList =
      parseStructValue(args.result).fields.mapIndexed { index, item ->
        CauseItem(index, item.getKeyDisplay(), item.getValueDisplay())
      }

    causesAdapter.submitList(causesList)

    requireActivity()
      .onBackPressedDispatcher.addCallback(
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            findNavController().popBackStack(R.id.recordFragment, false)
            this.isEnabled = false
          }
        }
      )
  }
}

fun Field.getKeyDisplay(): String {
  return when (key) {
    "Opacity" -> "Opacity"
    "tb" -> "Tuberculosis"
    "abnormal_majority_vote" -> "Abnormality"
    else -> ""
  }
}

fun Field.getValueDisplay(): String {
  return "${(value.numberValue * 100).toInt()}% likely"
}
