package com.google.android.sensory.example.data

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.fhir.datacapture.tryUnwrapContext
import com.google.android.fhir.datacapture.views.QuestionnaireViewItem
import com.google.android.fhir.datacapture.views.factories.QuestionnaireItemViewHolderDelegate
import com.google.android.fhir.datacapture.views.factories.QuestionnaireItemViewHolderFactory
import com.google.android.sensory.R
import com.google.android.sensory.example.SensingApplication
import com.google.android.sensory.sensing_sdk.SensingEngine
import com.google.android.sensory.sensing_sdk.capture.model.CaptureSettings
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.SensorType
import java.util.UUID
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse

object PPGSensorCaptureViewHolderFactory :
  QuestionnaireItemViewHolderFactory(R.layout.sensor_capture_view) {
  override fun getQuestionnaireItemViewHolderDelegate() =
    object : QuestionnaireItemViewHolderDelegate {
      override lateinit var questionnaireViewItem: QuestionnaireViewItem
      private lateinit var capturePpgButton: Button
      private lateinit var textView: TextView
      private lateinit var captureId: String
      private lateinit var context: AppCompatActivity
      private lateinit var sensingEngine: SensingEngine

      override fun init(itemView: View) {
        capturePpgButton = itemView.findViewById(R.id.sensor_capture_ppg)
        capturePpgButton.setText("Capture PPG")
        textView = itemView.findViewById(R.id.sensor_capture_status)
        context = itemView.context.tryUnwrapContext()!!
        sensingEngine = SensingApplication.sensingEngine(context)
      }


      override fun bind(questionnaireViewItem: QuestionnaireViewItem) {
        this.questionnaireViewItem = questionnaireViewItem
        captureId = if (questionnaireViewItem.answers.isEmpty())
          UUID.randomUUID().toString()
        else
          questionnaireViewItem.answers.first().valueCoding.code
        displayCapturePpgButton(questionnaireViewItem.questionnaireItem)
        capturePpgButton.setOnClickListener { view ->
          onCapturePpgButtonClicked(
            view,
            questionnaireViewItem
          )
        }
      }

      override fun setReadOnly(isReadOnly: Boolean) {
        capturePpgButton.isEnabled = !isReadOnly
      }

      private fun displayCapturePpgButton(questionnaireItem: Questionnaire.QuestionnaireItemComponent) {
        capturePpgButton.visibility = View.VISIBLE
        textView.visibility = View.VISIBLE
      }

      private fun onCapturePpgButtonClicked(
        view: View,
        questionnaireViewItem: QuestionnaireViewItem,
      ) {

        val folderId = UUID.randomUUID().toString()
        sensingEngine.captureSensorData(
          context = this.context,
          folderId = folderId,
          captureType = CaptureType.VIDEO_PPG,
          captureSettings = CaptureSettings(fileTypeMap = mapOf(SensorType.CAMERA to "jpeg")),
          captureId = captureId
        )

        val answer = QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent().apply {
          value = Coding().apply {
            code = captureId
            system = CaptureType.VIDEO_PPG.toString()
          }
        }
        questionnaireViewItem.setAnswer(answer)

        textView.text = "PPG captureId: $captureId"
      }

    }

  const val WIDGET_EXTENSION = "http://external-api-call/sensing-backbone"
  const val WIDGET_TYPE = "ppg-capture"

}