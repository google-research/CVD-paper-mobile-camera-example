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

package com.google.android.sensing.upload;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;

import io.minio.credentials.AssumeRoleBaseProvider;
import io.minio.credentials.Credentials;

/** Object representation of response XML of AssumeRoleWithCustomToken API.
 *
 * The process of converting Java code to Kotlin for XML parsing, particularly with nested classes,
 * may result in XML parsing errors. This is because in Kotlin, fields in data models need to be
 * explicitly marked with the '@field' annotation to mirror the behavior of Java classes. The
 * default Kotlin code conversion might not handle XML parsing as expected. Source:
 * https://stackoverflow.com/a/62051293
 */
@Root(name = "AssumeRoleWithCustomTokenResponse", strict = false)
@Namespace(reference = "https://sts.amazonaws.com/doc/2011-06-15/")
class CustomTokenIdentityResponse implements AssumeRoleBaseProvider.Response {
    @Path(value = "AssumeRoleWithCustomTokenResult")
    @Element(name = "Credentials")
    private Credentials credentials;

    public Credentials getCredentials() {
        return credentials;
    }
}