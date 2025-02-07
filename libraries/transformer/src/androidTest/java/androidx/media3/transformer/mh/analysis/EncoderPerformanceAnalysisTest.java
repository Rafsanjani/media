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

package androidx.media3.transformer.mh.analysis;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.transformer.AndroidTestUtil.recordTestSkipped;

import android.content.Context;
import android.media.MediaFormat;
import android.net.Uri;
import androidx.media3.common.util.Util;
import androidx.media3.transformer.AndroidTestUtil;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EncoderSelector;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.media3.transformer.VideoEncoderSettings;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Instrumentation tests for analyzing encoder performance settings. */
@RunWith(Parameterized.class)
public class EncoderPerformanceAnalysisTest {

  /** A non-realtime {@link MediaFormat#KEY_PRIORITY encoder priority}. */
  private static final int MEDIA_CODEC_PRIORITY_NON_REALTIME = 0;
  /** A realtime {@link MediaFormat#KEY_PRIORITY encoder priority}. */
  private static final int MEDIA_CODEC_PRIORITY_REALTIME = 1;

  private static final ImmutableList<String> INPUT_FILES =
      ImmutableList.of(
          AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING,
          AndroidTestUtil.MP4_REMOTE_4K60_PORTRAIT_URI_STRING);

  private static final ImmutableList<Integer> OPERATING_RATE_SETTINGS =
      ImmutableList.of(VideoEncoderSettings.NO_VALUE, 30, Integer.MAX_VALUE);

  private static final ImmutableList<Integer> PRIORITY_SETTINGS =
      ImmutableList.of(
          // Use NO_VALUE to skip setting priority.
          VideoEncoderSettings.NO_VALUE,
          MEDIA_CODEC_PRIORITY_NON_REALTIME,
          MEDIA_CODEC_PRIORITY_REALTIME);

  @Parameter(0)
  public @MonotonicNonNull String fileUri;

  @Parameter(1)
  public int operatingRate;

  @Parameter(2)
  public int priority;

  @Parameters(name = "analyzePerformance_{0}_OpRate={1}_Priority={2}")
  public static ImmutableList<Object[]> parameters() {
    ImmutableList.Builder<Object[]> parametersBuilder = new ImmutableList.Builder<>();
    for (int i = 0; i < INPUT_FILES.size(); i++) {
      for (int j = 0; j < OPERATING_RATE_SETTINGS.size(); j++) {
        for (int k = 0; k < PRIORITY_SETTINGS.size(); k++) {
          parametersBuilder.add(
              new Object[] {
                INPUT_FILES.get(i), OPERATING_RATE_SETTINGS.get(j), PRIORITY_SETTINGS.get(k)
              });
        }
      }
    }
    return parametersBuilder.build();
  }

  @Test
  public void analyzeEncoderPerformance() throws Exception {
    checkNotNull(fileUri);
    String filename = checkNotNull(Uri.parse(fileUri).getLastPathSegment());
    String testId =
        Util.formatInvariant(
            "analyzePerformance_%s_OpRate_%d_Priority_%d", filename, operatingRate, priority);
    Context context = ApplicationProvider.getApplicationContext();

    if (Util.SDK_INT < 23) {
      recordTestSkipped(
          context,
          testId,
          /* reason= */ "Skipping on this API version due to lack of support for setting operating"
              + " rate and priority.");
      return;
    }

    Map<String, Object> inputValues = new HashMap<>();
    inputValues.put("inputFilename", filename);
    inputValues.put("operatingRate", operatingRate);
    inputValues.put("priority", priority);

    Transformer transformer =
        new Transformer.Builder(context)
            .setRemoveAudio(true)
            .setEncoderFactory(
                new DefaultEncoderFactory(
                    EncoderSelector.DEFAULT,
                    new VideoEncoderSettings.Builder()
                        .setEncoderPerformanceParameters(operatingRate, priority)
                        .build(),
                    /* enableFallback= */ false))
            .build();

    new TransformerAndroidTestRunner.Builder(context, transformer)
        .setInputValues(inputValues)
        .build()
        .run(testId, fileUri);
  }
}
