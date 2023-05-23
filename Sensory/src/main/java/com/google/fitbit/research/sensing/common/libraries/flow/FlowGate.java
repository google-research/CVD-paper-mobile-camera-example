package com.google.fitbit.research.sensing.common.libraries.flow;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Gates signals from existing {@link Publisher}s.
 *
 * <p>This can be used to create temporary subscriptions that are started or stopped based on user
 * actions. Example:
 *
 * <pre>{@code
 * FlowGate gate = FlowGate.createClosed();
 * Publisher<Long> publisher;
 * Subscriber<Long> recorder;
 *
 * public void onStartClick() {
 *   gate.passThrough(publisher).subscribe(recorder);
 *   gate.open();
 * }
 *
 * public void onStopClick() {
 *   // Subscriber receives onComplete() and is terminated. Publisher can be re-used on next call
 *   // to onStartClick().
 *   gate.completeAndClose();
 * }
 * }</pre>
 */
@CheckReturnValue // see go/why-crv
public final class FlowGate {

  private final List<Runnable> onOpen = Collections.synchronizedList(new ArrayList<>());
  private final List<Runnable> onClose = Collections.synchronizedList(new ArrayList<>());
  private final AtomicBoolean opened = new AtomicBoolean(false);

  public static FlowGate createClosed() {
    return new FlowGate();
  }

  public static FlowGate createOpened() {
    FlowGate gate = new FlowGate();
    gate.open();
    return gate;
  }

  /**
   * Opens the gate. Subscriptions to {@link #passThrough} will receive signals until {@link
   * #completeAndClose} is called.
   */
  @CanIgnoreReturnValue
  public FlowGate open() {
    if (!opened.getAndSet(true)) {
      synchronized (onOpen) {
        for (Runnable op : onOpen) {
          op.run();
        }
        onOpen.clear();
      }
    }
    return this;
  }

  public boolean isOpen() {
    return opened.get();
  }

  /**
   * Wraps a {@link Publisher} so that signals are only sent while the {@link FlowGate} is {@link
   * #open}. If the wrapped Publisher signals {@code onNext} while the gate is closed, these signals
   * are dropped. Termination signals ({@code onComplete} and {@code onError}) will be delayed until
   * the gate is opened.
   *
   * <p>After the gate is closed by {@link #completeAndClose}, any previous subscriptions to {@link
   * #passThrough} will be permanently terminated with {@code onComplete}. Subsequent calls to
   * {@link #passThrough} will start sending signals after the gate is next {@link #open}ed.
   */
  public <T> Publisher<T> passThrough(Publisher<T> publisher) {
    if (opened.get()) {
      SwappablePublisher<T> swappable = SwappablePublisher.create(publisher);
      onClose.add(swappable::setComplete);
      return swappable;
    } else {
      SwappablePublisher<T> swappable = SwappablePublisher.create();
      onClose.add(swappable::setComplete);
      onOpen.add(() -> swappable.setPublisher(publisher));
      return swappable;
    }
  }

  /**
   * Chainable version of {@code passThrough(publisher).subscribe(subscriber)}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Start recording
   * gate = FlowGate.createClosed()
   *   .subscribe(publisher1, subscriber1)
   *   .subscribe(publisher2, subscriber2a, subscriber2b)
   *   .open();
   * // Stop recording
   * gate.completeAndClose();
   * }</pre>
   */
  @CanIgnoreReturnValue
  public <T> FlowGate subscribe(Publisher<T> publisher, Subscriber<? super T>... subscribers) {
    Publisher<T> gatePublisher = passThrough(publisher);
    for (Subscriber<? super T> s : subscribers) {
      gatePublisher.subscribe(s);
    }
    return this;
  }

  /**
   * Closes the gate. All subscriptions to {@link #passThrough} are signalled with {@code
   * onComplete} and terminated.
   */
  public synchronized void completeAndClose() {
    if (opened.getAndSet(false)) {
      synchronized (onClose) {
        for (Runnable op : onClose) {
          op.run();
        }
        onClose.clear();
      }
    }
  }
}