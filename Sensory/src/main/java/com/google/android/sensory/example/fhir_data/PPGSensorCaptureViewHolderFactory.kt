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
import com.google.android.sensory.R
import com.google.android.sensory.example.InstructionsFragment
import com.google.android.sensory.example.SensingApplication
import com.google.android.sensory.sensing_sdk.SensingEngine
import com.google.android.sensory.sensing_sdk.capture.CaptureFragment
import com.google.android.sensory.sensing_sdk.capture.CaptureSettings
import com.google.android.sensory.sensing_sdk.capture.SensorCaptureResult
import com.google.android.sensory.sensing_sdk.model.CaptureInfo
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
          CoroutineScope(Dispatchers.IO).launch {
            livePath.postValue(sensingEngine.listResourceInfoInCapture(captureId)[0].captureTitle)
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
          InstructionsFragment.INSTRUCTION_FRAGMENT_RESULT,
          context,
        ) { _, result ->
          context.supportFragmentManager.popBackStack()
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
                      titleMap = mapOf(SensorType.CAMERA to SensingApplication.APP_VERSION),
                      captureTitle = QUESTION_TITLE
                    ),
                  captureId = captureId,
                )
              )
              setSensorCaptureResultCollector { sensorCaptureResultFlow ->
                sensorCaptureResultFlow.collect {
                  if (it is SensorCaptureResult.ResourceStoringComplete) {
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
          context.supportFragmentManager
            .beginTransaction()
            .replace(R.id.nav_host_fragment, captureFragment)
            .setReorderingAllowed(true)
            .addToBackStack(null)
            .commit()
        }
        context.supportFragmentManager
          .beginTransaction()
          .replace(
            R.id.nav_host_fragment,
            InstructionsFragment().apply {
              arguments = bundleOf(InstructionsFragment.TITLE to QUESTION_TITLE)
            }
          )
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
  const val CAPTURE_TEXT = "Capture Video"
}
