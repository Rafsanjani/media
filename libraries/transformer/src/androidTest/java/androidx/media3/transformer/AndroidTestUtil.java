/*
 * Copyright 2021 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;

import android.content.Context;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/** Utilities for instrumentation tests. */
public final class AndroidTestUtil {
  // TODO(b/228865104): Add device capability based test skipping.
  public static final String MP4_ASSET_URI_STRING = "asset:///media/mp4/sample.mp4";
  public static final Format MP4_ASSET_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H264)
          .setWidth(1080)
          .setHeight(720)
          .setFrameRate(29.97f)
          .build();

  public static final String MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING =
      "asset:///media/mp4/sample_with_increasing_timestamps.mp4";
  public static final Format MP4_ASSET_WITH_INCREASING_TIMESTAMPS_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H264)
          .setWidth(1920)
          .setHeight(1080)
          .setFrameRate(30.00f)
          .build();

  /** Baseline profile level 3.0 H.264 stream, which should be supported on all devices. */
  public static final String MP4_ASSET_WITH_INCREASING_TIMESTAMPS_360P_15S_URI_STRING =
      "asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4";

  public static final String MP4_ASSET_SEF_URI_STRING =
      "asset:///media/mp4/sample_sef_slow_motion.mp4";
  public static final Format MP4_ASSET_SEF_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H264)
          .setWidth(320)
          .setHeight(240)
          .setFrameRate(30.472f)
          .build();

  public static final String MP4_REMOTE_10_SECONDS_URI_STRING =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4";
  public static final Format MP4_REMOTE_10_SECONDS_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H264)
          .setWidth(1280)
          .setHeight(720)
          .setFrameRate(29.97f)
          .build();

  /** Test clip transcoded from {@link #MP4_REMOTE_10_SECONDS_URI_STRING} with H264 and MP3. */
  public static final String MP4_REMOTE_H264_MP3_URI_STRING =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/%20android-screens-10s-h264-mp3.mp4";

  public static final Format MP4_REMOTE_H264_MP3_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H264)
          .setWidth(1280)
          .setHeight(720)
          .setFrameRate(29.97f)
          .build();

  public static final String MP4_REMOTE_4K60_PORTRAIT_URI_STRING =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/portrait_4k60.mp4";
  public static final Format MP4_REMOTE_4K60_PORTRAIT_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H264)
          .setWidth(3840)
          .setHeight(2160)
          .setFrameRate(57.39f)
          .build();

  // The 7 HIGHMOTION files are H264 and AAC.
  public static final String MP4_REMOTE_1280W_720H_5_SECOND_HIGHMOTION =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/1280w_720h_highmotion.mp4";
  public static final String MP4_REMOTE_1440W_1440H_5_SECOND_HIGHMOTION =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/1440w_1440h_highmotion.mp4";
  public static final String MP4_REMOTE_1920W_1080H_5_SECOND_HIGHMOTION =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/1920w_1080h_highmotion.mp4";
  public static final String MP4_REMOTE_3840W_2160H_5_SECOND_HIGHMOTION =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/3840w_2160h_highmotion.mp4";
  public static final String MP4_REMOTE_1280W_720H_30_SECOND_HIGHMOTION =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/1280w_720h_30s_highmotion.mp4";
  public static final String MP4_REMOTE_1920W_1080H_30_SECOND_HIGHMOTION =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/1920w_1080h_30s_highmotion.mp4";
  public static final String MP4_REMOTE_3840W_2160H_32_SECOND_HIGHMOTION =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/3840w_2160h_32s_highmotion.mp4";
  /**
   * Log in logcat and in an analysis file that this test was skipped.
   *
   * <p>Analysis file is a JSON summarising the test, saved to the application cache.
   *
   * <p>The analysis json will contain a {@code skipReason} key, with the reason for skipping the
   * test case.
   */
  public static void recordTestSkipped(Context context, String testId, String reason)
      throws JSONException, IOException {
    Log.i(testId, reason);
    JSONObject testJson = new JSONObject();
    testJson.put("skipReason", reason);

    writeTestSummaryToFile(context, testId, testJson);
  }

  /**
   * A {@link Codec.EncoderFactory} that forces encoding, wrapping {@link DefaultEncoderFactory}.
   */
  public static final Codec.EncoderFactory FORCE_ENCODE_ENCODER_FACTORY =
      new Codec.EncoderFactory() {
        @Override
        public Codec createForAudioEncoding(Format format, List<String> allowedMimeTypes)
            throws TransformationException {
          return Codec.EncoderFactory.DEFAULT.createForAudioEncoding(format, allowedMimeTypes);
        }

        @Override
        public Codec createForVideoEncoding(Format format, List<String> allowedMimeTypes)
            throws TransformationException {
          return Codec.EncoderFactory.DEFAULT.createForVideoEncoding(format, allowedMimeTypes);
        }

        @Override
        public boolean audioNeedsEncoding() {
          return true;
        }

        @Override
        public boolean videoNeedsEncoding() {
          return true;
        }
      };

  /**
   * Returns a {@link JSONObject} containing device specific details from {@link Build}, including
   * manufacturer, model, SDK version and build fingerprint.
   */
  public static JSONObject getDeviceDetailsAsJsonObject() throws JSONException {
    return new JSONObject()
        .put("manufacturer", Build.MANUFACTURER)
        .put("model", Build.MODEL)
        .put("sdkVersion", Build.VERSION.SDK_INT)
        .put("fingerprint", Build.FINGERPRINT);
  }

  /**
   * Converts an exception to a {@link JSONObject}.
   *
   * <p>If the exception is a {@link TransformationException}, {@code errorCode} is included.
   */
  public static JSONObject exceptionAsJsonObject(Exception exception) throws JSONException {
    JSONObject exceptionJson = new JSONObject();
    exceptionJson.put("message", exception.getMessage());
    exceptionJson.put("type", exception.getClass());
    if (exception instanceof TransformationException) {
      exceptionJson.put("errorCode", ((TransformationException) exception).errorCode);
    }
    exceptionJson.put("stackTrace", Log.getThrowableString(exception));
    return exceptionJson;
  }

  /**
   * Writes the summary of a test run to the application cache file.
   *
   * <p>The cache filename follows the pattern {@code <testId>-result.txt}.
   *
   * @param context The {@link Context}.
   * @param testId A unique identifier for the transformer test run.
   * @param testJson A {@link JSONObject} containing a summary of the test run.
   */
  public static void writeTestSummaryToFile(Context context, String testId, JSONObject testJson)
      throws IOException, JSONException {
    testJson.put("testId", testId).put("device", getDeviceDetailsAsJsonObject());

    String analysisContents = testJson.toString(/* indentSpaces= */ 2);

    // Log contents as well as writing to file, for easier visibility on individual device testing.
    for (String line : Util.split(analysisContents, "\n")) {
      Log.i(testId, line);
    }

    File analysisFile = createExternalCacheFile(context, /* fileName= */ testId + "-result.txt");
    try (FileWriter fileWriter = new FileWriter(analysisFile)) {
      fileWriter.write(analysisContents);
    }
  }

  /**
   * Checks whether the test should be skipped because the device is incapable of decoding and
   * encoding the given formats. If the test should be skipped, logs the reason for skipping.
   *
   * @param context The {@link Context context}.
   * @param testId The test ID.
   * @param decodingFormat The {@link Format format} to decode.
   * @param encodingFormat The {@link Format format} to encode.
   * @return Whether the test should be skipped.
   */
  public static boolean skipAndLogIfInsufficientCodecSupport(
      Context context, String testId, Format decodingFormat, Format encodingFormat)
      throws IOException, JSONException {
    boolean canDecode = false;
    @Nullable MediaCodecUtil.DecoderQueryException queryException = null;
    try {
      canDecode = canDecode(decodingFormat);
    } catch (MediaCodecUtil.DecoderQueryException e) {
      queryException = e;
    }

    boolean canEncode = canEncode(encodingFormat);

    if (canDecode && canEncode) {
      return false;
    }

    StringBuilder skipReasonBuilder = new StringBuilder();
    if (!canDecode) {
      skipReasonBuilder.append("Cannot decode ").append(decodingFormat).append('\n');
      if (queryException != null) {
        skipReasonBuilder.append(queryException).append('\n');
      }
    }
    if (!canEncode) {
      skipReasonBuilder.append("Cannot encode ").append(encodingFormat);
    }
    recordTestSkipped(context, testId, skipReasonBuilder.toString());
    return true;
  }

  private static boolean canDecode(Format format) throws MediaCodecUtil.DecoderQueryException {
    @Nullable
    MediaCodecInfo decoderInfo =
        MediaCodecUtil.getDecoderInfo(
            checkNotNull(format.sampleMimeType), /* secure= */ false, /* tunneling= */ false);
    if (decoderInfo == null) {
      return false;
    }
    // Use Format.NO_VALUE for frame rate to only check whether size is supported.
    return decoderInfo.isVideoSizeAndRateSupportedV21(
        format.width, format.height, /* frameRate= */ Format.NO_VALUE);
  }

  private static boolean canEncode(Format format) {
    String mimeType = checkNotNull(format.sampleMimeType);
    ImmutableList<android.media.MediaCodecInfo> supportedEncoders =
        EncoderUtil.getSupportedEncoders(mimeType);
    if (supportedEncoders.isEmpty()) {
      return false;
    }
    return EncoderUtil.isSizeSupported(
        supportedEncoders.get(0), mimeType, format.width, format.height);
  }

  /**
   * Creates a {@link File} of the {@code fileName} in the application cache directory.
   *
   * <p>If a file of that name already exists, it is overwritten.
   */
  /* package */ static File createExternalCacheFile(Context context, String fileName)
      throws IOException {
    File file = new File(context.getExternalCacheDir(), fileName);
    checkState(!file.exists() || file.delete(), "Could not delete file: " + file.getAbsolutePath());
    checkState(file.createNewFile(), "Could not create file: " + file.getAbsolutePath());
    return file;
  }

  private AndroidTestUtil() {}
}
