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

package com.google.android.sensory.example

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.navArgs
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.sensory.R

/** A fragment class to show anemia questionnaire on screen. */
class AnemiaScreenerFragment : Fragment(R.layout.fragment_anemia_screening) {

  private val anemiaScreenerViewModel: AnemiaScreenerViewModel by viewModels()
  private val args: AnemiaScreenerFragmentArgs by navArgs()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setUpActionBar()
    setupMenu()
    updateArguments()
    onBackPressed()
    observeResourcesSaveAction()
    if (savedInstanceState == null) {
      addQuestionnaireFragment()
    }
  }

  private fun setupMenu() {
    (requireActivity() as MainActivity).addMenuProvider(
      object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
          menuInflater.inflate(R.menu.anemia_screen_encounter_fragment_menu, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
          return when (menuItem.itemId) {
            R.id.action_add_patient_submit -> {
              onSubmitAction()
              true
            }
            android.R.id.home -> {
              showCancelScreenerQuestionnaireAlertDialog()
              true
            }
            else -> true
          }
        }
      },
      viewLifecycleOwner
    )
  }

  private fun setUpActionBar() {
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.anemia_screening)
      setDisplayHomeAsUpEnabled(true)
    }
  }

  private fun updateArguments() {
    requireArguments().putString(QUESTIONNAIRE_FILE_PATH_KEY, "anemia-study-questionnaire.json")
  }

  private fun addQuestionnaireFragment() {
    childFragmentManager.commit {
      replace(
        R.id.screener_container,
        anemiaScreenerViewModel.questionnaireFragment,
        QUESTIONNAIRE_FRAGMENT_TAG
      )
    }
  }

  private fun onSubmitAction() {
    val questionnaireFragment =
      childFragmentManager.findFragmentByTag(QUESTIONNAIRE_FRAGMENT_TAG) as QuestionnaireFragment
    anemiaScreenerViewModel.saveScreenerEncounter(
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
            NavHostFragment.findNavController(this@AnemiaScreenerFragment).navigateUp()
          }
          setNegativeButton(getString(android.R.string.no)) { _, _ -> }
        }
        builder.create()
      }
    alertDialog?.show()
  }

  private fun onBackPressed() {
    activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) {
      showCancelScreenerQuestionnaireAlertDialog()
    }
  }

  private fun observeResourcesSaveAction() {
    anemiaScreenerViewModel.isResourcesSaved.observe(viewLifecycleOwner) {
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

  override fun onDestroy() {
    super.onDestroy()
  }

  override fun onDestroyView() {
    super.onDestroyView()
  }

  companion object {
    const val QUESTIONNAIRE_FILE_PATH_KEY = "questionnaire-file-path-key"
    const val QUESTIONNAIRE_FRAGMENT_TAG = "questionnaire-fragment-tag"
  }
}
