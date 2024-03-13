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
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.findFragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.fhir.datacapture.tryUnwrapContext
import com.google.android.fhir.datacapture.views.QuestionnaireViewItem
import com.google.android.fhir.datacapture.views.factories.QuestionnaireItemViewHolderDelegate
import com.google.android.fhir.datacapture.views.factories.QuestionnaireItemViewHolderFactory
import com.google.android.sensing.SensingEngine
import com.google.android.sensing.capture.CaptureFragment
import com.google.android.sensing.capture.CaptureSettings
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.CaptureType
import com.google.android.sensing.model.InternalSensorType
import com.google.android.sensory.InstructionsFragment
import com.google.android.sensory.R
import com.google.android.sensory.SensingApplication
import java.io.File
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.QuestionnaireResponse

object PhotoCaptureViewHolderFactory :
  QuestionnaireItemViewHolderFactory(com.google.android.fhir.datacapture.R.layout.attachment_view) {
  override fun getQuestionnaireItemViewHolderDelegate() =
    object : QuestionnaireItemViewHolderDelegate {
      override lateinit var questionnaireViewItem: QuestionnaireViewItem
      private lateinit var takePhotoButton: Button
      private lateinit var photoPreview: ConstraintLayout
      private lateinit var photoThumbnail: ImageView
      private lateinit var photoTitle: TextView
      private lateinit var context: AppCompatActivity
      private lateinit var internalFilesDir: File
      private lateinit var QUESTION_TITLE: String

      private lateinit var sensingEngine: SensingEngine

      override fun init(itemView: View) {
        ViewHolderFactoryUtil.removeUnwantedViews(itemView)
        takePhotoButton = itemView.findViewById(com.google.android.fhir.datacapture.R.id.take_photo)
        takePhotoButton.text = CAPTURE_TEXT
        photoPreview = itemView.findViewById(com.google.android.fhir.datacapture.R.id.photo_preview)
        photoThumbnail =
          itemView.findViewById(com.google.android.fhir.datacapture.R.id.photo_thumbnail)
        photoTitle = itemView.findViewById(com.google.android.fhir.datacapture.R.id.photo_title)
        context = itemView.context.tryUnwrapContext()!!
        internalFilesDir = context.filesDir
        sensingEngine = SensingApplication.sensingEngine(context)
      }

      override fun bind(questionnaireViewItem: QuestionnaireViewItem) {
        this.questionnaireViewItem = questionnaireViewItem
        QUESTION_TITLE = questionnaireViewItem.questionnaireItem.text
        displayOrClearInitialPreview()
        displayTakePhotoButton(/*questionnaireItem*/ )
        takePhotoButton.setOnClickListener { view -> onTakePhotoButtonClicked(view) }
      }

      override fun setReadOnly(isReadOnly: Boolean) {
        takePhotoButton.isEnabled = !isReadOnly
      }

      private fun displayTakePhotoButton(
      /*questionnaireItem: Questionnaire.QuestionnaireItemComponent*/
      ) {
        takePhotoButton.visibility = View.VISIBLE
      }

      private fun displayOrClearInitialPreview() {
        val answer = questionnaireViewItem.answers.firstOrNull()

        // Clear preview if there is no answer to prevent showing old previews in views that have
        // been recycled.
        if (answer == null) {
          clearPhotoPreview()
          return
        }

        answer.valueCoding?.let {
          // valueCoding is non-null only when image resource has been stored.
          displayPreview(attachmentTitle = QUESTION_TITLE, attachmentUri = it.display.toUri())
        }
      }

      private fun onTakePhotoButtonClicked(
        view: View /*, questionnaireItem: Questionnaire.QuestionnaireItemComponent*/
      ) {
        val parentFragmentsChildFragmentManager =
          view.findFragment<QuestionnaireFragment>().parentFragmentManager
        parentFragmentsChildFragmentManager.setFragmentResultListener(
          CaptureFragment.TAG,
          context
        ) { _, result ->
          if (result.getBoolean(CaptureFragment.CAPTURED)) {
            // Following condition arises when user presses back button from CaptureFragment and the
            // fragment is removed from backstack by the BackPressCallback defined in
            // ScreenerFragment
            if (parentFragmentsChildFragmentManager.backStackEntryCount >= 1) {
              parentFragmentsChildFragmentManager.popBackStack()
            }

            val captureId = result.getString(CaptureFragment.CAPTURED_ID, "")
            context.lifecycleScope.launch {
              sensingEngine.getCaptureInfo(captureId).resourceInfoList.forEach {
                val answer =
                  QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent().apply {
                    value =
                      Coding().apply {
                        // store captureId to recapture again
                        code = captureId
                        system = "${PPGSensorCaptureViewHolderFactory.WIDGET_EXTENSION}/CaptureInfo"
                        display =
                          ViewHolderFactoryUtil.getFirstOrNullImageUri(
                              it.localLocation,
                              it.contentType
                            )
                            ?.toString()
                      }
                  }
                /**
                 * Using setAnswer temporarily for single ResourceInfo scenarios.
                 *
                 * Ideally, switch to questionnaireViewItem.addAnswer when handling multiple sensors
                 * (multiple ResourceInfo).
                 *
                 * Note: addAnswer API not yet released in the current library.
                 */
                questionnaireViewItem.setAnswer(answer)
              }
            }
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
          val captureId = questionnaireViewItem.answers.firstOrNull()?.valueCoding?.code
          val captureFragment =
            CaptureFragment().apply {
              setCaptureInfo(
                CaptureInfo(
                  externalIdentifier = fhirPatientId,
                  captureType = CaptureType.IMAGE,
                  captureFolder =
                    "Sensory_${SensingApplication.APP_VERSION}/Participant_$fhirPatientId/$QUESTION_TITLE",
                  captureSettings =
                    CaptureSettings(
                      fileTypeMap = mapOf(InternalSensorType.CAMERA to "jpeg"),
                      metaDataTypeMap = mapOf(InternalSensorType.CAMERA to "tsv"),
                      captureTitle = QUESTION_TITLE
                    ),
                  recapture = captureId != null,
                  captureId = captureId,
                )
              )
            }
          parentFragmentsChildFragmentManager
            .beginTransaction()
            .add(R.id.screener_container, captureFragment, CaptureFragment.TAG)
            .addToBackStack(null)
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

      fun displayPreview(
        attachmentTitle: String,
        attachmentByteArray: ByteArray? = null,
        attachmentUri: Uri? = null,
      ) {
        takePhotoButton.text = "Retake Image"
        if (attachmentByteArray != null) {
          loadPhotoPreview(attachmentByteArray, attachmentTitle)
        } else if (attachmentUri != null) {
          loadPhotoPreview(attachmentUri, attachmentTitle)
        }
      }

      private fun loadPhotoPreview(byteArray: ByteArray, title: String) {
        photoPreview.visibility = View.VISIBLE
        Glide.with(context).load(byteArray).into(photoThumbnail)
        photoTitle.text = title
      }

      private fun loadPhotoPreview(uri: Uri, title: String) {
        photoPreview.visibility = View.VISIBLE
        Glide.with(context).load(uri).into(photoThumbnail)
        photoTitle.text = title
      }

      fun clearPhotoPreview() {
        takePhotoButton.text = "Capture Image"
        photoPreview.visibility = View.GONE
        Glide.with(context).clear(photoThumbnail)
        photoTitle.text = ""
      }
    }

  const val WIDGET_EXTENSION = "http://external-api-call/sensing-backbone"
  const val WIDGET_TYPE = "photo-capture"
  const val CAPTURE_TEXT = "Capture Image"
}
