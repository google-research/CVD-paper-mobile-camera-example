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

object ScreeningConfig {
  const val ANEMIA_QUESTIONNAIRE_PATH = "anemia-study-questionnaire.json"
  val ANEMIA_SCREENING_STRUCTURE_MAPPING =
    """
          map "http://example.org/fhir/StructureMap/SensorCapture" = 'SensorCapture'
          uses "http://hl7.org/fhir/StructureDefinition/QuestionnaireReponse" as source
          uses "http://hl7.org/fhir/StructureDefinition/Bundle" as target
          
          group SensorCapture(source src : QuestionnaireResponse, target bundle: Bundle) {
            src -> bundle.id = uuid() "rule_bundle_id";
            src -> bundle.type = 'collection' "rule_bundle_type";
            src -> bundle.entry as entry, entry.resource = create('DocumentReference') as ppgdocref then
              ExtractPPGDocumentReference(src, ppgdocref) "rule_extract_document_reference";
            src -> bundle.entry as entry, entry.resource = create('DocumentReference') as photofingernailscloseddocref then
              ExtractFingernailsClosedPhotoDocumentReference(src, photofingernailscloseddocref) "rule_extract_photo_fingernails_closed_document_reference";
            src -> bundle.entry as entry, entry.resource = create('DocumentReference') as photofingernailsopendocref then
              ExtractFingernailsOpenPhotoDocumentReference(src, photofingernailsopendocref) "rule_extract_photo_fingernails_open_document_reference";
            src -> bundle.entry as entry, entry.resource = create('DocumentReference') as photoconjunctivadocref then
              ExtractConjunctivaPhotoDocumentReference(src, photoconjunctivadocref) "rule_extract_photo_conjunctiva_document_reference";
          }

          group ExtractPPGDocumentReference(source src : QuestionnaireResponse, target tgt : Patient) {
            src.item as item where(linkId = 'sensing-capture-group-ppg') then {
              item.item as inner_item where (linkId = 'ppg-capture-api-call') then {
                inner_item.answer first as ans then {
                  ans.value as coding then {
                    coding.code as val -> tgt.type = val "rule_ppg_capture_id";
                  };
                };
              };
            };
          }
          
          group ExtractFingernailsOpenPhotoDocumentReference(source src : QuestionnaireResponse, target tgt : Patient) {
            src.item as item where(linkId = 'sensing-capture-group-fingernails-open') then {
              item.item as inner_item where (linkId = 'photo-capture-fingernails-open-api-call') then {
                inner_item.answer first as ans then {
                  ans.value as coding then {
                    coding.code as val -> tgt.type = val "rule_photo_capture_id";
                  };
                };
              };
            };
          }
          
          group ExtractFingernailsClosedPhotoDocumentReference(source src : QuestionnaireResponse, target tgt : Patient) {
            src.item as item where(linkId = 'sensing-capture-group-fingernails-closed') then {
              item.item as inner_item where (linkId = 'photo-capture-fingernails-closed-api-call') then {
                inner_item.answer first as ans then {
                  ans.value as coding then {
                    coding.code as val -> tgt.type = val "rule_photo_capture_id";
                  };
                };
              };
            };
          }
          
          group ExtractConjunctivaPhotoDocumentReference(source src : QuestionnaireResponse, target tgt : Patient) {
            src.item as item where(linkId = 'sensing-capture-group-conjunctiva') then {
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

  private const val PPG_QUESTIONNAIRE_PATH = "ppg-questionnaire.json"
  private val PPG_SCREENING_STRUCTURE_MAPPING =
    """
          map "http://example.org/fhir/StructureMap/SensorCapture" = 'SensorCapture'
          uses "http://hl7.org/fhir/StructureDefinition/QuestionnaireReponse" as source
          uses "http://hl7.org/fhir/StructureDefinition/Bundle" as target
          
          group SensorCapture(source src : QuestionnaireResponse, target bundle: Bundle) {
            src -> bundle.id = uuid() "rule_bundle_id";
            src -> bundle.type = 'collection' "rule_bundle_type";
            src -> bundle.entry as entry, entry.resource = create('DocumentReference') as ppgdocref then
              ExtractPPGDocumentReference(src, ppgdocref) "rule_extract_document_reference";
          }

          group ExtractPPGDocumentReference(source src : QuestionnaireResponse, target tgt : Patient) {
            src.item as item where(linkId = 'sensing-capture-group-ppg') then {
              item.item as inner_item where (linkId = 'ppg-capture-api-call') then {
                inner_item.answer first as ans then {
                  ans.value as coding then {
                    coding.code as val -> tgt.type = val "rule_ppg_capture_id";
                  };
                };
              };
            };
          }
    """.trimIndent()
  val structureMapping = PPG_SCREENING_STRUCTURE_MAPPING
  const val questionnairePath = PPG_QUESTIONNAIRE_PATH
}
