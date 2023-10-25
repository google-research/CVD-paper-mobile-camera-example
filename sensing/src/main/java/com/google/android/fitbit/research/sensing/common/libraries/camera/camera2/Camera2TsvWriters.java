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

package com.google.android.fitbit.research.sensing.common.libraries.camera.camera2;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.RggbChannelVector;
import android.util.Range;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.android.fitbit.research.sensing.common.libraries.storage.TsvWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;

/** {@link TsvWriter}s for camera2 metadata. */
@CheckReturnValue
public final class Camera2TsvWriters {

  /**
   * {@link TsvWriter} that serializes an entire {@link CaptureResult} and its corresponding {@link
   * CaptureRequest}.
   *
   * <p>Each row contains a key name and its corresponding values from {@link CaptureRequest} and
   * {@link CaptureResult}, if present.
   */
  public static final TsvWriter<CaptureResult> FULL_CAPTURE_RESULT_REQUEST =
      new TsvWriter<CaptureResult>() {
        @Override
        public ImmutableList<String> header() {
          return ImmutableList.of("key", "resultValue", "requestValue");
        }

        @Override
        public ImmutableList<String> row(CaptureResult captureResult) {
          ImmutableList.Builder<String> rowBuilder = ImmutableList.<String>builder();
          rowBuilder.add("frameNumber"); // Column: key
          rowBuilder.add(String.valueOf(captureResult.getFrameNumber())); // Column: resultValue
          rowBuilder.add(""); // Column: requestValue
          // Create map of CaptureRequest keys/values.
          CaptureRequest captureRequest = captureResult.getRequest();
          HashMap<String, Object> requestKv = new HashMap<>();
          for (CaptureRequest.Key<?> key : captureRequest.getKeys()) {
            requestKv.put(key.getName(), captureRequest.get(key));
          }
          // Populate rows for CaptureResult keys, values, and corresponding CaptureRequest values
          // if they exist.
          List<CaptureResult.Key<?>> resultKeys = captureResult.getKeys();
          for (CaptureResult.Key<?> key : resultKeys) {
            String keyName = key.getName();
            rowBuilder.add(key.getName()); // Column: key
            rowBuilder.add(objToString(captureResult.get(key))); // Column: resultValue
            rowBuilder.add(objToString(requestKv.remove(keyName))); // Column: requestValue
          }
          // Populate rows for CaptureRequest keys and values that do not have a corresponding
          // CaptureResult key/value.
          for (Entry<String, Object> entry : requestKv.entrySet()) {
            rowBuilder.add(entry.getKey()); // Column: key
            rowBuilder.add(""); // Column: resultValue
            rowBuilder.add(objToString(entry.getValue())); // Column: requestValue
          }
          return rowBuilder.build();
        }
      };
  /**
   * {@link TsvWriter} that serializes an entire {@link CameraCharacteristics}
   *
   * <p>Each row contains a key name and its corresponding value.
   */
  public static final TsvWriter<CameraCharacteristics> FULL_CAMERA_CHARACTERISTICS =
      new TsvWriter<CameraCharacteristics>() {
        @Override
        public ImmutableList<String> header() {
          return ImmutableList.of("characteristicKey", "characteristicValue");
        }

        @Override
        public ImmutableList<String> row(CameraCharacteristics characteristics) {
          ImmutableList.Builder<String> rowBuilder = ImmutableList.<String>builder();
          for (CameraCharacteristics.Key<?> key : characteristics.getKeys()) {
            rowBuilder.add(key.getName());
            rowBuilder.add(objToString(characteristics.get(key)));
          }
          return rowBuilder.build();
        }
      };

  private Camera2TsvWriters() {}

  public static CaptureResultTsvWriterBuilder captureResultBuilder() {
    return new CaptureResultTsvWriterBuilder();
  }

  private static String objToString(Object value) {
    if (value == null) {
      return "";
    } else if (value.getClass().isArray()) {
      return arrayToString(value);
    } else {
      return value.toString();
    }
  }

  private static String arrayToString(Object obj) {
    if (obj instanceof Object[]) {
      return Arrays.deepToString((Object[]) obj);
    }
    if (obj instanceof boolean[]) {
      return Arrays.toString((boolean[]) obj);
    }
    if (obj instanceof char[]) {
      return Arrays.toString((char[]) obj);
    }
    if (obj instanceof byte[]) {
      return Arrays.toString((byte[]) obj);
    }
    if (obj instanceof short[]) {
      return Arrays.toString((short[]) obj);
    }
    if (obj instanceof int[]) {
      return Arrays.toString((int[]) obj);
    }
    if (obj instanceof long[]) {
      return Arrays.toString((long[]) obj);
    }
    if (obj instanceof float[]) {
      return Arrays.toString((float[]) obj);
    }
    if (obj instanceof double[]) {
      return Arrays.toString((double[]) obj);
    }
    return "";
  }

  /**
   * Builds a {@link TsvWriter} that records separate columns for {@link CaptureResult} keys and
   * values.
   */
  public static class CaptureResultTsvWriterBuilder extends TsvWriter.Builder<CaptureResult> {

    CaptureResultTsvWriterBuilder() {
      super();
    }

    public CaptureResultTsvWriterBuilder addFrameNumberColumn() {
      return (CaptureResultTsvWriterBuilder)
          addColumn("frameNumber", CaptureResult::getFrameNumber);
    }

    public CaptureResultTsvWriterBuilder addColumn(CaptureResult.Key<?> key) {
      return (CaptureResultTsvWriterBuilder) addColumn(key.getName(), (result) -> result.get(key));
    }

    public CaptureResultTsvWriterBuilder addRggbChannelVectorColumn(
        CaptureResult.Key<RggbChannelVector> key) {
      return this.addTransformedColumn(key.getName() + "_red", key, RggbChannelVector::getRed)
          .addTransformedColumn(key.getName() + "_greenEven", key, RggbChannelVector::getGreenEven)
          .addTransformedColumn(key.getName() + "_greenOdd", key, RggbChannelVector::getGreenOdd)
          .addTransformedColumn(key.getName() + "_blue", key, RggbChannelVector::getBlue);
    }

    public CaptureResultTsvWriterBuilder addRangeColumn(CaptureResult.Key<Range<Integer>> key) {
      return this.addTransformedColumn(key.getName() + "_lower", key, Range::getLower)
          .addTransformedColumn(key.getName() + "_upper", key, Range::getUpper);
    }

    private <V> CaptureResultTsvWriterBuilder addTransformedColumn(
        String columnName, CaptureResult.Key<V> key, Function<V, Object> transform) {
      return (CaptureResultTsvWriterBuilder)
          addColumn(
              columnName,
              (result) -> {
                V value = result.get(key);
                return value == null ? null : transform.apply(value);
              });
    }
  }
}
