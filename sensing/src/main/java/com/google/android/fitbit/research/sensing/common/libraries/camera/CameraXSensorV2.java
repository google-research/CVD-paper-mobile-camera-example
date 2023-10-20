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
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExtendableBuilder;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.UseCase;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import com.google.auto.value.AutoBuilder;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.android.fitbit.research.sensing.common.libraries.sensors.ForwardingLifecyclePublisher;
import com.google.android.fitbit.research.sensing.common.libraries.sensors.LifecyclePublisher;
import com.google.android.fitbit.research.sensing.common.libraries.sensors.MobileSensorV2;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;

/** Implementation of {@link MobileSensorV2} that publishes camera frames from CameraX. */
@CheckReturnValue
public final class CameraXSensorV2 implements MobileSensorV2<SharedImageProxy> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final UseCase[] NO_USE_CASES = new UseCase[0];

  private final CameraSelector cameraSelector;
  private final ImmutableList<UseCase> useCases;
  private final LifecycleRegistry lifecycle;
  private final ListenableFuture<ProcessCameraProvider> processCameraProvider;
  private final ForwardingLifecyclePublisher<SharedImageProxy> lifecycleImageProxyPublisher;
  private final ImageProxyPublisher imageProxyPublisher;

  private UseCase[] boundUseCases = NO_USE_CASES;
  @Nullable private Camera camera;
  private final DefaultLifecycleObserver lifecycleObserver =
      new DefaultLifecycleObserver() {
        @Override
        public void onCreate(LifecycleOwner owner) {
          ProcessCameraProvider cameraProvider = null;
          try {
            cameraProvider = Futures.getDone(processCameraProvider);
          } catch (ExecutionException e) {
            logger.atSevere().withCause(e).log("Failed to get camera provider");
            lifecycleImageProxyPublisher.setException(e);
            return;
          }

          boundUseCases = new UseCase[useCases.size() + 1];
          useCases.toArray(boundUseCases);
          boundUseCases[boundUseCases.length - 1] = imageProxyPublisher.getImageAnalysis();

          try {
            camera = cameraProvider.bindToLifecycle(owner, cameraSelector, boundUseCases);
            lifecycleImageProxyPublisher.set(imageProxyPublisher);
          } catch (IllegalStateException | IllegalArgumentException e) {
            logger.atSevere().withCause(e).log("Failed to bind camera");
            lifecycleImageProxyPublisher.setException(e);
          }

          lifecycleImageProxyPublisher.onCreate(owner);
          lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        }

        @Override
        public void onStart(LifecycleOwner owner) {
          lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START);
          lifecycleImageProxyPublisher.onStart(owner);
        }

        @Override
        public void onStop(LifecycleOwner owner) {
          lifecycleImageProxyPublisher.onStop(owner);
          lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        }

        @Override
        public void onDestroy(LifecycleOwner owner) {
          imageProxyPublisher.close();
          lifecycleImageProxyPublisher.onDestroy(owner);

          try {
            ProcessCameraProvider cameraProvider = Futures.getDone(processCameraProvider);
            cameraProvider.unbind(boundUseCases);
          } catch (ExecutionException e) {
            // We already checked this in onCreate(), so no need to log again.
          }
          boundUseCases = NO_USE_CASES;

          lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        }
      };

  CameraXSensorV2(
      Context context,
      CameraSelector cameraSelector,
      ImmutableList<UseCase> useCases,
      ExtendableBuilder<ImageAnalysis> imageAnalysisBuilder,
      Executor executor,
      LifecycleOwner boundLifecycle) {
    this.cameraSelector = cameraSelector;
    this.useCases = useCases;
    this.lifecycle = new LifecycleRegistry(this);
    this.imageProxyPublisher = ImageProxyPublisher.create(imageAnalysisBuilder, executor);
    this.lifecycleImageProxyPublisher = ForwardingLifecyclePublisher.create();

    this.processCameraProvider = ProcessCameraProvider.getInstance(context);
    bindToLifecycle(boundLifecycle, context);
  }

  // Executors should be replaced in TikTok apps
  @SuppressLint("ConcurrentForbiddenDependencies")
  public static Builder builder() {
    return new AutoBuilder_CameraXSensorV2_Builder()
        .setImageAnalysisBuilder(new ImageAnalysis.Builder())
        .setExecutor(
            Executors.newSingleThreadExecutor(
                (r) -> {
                  Thread thread = new Thread(r);
                  thread.setName(CameraXSensorV2.class.getName() + ".ImageAnalysisThread");
                  return thread;
                }))
        .setCameraSelector(CameraSelector.DEFAULT_BACK_CAMERA);
  }

  @SuppressWarnings(
      "TikTok.AndroidContextLeak") // boundLifecycle isn't used in a background executor
  private void bindToLifecycle(LifecycleOwner boundLifecycle, Context context) {
    Lifecycle lifecycleToObserve = boundLifecycle.getLifecycle();
    processCameraProvider.addListener(
        // If ProcessCameraProvider.getInstance() fails, errors are handled in onCreate().
        () -> lifecycleToObserve.addObserver(lifecycleObserver),
        ContextCompat.getMainExecutor(context));
  }

  @Override
  public LifecyclePublisher<SharedImageProxy> dataPublisher() {
    return lifecycleImageProxyPublisher;
  }

  @Override
  public Lifecycle getLifecycle() {
    return lifecycle;
  }

  /**
   * Returns the current camera.
   *
   * <p>Only populated after {@link #getLifecycle} has reached {@code onCreate}.
   */
  @Nullable
  public Camera getCamera() {
    return camera;
  }

  /**
   * Returns the {@link CameraInfo} for {@link #getCamera}.
   *
   * <p>Only populated after {@link #getLifecycle} has reached {@code onCreate}.
   */
  @Nullable
  public CameraInfo getCameraInfo() {
    return camera == null ? null : camera.getCameraInfo();
  }

  /**
   * Returns the {@link CameraControl} for {@link #getCamera}.
   *
   * <p>Only populated after {@link #getLifecycle} has reached {@code onCreate}.
   */
  @Nullable
  public CameraControl getCameraControl() {
    return camera == null ? null : camera.getCameraControl();
  }

  /**
   * Returns the {@link ImageAnalysis} instance driving {@link #dataPublisher}.
   *
   * <p>Only populated after {@link #getLifecycle} has reached {@code onCreate} and before {@code
   * onCreate}. The analyzer in this image analysis should not be changed.
   */
  @Nullable
  public ImageAnalysis getImageAnalysis() {
    return getUseCase(ImageAnalysis.class);
  }

  /**
   * Returns a {@link UseCase} managed by this {@link CameraXSensorV2}.
   *
   * <p>Only populated after {@link #getLifecycle} has reached {@code onCreate} and before {@code
   * onDestroy}.
   */
  @Nullable
  @SuppressWarnings("unchecked") // Manual type check before casting.
  public <U extends UseCase> U getUseCase(Class<U> useCaseClass) {
    final UseCase[] useCases = boundUseCases;
    for (UseCase useCase : useCases) {
      if (useCase.getClass().equals(useCaseClass)) {
        return (U) useCase;
      }
    }
    return null;
  }

  /** Builder for new {@link CameraXSensorV2} instances. */
  @AutoBuilder(ofClass = CameraXSensorV2.class)
  public abstract static class Builder implements MobileSensorV2.Builder<SharedImageProxy> {

    private final ImmutableList.Builder<UseCase> useCases = ImmutableList.<UseCase>builder();

    @Override
    public abstract Builder setContext(Context context);

    @Override
    public abstract Builder setBoundLifecycle(LifecycleOwner lifecycleOwner);

    public abstract Builder setCameraSelector(CameraSelector cameraSelector);

    abstract ExtendableBuilder<ImageAnalysis> getImageAnalysisBuilder();

    public abstract Builder setImageAnalysisBuilder(ExtendableBuilder<ImageAnalysis> builder);

    public abstract Builder setExecutor(Executor executor);

    /**
     * Adds a {@link UseCase} to the camera.
     *
     * <p>{@link ImageAnalysis} instances should not be added via {@code addUseCase}. The {@code
     * ImageAnalysis} instance used by {@code CameraXSensorV2} can be configured using {@link
     * #setImageAnalysisBuilder}.
     *
     * @throws IllegalArgumentException if the UseCase is an ImageAnalysis instance.
     */
    @CanIgnoreReturnValue
    public Builder addUseCase(UseCase useCase) {
      Preconditions.checkArgument(
          !(useCase instanceof ImageAnalysis),
          "Use setImageAnalysisBuilder instead of addUseCase to configure ImageAnalysis");
      useCases.add(useCase);
      return this;
    }

    abstract Builder setUseCases(ImmutableList<UseCase> useCases);

    @Override
    public CameraXSensorV2 build() {
      return this.setUseCases(useCases.build()).autoBuild();
    }

    abstract CameraXSensorV2 autoBuild();
  }
}
