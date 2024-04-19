/*
 * Copyright 2023-2024 Google LLC
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

import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

/**
 * Configures the Licensee Gradle plugin for the sensing module.
 *
 * Licensee helps manage and enforce allowed licenses for dependencies in your project, ensuring
 * compliance with your chosen open source licenses.
 *
 * @see [Licensee Plugin](https://github.com/cashapp/licensee)
 */
fun Project.configureLicensee() {
  apply(plugin = "app.cash.licensee")
  configure<app.cash.licensee.LicenseeExtension> {
    allow("Apache-2.0")
    allow("BSD-2-Clause")
    allow("BSD-3-Clause")
    allow("MIT")

    // Streaming API for XML (StAX)
    allowDependency("javax.xml.stream", "stax-api", "1.0") {
      because("Dual-licensed under CDDL 1.0 and GPL v3.")
    }

    // TODO remove this when BlobStoreServiceImpl is moved to application layer
    allowDependency("org.bouncycastle", "bcprov-jdk15on", "1.69") {
      because(
        "This is a transitive dependency in minio-java library - doesn't include its code or binaries., https://www.bouncycastle.org/licence.html"
      )
    }

    // SQL Cipher.
    allowDependency("net.zetetic", "android-database-sqlcipher", "4.5.4") {
      because("Custom license, essentially BSD-3. https://www.zetetic.net/sqlcipher/license/")
    }

    ignoreDependencies("junit", "junit") {
      because("JUnit is used in tests only, so it is not distributed with our library")
    }
  }
}
