# Sensory

Sensory is an illustrative code intended to accompany an upcoming research
publication on models trained on mobile-sensor data to evaluate CVD risk.

## Features

1. Provides an example of how to collect camera based data (signals) on
   Android devices that would be appropriate for such models. 
2. Provides an example functionality for uploading processed images and image 
   sequences to a blob-storage that can be owned by a 3P, which is a common need
   for data collection programs. This code is provided to underscore the need 
   for robust uploading capabilities for these use cases.  It is incomplete for 
   production use, which would also need error-handling, authentication, etc.
3. Integrates with FHIR-SDK to generate forms (participant registration and data
   capturing) using FHIR-SDC which generates FHIR resources (Patient, 
   DocumentReference). Note: One needs to configure fhir server to sync fhir 
   resources.

## Setting up Upload Configuration:-

1. Create file Sensory/src/main/assets/local.properties
2. Add following information: 
```
   FHIR_BASE_URL=<fhir server url>
   BLOBSTORE_BASE_URL=<Minio Blobstore Base Url>
   BLOBSTORE_BASE_ACCESS_URL=<Minio Blobstore Data Access Url>
   BLOBSTORE_BUCKET_NAME=<Minio Blobstore Bucket Name>
   BLOBSTORE_USER=<Minio Blobstore Account Username>
   BLOBSTORE_PASSWORD=<Minio Blobstore Account Password>
```

## Fhir SDC Questionnaires
To support the above questionnaires we followed instructions from [here](https://github.com/google/android-fhir/wiki/SDCL%3A-Customize-how-a-Questionnaire-is-displayed#custom-questionnaire-components) and 
added 2 custom widget types: PhotoCaptureViewHolderFactory.WIDGET_TYPE & 
PPGSensorCaptureViewHolderFactory.WIDGET_TYPE.
Following are 2 questionnaires that can be used with this app:-
1. ScreeningConfig.PPG_QUESTIONNAIRE_PATH: To collect only PPG data.
2. ScreeningConfig.ANEMIA_QUESTIONNAIRE_PATH: To collect images of eye, nails 
along with PPG data for anemia use-case.

Set `ScreeningConfig.questionnairePath` and `ScreeningConfig.structureMapping` 
to whichever questionnaire you want to use. Additionally, you can test the app 
with your questionnaire designed using the same custom widget types by adding 
questionnaire-path and it's required structure-mapping in the ScreeningConfig.


This is not an official Google product. The code is not intended for use in any
clinical settings. It is not intended to be a medical device and is not
intended for clinical use of any kind, including but not limited to diagnosis or
prognosis. No representations or warranties are made. User or licensee is
responsible for verifying and validating adherence to relevant security and data
management practices and policies.