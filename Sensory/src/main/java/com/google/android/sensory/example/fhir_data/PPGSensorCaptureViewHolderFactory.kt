package com.google.android.sensory.example.fhir_data

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.lifecycle.MutableLiveData
import com.bumptech.glide.Glide
import com.google.android.fhir.datacapture.tryUnwrapContext
import com.google.android.fhir.datacapture.views.QuestionnaireViewItem
import com.google.android.fhir.datacapture.views.factories.QuestionnaireItemViewHolderDelegate
import com.google.android.fhir.datacapture.views.factories.QuestionnaireItemViewHolderFactory
import com.google.android.material.card.MaterialCardView
import com.google.android.sensory.R
import com.google.android.sensory.example.InstructionsFragment
import com.google.android.sensory.example.SensingApplication
import com.google.android.sensory.sensing_sdk.SensingEngine
import com.google.android.sensory.sensing_sdk.capture.CaptureFragment
import com.google.android.sensory.sensing_sdk.capture.CaptureSettings
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.SensorType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse

object PPGSensorCaptureViewHolderFactory :
  QuestionnaireItemViewHolderFactory(com.google.android.fhir.datacapture.R.layout.attachment_view) {
  override fun getQuestionnaireItemViewHolderDelegate() =
    object : QuestionnaireItemViewHolderDelegate {
      override lateinit var questionnaireViewItem: QuestionnaireViewItem
      private lateinit var takePhotoButton: Button
      private lateinit var filePreview: ConstraintLayout
      private lateinit var fileIcon: ImageView
      private lateinit var fileTitle: TextView
      private lateinit var context: AppCompatActivity
      private lateinit var sensingEngine: SensingEngine

      override fun init(itemView: View) {
        itemView.findViewById<Button>(com.google.android.fhir.datacapture.R.id.helpButton).visibility =
          View.GONE
        itemView.findViewById<MaterialCardView>(com.google.android.fhir.datacapture.R.id.helpCardView).visibility =
          View.GONE
        itemView.findViewById<TextView>(com.google.android.fhir.datacapture.R.id.helpText).visibility =
          View.GONE
        itemView.findViewById<Button>(com.google.android.fhir.datacapture.R.id.file_delete).visibility =
          View.GONE
        takePhotoButton = itemView.findViewById(com.google.android.fhir.datacapture.R.id.take_photo)
        takePhotoButton.text = "Capture PPG"
        filePreview = itemView.findViewById(com.google.android.fhir.datacapture.R.id.file_preview)
        fileIcon = itemView.findViewById(com.google.android.fhir.datacapture.R.id.file_icon)
        fileTitle = itemView.findViewById(com.google.android.fhir.datacapture.R.id.file_title)
        context = itemView.context.tryUnwrapContext()!!
        sensingEngine = SensingApplication.sensingEngine(context)
      }

      override fun bind(questionnaireViewItem: QuestionnaireViewItem) {
        this.questionnaireViewItem = questionnaireViewItem
        displayOrClearInitialPreview()
        displayCapturePpgButton(questionnaireViewItem.questionnaireItem)
        takePhotoButton.setOnClickListener { view ->
          onCapturePpgButtonClicked(
            view,
            questionnaireViewItem
          )
        }
      }

      override fun setReadOnly(isReadOnly: Boolean) {
        takePhotoButton.isEnabled = !isReadOnly
      }

      private fun displayCapturePpgButton(questionnaireItem: Questionnaire.QuestionnaireItemComponent) {
        takePhotoButton.visibility = View.VISIBLE
      }

      private fun displayOrClearInitialPreview() {
        val answer = questionnaireViewItem.answers.firstOrNull()
        // Clear preview if there is no answer to prevent showing old previews in views that have
        // been recycled.
        if (answer == null) {
          clearFilePreview()
          return
        }

        answer.valueCoding?.let { code ->
          val captureId = code.code
          val livePath = MutableLiveData<String>()
          CoroutineScope(Dispatchers.IO).launch {
            livePath.postValue(sensingEngine.listResourceInfoInCapture(captureId)[0].title)
          }
          livePath.observe(context) {
            loadFilePreview(com.google.android.fhir.datacapture.R.drawable.ic_document_file, it)
          }
        }
      }

      private fun onCapturePpgButtonClicked(
        view: View,
        questionnaireViewItem: QuestionnaireViewItem,
      ) {
        context.supportFragmentManager.setFragmentResultListener(
          CaptureFragment.CAPTURE_COMPLETE,
          context
        ) { _, result ->
          val captured = result.getBoolean(CaptureFragment.CAPTURED)
          if (!captured) {
            return@setFragmentResultListener
          }
          val captureId = result.getString(CaptureFragment.CAPTURE_ID)
          val answer = QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent().apply {
            value = Coding().apply {
              code = captureId
              system = CaptureType.VIDEO_PPG.toString()
            }
          }
          questionnaireViewItem.setAnswer(answer)
        }

        context.supportFragmentManager.setFragmentResultListener(
          InstructionsFragment.INSTRUCTION_FRAGMENT_RESULT,
          context,
        ) { _, result ->
          context.supportFragmentManager.popBackStack()
          val instructionsUnderstood =
            result.getBoolean(InstructionsFragment.INSTRUCTION_UNDERSTOOD)
          if (!instructionsUnderstood) {
            return@setFragmentResultListener
          }
          /** [TODO] Need to get patientId anyhow! */

          val participantId = context
            .getSharedPreferences(SensingApplication.SHARED_PREFS_KEY, Context.MODE_PRIVATE)
            .getString(SensingApplication.CURRENT_PATIENT_ID, null)!!
          val captureId = questionnaireViewItem.answers.firstOrNull()?.valueCoding?.code
          val captureFragment = sensingEngine.captureFragment(
            participantId = participantId,
            captureType = CaptureType.VIDEO_PPG,
            captureSettings = CaptureSettings(
              fileTypeMap = mapOf(SensorType.CAMERA to "jpeg"),
              metaDataTypeMap = mapOf(SensorType.CAMERA to "tsv"),
              title = "PPG_Signal"
            ),
            captureId = captureId
          )
          context.supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, captureFragment)
            .setReorderingAllowed(true)
            .addToBackStack(null)
            .commit()
        }
        context.supportFragmentManager.beginTransaction()
          .replace(R.id.nav_host_fragment, InstructionsFragment().apply {
            arguments = bundleOf(InstructionsFragment.LAYOUT to R.layout.fragment_ppg_instructions)
          })
          .setReorderingAllowed(true)
          .addToBackStack(null)
          .commit()
      }

      private fun loadFilePreview(@DrawableRes iconResource: Int, title: String) {
        filePreview.visibility = View.VISIBLE
        Glide.with(context).load(iconResource).into(fileIcon)
        fileTitle.text = title
      }

      private fun clearFilePreview() {
        filePreview.visibility = View.GONE
        Glide.with(context).clear(fileIcon)
        fileTitle.text = ""
      }

    }

  const val WIDGET_EXTENSION = "http://external-api-call/sensing-backbone"
  const val WIDGET_TYPE = "ppg-capture"

}