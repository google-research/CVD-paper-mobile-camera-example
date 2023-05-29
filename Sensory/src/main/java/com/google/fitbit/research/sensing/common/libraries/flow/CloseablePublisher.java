package com.google.fitbit.research.sensing.common.libraries.flow;

import org.reactivestreams.Publisher;

/**
 * Represents a stream that can be closed to terminate all existing and future subscriptions with
 * {@code onComplete}.
 */
public interface CloseablePublisher<T> extends Publisher<T>, AutoCloseable {

}