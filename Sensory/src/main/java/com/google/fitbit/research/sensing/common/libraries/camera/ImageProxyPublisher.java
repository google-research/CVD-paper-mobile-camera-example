package com.google.fitbit.research.sensing.common.libraries.camera;

import androidx.camera.core.ExtendableBuilder;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import com.google.fitbit.research.sensing.common.libraries.flow.CloseablePublisher;
import com.google.fitbit.research.sensing.common.libraries.flow.DirectPublisher;
import java.util.concurrent.Executor;
import org.reactivestreams.Subscriber;

/**
 * Publishes camera frames from an {@link ImageAnalysis}.
 */
public final class ImageProxyPublisher implements CloseablePublisher<SharedImageProxy> {

  private final ImageAnalysis imageAnalysis;
  private final DirectPublisher<SharedImageProxy> outputPublisher;
  private final ImageAnalysis.Analyzer analyzer =
      new ImageAnalysis.Analyzer() {
        @Override
        public void analyze(ImageProxy imageProxy) {
          if (outputPublisher.isTerminal()) {
            imageAnalysis.clearAnalyzer();
          } else {
            SharedImageProxy shared = new SharedImageProxy(imageProxy, ImageProxyPublisher.this);
            outputPublisher.next(shared);
            shared.close();
          }
        }
      };

  private ImageProxyPublisher(
      ExtendableBuilder<ImageAnalysis> imageAnalysisBuilder, Executor executor) {
    this.imageAnalysis = imageAnalysisBuilder.build();
    this.outputPublisher = new DirectPublisher<>();
    imageAnalysis.setAnalyzer(executor, analyzer);
  }

  public static ImageProxyPublisher create(
      ExtendableBuilder<ImageAnalysis> imageAnalysisBuilder, Executor executor) {
    return new ImageProxyPublisher(imageAnalysisBuilder, executor);
  }

  @Override
  public void subscribe(Subscriber<? super SharedImageProxy> subscriber) {
    outputPublisher.subscribe(subscriber);
  }

  /**
   * Returns the {@link ImageAnalysis} that publishes to this stream.
   */
  public ImageAnalysis getImageAnalysis() {
    return imageAnalysis;
  }

  @Override
  public void close() {
    imageAnalysis.clearAnalyzer();
    outputPublisher.complete();
  }
}