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
   DocumentReference)

## Setting up Upload Configuration:-

1. Create file Sensory/src/main/assets/local.properties
2. Add following information: 
```
   BASE_URL=<Minio Upload Base Url>
   BASE_ACCESS_URL=<Minio Data Access Url>
   BUCKET_NAME=<Minio Bucket Name>
   USER=<Minio Account Username>
   PASSWORD=<Minio Account Password>
```


This is not an official Google product. The code is not intended for use in any
clinical settings. It is not intended to be a medical device and is not
intended for clinical use of any kind, including but not limited to diagnosis or
prognosis. No representations or warranties are made. User or licensee is
responsible for verifying and validating adherence to relevant security and data
management practices and policies.