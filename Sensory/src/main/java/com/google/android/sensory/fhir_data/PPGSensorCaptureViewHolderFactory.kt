/*
 * Copyright 2023-2024 Google LLC
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

package com.google.android.sensory.fhir_data

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.findFragment
import com.bumptech.glide.Glide
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.fhir.datacapture.tryUnwrapContext
import com.google.android.fhir.datacapture.views.QuestionnaireViewItem
import com.google.android.fhir.datacapture.views.factories.QuestionnaireItemViewHolderDelegate
import com.google.android.fhir.datacapture.views.factories.QuestionnaireItemViewHolderFactory
import com.google.android.sensing.capture.sample.CameraCaptureFragment
import com.google.android.sensing.capture.sensors.CameraCaptureRequest
import com.google.android.sensory.InstructionsFragment
import com.google.android.sensory.R
import com.google.android.sensory.SensingApplication
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

      private lateinit var QUESTION_TITLE: String

      override fun init(itemView: View) {
        ViewHolderFactoryUtil.removeUnwantedViews(itemView)
        takePhotoButton = itemView.findViewById(com.google.android.fhir.datacapture.R.id.take_photo)
        takePhotoButton.text = CAPTURE_TEXT
        filePreview = itemView.findViewById(com.google.android.fhir.datacapture.R.id.file_preview)
        fileIcon = itemView.findViewById(com.google.android.fhir.datacapture.R.id.file_icon)
        fileTitle = itemView.findViewById(com.google.android.fhir.datacapture.R.id.file_title)
        context = itemView.context.tryUnwrapContext()!!
      }

      override fun bind(questionnaireViewItem: QuestionnaireViewItem) {
        this.questionnaireViewItem = questionnaireViewItem
        QUESTION_TITLE = questionnaireViewItem.questionnaireItem.text
        displayOrClearInitialPreview()
        displayCapturePpgButton(questionnaireViewItem.questionnaireItem)
        takePhotoButton.setOnClickListener { view ->
          onCapturePpgButtonClicked(view, questionnaireViewItem)
        }
      }

      override fun setReadOnly(isReadOnly: Boolean) {
        takePhotoButton.isEnabled = !isReadOnly
      }

      private fun displayCapturePpgButton(
        questionnaireItem: Questionnaire.QuestionnaireItemComponent
      ) {
        takePhotoButton.visibility = View.VISIBLE
      }

      private fun displayOrClearInitialPreview() {
        /**
         * Using firstOrNull temporarily for single answer scenarios.
         *
         * Ideally, switch to viewing multiple placeholders for each answer.
         */
        val answer = questionnaireViewItem.answers.firstOrNull()
        // Clear preview if there is no answer to prevent showing old previews in views that have
        // been recycled.
        if (answer == null) {
          clearFilePreview()
          return
        }

        answer.valueCoding?.let { code ->
          // valueCoding is non-null only when resources have been created.
          loadFilePreview(
            com.google.android.fhir.datacapture.R.drawable.ic_document_file,
            code.display
          )
        }
          ?: clearFilePreview()
      }

      private fun onCapturePpgButtonClicked(
        view: View,
        questionnaireViewItem: QuestionnaireViewItem,
      ) {
        val parentFragmentsChildFragmentManager =
          view.findFragment<QuestionnaireFragment>().parentFragmentManager
        parentFragmentsChildFragmentManager.setFragmentResultListener(
          CameraCaptureFragment.TAG,
          context
        ) { _, result ->
          if (result.getBoolean(CameraCaptureFragment.CAPTURED)) {
            // Following condition arises when user presses back button from CaptureFragment and the
            // fragment is removed from backstack by the BackPressCallback defined in
            // ScreenerFragment
            if (parentFragmentsChildFragmentManager.backStackEntryCount >= 1) {
              parentFragmentsChildFragmentManager.popBackStack()
            }
            val captureId = result.getString(CameraCaptureFragment.CAPTURED_ID, "")
            val answer =
              QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent().apply {
                value =
                  Coding().apply {
                    // store captureId to recapture again
                    code = captureId
                    system = "$WIDGET_EXTENSION/CaptureInfo"
                    display = QUESTION_TITLE
                  }
              }
            questionnaireViewItem.setAnswer(answer)
          }
        }
        parentFragmentsChildFragmentManager.setFragmentResultListener(
          InstructionsFragment.INSTRUCTION_FRAGMENT_RESULT,
          context,
        ) { _, result ->
          parentFragmentsChildFragmentManager.popBackStack()
          val instructionsUnderstood =
            result.getBoolean(InstructionsFragment.INSTRUCTION_UNDERSTOOD)
          if (!instructionsUnderstood) {
            return@setFragmentResultListener
          }
          val fhirPatientId =
            context
              .getSharedPreferences(SensingApplication.SHARED_PREFS_KEY, Context.MODE_PRIVATE)
              .getString(SensingApplication.CURRENT_PATIENT_ID, null)!!
          val cameraCaptureFragment =
            CameraCaptureFragment().apply {
              setCaptureRequest(
                CameraCaptureRequest.ImageStreamRequest(
                  externalIdentifier = fhirPatientId,
                  outputFolder =
                    "Sensory_${SensingApplication.APP_VERSION}/Participant_$fhirPatientId/$QUESTION_TITLE",
                  outputTitle = QUESTION_TITLE,
                  bufferCapacity = Int.MAX_VALUE
                )
              )
            }
          parentFragmentsChildFragmentManager
            .beginTransaction()
            .add(R.id.screener_container, cameraCaptureFragment, CameraCaptureFragment.TAG)
            .addToBackStack(CameraCaptureFragment.TAG)
            .commit()
        }
        parentFragmentsChildFragmentManager
          .beginTransaction()
          .add(
            R.id.screener_container,
            InstructionsFragment().apply {
              arguments = bundleOf(InstructionsFragment.TITLE to QUESTION_TITLE)
            }
          )
          .addToBackStack(null)
          .commit()
      }

      private fun loadFilePreview(@DrawableRes iconResource: Int, title: String) {
        takePhotoButton.text = "Retake Video"
        filePreview.visibility = View.VISIBLE
        Glide.with(context).load(iconResource).into(fileIcon)
        fileTitle.text = title
      }

      private fun clearFilePreview() {
        takePhotoButton.text = "Capture Video"
        filePreview.visibility = View.GONE
        Glide.with(context).clear(fileIcon)
        fileTitle.text = ""
      }
    }

  const val WIDGET_EXTENSION = "http://external-api-call/sensing-backbone"
  const val WIDGET_TYPE = "ppg-capture"
  const val CAPTURE_TEXT = "Capture Video"
  const val TAG = "PPGSensorCaptureViewHolderFactory"
}
