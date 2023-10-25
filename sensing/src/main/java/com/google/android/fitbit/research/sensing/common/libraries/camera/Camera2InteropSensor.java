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

package com.google.android.fitbit.research.sensing.common.libraries.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.TotalCaptureResult;
import androidx.camera.camera2.interop.Camera2CameraControl;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import com.google.auto.value.AutoBuilder;
import com.google.android.fitbit.research.sensing.common.libraries.camera.camera2.CameraCapturePublisher;
import com.google.android.fitbit.research.sensing.common.libraries.sensors.LifecycleFlows;
import com.google.android.fitbit.research.sensing.common.libraries.sensors.LifecyclePublisher;
import com.google.android.fitbit.research.sensing.common.libraries.sensors.MobileSensorV2;
import javax.annotation.Nullable;

/** Extends {@link CameraXSensorV2} with {@link Camera2Interop}-based capabilities. */
@SuppressLint("UnsafeOptInUsageError")
public final class Camera2InteropSensor implements MobileSensorV2<SharedImageProxy> {

  private final CameraXSensorV2 cameraXSensor;
  private final LifecycleRegistry lifecycle;
  private final LifecyclePublisher<TotalCaptureResult> captureResultPublisher;

  Camera2InteropSensor(
      Context context,
      LifecycleOwner boundLifecycle,
      CameraXSensorV2.Builder cameraXSensorBuilder) {
    this.lifecycle = new LifecycleRegistry(this);

    CameraCapturePublisher captureCallback = new CameraCapturePublisher();
    new Camera2Interop.Extender<>(cameraXSensorBuilder.getImageAnalysisBuilder())
        .setSessionCaptureCallback(captureCallback);
    this.cameraXSensor =
        cameraXSensorBuilder.setContext(context).setBoundLifecycle(boundLifecycle).build();
    this.captureResultPublisher =
        LifecycleFlows.lifecyclePublisher(captureCallback, cameraXSensor, context);

    LifecycleFlows.addObserver(
        cameraXSensor,
        (LifecycleEventObserver) (source, event) -> lifecycle.handleLifecycleEvent(event),
        context);
  }

  public static Builder builder() {
    return new AutoBuilder_Camera2InteropSensor_Builder()
        .setCameraXSensorBuilder(CameraXSensorV2.builder());
  }

  @Override
  public LifecyclePublisher<SharedImageProxy> dataPublisher() {
    return cameraXSensor.dataPublisher();
  }

  /** Stream of camera capture results for camera frames produced by {@link #dataPublisher} */
  public LifecyclePublisher<TotalCaptureResult> captureResultPublisher() {
    return captureResultPublisher;
  }

  @Override
  public Lifecycle getLifecycle() {
    return lifecycle;
  }

  /** Returns the underlying {@link CameraXSensorV2}. */
  public CameraXSensorV2 getCameraXSensor() {
    return cameraXSensor;
  }

  /** Returns camera2 controls for the current camera. Only available after {@code onCreate}. */
  @Nullable
  public Camera2CameraControl getCamera2Control() {
    CameraControl control = cameraXSensor.getCameraControl();
    return control == null ? null : Camera2CameraControl.from(control);
  }

  /** Returns camera2 info for the current camera. Only available after {@code onCreate}. */
  @Nullable
  public Camera2CameraInfo getCamera2Info() {
    CameraInfo info = cameraXSensor.getCameraInfo();
    return info == null ? null : Camera2CameraInfo.from(info);
  }

  /** Builder for new {@link CameraXSensorV2} instances. */
  @AutoBuilder(ofClass = Camera2InteropSensor.class)
  public abstract static class Builder implements MobileSensorV2.Builder<SharedImageProxy> {

    @Override
    public abstract Builder setContext(Context context);

    @Override
    public abstract Builder setBoundLifecycle(LifecycleOwner lifecycleOwner);

    public abstract Builder setCameraXSensorBuilder(CameraXSensorV2.Builder cameraXSensorBuilder);

    @Override
    public abstract Camera2InteropSensor build();
  }
}
