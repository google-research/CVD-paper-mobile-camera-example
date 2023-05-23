package com.google.fitbit.research.sensing.common.libraries.camera;

import android.media.Image;
import androidx.camera.core.ImageProxy;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.fitbit.research.sensing.common.libraries.flow.DirectProcessor;
import com.google.fitbit.research.sensing.common.libraries.flow.RootShared;
import com.google.fitbit.research.sensing.common.libraries.flow.Shared;
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
@CheckReturnValue // see go/why-crv
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

  public SharedImageProxy(ImageProxy imageProxy, Publisher<SharedImageProxy> key) {
    super(imageProxy, key);
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

  /**
   * Convenience method for transforming an {@code Publisher<SharedImageProxy>} into a {@code
   * Publisher<Shared<Image>>}
   */
  public static Publisher<Shared<Image>> asImagePublisher(Publisher<SharedImageProxy> publisher) {
    return DirectProcessor.transformPublisher(publisher, SharedImageProxy::asImage);
  }
}