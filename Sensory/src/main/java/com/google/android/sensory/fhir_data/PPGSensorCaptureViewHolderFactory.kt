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
import androidx.lifecycle.MutableLiveData
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
import com.google.android.sensing.capture.SensorCaptureResult
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.model.CaptureType
import com.google.android.sensing.model.SensorType
import com.google.android.sensory.InstructionsFragment
import com.google.android.sensory.R
import com.google.android.sensory.SensingApplication
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

      private lateinit var QUESTION_TITLE: String

      override fun init(itemView: View) {
        ViewHolderFactoryUtil.removeUnwantedViews(itemView)
        takePhotoButton = itemView.findViewById(com.google.android.fhir.datacapture.R.id.take_photo)
        takePhotoButton.text = CAPTURE_TEXT
        filePreview = itemView.findViewById(com.google.android.fhir.datacapture.R.id.file_preview)
        fileIcon = itemView.findViewById(com.google.android.fhir.datacapture.R.id.file_icon)
        fileTitle = itemView.findViewById(com.google.android.fhir.datacapture.R.id.file_title)
        context = itemView.context.tryUnwrapContext()!!
        sensingEngine = SensingApplication.sensingEngine(context)
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
          context.lifecycleScope.launch {
            val resourceMetaInfoList = sensingEngine.listResourceMetaInfoInCapture(captureId)
            if (resourceMetaInfoList.isEmpty()) {
              clearFilePreview()
              return@launch
            }
            livePath.postValue(resourceMetaInfoList[0].captureTitle)
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
          val participantId =
            context
              .getSharedPreferences(SensingApplication.SHARED_PREFS_KEY, Context.MODE_PRIVATE)
              .getString(SensingApplication.CURRENT_PATIENT_ID, null)!!
          val captureId = questionnaireViewItem.answers.firstOrNull()?.valueCoding?.code
          val captureFragment =
            CaptureFragment().apply {
              setCaptureInfo(
                CaptureInfo(
                  participantId = participantId,
                  captureType = CaptureType.VIDEO_PPG,
                  captureFolder = "Sensory/Participant_$participantId/$QUESTION_TITLE",
                  captureSettings =
                    CaptureSettings(
                      fileTypeMap = mapOf(SensorType.CAMERA to "jpeg"),
                      metaDataTypeMap = mapOf(SensorType.CAMERA to "tsv"),
                      contextMap = mapOf(SensorType.CAMERA to SensingApplication.APP_VERSION),
                      captureTitle = QUESTION_TITLE,
                      ppgTimer = 30
                    ),
                  recapture = captureId != null,
                  captureId = captureId,
                )
              )
              setResultCallback { sensorCaptureResultFlow ->
                sensorCaptureResultFlow.collect {
                  if (it is SensorCaptureResult.ResourcesStored) {
                    val answer =
                      QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent().apply {
                        value =
                          Coding().apply {
                            code = it.captureId
                            system = CaptureType.VIDEO_PPG.name
                          }
                      }
                    questionnaireViewItem.setAnswer(answer)
                  }
                }
              }
            }
          parentFragmentsChildFragmentManager
            .beginTransaction()
            .add(R.id.screener_container, captureFragment, CaptureFragment.TAG)
            .addToBackStack(CaptureFragment.TAG)
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
