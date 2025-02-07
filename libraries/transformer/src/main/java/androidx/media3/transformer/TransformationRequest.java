/*
 * Copyright 2020 The Android Open Source Project
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

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.extractor.mp4.Mp4Extractor;

/** A media transformation request. */
@UnstableApi
public final class TransformationRequest {

  /** A builder for {@link TransformationRequest} instances. */
  public static final class Builder {

    private boolean flattenForSlowMotion;
    private float scaleX;
    private float scaleY;
    private float rotationDegrees;
    private int outputHeight;
    @Nullable private String audioMimeType;
    @Nullable private String videoMimeType;
    private boolean enableRequestSdrToneMapping;
    private boolean enableHdrEditing;

    /**
     * Creates a new instance with default values.
     *
     * <p>Use {@link TransformationRequest#buildUpon()} to obtain a builder representing an existing
     * {@link TransformationRequest}.
     */
    public Builder() {
      scaleX = 1;
      scaleY = 1;
      outputHeight = C.LENGTH_UNSET;
    }

    private Builder(TransformationRequest transformationRequest) {
      this.flattenForSlowMotion = transformationRequest.flattenForSlowMotion;
      this.scaleX = transformationRequest.scaleX;
      this.scaleY = transformationRequest.scaleY;
      this.rotationDegrees = transformationRequest.rotationDegrees;
      this.outputHeight = transformationRequest.outputHeight;
      this.audioMimeType = transformationRequest.audioMimeType;
      this.videoMimeType = transformationRequest.videoMimeType;
      this.enableRequestSdrToneMapping = transformationRequest.enableRequestSdrToneMapping;
      this.enableHdrEditing = transformationRequest.enableHdrEditing;
    }

    /**
     * Sets whether the input should be flattened for media containing slow motion markers.
     *
     * <p>The transformed output is obtained by removing the slow motion metadata and by actually
     * slowing down the parts of the video and audio streams defined in this metadata. The default
     * value for {@code flattenForSlowMotion} is {@code false}.
     *
     * <p>Only Samsung Extension Format (SEF) slow motion metadata type is supported. The
     * transformation has no effect if the input does not contain this metadata type.
     *
     * <p>For SEF slow motion media, the following assumptions are made on the input:
     *
     * <ul>
     *   <li>The input container format is (unfragmented) MP4.
     *   <li>The input contains an AVC video elementary stream with temporal SVC.
     *   <li>The recording frame rate of the video is 120 or 240 fps.
     * </ul>
     *
     * <p>If specifying a {@link MediaSource.Factory} using {@link
     * Transformer.Builder#setMediaSourceFactory(MediaSource.Factory)}, make sure that {@link
     * Mp4Extractor#FLAG_READ_SEF_DATA} is set on the {@link Mp4Extractor} used. Otherwise, the slow
     * motion metadata will be ignored and the input won't be flattened.
     *
     * @param flattenForSlowMotion Whether to flatten for slow motion.
     * @return This builder.
     */
    public Builder setFlattenForSlowMotion(boolean flattenForSlowMotion) {
      this.flattenForSlowMotion = flattenForSlowMotion;
      return this;
    }

    /**
     * Sets the x and y axis scaling factors to apply to each frame's width and height, stretching
     * the video along these axes appropriately.
     *
     * <p>The default value for {@code scaleX} and {@code scaleY}, 1, corresponds to not scaling
     * along the x and y axes, respectively.
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
     * Sets the rotation, in degrees, counterclockwise, to apply to each frame.
     *
     * <p>The output frame's width and height are automatically adjusted to preserve all input
     * pixels. The rotated input frame is fitted inside an enclosing black rectangle if its edges
     * aren't parallel to the x and y axes.
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

    /**
     * Sets the output resolution using the output height.
     *
     * <p>Output width of the displayed video will scale to preserve the video's aspect ratio after
     * other transformations.
     *
     * <p>For example, a 1920x1440 video can be scaled to 640x480 by calling setResolution(480).
     *
     * <p>The default value, {@link C#LENGTH_UNSET}, leaves the width and height unchanged unless
     * {@linkplain #setScale(float,float) scaling} or @linkplain #setRotationDegrees(float)
     * rotation} are requested.
     *
     * @param outputHeight The output height of the displayed video, in pixels.
     * @return This builder.
     */
    public Builder setResolution(int outputHeight) {
      this.outputHeight = outputHeight;
      return this;
    }

    /**
     * Sets the video MIME type of the output.
     *
     * <p>The default value is {@code null} which corresponds to using the same MIME type as the
     * input. Supported MIME types are:
     *
     * <ul>
     *   <li>{@link MimeTypes#VIDEO_H263}
     *   <li>{@link MimeTypes#VIDEO_H264}
     *   <li>{@link MimeTypes#VIDEO_H265} from API level 24
     *   <li>{@link MimeTypes#VIDEO_MP4V}
     * </ul>
     *
     * @param videoMimeType The MIME type of the video samples in the output.
     * @return This builder.
     * @throws IllegalArgumentException If the {@code videoMimeType} is non-null but not a video
     *     {@linkplain MimeTypes MIME type}.
     */
    public Builder setVideoMimeType(@Nullable String videoMimeType) {
      checkArgument(
          videoMimeType == null || MimeTypes.isVideo(videoMimeType),
          "Not a video MIME type: " + videoMimeType);
      this.videoMimeType = videoMimeType;
      return this;
    }

    /**
     * Sets the audio MIME type of the output.
     *
     * <p>The default value is {@code null} which corresponds to using the same MIME type as the
     * input. Supported MIME types are:
     *
     * <ul>
     *   <li>{@link MimeTypes#AUDIO_AAC}
     *   <li>{@link MimeTypes#AUDIO_AMR_NB}
     *   <li>{@link MimeTypes#AUDIO_AMR_WB}
     * </ul>
     *
     * @param audioMimeType The MIME type of the audio samples in the output.
     * @return This builder.
     * @throws IllegalArgumentException If the {@code audioMimeType} is non-null but not an audio
     *     {@linkplain MimeTypes MIME type}.
     */
    public Builder setAudioMimeType(@Nullable String audioMimeType) {
      checkArgument(
          audioMimeType == null || MimeTypes.isAudio(audioMimeType),
          "Not an audio MIME type: " + audioMimeType);
      this.audioMimeType = audioMimeType;
      return this;
    }

    /**
     * Sets whether to request tone-mapping to standard dynamic range (SDR). If enabled and
     * supported, high dynamic range (HDR) input will be tone-mapped into an SDR opto-electrical
     * transfer function before processing.
     *
     * <p>The setting has no effect if the input is already in SDR, or if tone-mapping is not
     * supported. Currently tone-mapping is only guaranteed to be supported from Android T onwards.
     *
     * @param enableRequestSdrToneMapping Whether to request tone-mapping down to SDR.
     * @return This builder.
     */
    public Builder setEnableRequestSdrToneMapping(boolean enableRequestSdrToneMapping) {
      this.enableRequestSdrToneMapping = enableRequestSdrToneMapping;
      return this;
    }

    /**
     * Sets whether to attempt to process any input video stream as a high dynamic range (HDR)
     * signal.
     *
     * <p>This method is experimental, and will be renamed or removed in a future release. The HDR
     * editing feature is under development and is intended for developing/testing HDR processing
     * and encoding support. HDR editing can't be enabled at the same time as {@linkplain
     * #setEnableRequestSdrToneMapping(boolean) SDR tone-mapping}.
     *
     * @param enableHdrEditing Whether to attempt to process any input video stream as a high
     *     dynamic range (HDR) signal.
     * @return This builder.
     */
    public Builder experimental_setEnableHdrEditing(boolean enableHdrEditing) {
      this.enableHdrEditing = enableHdrEditing;
      return this;
    }

    /** Builds a {@link TransformationRequest} instance. */
    public TransformationRequest build() {
      return new TransformationRequest(
          flattenForSlowMotion,
          scaleX,
          scaleY,
          rotationDegrees,
          outputHeight,
          audioMimeType,
          videoMimeType,
          enableRequestSdrToneMapping,
          enableHdrEditing);
    }
  }

  /**
   * Whether the input should be flattened for media containing slow motion markers.
   *
   * @see Builder#setFlattenForSlowMotion(boolean)
   */
  public final boolean flattenForSlowMotion;
  /**
   * The requested scale factor, on the x-axis, of the output video, or 1 if inferred from the
   * input.
   *
   * @see Builder#setScale(float, float)
   */
  public final float scaleX;
  /**
   * The requested scale factor, on the y-axis, of the output video, or 1 if inferred from the
   * input.
   *
   * @see Builder#setScale(float, float)
   */
  public final float scaleY;
  /**
   * The requested rotation, in degrees, of the output video, or 0 if inferred from the input.
   *
   * @see Builder#setRotationDegrees(float)
   */
  public final float rotationDegrees;
  /**
   * The requested height of the output video, or {@link C#LENGTH_UNSET} if inferred from the input.
   *
   * @see Builder#setResolution(int)
   */
  public final int outputHeight;
  /**
   * The requested output audio sample {@linkplain MimeTypes MIME type}, or {@code null} if inferred
   * from the input.
   *
   * @see Builder#setAudioMimeType(String)
   */
  @Nullable public final String audioMimeType;
  /**
   * The requested output video sample {@linkplain MimeTypes MIME type}, or {@code null} if inferred
   * from the input.
   *
   * @see Builder#setVideoMimeType(String)
   */
  @Nullable public final String videoMimeType;
  /** Whether to request tone-mapping to standard dynamic range (SDR). */
  public final boolean enableRequestSdrToneMapping;

  /**
   * Whether to attempt to process any input video stream as a high dynamic range (HDR) signal.
   *
   * @see Builder#experimental_setEnableHdrEditing(boolean)
   */
  public final boolean enableHdrEditing;

  private TransformationRequest(
      boolean flattenForSlowMotion,
      float scaleX,
      float scaleY,
      float rotationDegrees,
      int outputHeight,
      @Nullable String audioMimeType,
      @Nullable String videoMimeType,
      boolean enableRequestSdrToneMapping,
      boolean enableHdrEditing) {
    checkArgument(!enableHdrEditing || !enableRequestSdrToneMapping);
    this.flattenForSlowMotion = flattenForSlowMotion;
    this.scaleX = scaleX;
    this.scaleY = scaleY;
    this.rotationDegrees = rotationDegrees;
    this.outputHeight = outputHeight;
    this.audioMimeType = audioMimeType;
    this.videoMimeType = videoMimeType;
    this.enableRequestSdrToneMapping = enableRequestSdrToneMapping;
    this.enableHdrEditing = enableHdrEditing;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TransformationRequest)) {
      return false;
    }
    TransformationRequest that = (TransformationRequest) o;
    return flattenForSlowMotion == that.flattenForSlowMotion
        && scaleX == that.scaleX
        && scaleY == that.scaleY
        && rotationDegrees == that.rotationDegrees
        && outputHeight == that.outputHeight
        && Util.areEqual(audioMimeType, that.audioMimeType)
        && Util.areEqual(videoMimeType, that.videoMimeType)
        && enableRequestSdrToneMapping == that.enableRequestSdrToneMapping
        && enableHdrEditing == that.enableHdrEditing;
  }

  @Override
  public int hashCode() {
    int result = (flattenForSlowMotion ? 1 : 0);
    result = 31 * result + Float.floatToIntBits(scaleX);
    result = 31 * result + Float.floatToIntBits(scaleY);
    result = 31 * result + Float.floatToIntBits(rotationDegrees);
    result = 31 * result + outputHeight;
    result = 31 * result + (audioMimeType != null ? audioMimeType.hashCode() : 0);
    result = 31 * result + (videoMimeType != null ? videoMimeType.hashCode() : 0);
    result = 31 * result + (enableRequestSdrToneMapping ? 1 : 0);
    result = 31 * result + (enableHdrEditing ? 1 : 0);
    return result;
  }

  /**
   * Returns a new {@link TransformationRequest.Builder} initialized with the values of this
   * instance.
   */
  public Builder buildUpon() {
    return new Builder(this);
  }
}
