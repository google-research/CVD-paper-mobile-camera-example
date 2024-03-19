/*
 * Copyright 2024 Google LLC
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

package com.google.android.sensing.impl

import android.content.Context
import android.os.Build
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.google.android.sensing.SensingEngine
import com.google.android.sensing.SensingEngineConfiguration
import com.google.android.sensing.SensorManager
import com.google.android.sensing.model.CaptureInfo
import com.google.android.sensing.testing.TEST_EXTERNAL_ID
import com.google.android.sensing.testing.TEST_OUTPUT_FOLDER
import com.google.android.sensing.testing.TEST_SENSOR_FACTORY
import com.google.android.sensing.testing.TEST_SENSOR_TYPE
import com.google.android.sensing.testing.TestApplication
import com.google.android.sensing.testing.TestSensorCaptureRequest
import com.google.android.sensing.testing.TestSensorInitConfig
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Unit tests for [SensorManagerImpl]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], application = TestApplication::class)
class SensorManagerImplTest {
  private val sensorManager =
    SensorManager.getInstance(ApplicationProvider.getApplicationContext<TestApplication>())

  private val sensingEngine =
    SensingEngine.getInstance(ApplicationProvider.getApplicationContext<TestApplication>())

  @Before
  fun beforeSetup() {
    check(
      ApplicationProvider.getApplicationContext<TestApplication>()
        is SensingEngineConfiguration.Provider
    ) {
      "Few tests require a custom application class that implements SensingEngineConfiguration.Provider"
    }
  }

  @After
  fun afterSetup() {
    sensorManager.reset(TEST_SENSOR_TYPE)
    sensorManager.unregisterSensorFactory(TEST_SENSOR_TYPE)
  }

  @Test
  fun registerSensorFactory_checkRegistration_shouldBeTrue() {
    sensorManager.registerSensorFactory(TEST_SENSOR_TYPE, TEST_SENSOR_FACTORY)

    val checkRegistration = sensorManager.checkRegistration(TEST_SENSOR_TYPE)

    assert(checkRegistration)
  }

  @Test
  fun registerSensorFactory_reRegister_shouldGiveIllegalStateException() {
    sensorManager.registerSensorFactory(TEST_SENSOR_TYPE, TEST_SENSOR_FACTORY)

    try {
      sensorManager.registerSensorFactory(TEST_SENSOR_TYPE, TEST_SENSOR_FACTORY)
    } catch (e: Exception) {
      assert(e is IllegalStateException)
    }
  }

  @Test
  fun unregisterSensorFactory_checkRegistration_shouldBeFalse() {
    sensorManager.registerSensorFactory(TEST_SENSOR_TYPE, TEST_SENSOR_FACTORY)
    assert(sensorManager.checkRegistration(TEST_SENSOR_TYPE))

    sensorManager.unregisterSensorFactory(TEST_SENSOR_TYPE)

    assert(!sensorManager.checkRegistration(TEST_SENSOR_TYPE))
  }

  @Test
  fun registerSensorFactory_startCapturing_throwsExceptionAsInitNotCalled() {
    sensorManager.registerSensorFactory(TEST_SENSOR_TYPE, TEST_SENSOR_FACTORY)

    runTest {
      try {
        sensorManager.start(TEST_SENSOR_TYPE, TestSensorCaptureRequest())
      } catch (e: Exception) {
        assert(e is java.lang.IllegalStateException)
      }
    }
  }

  @Test
  fun registerSensorFactory_initThenStart_shouldStartCapturing() {
    sensorManager.registerSensorFactory(TEST_SENSOR_TYPE, TEST_SENSOR_FACTORY)
    val mockContext = Mockito.mock(Context::class.java)
    val testLifecycleOwner = TestLifecycleOwner()

    runTest {
      sensorManager.init(TEST_SENSOR_TYPE, mockContext, testLifecycleOwner, TestSensorInitConfig())
      sensorManager.start(TEST_SENSOR_TYPE, TestSensorCaptureRequest())

      assert(sensorManager.isStarted(TEST_SENSOR_TYPE))
    }
  }

  @Test
  fun registerThenStart_stopCapturing_shouldStopSuccessfully() {
    sensorManager.registerSensorFactory(TEST_SENSOR_TYPE, TEST_SENSOR_FACTORY)
    val mockContext = Mockito.mock(Context::class.java)
    val testLifecycleOwner = TestLifecycleOwner()

    runTest {
      sensorManager.init(TEST_SENSOR_TYPE, mockContext, testLifecycleOwner, TestSensorInitConfig())
      sensorManager.start(TEST_SENSOR_TYPE, TestSensorCaptureRequest())

      assert(sensorManager.isStarted(TEST_SENSOR_TYPE))

      sensorManager.stop(TEST_SENSOR_TYPE)

      assert(!sensorManager.isStarted(TEST_SENSOR_TYPE))
    }
  }

  @Test
  fun registerSensorFactoryThenStart_callReset_shouldRemoveSensorComponents() {
    sensorManager.registerSensorFactory(TEST_SENSOR_TYPE, TEST_SENSOR_FACTORY)
    val mockContext = Mockito.mock(Context::class.java)
    val testLifecycleOwner = TestLifecycleOwner()

    runTest {
      sensorManager.init(TEST_SENSOR_TYPE, mockContext, testLifecycleOwner, TestSensorInitConfig())
      sensorManager.start(TEST_SENSOR_TYPE, TestSensorCaptureRequest())
      sensorManager.reset(TEST_SENSOR_TYPE)

      // start will give IllegalStateException
      try {
        sensorManager.start(TEST_SENSOR_TYPE, TestSensorCaptureRequest())
      } catch (e: Exception) {
        assert(e is java.lang.IllegalStateException)
      }
    }
  }

  @Test
  fun registerSensorFactory_registerListenerThenStart_shouldReceiveCaptureEvents() {
    sensorManager.registerSensorFactory(TEST_SENSOR_TYPE, TEST_SENSOR_FACTORY)
    val mockContext = Mockito.mock(Context::class.java)
    val testLifecycleOwner = TestLifecycleOwner()

    runTest {
      sensorManager.init(TEST_SENSOR_TYPE, mockContext, testLifecycleOwner, TestSensorInitConfig())
      sensorManager.registerListener(
        TEST_SENSOR_TYPE,
        object : SensorManager.AppDataCaptureListener {
          override fun onStart(captureInfo: CaptureInfo) {
            assert(captureInfo.captureFolder == TEST_OUTPUT_FOLDER)
            assert(captureInfo.externalIdentifier == TEST_EXTERNAL_ID)
          }

          override fun onComplete(captureInfo: CaptureInfo) {
            assert(captureInfo.resourceInfoList.isNotEmpty())
          }

          override fun onError(exception: Exception, captureInfo: CaptureInfo?) {}
        }
      )
      sensorManager.start(TEST_SENSOR_TYPE, TestSensorCaptureRequest())
    }
  }

  @Test
  fun registerSensorFactoryListenerThenStart_stopCapture_shouldCreateDbRecords() {
    sensorManager.registerSensorFactory(TEST_SENSOR_TYPE, TEST_SENSOR_FACTORY)
    val mockContext = Mockito.mock(Context::class.java)
    val testLifecycleOwner = TestLifecycleOwner()

    runTest {
      sensorManager.init(TEST_SENSOR_TYPE, mockContext, testLifecycleOwner, TestSensorInitConfig())
      sensorManager.registerListener(
        TEST_SENSOR_TYPE,
        object : SensorManager.AppDataCaptureListener {
          override fun onStart(captureInfo: CaptureInfo) {
            assert(captureInfo.captureId != null)
            assert(captureInfo.captureFolder == TEST_OUTPUT_FOLDER)
            assert(captureInfo.externalIdentifier == TEST_EXTERNAL_ID)
          }

          override fun onComplete(captureInfo: CaptureInfo) {
            assert(captureInfo.captureId != null)
            assert(captureInfo.resourceInfoList.isNotEmpty())
            runTest {
              val captureInfoInRecords = sensingEngine.getCaptureInfo(captureInfo.captureId!!)
              assert(captureInfoInRecords == captureInfo)
            }
          }

          override fun onError(exception: Exception, captureInfo: CaptureInfo?) {}
        }
      )
      sensorManager.start(TEST_SENSOR_TYPE, TestSensorCaptureRequest())
    }
  }

  // TODO multi capture with same sensor instance

  // TODO test cancel API
}
