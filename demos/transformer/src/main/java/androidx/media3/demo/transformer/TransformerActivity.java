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
package androidx.media3.demo.transformer;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static androidx.media3.common.util.Assertions.checkNotNull;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.util.DebugTextViewHelper;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EncoderSelector;
import androidx.media3.transformer.GlFrameProcessor;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.TransformationException;
import androidx.media3.transformer.TransformationRequest;
import androidx.media3.transformer.TransformationResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** An {@link Activity} that transforms and plays media using {@link Transformer}. */
public final class TransformerActivity extends AppCompatActivity {
  private static final String TAG = "TransformerActivity";

  private @MonotonicNonNull PlayerView playerView;
  private @MonotonicNonNull TextView debugTextView;
  private @MonotonicNonNull TextView informationTextView;
  private @MonotonicNonNull ViewGroup progressViewGroup;
  private @MonotonicNonNull LinearProgressIndicator progressIndicator;
  private @MonotonicNonNull Stopwatch transformationStopwatch;
  private @MonotonicNonNull AspectRatioFrameLayout debugFrame;

  @Nullable private DebugTextViewHelper debugTextViewHelper;
  @Nullable private ExoPlayer player;
  @Nullable private Transformer transformer;
  @Nullable private File externalCacheFile;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.transformer_activity);

    playerView = findViewById(R.id.player_view);
    debugTextView = findViewById(R.id.debug_text_view);
    informationTextView = findViewById(R.id.information_text_view);
    progressViewGroup = findViewById(R.id.progress_view_group);
    progressIndicator = findViewById(R.id.progress_indicator);
    debugFrame = findViewById(R.id.debug_aspect_ratio_frame_layout);

    transformationStopwatch =
        Stopwatch.createUnstarted(
            new Ticker() {
              public long read() {
                return android.os.SystemClock.elapsedRealtimeNanos();
              }
            });
  }

  @Override
  protected void onStart() {
    super.onStart();

    checkNotNull(progressIndicator);
    checkNotNull(informationTextView);
    checkNotNull(transformationStopwatch);
    checkNotNull(playerView);
    checkNotNull(debugTextView);
    checkNotNull(progressViewGroup);
    checkNotNull(debugFrame);
    startTransformation();

    playerView.onResume();
  }

  @Override
  protected void onStop() {
    super.onStop();

    checkNotNull(transformer).cancel();
    transformer = null;

    // The stop watch is reset after cancelling the transformation, in case cancelling causes the
    // stop watch to be stopped in a transformer callback.
    checkNotNull(transformationStopwatch).reset();

    checkNotNull(playerView).onPause();
    releasePlayer();

    checkNotNull(externalCacheFile).delete();
    externalCacheFile = null;
  }

  @RequiresNonNull({
    "playerView",
    "debugTextView",
    "informationTextView",
    "progressIndicator",
    "transformationStopwatch",
    "progressViewGroup",
    "debugFrame",
  })
  private void startTransformation() {
    requestTransformerPermission();

    Intent intent = getIntent();
    Uri uri = checkNotNull(intent.getData());
    try {
      externalCacheFile = createExternalCacheFile("transformer-output.mp4");
      String filePath = externalCacheFile.getAbsolutePath();
      @Nullable Bundle bundle = intent.getExtras();
      Transformer transformer = createTransformer(bundle, filePath);
      transformationStopwatch.start();
      transformer.startTransformation(MediaItem.fromUri(uri), filePath);
      this.transformer = transformer;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    informationTextView.setText(R.string.transformation_started);
    playerView.setVisibility(View.GONE);
    Handler mainHandler = new Handler(getMainLooper());
    ProgressHolder progressHolder = new ProgressHolder();
    mainHandler.post(
        new Runnable() {
          @Override
          public void run() {
            if (transformer != null
                && transformer.getProgress(progressHolder)
                    != Transformer.PROGRESS_STATE_NO_TRANSFORMATION) {
              progressIndicator.setProgress(progressHolder.progress);
              informationTextView.setText(
                  getString(
                      R.string.transformation_timer,
                      transformationStopwatch.elapsed(TimeUnit.SECONDS)));
              mainHandler.postDelayed(/* r= */ this, /* delayMillis= */ 500);
            }
          }
        });
  }

  // Create a cache file, resetting it if it already exists.
  private File createExternalCacheFile(String fileName) throws IOException {
    File file = new File(getExternalCacheDir(), fileName);
    if (file.exists() && !file.delete()) {
      throw new IllegalStateException("Could not delete the previous transformer output file");
    }
    if (!file.createNewFile()) {
      throw new IllegalStateException("Could not create the transformer output file");
    }
    return file;
  }

  @RequiresNonNull({
    "playerView",
    "debugTextView",
    "informationTextView",
    "transformationStopwatch",
    "progressViewGroup",
    "debugFrame",
  })
  private Transformer createTransformer(@Nullable Bundle bundle, String filePath) {
    Transformer.Builder transformerBuilder = new Transformer.Builder(/* context= */ this);
    if (bundle != null) {
      TransformationRequest.Builder requestBuilder = new TransformationRequest.Builder();
      requestBuilder.setFlattenForSlowMotion(
          bundle.getBoolean(ConfigurationActivity.SHOULD_FLATTEN_FOR_SLOW_MOTION));
      @Nullable String audioMimeType = bundle.getString(ConfigurationActivity.AUDIO_MIME_TYPE);
      if (audioMimeType != null) {
        requestBuilder.setAudioMimeType(audioMimeType);
      }
      @Nullable String videoMimeType = bundle.getString(ConfigurationActivity.VIDEO_MIME_TYPE);
      if (videoMimeType != null) {
        requestBuilder.setVideoMimeType(videoMimeType);
      }
      int resolutionHeight =
          bundle.getInt(
              ConfigurationActivity.RESOLUTION_HEIGHT, /* defaultValue= */ C.LENGTH_UNSET);
      if (resolutionHeight != C.LENGTH_UNSET) {
        requestBuilder.setResolution(resolutionHeight);
      }

      float scaleX = bundle.getFloat(ConfigurationActivity.SCALE_X, /* defaultValue= */ 1);
      float scaleY = bundle.getFloat(ConfigurationActivity.SCALE_Y, /* defaultValue= */ 1);
      requestBuilder.setScale(scaleX, scaleY);

      float rotateDegrees =
          bundle.getFloat(ConfigurationActivity.ROTATE_DEGREES, /* defaultValue= */ 0);
      requestBuilder.setRotationDegrees(rotateDegrees);

      requestBuilder.setEnableRequestSdrToneMapping(
          bundle.getBoolean(ConfigurationActivity.ENABLE_REQUEST_SDR_TONE_MAPPING));
      requestBuilder.experimental_setEnableHdrEditing(
          bundle.getBoolean(ConfigurationActivity.ENABLE_HDR_EDITING));
      transformerBuilder
          .setTransformationRequest(requestBuilder.build())
          .setRemoveAudio(bundle.getBoolean(ConfigurationActivity.SHOULD_REMOVE_AUDIO))
          .setRemoveVideo(bundle.getBoolean(ConfigurationActivity.SHOULD_REMOVE_VIDEO))
          .setEncoderFactory(
              new DefaultEncoderFactory(
                  EncoderSelector.DEFAULT,
                  /* enableFallback= */ bundle.getBoolean(ConfigurationActivity.ENABLE_FALLBACK)));

      ImmutableList.Builder<GlFrameProcessor> frameProcessors = new ImmutableList.Builder<>();
      @Nullable
      boolean[] selectedFrameProcessors =
          bundle.getBooleanArray(ConfigurationActivity.DEMO_FRAME_PROCESSORS_SELECTIONS);
      if (selectedFrameProcessors != null) {
        if (selectedFrameProcessors[0]) {
          frameProcessors.add(AdvancedFrameProcessorFactory.createDizzyCropFrameProcessor());
        }
        if (selectedFrameProcessors[1]) {
          frameProcessors.add(
              new PeriodicVignetteFrameProcessor(
                  bundle.getFloat(ConfigurationActivity.PERIODIC_VIGNETTE_CENTER_X),
                  bundle.getFloat(ConfigurationActivity.PERIODIC_VIGNETTE_CENTER_Y),
                  /* minInnerRadius= */ bundle.getFloat(
                      ConfigurationActivity.PERIODIC_VIGNETTE_INNER_RADIUS),
                  /* maxInnerRadius= */ bundle.getFloat(
                      ConfigurationActivity.PERIODIC_VIGNETTE_OUTER_RADIUS),
                  bundle.getFloat(ConfigurationActivity.PERIODIC_VIGNETTE_OUTER_RADIUS)));
        }
        if (selectedFrameProcessors[2]) {
          frameProcessors.add(AdvancedFrameProcessorFactory.createSpin3dFrameProcessor());
        }
        if (selectedFrameProcessors[3]) {
          frameProcessors.add(new BitmapOverlayFrameProcessor());
        }
        if (selectedFrameProcessors[4]) {
          frameProcessors.add(AdvancedFrameProcessorFactory.createZoomInTransitionFrameProcessor());
        }
        transformerBuilder.setFrameProcessors(frameProcessors.build());
      }
    }
    return transformerBuilder
        .addListener(
            new Transformer.Listener() {
              @Override
              public void onTransformationCompleted(
                  MediaItem mediaItem, TransformationResult transformationResult) {
                TransformerActivity.this.onTransformationCompleted(filePath);
              }

              @Override
              public void onTransformationError(
                  MediaItem mediaItem, TransformationException exception) {
                TransformerActivity.this.onTransformationError(exception);
              }
            })
        .setDebugViewProvider(new DemoDebugViewProvider())
        .build();
  }

  @RequiresNonNull({
    "informationTextView",
    "progressViewGroup",
    "debugFrame",
    "transformationStopwatch",
  })
  private void onTransformationError(TransformationException exception) {
    transformationStopwatch.stop();
    informationTextView.setText(R.string.transformation_error);
    progressViewGroup.setVisibility(View.GONE);
    debugFrame.removeAllViews();
    Toast.makeText(
            TransformerActivity.this, "Transformation error: " + exception, Toast.LENGTH_LONG)
        .show();
    Log.e(TAG, "Transformation error", exception);
  }

  @RequiresNonNull({
    "playerView",
    "debugTextView",
    "informationTextView",
    "progressViewGroup",
    "debugFrame",
    "transformationStopwatch",
  })
  private void onTransformationCompleted(String filePath) {
    transformationStopwatch.stop();
    informationTextView.setText(
        getString(
            R.string.transformation_completed, transformationStopwatch.elapsed(TimeUnit.SECONDS)));
    progressViewGroup.setVisibility(View.GONE);
    debugFrame.removeAllViews();
    playerView.setVisibility(View.VISIBLE);
    playMediaItem(MediaItem.fromUri("file://" + filePath));
    Log.d(TAG, "Output file path: file://" + filePath);
  }

  @RequiresNonNull({"playerView", "debugTextView"})
  private void playMediaItem(MediaItem mediaItem) {
    playerView.setPlayer(null);
    releasePlayer();

    ExoPlayer player = new ExoPlayer.Builder(/* context= */ this).build();
    playerView.setPlayer(player);
    player.setMediaItem(mediaItem);
    player.play();
    player.prepare();
    this.player = player;
    debugTextViewHelper = new DebugTextViewHelper(player, debugTextView);
    debugTextViewHelper.start();
  }

  private void releasePlayer() {
    if (debugTextViewHelper != null) {
      debugTextViewHelper.stop();
      debugTextViewHelper = null;
    }
    if (player != null) {
      player.release();
      player = null;
    }
  }

  private void requestTransformerPermission() {
    if (Util.SDK_INT < 23) {
      return;
    }
    if (checkSelfPermission(READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {READ_EXTERNAL_STORAGE}, /* requestCode= */ 0);
    }
  }

  private final class DemoDebugViewProvider implements Transformer.DebugViewProvider {

    @Nullable
    @Override
    public SurfaceView getDebugPreviewSurfaceView(int width, int height) {
      // Update the UI on the main thread and wait for the output surface to be available.
      CountDownLatch surfaceCreatedCountDownLatch = new CountDownLatch(1);
      SurfaceView surfaceView = new SurfaceView(/* context= */ TransformerActivity.this);
      runOnUiThread(
          () -> {
            AspectRatioFrameLayout debugFrame = checkNotNull(TransformerActivity.this.debugFrame);
            debugFrame.addView(surfaceView);
            debugFrame.setAspectRatio((float) width / height);
            surfaceView
                .getHolder()
                .addCallback(
                    new SurfaceHolder.Callback() {
                      @Override
                      public void surfaceCreated(SurfaceHolder surfaceHolder) {
                        surfaceCreatedCountDownLatch.countDown();
                      }

                      @Override
                      public void surfaceChanged(
                          SurfaceHolder surfaceHolder, int format, int width, int height) {
                        // Do nothing.
                      }

                      @Override
                      public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                        // Do nothing.
                      }
                    });
          });
      try {
        surfaceCreatedCountDownLatch.await();
      } catch (InterruptedException e) {
        Log.w(TAG, "Interrupted waiting for debug surface.");
        Thread.currentThread().interrupt();
        return null;
      }
      return surfaceView;
    }
  }
}
