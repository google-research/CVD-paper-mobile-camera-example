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

package com.google.android.fitbit.research.sensing.common.libraries.camera.storage;

import static java.lang.Math.min;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CheckReturnValue;
import java.nio.ByteBuffer;

/** Utility methods for encoding images. */
@CheckReturnValue
public final class ImageEncoders {

  private ImageEncoders() {}

  /**
   * Converts an {@link Image} with format {@link ImageFormat.YUV_420_888} to a {@link YuvImage}.
   */
  public static YuvImage toYuvImage(Image image) {
    Preconditions.checkArgument(
        image.getFormat() == ImageFormat.YUV_420_888,
        "Expected image with format YUV_420_888, got %s",
        image.getFormat());

    // Implementation shamelessly copied from
    // http://cs/androidx-platform-dev/camera/camera-core/src/main/java/androidx/camera/core/internal/utils/ImageUtil.java;l=143;rcl=45c97ad58f9d0326aead3c1a5cc432c7ec6b685a

    Image.Plane yPlane = image.getPlanes()[0];
    Image.Plane uPlane = image.getPlanes()[1];
    Image.Plane vPlane = image.getPlanes()[2];

    ByteBuffer yBuffer = yPlane.getBuffer();
    ByteBuffer uBuffer = uPlane.getBuffer();
    ByteBuffer vBuffer = vPlane.getBuffer();
    yBuffer.rewind();
    uBuffer.rewind();
    vBuffer.rewind();

    int ySize = yBuffer.remaining();

    int position = 0;
    byte[] nv21 = new byte[ySize + (image.getWidth() * image.getHeight() / 2)];

    // Add the full y buffer to the array. If rowStride > 1, some padding may be skipped.
    for (int row = 0; row < image.getHeight(); row++) {
      yBuffer.get(nv21, position, image.getWidth());
      position += image.getWidth();
      yBuffer.position(min(ySize, yBuffer.position() - image.getWidth() + yPlane.getRowStride()));
    }

    int chromaHeight = image.getHeight() / 2;
    int chromaWidth = image.getWidth() / 2;
    int vRowStride = vPlane.getRowStride();
    int uRowStride = uPlane.getRowStride();
    int vPixelStride = vPlane.getPixelStride();
    int uPixelStride = uPlane.getPixelStride();

    // Interleave the u and v frames, filling up the rest of the buffer. Use two line buffers to
    // perform faster bulk gets from the byte buffers.
    byte[] vLineBuffer = new byte[vRowStride];
    byte[] uLineBuffer = new byte[uRowStride];
    for (int row = 0; row < chromaHeight; row++) {
      vBuffer.get(vLineBuffer, 0, min(vRowStride, vBuffer.remaining()));
      uBuffer.get(uLineBuffer, 0, min(uRowStride, uBuffer.remaining()));
      int vLineBufferPosition = 0;
      int uLineBufferPosition = 0;
      for (int col = 0; col < chromaWidth; col++) {
        nv21[position++] = vLineBuffer[vLineBufferPosition];
        nv21[position++] = uLineBuffer[uLineBufferPosition];
        vLineBufferPosition += vPixelStride;
        uLineBufferPosition += uPixelStride;
      }
    }
    return new YuvImage(
        nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), /*strides=*/ null);
  }

  /**
   * Converts an Image to a Bitmap.
   *
   * <p>This involves a YUV_420_888 to RBGA_8888 (32RGBA) conversion.
   *
   * <p>Throws if the input Image is invalid.
   *
   * <p>Taken from package com.google.android.apps.lens.spatial.capture.utils, in turn taken from
   * java/com/google/android/libraries/wordlens/util/ImageUtils.java.
   */
  public static Bitmap imageToBitmap(Context ctx, Image image) throws InvalidImageException {
    // Get the YUV data.
    final ByteBuffer yuvBytes = imageToByteBuffer(image);
    return yuvBytesToBitmap(ctx, yuvBytes, image.getWidth(), image.getHeight());
  }

  /**
   * Taken from package com.google.android.apps.lens.spatial.capture.utils, in turn taken from
   * java/com/google/android/libraries/wordlens/util/ImageUtils.java.
   */
  public static Bitmap yuvBytesToBitmap(Context ctx, ByteBuffer yuvBytes, int width, int height) {
    // Convert YUV to RGB.
    final RenderScript rs = RenderScript.create(ctx);

    final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    final Allocation allocationRgb = Allocation.createFromBitmap(rs, bitmap);

    // The ByteBuffer 'output' was initialized using allocateDirect() inside imageToByteBuffer,
    // using yuvByes.array() is legitimate.
    final Allocation allocationYuv =
        Allocation.createSized(rs, Element.U8(rs), yuvBytes.array().length);
    allocationYuv.copyFrom(yuvBytes.array());

    final ScriptIntrinsicYuvToRGB scriptYuvToRgb =
        ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
    scriptYuvToRgb.setInput(allocationYuv);
    scriptYuvToRgb.forEach(allocationRgb);

    allocationRgb.copyTo(bitmap);

    allocationYuv.destroy();
    allocationRgb.destroy();
    rs.destroy();

    return bitmap;
  }

  /** Converts an Image to a ByteBuffer. */
  private static ByteBuffer imageToByteBuffer(final Image image) throws InvalidImageException {
    final Rect crop = image.getCropRect();
    final int width = crop.width();
    final int height = crop.height();

    final Image.Plane[] planes = image.getPlanes();

    if (planes.length == 0) {
      throw new InvalidImageException(
          "Invalid Image object width " + width + " height " + height + " with zero planes");
    }

    final byte[] rowData = new byte[planes[0].getRowStride()];
    final int bufferSize =
        image.getWidth()
            * image.getHeight()
            * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
            / 8;
    final ByteBuffer output = ByteBuffer.allocateDirect(bufferSize);

    int channelOffset = 0;
    int outputStride = 0;

    for (int planeIndex = 0; planeIndex < 3; planeIndex++) {
      if (planeIndex == 0) {
        channelOffset = 0;
        outputStride = 1;
      } else if (planeIndex == 1) {
        channelOffset = width * height + 1;
        outputStride = 2;
      } else if (planeIndex == 2) {
        channelOffset = width * height;
        outputStride = 2;
      }

      final ByteBuffer buffer = planes[planeIndex].getBuffer();
      final int rowStride = planes[planeIndex].getRowStride();
      final int pixelStride = planes[planeIndex].getPixelStride();

      final int shift = (planeIndex == 0) ? 0 : 1;
      final int widthShifted = width >> shift;
      final int heightShifted = height >> shift;

      buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));

      for (int row = 0; row < heightShifted; row++) {
        final int length;

        if (pixelStride == 1 && outputStride == 1) {
          length = widthShifted;
          // The ByteBuffer 'output' was initialized using allocateDirect(), and the computed
          // channelOffset and length are a required part of the image conversion.
          buffer.get(output.array(), channelOffset, length);
          channelOffset += length;
        } else {
          length = (widthShifted - 1) * pixelStride + 1;
          buffer.get(rowData, 0, length);

          for (int col = 0; col < widthShifted; col++) {
            // The ByteBuffer 'output' was initialized using allocateDirect(), and the computed
            // channelOffset and length are a required part of the image conversion.
            output.array()[channelOffset] = rowData[col * pixelStride];
            channelOffset += outputStride;
          }
        }

        if (row < heightShifted - 1) {
          buffer.position(buffer.position() + rowStride - length);
        }
      }
    }

    return output;
  }

  /** Exception thrown when an image cannot be converted to a Bitmap. */
  public static final class InvalidImageException extends Exception {

    InvalidImageException(String msg) {
      super(msg);
    }
  }
}
