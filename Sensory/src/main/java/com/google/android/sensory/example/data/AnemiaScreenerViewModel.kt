/*
 * Copyright 2021 Google LLC
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

package com.google.android.sensory.example.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.datacapture.mapping.StructureMapExtractionContext
import com.google.android.fhir.testing.jsonParser
import com.google.android.sensory.example.SensingApplication
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Attachment
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.DocumentReference
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Enumerations
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.utils.StructureMapUtilities

/** ViewModel for screener questionnaire screen {@link ScreenerEncounterFragment}. */
class AnemiaScreenerViewModel(application: Application, private val state: SavedStateHandle) :
  AndroidViewModel(application) {
  val questionnaire: String
    get() = getQuestionnaireJson()
  val isResourcesSaved = MutableLiveData<Boolean>()

  val context = application.applicationContext

  private val questionnaireResource: Questionnaire
    get() =
      FhirContext.forCached(FhirVersionEnum.R4).newJsonParser().parseResource(questionnaire) as
        Questionnaire
  private var questionnaireJson: String? = null
  private var fhirEngine: FhirEngine = SensingApplication.fhirEngine(application.applicationContext)

  /**
   * Saves screener encounter questionnaire response into the application database.
   *
   * @param questionnaireResponse screener encounter questionnaire response
   */
  fun saveScreenerEncounter(questionnaireResponse: QuestionnaireResponse, patientId: String) {
    viewModelScope.launch {
      val mapping =
        """
          map "http://example.org/fhir/StructureMap/SensorCapture" = 'SensorCapture'
          uses "http://hl7.org/fhir/StructureDefinition/QuestionnaireReponse" as source
          uses "http://hl7.org/fhir/StructureDefinition/Bundle" as target

          group SensorCapture(source src : QuestionnaireResponse, target bundle: Bundle) {
            src -> bundle.id = uuid() "rule_bundle_id";
            src -> bundle.type = 'collection' "rule_bundle_type";
            src -> bundle.entry as entry, entry.resource = create('DocumentReference') as ppgdocref then
              ExtractPPGDocumentReference(src, ppgdocref) "rule_extract_document_reference";
            src -> bundle.entry as entry, entry.resource = create('DocumentReference') as photofingernailsdocref then
              ExtractFingernailsPhotoDocumentReference(src, photofingernailsdocref) "rule_extract_photo_fingernails_document_reference";
            src -> bundle.entry as entry, entry.resource = create('DocumentReference') as photoconjunctivadocref then
              ExtractConjunctivaPhotoDocumentReference(src, photoconjunctivadocref) "rule_extract_photo_conjunctiva_document_reference";
          }

          group ExtractPPGDocumentReference(source src : QuestionnaireResponse, target tgt : Patient) {
            src.item as item where(linkId = 'sensing-capture-group') then {
              item.item as inner_item where (linkId = 'ppg-capture-api-call') then {
                inner_item.answer first as ans then {
                  ans.value as coding then {
                    coding.code as val -> tgt.type = val "rule_ppg_capture_id";
                  };
                };
              };
          };
        }
          
          group ExtractFingernailsPhotoDocumentReference(source src : QuestionnaireResponse, target tgt : Patient) {
            src.item as item where(linkId = 'sensing-capture-group') then {
              item.item as inner_item where (linkId = 'photo-capture-fingernails-api-call') then {
                    inner_item.answer first as ans then {
                      ans.value as coding then {
                        coding.code as val -> tgt.type = val "rule_photo_capture_id";
                      };
                    };
                  };
            };
          }
          
          group ExtractConjunctivaPhotoDocumentReference(source src : QuestionnaireResponse, target tgt : Patient) {
            src.item as item where(linkId = 'sensing-capture-group') then {
              item.item as inner_item where (linkId = 'photo-capture-conjunctiva-api-call') then {
                    inner_item.answer first as ans then {
                      ans.value as coding then {
                        coding.code as val -> tgt.type = val "rule_photo_capture_id";
                      };
                    };
                  };
            };
          }
        """.trimIndent()

      val iParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()

      val uriTestQuestionnaire =
        iParser.parseResource(Questionnaire::class.java, questionnaireJson) as Questionnaire

      val uriTestQuestionnaireResponse =
        iParser.parseResource(
          QuestionnaireResponse::class.java,
          iParser.encodeResourceToString(questionnaireResponse)
        )
          as QuestionnaireResponse

      val bundle =
        ResourceMapper.extract(
          uriTestQuestionnaire,
          uriTestQuestionnaireResponse,
          StructureMapExtractionContext(context = context) { _, worker ->
            StructureMapUtilities(worker).parse(mapping, "")
          },
        )

      val subjectReference = Reference("Patient/$patientId")
      val encounterId = generateUuid()
      if (isRequiredFieldMissing(bundle)) {
        isResourcesSaved.value = false
        return@launch
      }
      saveResources(bundle, subjectReference, encounterId)
      isResourcesSaved.value = true
    }
  }

  private suspend fun saveResources(
    bundle: Bundle,
    subjectReference: Reference,
    encounterId: String,
  ) {
    val encounterReference = Reference("Encounter/$encounterId")
    bundle.entry.forEach {
      when (val resource = it.resource) {
        is DocumentReference -> {
          val captureId = resource.type.coding[0].code
          resource.id = generateUuid()
          resource.status = Enumerations.DocumentReferenceStatus.CURRENT
          resource.subject = subjectReference
          resource.date = Date()

          // modify data based on the nature of the capture (obtained from captureId)
          val data = Attachment().apply {
            contentType = "application/gzip" // this is for PPG
            url =
              "http://localhost:9001/bucket/$captureId/SENSOR_TYPE/attachment.zip" // getBlobStoreUrl()
            title = "PPG data collected for 30 seconds" // this is for PPG
            creation = Date()
          }

          val dataList: MutableList<DocumentReference.DocumentReferenceContentComponent> =
            mutableListOf(
              DocumentReference.DocumentReferenceContentComponent(data)
            )
          resource.content = dataList
          resource.description = ""
          saveResourceToDatabase(resource)
        }

        is Observation -> {
          if (resource.hasCode()) {
            resource.id = generateUuid()
            resource.subject = subjectReference
            resource.encounter = encounterReference
            saveResourceToDatabase(resource)
          }
        }

        is Condition -> {
          if (resource.hasCode()) {
            resource.id = generateUuid()
            resource.subject = subjectReference
            resource.encounter = encounterReference
            saveResourceToDatabase(resource)
          }
        }

        is Encounter -> {
          resource.subject = subjectReference
          resource.id = encounterId
          saveResourceToDatabase(resource)
        }
      }
    }
  }

  private fun isRequiredFieldMissing(bundle: Bundle): Boolean {
    bundle.entry.forEach {
      val resource = it.resource
      when (resource) {
        is Observation -> {
          if (resource.hasValueQuantity() && !resource.valueQuantity.hasValueElement()) {
            return true
          }
        }
        // TODO check other resources inputs
      }
    }
    return false
  }

  private suspend fun saveResourceToDatabase(resource: Resource) {
    fhirEngine.create(resource)
  }

  private fun getQuestionnaireJson(): String {
    questionnaireJson?.let {
      return it!!
    }
    questionnaireJson =
      readFileFromAssets(state[AnemiaScreenerFragment.QUESTIONNAIRE_FILE_PATH_KEY]!!)
    val questionnaire = jsonParser.parseResource(questionnaireJson) as Questionnaire
    return questionnaireJson!!
  }

  private fun readFileFromAssets(filename: String): String {
    return getApplication<Application>().assets.open(filename).bufferedReader().use {
      it.readText()
    }
  }

  private fun generateUuid(): String {
    return UUID.randomUUID().toString()
  }
}
