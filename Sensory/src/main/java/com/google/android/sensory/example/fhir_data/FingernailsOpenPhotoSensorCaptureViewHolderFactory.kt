package com.google.android.sensory.example.fhir_data

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
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.QuestionnaireResponse

object FingernailsOpenPhotoSensorCaptureViewHolderFactory :
  QuestionnaireItemViewHolderFactory(com.google.android.fhir.datacapture.R.layout.attachment_view) {
  override fun getQuestionnaireItemViewHolderDelegate() =
    object : QuestionnaireItemViewHolderDelegate {
      override lateinit var questionnaireViewItem: QuestionnaireViewItem
      private lateinit var takePhotoButton: Button
      private lateinit var photoPreview: ConstraintLayout
      private lateinit var photoThumbnail: ImageView
      private lateinit var photoTitle: TextView
      private lateinit var context: AppCompatActivity
      private lateinit var sensingEngine: SensingEngine


      override fun init(itemView: View) {
        itemView.findViewById<Button>(com.google.android.fhir.datacapture.R.id.helpButton).visibility = View.GONE
        itemView.findViewById<MaterialCardView>(com.google.android.fhir.datacapture.R.id.helpCardView).visibility = View.GONE
        itemView.findViewById<TextView>(com.google.android.fhir.datacapture.R.id.helpText).visibility = View.GONE
        itemView.findViewById<Button>(com.google.android.fhir.datacapture.R.id.photo_delete).visibility = View.GONE
        takePhotoButton = itemView.findViewById(com.google.android.fhir.datacapture.R.id.take_photo)
        takePhotoButton.text = "Capture Fingernails Open Photo"
        photoPreview = itemView.findViewById(com.google.android.fhir.datacapture.R.id.photo_preview)
        photoThumbnail = itemView.findViewById(com.google.android.fhir.datacapture.R.id.photo_thumbnail)
        photoTitle = itemView.findViewById(com.google.android.fhir.datacapture.R.id.photo_title)
        context = itemView.context.tryUnwrapContext()!!
        sensingEngine = SensingApplication.sensingEngine(context)
      }


      override fun bind(questionnaireViewItem: QuestionnaireViewItem) {
        this.questionnaireViewItem = questionnaireViewItem
        displayOrClearInitialPreview()
        displayTakePhotoButton(/*questionnaireItem*/)
        takePhotoButton.setOnClickListener { view -> onTakePhotoButtonClicked(view) }
      }

      override fun setReadOnly(isReadOnly: Boolean) {
        takePhotoButton.isEnabled = !isReadOnly
      }

      private fun displayTakePhotoButton(/*questionnaireItem: Questionnaire.QuestionnaireItemComponent*/) {
        takePhotoButton.visibility = View.VISIBLE
      }

      private fun displayOrClearInitialPreview(){
        val answer = questionnaireViewItem.answers.firstOrNull()

        // Clear preview if there is no answer to prevent showing old previews in views that have
        // been recycled.
        if (answer == null) {
          clearPhotoPreview()
          return
        }

        answer.valueCoding?.let { code ->
          val captureId = code.code
          val livePath = MutableLiveData<Uri>()
          CoroutineScope(Dispatchers.IO).launch {
            livePath.postValue(getFirstOrNullImageUri(sensingEngine
              .listResourceInfoInCapture(captureId)[0]
              .resourceFolderPath)!!)
          }
          livePath.observe(context){
            displayPreview(
              attachmentTitle = "Fingernails Open",
              attachmentUri = it
            )
          }
        }
      }

      private fun onTakePhotoButtonClicked(view: View/*, questionnaireItem: Questionnaire.QuestionnaireItemComponent*/) {
        context.supportFragmentManager.setFragmentResultListener(
          CaptureFragment.CAPTURE_COMPLETE,
          context
        ) { _, result ->
          context.supportFragmentManager.popBackStack()
          val captured = result.getBoolean(CaptureFragment.CAPTURED)
          if(!captured){
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
          val instructionsUnderstood = result.getBoolean(InstructionsFragment.INSTRUCTION_UNDERSTOOD)
          if(!instructionsUnderstood){
            return@setFragmentResultListener
          }
          /** [TODO] Need to get patientId anyhow! */
          val participantId = context
            .getSharedPreferences(SensingApplication.SHARED_PREFS_KEY, Context.MODE_PRIVATE)
            .getString(SensingApplication.CURRENT_PATIENT_ID, null)!!
          val captureId = questionnaireViewItem.answers.firstOrNull()?.valueCoding?.code
          val captureFragment = sensingEngine.captureFragment(
            participantId = participantId,
            captureType = CaptureType.IMAGE,
            captureSettings = CaptureSettings(
              fileTypeMap = mapOf(SensorType.CAMERA to "jpeg"),
              metaDataTypeMap = mapOf(SensorType.CAMERA to "tsv"),
              title = "Fingernails Open"
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
            arguments = bundleOf(InstructionsFragment.LAYOUT to R.layout.fragment_fingernails_open_instructions)
          })
          .setReorderingAllowed(true)
          .addToBackStack(null)
          .commit()
      }

      private fun getFirstOrNullImageUri(root: String): Uri? {
        val file = File(root)
        return file.listFiles()?.firstOrNull {
          it.extension == "jpeg"
        }?.toUri()
      }

      private fun displayPreview(
        attachmentTitle: String,
        attachmentByteArray: ByteArray? = null,
        attachmentUri: Uri? = null
      ) {
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

      private fun clearPhotoPreview() {
        photoPreview.visibility = View.GONE
        Glide.with(context).clear(photoThumbnail)
        photoTitle.text = ""
      }
    }

  const val WIDGET_EXTENSION = "http://external-api-call/sensing-backbone"
  const val WIDGET_TYPE = "photo-fingernails-open-capture"
}