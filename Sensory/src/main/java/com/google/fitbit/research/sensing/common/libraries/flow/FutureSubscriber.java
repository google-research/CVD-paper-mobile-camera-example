package com.google.fitbit.research.sensing.common.libraries.flow;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CheckReturnValue;
import org.reactivestreams.Subscriber;

/**
 * Interface for {@link Subscriber}s that allow the creation of one-off {@link ListenableFuture}s
 * from ReactiveStreams.
 *
 * <p>These Subscribers generally subscribe to a stream and wait for the end of the stream or for a
 * specific condition to occur within the stream. Once this happens, the subscription will be
 * cleaned up and {@link #resultFuture} will resolve.
 *
 * <p>Once resolved, instances of FutureSubscriber should not be resubscribed, as {@link
 * #resultFuture} will remain permanently resolved.
 */
@CheckReturnValue 
public interface FutureSubscriber<I, O> extends Subscriber<I> {

  /**
   * Resolves when a specific condition is met, or if the stream terminates.
   */
  public ListenableFuture<O> resultFuture();
}