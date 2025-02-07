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
package androidx.media3.demo.transformer;

import android.graphics.Matrix;
import androidx.media3.common.C;
import androidx.media3.common.util.Util;
import androidx.media3.transformer.AdvancedFrameProcessor;
import androidx.media3.transformer.GlFrameProcessor;

/**
 * Factory for {@link GlFrameProcessor GlFrameProcessors} that create video effects by applying
 * transformation matrices to the individual video frames using {@link AdvancedFrameProcessor}.
 */
/* package */ final class AdvancedFrameProcessorFactory {
  /**
   * Returns a {@link GlFrameProcessor} that rescales the frames over the first {@value
   * #ZOOM_DURATION_SECONDS} seconds, such that the rectangle filled with the input frame increases
   * linearly in size from a single point to filling the full output frame.
   */
  public static GlFrameProcessor createZoomInTransitionFrameProcessor() {
    return new AdvancedFrameProcessor(
        /* matrixProvider= */ AdvancedFrameProcessorFactory::calculateZoomInTransitionMatrix);
  }

  /**
   * Returns a {@link GlFrameProcessor} that crops frames to a rectangle that moves on an ellipse.
   */
  public static GlFrameProcessor createDizzyCropFrameProcessor() {
    return new AdvancedFrameProcessor(
        /* matrixProvider= */ AdvancedFrameProcessorFactory::calculateDizzyCropMatrix);
  }

  /**
   * Returns a {@link GlFrameProcessor} that rotates a frame in 3D around the y-axis and applies
   * perspective projection to 2D.
   */
  public static GlFrameProcessor createSpin3dFrameProcessor() {
    return new AdvancedFrameProcessor(
        /* matrixProvider= */ AdvancedFrameProcessorFactory::calculate3dSpinMatrix);
  }

  private static final float ZOOM_DURATION_SECONDS = 2f;
  private static final float DIZZY_CROP_ROTATION_PERIOD_US = 1_500_000f;

  private static Matrix calculateZoomInTransitionMatrix(long presentationTimeUs) {
    Matrix transformationMatrix = new Matrix();
    float scale = Math.min(1, presentationTimeUs / (C.MICROS_PER_SECOND * ZOOM_DURATION_SECONDS));
    transformationMatrix.postScale(/* sx= */ scale, /* sy= */ scale);
    return transformationMatrix;
  }

  private static android.graphics.Matrix calculateDizzyCropMatrix(long presentationTimeUs) {
    double theta = presentationTimeUs * 2 * Math.PI / DIZZY_CROP_ROTATION_PERIOD_US;
    float centerX = 0.5f * (float) Math.cos(theta);
    float centerY = 0.5f * (float) Math.sin(theta);
    android.graphics.Matrix transformationMatrix = new android.graphics.Matrix();
    transformationMatrix.postTranslate(/* dx= */ centerX, /* dy= */ centerY);
    transformationMatrix.postScale(/* sx= */ 2f, /* sy= */ 2f);
    return transformationMatrix;
  }

  private static float[] calculate3dSpinMatrix(long presentationTimeUs) {
    float[] transformationMatrix = new float[16];
    android.opengl.Matrix.frustumM(
        transformationMatrix,
        /* offset= */ 0,
        /* left= */ -1f,
        /* right= */ 1f,
        /* bottom= */ -1f,
        /* top= */ 1f,
        /* near= */ 3f,
        /* far= */ 5f);
    android.opengl.Matrix.translateM(
        transformationMatrix, /* mOffset= */ 0, /* x= */ 0f, /* y= */ 0f, /* z= */ -4f);
    float theta = Util.usToMs(presentationTimeUs) / 10f;
    android.opengl.Matrix.rotateM(
        transformationMatrix, /* mOffset= */ 0, theta, /* x= */ 0f, /* y= */ 1f, /* z= */ 0f);
    return transformationMatrix;
  }
}
