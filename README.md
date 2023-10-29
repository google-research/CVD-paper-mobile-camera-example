# Sensory

Sensory is an illustrative code intended to accompany an upcoming research publication on models trained on mobile-sensor data to evaluate CVD risk.

## Features

1. **Capture**: Provides an example of how to capture camera data on Android devices that would be appropriate for such models. 
2. **Upload**: Provides an example functionality for uploading processed images and image sequences to a blob-storage that can be owned by a 3P, which is a common need for data collection programs. This code is provided to underscore the need for robust uploading capabilities for these use cases.  It is incomplete for production use, which would also need error-handling, authentication, etc.
3. **FHIR Questionnaires**: Integrates with FHIR-SDK to generate forms (participant registration and data capturing) using FHIR-SDC which generates FHIR resources (Patient, DocumentReference). Note: One needs to configure fhir server to sync fhir resources.

# Setup
## Capture
1. Create a `CaptureFragment` instance. Set `CaptureInfo` and `CaptureResult` collector.
   ```agsl
   val captureFragment =
       CaptureFragment().apply {
         setCaptureInfo(
           CaptureInfo(
             captureId = captureId,
             participantId = participantId,
             captureType = CaptureType.VIDEO_PPG,
             captureFolder = "Sensory/Participant_$participantId/$QUESTION_TITLE",
             captureSettings = CaptureSettings(...)
           )
         )
         setSensorCaptureResultCollector { sensorCaptureResultFlow ->
           sensorCaptureResultFlow.collect {
             if (it is SensorCaptureResult.ResourcesStored) {
               ...
             }
           }
         }
       }
   ```
2. Simply add to / replace any fragment container with this fragment.
   ```agsl
   context.supportFragmentManager
   .beginTransaction()
   .replace(R.id.nav_host_fragment, captureFragment)
   .setReorderingAllowed(true)
   .addToBackStack(null)
   .commit()
   ```

## Upload:-

### Setup MinIO Server

1. Run MinIO server as docker container: 
   
   `sudo docker run -p 9000:9000 -p 9001:9001 quay.io/minio/minio server /data --console-address ":9001"`
   
   **Troubleshooting**: You might need to run `ufw allow 9000:9010/tcp` before running the docker command. [link](https://github.com/minio/minio#allow-port-access-for-firewalls).
2. Go to http://localhost:9001/ and sign in. Default admin credentials are:-
   ```
   username: minioadmin
   password: minioadmin
   ```
3. Create a bucket `<bucket-name>`

Full details and other ways of setting up MinIO: [Here](https://github.com/minio/minio)

### App Configuration
1. Create file `Sensory/src/main/assets/local.properties`
2. Add following information: 
   ```
   FHIR_BASE_URL=<fhir server url>
   BLOBSTORE_BASE_URL=<Minio Blobstore Base Url>
   BLOBSTORE_BASE_ACCESS_URL=<Minio Blobstore Data Access Url>
   BLOBSTORE_BUCKET_NAME=<bucket-name>
   BLOBSTORE_USER=<Minio Blobstore Account Username>
   BLOBSTORE_PASSWORD=<Minio Blobstore Account Password>
   ```
   **Note**: Storing credentials in the local file has been done since this is for research purpose only. In production case there should be a different authentication service that provides with security key and access key.

### Upload API
Provides 2 APIs to upload captured data:-
1. Periodic upload: `SensingUploadSync.enqueueUploadPeriodicWork(context)`
2. One time upload: `SensingUploadSync.enqueueUploadUniqueWork(context)`

## Fhir SDC Questionnaires
Following are 2 questionnaires that can be used with this app:-
1. `ScreeningConfig.PPG_QUESTIONNAIRE_PATH`: To collect only PPG data.
2. `ScreeningConfig.ANEMIA_QUESTIONNAIRE_PATH`: To collect images of eye, nails along with PPG data for anemia use-case.

To support the above questionnaires we followed instructions from [here](https://github.com/google/android-fhir/wiki/SDCL%3A-Customize-how-a-Questionnaire-is-displayed#custom-questionnaire-components) and added 2 custom widget types: PhotoCaptureViewHolderFactory.WIDGET_TYPE & PPGSensorCaptureViewHolderFactory.WIDGET_TYPE.

Configurations like custom structure-mapping for these 2 questionnaires are defined in `ScreeningConfig`. Set `ScreeningConfig.questionnairePath` and `ScreeningConfig.structureMapping`to whichever questionnaire you want to use. Additionally, you can test the app with your questionnaire designed using the same custom widget types by adding questionnaire-path and it's required structure-mapping in the ScreeningConfig.


# Disclaimer

This is not an official Google product. The code is not intended for use in any clinical settings. It is not intended to be a medical device and is not intended for clinical use of any kind, including but not limited to diagnosis or prognosis. No representations or warranties are made. User or licensee is responsible for verifying and validating adherence to relevant security and data management practices and policies.