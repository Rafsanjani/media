/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.content.Context;
import android.graphics.Matrix;
import android.util.Size;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Applies a simple rotation and/or scale in the vertex shader.
 *
 * <p>All input frames' pixels will be preserved and copied into an output frame, potentially
 * changing the width and height of the frame by scaling dimensions to fit.
 *
 * <p>The background color of the output frame will be black.
 */
@UnstableApi
public final class ScaleToFitFrameProcessor implements GlFrameProcessor {

  /** A builder for {@link ScaleToFitFrameProcessor} instances. */
  public static final class Builder {

    // Optional fields.
    private float scaleX;
    private float scaleY;
    private float rotationDegrees;

    /** Creates a builder with default values. */
    public Builder() {
      scaleX = 1;
      scaleY = 1;
      rotationDegrees = 0;
    }

    /**
     * Sets the x and y axis scaling factors to apply to each frame's width and height.
     *
     * <p>The values default to 1, which corresponds to not scaling along both axes.
     *
     * @param scaleX The multiplier by which the frame will scale horizontally, along the x-axis.
     * @param scaleY The multiplier by which the frame will scale vertically, along the y-axis.
     * @return This builder.
     */
    public Builder setScale(float scaleX, float scaleY) {
      this.scaleX = scaleX;
      this.scaleY = scaleY;
      return this;
    }

    /**
     * Sets the counterclockwise rotation degrees.
     *
     * <p>The default value, 0, corresponds to not applying any rotation.
     *
     * @param rotationDegrees The counterclockwise rotation, in degrees.
     * @return This builder.
     */
    public Builder setRotationDegrees(float rotationDegrees) {
      this.rotationDegrees = rotationDegrees;
      return this;
    }

    public ScaleToFitFrameProcessor build() {
      return new ScaleToFitFrameProcessor(scaleX, scaleY, rotationDegrees);
    }
  }

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private final Matrix transformationMatrix;

  private @MonotonicNonNull AdvancedFrameProcessor advancedFrameProcessor;
  private @MonotonicNonNull Size outputSize;
  private @MonotonicNonNull Matrix adjustedTransformationMatrix;

  /**
   * Creates a new instance.
   *
   * @param scaleX The multiplier by which the frame will scale horizontally, along the x-axis.
   * @param scaleY The multiplier by which the frame will scale vertically, along the y-axis.
   * @param rotationDegrees How much to rotate the frame counterclockwise, in degrees.
   */
  private ScaleToFitFrameProcessor(float scaleX, float scaleY, float rotationDegrees) {
    this.transformationMatrix = new Matrix();
    this.transformationMatrix.postScale(scaleX, scaleY);
    this.transformationMatrix.postRotate(rotationDegrees);
  }

  @Override
  public void initialize(Context context, int inputTexId, int inputWidth, int inputHeight)
      throws IOException {
    configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    advancedFrameProcessor = new AdvancedFrameProcessor(adjustedTransformationMatrix);
    advancedFrameProcessor.initialize(context, inputTexId, inputWidth, inputHeight);
  }

  @Override
  public Size getOutputSize() {
    return checkStateNotNull(outputSize);
  }

  @Override
  public void drawFrame(long presentationTimeUs) {
    checkStateNotNull(advancedFrameProcessor).drawFrame(presentationTimeUs);
  }

  @Override
  public void release() {
    if (advancedFrameProcessor != null) {
      advancedFrameProcessor.release();
    }
  }

  @EnsuresNonNull("adjustedTransformationMatrix")
  @VisibleForTesting // Allows robolectric testing of output size calculation without OpenGL.
  /* package */ void configureOutputSizeAndTransformationMatrix(int inputWidth, int inputHeight) {
    checkArgument(inputWidth > 0, "inputWidth must be positive");
    checkArgument(inputHeight > 0, "inputHeight must be positive");

    adjustedTransformationMatrix = new Matrix(transformationMatrix);

    if (transformationMatrix.isIdentity()) {
      outputSize = new Size(inputWidth, inputHeight);
      return;
    }

    float inputAspectRatio = (float) inputWidth / inputHeight;
    // Scale frames by inputAspectRatio, to account for OpenGL's normalized device
    // coordinates (NDC) (a square from -1 to 1 for both x and y) and preserve rectangular
    // display of input pixels during transformations (ex. rotations). With scaling,
    // transformationMatrix operations operate on a rectangle for x from -inputAspectRatio to
    // inputAspectRatio, and y from -1 to 1.
    adjustedTransformationMatrix.preScale(/* sx= */ inputAspectRatio, /* sy= */ 1f);
    adjustedTransformationMatrix.postScale(/* sx= */ 1f / inputAspectRatio, /* sy= */ 1f);

    // Modify transformationMatrix to keep input pixels.
    float[][] transformOnNdcPoints = {{-1, -1, 0, 1}, {-1, 1, 0, 1}, {1, -1, 0, 1}, {1, 1, 0, 1}};
    float minX = Float.MAX_VALUE;
    float maxX = Float.MIN_VALUE;
    float minY = Float.MAX_VALUE;
    float maxY = Float.MIN_VALUE;
    for (float[] transformOnNdcPoint : transformOnNdcPoints) {
      adjustedTransformationMatrix.mapPoints(transformOnNdcPoint);
      minX = min(minX, transformOnNdcPoint[0]);
      maxX = max(maxX, transformOnNdcPoint[0]);
      minY = min(minY, transformOnNdcPoint[1]);
      maxY = max(maxY, transformOnNdcPoint[1]);
    }

    float scaleX = (maxX - minX) / GlUtil.LENGTH_NDC;
    float scaleY = (maxY - minY) / GlUtil.LENGTH_NDC;
    adjustedTransformationMatrix.postScale(1f / scaleX, 1f / scaleY);
    outputSize = new Size(Math.round(inputWidth * scaleX), Math.round(inputHeight * scaleY));
  }
}
