package com.google.fitbit.research.sensing.common.libraries.flow;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Publisher that immediately terminates all Subscribers with {@code onComplete}.
 */
public final class CompletedPublisher<T> implements Publisher<T> {

  private static final Subscription NO_OP_SUBSCRIPTION =
      new Subscription() {
        @Override
        public void request(long n) {
        }

        @Override
        public void cancel() {
        }
      };

  private CompletedPublisher() {
  }

  public static <T> CompletedPublisher<T> create() {
    return new CompletedPublisher<T>();
  }

  @Override
  public void subscribe(Subscriber<? super T> subscriber) {
    // Reactive Streams Rule 1.12:
    // "Publisher.subscribe MUST call onSubscribe on the provided Subscriber prior to any other
    // signals to that Subscriber"
    // https://github.com/reactive-streams/reactive-streams-jvm#1-publisher-code
    subscriber.onSubscribe(NO_OP_SUBSCRIPTION);
    subscriber.onComplete();
  }
}