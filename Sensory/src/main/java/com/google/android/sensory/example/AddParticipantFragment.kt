package com.google.android.sensory.example

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.sensory.R
import com.google.android.sensory.example.data.AnemiaScreenerFragment
import com.google.android.sensory.sensing_sdk.capture.model.CaptureSettings
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.SensorType
import org.hl7.fhir.r4.model.QuestionnaireResponse

/** A fragment class to show patient registration screen using SDC's [QuestionnaireFragment].
 * A participant is internally handled as a Fhir Patient. */
class AddParticipantFragment : Fragment(R.layout.fragment_add_participant) {

  private val viewModel: AddParticipantViewModel by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setUpActionBar()
    updateArguments()
    if (savedInstanceState == null) {
      addQuestionnaireFragment()
    }
    setupMenu()
    observePatientSaveAction()
  }

  private fun setupMenu(){
    (requireActivity() as MainActivity).addMenuProvider(object: MenuProvider{
      override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.add_patient_fragment_menu, menu)
      }

      override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
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

    }, viewLifecycleOwner)
  }

  private fun setUpActionBar() {
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      title = requireContext().getString(R.string.add_patient)
      setDisplayHomeAsUpEnabled(true)
    }
  }

  private fun updateArguments() {
    requireArguments()
      .putString(QUESTIONNAIRE_FILE_PATH_KEY, "new-participant-registration.json")
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
    viewModel.patientSaved.observe(viewLifecycleOwner) {
      if (it == null) {
        Toast.makeText(requireContext(), "Inputs are missing.", Toast.LENGTH_SHORT).show()
        return@observe
      }
      Toast.makeText(requireContext(), "Patient is saved.", Toast.LENGTH_SHORT).show()
      SensingApplication.sensingEngine(requireContext().applicationContext).captureSensorData(
        context = requireActivity(),
        folderId = "Participant_${it.idElement.idPart}",
        captureType = CaptureType.VIDEO_PPG,
        captureSettings =  CaptureSettings(fileTypeMap = mapOf(SensorType.CAMERA to "jpeg")),
        captureId = null
      )
      findNavController().navigate(
        AddParticipantFragmentDirections.actionAddParticipantFragmentToAnemiaScreenerFragment(it.idElement.idPart))
    }
  }

  companion object {
    const val QUESTIONNAIRE_FILE_PATH_KEY = "questionnaire-file-path-key"
    const val QUESTIONNAIRE_FRAGMENT_TAG = "questionnaire-fragment-tag"
  }
}
