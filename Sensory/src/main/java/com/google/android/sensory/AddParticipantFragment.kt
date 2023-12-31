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

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.google.android.fhir.datacapture.QuestionnaireFragment
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.QuestionnaireResponse

/**
 * A fragment class to show patient registration screen using SDC's [QuestionnaireFragment]. A
 * participant is internally handled as a Fhir Patient.
 */
class AddParticipantFragment : Fragment(R.layout.fragment_add_participant) {

  private val viewModel: AddParticipantViewModel by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setUpActionBar()
    updateArguments()
    if (savedInstanceState == null) {
      addQuestionnaireFragment()
    }
    setHasOptionsMenu(true)
    observePatientSaveAction()
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    super.onCreateOptionsMenu(menu, inflater)
    inflater.inflate(R.menu.add_patient_fragment_menu, menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.action_add_patient_submit -> {
        onSubmitAction()
        true
      }
      android.R.id.home -> {
        NavHostFragment.findNavController(this@AddParticipantFragment).navigateUp()
        true
      }
      else -> true
    }
  }

  private fun setUpActionBar() {
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.add_patient)
      setDisplayHomeAsUpEnabled(true)
    }
  }

  private fun updateArguments() {
    requireArguments().putString(QUESTIONNAIRE_FILE_PATH_KEY, "new-participant-registration.json")
  }

  private fun addQuestionnaireFragment() {
    childFragmentManager.commit {
      add(
        R.id.add_patient_container,
        QuestionnaireFragment.builder().setQuestionnaire(viewModel.questionnaire).build(),
        QUESTIONNAIRE_FRAGMENT_TAG
      )
    }
  }

  private fun onSubmitAction() {
    val questionnaireFragment =
      childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG) as QuestionnaireFragment
    savePatient(questionnaireFragment.getQuestionnaireResponse())
  }

  private fun savePatient(questionnaireResponse: QuestionnaireResponse) {
    viewModel.savePatient(questionnaireResponse)
  }

  private fun observePatientSaveAction() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.patientSaved.observe(viewLifecycleOwner) {
        if (it == null) {
          Toast.makeText(requireContext(), "Inputs are missing.", Toast.LENGTH_SHORT).show()
          return@observe
        }
        val sharedPrefs =
          requireActivity()
            .getSharedPreferences(SensingApplication.SHARED_PREFS_KEY, Context.MODE_PRIVATE)
        sharedPrefs.edit().putString(SensingApplication.CURRENT_PATIENT_ID, it.id).apply()
        Toast.makeText(requireContext(), "Patient is saved.", Toast.LENGTH_SHORT).show()
        findNavController()
          .navigate(
            AddParticipantFragmentDirections.actionAddParticipantFragmentToScreenerFragment(
              it.idElement.idPart
            )
          )
      }
    }
  }

  companion object {
    const val QUESTIONNAIRE_FILE_PATH_KEY = "questionnaire-file-path-key"
    const val QUESTIONNAIRE_FRAGMENT_TAG = "questionnaire-fragment-tag"
  }
}
