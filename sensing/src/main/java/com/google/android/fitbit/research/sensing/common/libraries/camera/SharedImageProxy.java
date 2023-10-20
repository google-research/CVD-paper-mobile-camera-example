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

import android.media.Image;
import androidx.camera.core.ImageProxy;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.android.fitbit.research.sensing.common.libraries.flow.DirectProcessor;
import com.google.android.fitbit.research.sensing.common.libraries.flow.RootShared;
import com.google.android.fitbit.research.sensing.common.libraries.flow.Shared;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * {@link ImageProxy} wrapped with {@link Shared}. See {@link Shared} for more details.
 *
 * <p>Sample usage:
 *
 * <pre>{@code
 * public class ImageSubscriber implements Subscriber<SharedImageProxy> {
 *   @Override
 *   public void onNext(SharedImageProxy shared) {
 *     try (Shared.Holder<ImageProxy> holder = shared.acquire(this)) {
 *       ImageProxy imageProxy = holder.get();
 *       processImage(imageProxy.getImage());
 *     }
 *     // Don't manually call ImageProxy.close(); Shared will handle it automatically when all
 *     // other Subscribers that use the same SharedImageProxy are also finished.
 *   }
 * }
 * }</pre>
 */
@CheckReturnValue
public final class SharedImageProxy extends RootShared<ImageProxy> {

  private final Shared<Image> image =
      new Shared<Image>() {
        @Override
        public Shared.Holder<Image> acquire(Subscriber<?> key) {
          return new ImageHolder(SharedImageProxy.this.acquire(key));
        }

        @Override
        public void release(Subscriber<?> key) {
          SharedImageProxy.this.release(key);
        }
      };

  public SharedImageProxy(ImageProxy imageProxy, Publisher<SharedImageProxy> key) {
    super(imageProxy, key);
  }

  /**
   * Convenience method for transforming an {@code Publisher<SharedImageProxy>} into a {@code
   * Publisher<Shared<Image>>}
   */
  public static Publisher<Shared<Image>> asImagePublisher(Publisher<SharedImageProxy> publisher) {
    return DirectProcessor.transformPublisher(publisher, SharedImageProxy::asImage);
  }

  /**
   * Returns a shared {@link Image} that uses the same keys as SharedImageProxy.
   *
   * <p>This serves as bridge between CameraX-based {@link org.reactivestreams.Publisher}s and
   * Camera2-based {@link org.reactivestreams.Subscriber}s.
   */
  public Shared<Image> asImage() {
    return image;
  }

  private static final class ImageHolder implements Shared.Holder<Image> {

    private final Shared.Holder<ImageProxy> delegate;

    ImageHolder(Shared.Holder<ImageProxy> delegate) {
      this.delegate = delegate;
    }

    @Override
    public Image get() {
      return delegate.get().getImage();
    }

    @Override
    public void close() {
      delegate.close();
    }
  }
}
