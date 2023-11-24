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
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.navArgs
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.sensory.fhir_data.ScreeningConfig

/** A fragment class to show questionnaire on screen. */
class ScreenerFragment : Fragment(R.layout.fragment_screening) {

  private val screenerViewModel: ScreenerViewModel by viewModels()
  private val args: ScreenerFragmentArgs by navArgs()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setUpActionBar()
    setHasOptionsMenu(true)
    updateArguments()
    registerBackPressCallback()
    observeResourcesSaveAction()
    if (savedInstanceState == null) {
      addQuestionnaireFragment()
    }
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    super.onCreateOptionsMenu(menu, inflater)
    inflater.inflate(R.menu.screen_encounter_fragment_menu, menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.action_add_patient_submit -> {
        onSubmitAction()
        true
      }
      android.R.id.home -> {
        onBackPressed()
        true
      }
      else -> true
    }
  }

  private fun setUpActionBar() {
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      show()
      title = requireContext().getString(R.string.screening)
      setDisplayHomeAsUpEnabled(true)
    }
  }

  private fun updateArguments() {
    requireArguments().putString(QUESTIONNAIRE_FILE_PATH_KEY, ScreeningConfig.questionnairePath)
    requireArguments().putString(QUESTIONNAIRE_CUSTOM_MAPPING, ScreeningConfig.structureMapping)
  }

  private fun addQuestionnaireFragment() {
    val questionnaireFragment =
      childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG) as QuestionnaireFragment?
    childFragmentManager.commit {
      add(
        R.id.screener_container,
        questionnaireFragment
          ?: QuestionnaireFragment.builder()
            .setQuestionnaire(screenerViewModel.questionnaireString)
            .setCustomQuestionnaireItemViewHolderFactoryMatchersProvider(
              SensingApplication.CUSTOM_VIEW_HOLDER_FACTORY_TAG
            )
            .setShowSubmitButton(false)
            .build(),
        QUESTIONNAIRE_FRAGMENT_TAG
      )
    }
  }

  private fun onSubmitAction() {
    val questionnaireFragment =
      childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG) as QuestionnaireFragment
    screenerViewModel.saveScreenerEncounter(
      questionnaireFragment.getQuestionnaireResponse(),
      args.patientId
    )
  }

  private fun showCancelScreenerQuestionnaireAlertDialog() {
    val alertDialog: AlertDialog? =
      activity?.let {
        val builder = AlertDialog.Builder(it)
        builder.apply {
          setMessage(getString(R.string.cancel_questionnaire_message))
          setPositiveButton(getString(android.R.string.yes)) { _, _ ->
            NavHostFragment.findNavController(this@ScreenerFragment).navigateUp()
          }
          setNegativeButton(getString(android.R.string.no)) { _, _ -> }
        }
        builder.create()
      }
    alertDialog?.show()
  }

  private fun registerBackPressCallback() {
    activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) { onBackPressed() }
  }

  private fun onBackPressed() {
    if (childFragmentManager.backStackEntryCount >= 1) {
      childFragmentManager.popBackStack()
      return
    }
    showCancelScreenerQuestionnaireAlertDialog()
  }

  private fun observeResourcesSaveAction() {
    screenerViewModel.isResourcesSaved.observe(viewLifecycleOwner) {
      if (!it) {
        Toast.makeText(requireContext(), getString(R.string.inputs_missing), Toast.LENGTH_SHORT)
          .show()
        return@observe
      }
      Toast.makeText(requireContext(), getString(R.string.resources_saved), Toast.LENGTH_SHORT)
        .show()
      NavHostFragment.findNavController(this).navigateUp()
    }
  }

  companion object {
    const val QUESTIONNAIRE_FILE_PATH_KEY = "questionnaire-file-path-key"
    const val QUESTIONNAIRE_CUSTOM_MAPPING = "questionnaire-custom-mapping"
    const val QUESTIONNAIRE_FRAGMENT_TAG = "questionnaire-fragment-tag"
  }
}
