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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Size;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.transformer.GlFrameProcessor;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link GlFrameProcessor} that periodically dims the frames such that pixels are darker the
 * further they are away from the frame center.
 */
/* package */ final class PeriodicVignetteFrameProcessor implements GlFrameProcessor {
  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private static final String VERTEX_SHADER_PATH = "vertex_shader_copy_es2.glsl";
  private static final String FRAGMENT_SHADER_PATH = "fragment_shader_vignette_es2.glsl";
  private static final float DIMMING_PERIOD_US = 5_600_000f;

  private float centerX;
  private float centerY;
  private float minInnerRadius;
  private float deltaInnerRadius;
  private float outerRadius;

  private @MonotonicNonNull Size outputSize;
  private @MonotonicNonNull GlProgram glProgram;

  /**
   * Creates a new instance.
   *
   * <p>The inner radius of the vignette effect oscillates smoothly between {@code minInnerRadius}
   * and {@code maxInnerRadius}.
   *
   * <p>The pixels between the inner radius and the {@code outerRadius} are darkened linearly based
   * on their distance from {@code innerRadius}. All pixels outside {@code outerRadius} are black.
   *
   * <p>The parameters are given in normalized texture coordinates from 0 to 1.
   *
   * @param context The {@link Context}.
   * @param centerX The x-coordinate of the center of the effect.
   * @param centerY The y-coordinate of the center of the effect.
   * @param minInnerRadius The lower bound of the radius that is unaffected by the effect.
   * @param maxInnerRadius The upper bound of the radius that is unaffected by the effect.
   * @param outerRadius The radius after which all pixels are black.
   */
  public PeriodicVignetteFrameProcessor(
      float centerX, float centerY, float minInnerRadius, float maxInnerRadius, float outerRadius) {
    checkArgument(minInnerRadius <= maxInnerRadius);
    checkArgument(maxInnerRadius <= outerRadius);
    this.centerX = centerX;
    this.centerY = centerY;
    this.minInnerRadius = minInnerRadius;
    this.deltaInnerRadius = maxInnerRadius - minInnerRadius;
    this.outerRadius = outerRadius;
  }

  @Override
  public void initialize(Context context, int inputTexId, int inputWidth, int inputHeight)
      throws IOException {
    outputSize = new Size(inputWidth, inputHeight);
    glProgram = new GlProgram(context, VERTEX_SHADER_PATH, FRAGMENT_SHADER_PATH);
    glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
    glProgram.setFloatsUniform("uCenter", new float[] {centerX, centerY});
    glProgram.setFloatsUniform("uOuterRadius", new float[] {outerRadius});
    // Draw the frame on the entire normalized device coordinate space, from -1 to 1, for x and y.
    glProgram.setBufferAttribute(
        "aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.RECTANGLE_VERTICES_COUNT);
    glProgram.setBufferAttribute(
        "aTexSamplingCoord", GlUtil.getTextureCoordinateBounds(), GlUtil.RECTANGLE_VERTICES_COUNT);
  }

  @Override
  public Size getOutputSize() {
    return checkStateNotNull(outputSize);
  }

  @Override
  public void drawFrame(long presentationTimeUs) {
    checkStateNotNull(glProgram).use();
    double theta = presentationTimeUs * 2 * Math.PI / DIMMING_PERIOD_US;
    float innerRadius = minInnerRadius + deltaInnerRadius * (0.5f - 0.5f * (float) Math.cos(theta));
    glProgram.setFloatsUniform("uInnerRadius", new float[] {innerRadius});
    glProgram.bindAttributesAndUniforms();
    // The four-vertex triangle strip forms a quad.
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
  }

  @Override
  public void release() {
    if (glProgram != null) {
      glProgram.delete();
    }
  }
}
