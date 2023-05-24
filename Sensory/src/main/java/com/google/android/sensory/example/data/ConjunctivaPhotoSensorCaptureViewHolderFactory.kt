package com.google.android.sensory.example.data

// import com.google.android.sensory.example.data.ConjunctivaPhotoCaptureUtils
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
import com.google.android.sensory.example.data.AnemiaScreenerFragment.Companion.SENSOR_CAPTURE_FOLDER
import com.google.android.sensory.sensing_sdk.SensingEngine
import com.google.android.sensory.sensing_sdk.capture.model.CaptureSettings
import com.google.android.sensory.sensing_sdk.model.CaptureType
import com.google.android.sensory.sensing_sdk.model.SensorType
import java.util.UUID
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.QuestionnaireResponse

object ConjunctivaPhotoSensorCaptureViewHolderFactory :
  QuestionnaireItemViewHolderFactory(R.layout.sensor_capture_view) {
  override fun getQuestionnaireItemViewHolderDelegate() =
    object : QuestionnaireItemViewHolderDelegate {
      override lateinit var questionnaireViewItem: QuestionnaireViewItem
      private lateinit var capturePhotoButton: Button
      private lateinit var textView: TextView
      private var captureId: String? = null
      private lateinit var context: AppCompatActivity
      private lateinit var sensingEngine: SensingEngine


      override fun init(itemView: View) {
        capturePhotoButton = itemView.findViewById(R.id.sensor_capture_ppg)
        capturePhotoButton.setText("Capture Conjunctiva Photo")
        textView = itemView.findViewById(R.id.sensor_capture_status)
        context = itemView.context.tryUnwrapContext()!!
        sensingEngine = SensingApplication.sensingEngine(context)
      }


      override fun bind(questionnaireViewItem: QuestionnaireViewItem) {
        this.questionnaireViewItem = questionnaireViewItem
        displayTakePhotoButton(/*questionnaireItem*/)
        capturePhotoButton.setOnClickListener { view -> onTakePhotoButtonClicked(view) }
      }

      override fun setReadOnly(isReadOnly: Boolean) {
        capturePhotoButton.isEnabled = !isReadOnly
      }

      private fun displayTakePhotoButton(/*questionnaireItem: Questionnaire.QuestionnaireItemComponent*/) {
        capturePhotoButton.visibility = View.VISIBLE
        textView.visibility = View.VISIBLE
      }

      private fun onTakePhotoButtonClicked(view: View/*, questionnaireItem: Questionnaire.QuestionnaireItemComponent*/) {
        val folderId = SENSOR_CAPTURE_FOLDER
        sensingEngine.captureSensorData(
          context = this.context,
          folderId = folderId,
          captureType = CaptureType.IMAGE,
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

        textView.text = "Photo captureId: $captureId"
      }
    }

  const val WIDGET_EXTENSION = "http://external-api-call/sensing-backbone"
  const val WIDGET_TYPE = "photo-conjunctiva-capture"
}