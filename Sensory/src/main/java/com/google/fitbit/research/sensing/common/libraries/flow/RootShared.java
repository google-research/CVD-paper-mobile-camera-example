package com.google.fitbit.research.sensing.common.libraries.flow;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.HashSet;
import java.util.Set;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Implementation of {@link Shared} that requires an initial root key.
 *
 * <p>This is generally done in a {@link org.reactivestreams.Publisher} so that multiple {@link
 * org.reactivestreams.Subscriber}s can safely shared the same resource.
 */
@CheckReturnValue // see go/why-crv
public class RootShared<T extends AutoCloseable> implements Shared<T>, AutoCloseable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private T value;
  private Object rootKey;
  private final Set<Object> keys;

  public RootShared(T value, Publisher<?> rootKey) {
    checkNotNull(value);
    checkNotNull(rootKey);
    this.value = value;
    this.rootKey = rootKey;
    this.keys = new HashSet<>();
    this.keys.add(rootKey);
  }

  private class InnerHolder implements Shared.Holder<T> {
    private final Object key;

    private InnerHolder(Object key) {
      this.key = key;
    }

    @Override
    public void close() {
      releaseInternal(key);
    }

    @Override
    public T get() {
      synchronized (keys) {
        checkState(keys.contains(key), "Cannot retrieve value from closed Holder");
        checkNotNull(value);
        return value;
      }
    }
  }

  @Override
  public Shared.Holder<T> acquire(Subscriber<?> key) {
    checkNotNull(key);
    synchronized (keys) {
      if (keys.isEmpty()) {
        throw new ClosedException();
      }
      keys.add(key);
      return new InnerHolder(key);
    }
  }

  @Override
  public void release(Subscriber<?> key) {
    releaseInternal(key);
  }

  private void releaseInternal(Object key) {
    synchronized (keys) {
      if (keys.remove(key) && value != null && keys.isEmpty()) {
        try {
          value.close();
        } catch (RuntimeException e) {
          throw e;
        } catch (Exception e) {
          logger.atWarning().withCause(e).log("Unexpected exception while closing %s", value);
        }
        value = null;
      }
    }
  }

  @Override
  public void close() {
    releaseInternal(rootKey);
    rootKey = null;
  }

  /** Thrown when {@link #acquire} fails because the underlying object has already been closed. */
  public static final class ClosedException extends RuntimeException {
    public ClosedException() {
      super(
          "Failed to acquire a Shared that has already been fully closed. Check that all uses of"
              + " Subscriber<Shared> and Processor<Shared> are immediately calling acquire() in"
              + " onNext().");
    }
  }
}