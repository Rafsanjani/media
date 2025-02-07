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
package androidx.media3.exoplayer.rtsp.reader;

import static androidx.media3.common.util.Assertions.checkStateNotNull;

import androidx.media3.common.C;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.rtsp.RtpPacket;
import androidx.media3.exoplayer.rtsp.RtpPayloadFormat;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.TrackOutput;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Parses a VP8 byte stream carried on RTP packets, and extracts VP8 individual video frames as
 * defined in RFC7741.
 */
/* package */ final class RtpVp8Reader implements RtpPayloadReader {
  private static final String TAG = "RtpVP8Reader";

  /** VP9 uses a 90 KHz media clock (RFC7741 Section 4.1). */
  private static final long MEDIA_CLOCK_FREQUENCY = 90_000;

  private final RtpPayloadFormat payloadFormat;

  private @MonotonicNonNull TrackOutput trackOutput;

  /**
   * First received RTP timestamp. All RTP timestamps are dimension-less, the time base is defined
   * by {@link #MEDIA_CLOCK_FREQUENCY}.
   */
  private long firstReceivedTimestamp;

  private int previousSequenceNumber;
  /** The combined size of a sample that is fragmented into multiple RTP packets. */
  private int fragmentedSampleSizeBytes;

  private long startTimeOffsetUs;
  /**
   * Whether the first packet of one VP8 frame is received. A VP8 frame can be split into two RTP
   * packets.
   */
  private boolean gotFirstPacketOfVp8Frame;

  private boolean isKeyFrame;
  private boolean isOutputFormatSet;

  /** Creates an instance. */
  public RtpVp8Reader(RtpPayloadFormat payloadFormat) {
    this.payloadFormat = payloadFormat;
    firstReceivedTimestamp = C.TIME_UNSET;
    previousSequenceNumber = C.INDEX_UNSET;
    fragmentedSampleSizeBytes = C.LENGTH_UNSET;
    // The start time offset must be 0 until the first seek.
    startTimeOffsetUs = 0;
    gotFirstPacketOfVp8Frame = false;
    isKeyFrame = false;
    isOutputFormatSet = false;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, int trackId) {
    trackOutput = extractorOutput.track(trackId, C.TRACK_TYPE_VIDEO);
    trackOutput.format(payloadFormat.format);
  }

  @Override
  public void onReceivingFirstPacket(long timestamp, int sequenceNumber) {}

  @Override
  public void consume(
      ParsableByteArray data, long timestamp, int sequenceNumber, boolean rtpMarker) {
    checkStateNotNull(trackOutput);

    boolean isValidVP8Descriptor = validateVp8Descriptor(data, sequenceNumber);
    if (isValidVP8Descriptor) {
      //  VP8 Payload Header is defined in RFC7741 Section 4.3.
      if (fragmentedSampleSizeBytes == C.LENGTH_UNSET && gotFirstPacketOfVp8Frame) {
        isKeyFrame = (data.peekUnsignedByte() & 0x01) == 0;
      }
      if (!isOutputFormatSet) {
        // Parsing frame data to get width and height, RFC6386 Section 19.1.
        int currPosition = data.getPosition();
        // Skips the frame_tag and start_code.
        data.setPosition(currPosition + 6);
        // RFC6386 Section 19.1 specifically uses little endian.
        int width = data.readLittleEndianUnsignedShort() & 0x3fff;
        int height = data.readLittleEndianUnsignedShort() & 0x3fff;
        data.setPosition(currPosition);

        if (width != payloadFormat.format.width || height != payloadFormat.format.height) {
          trackOutput.format(
              payloadFormat.format.buildUpon().setWidth(width).setHeight(height).build());
        }
        isOutputFormatSet = true;
      }

      int fragmentSize = data.bytesLeft();
      trackOutput.sampleData(data, fragmentSize);
      fragmentedSampleSizeBytes += fragmentSize;

      if (rtpMarker) {
        if (firstReceivedTimestamp == C.TIME_UNSET) {
          firstReceivedTimestamp = timestamp;
        }
        long timeUs = toSampleUs(startTimeOffsetUs, timestamp, firstReceivedTimestamp);
        trackOutput.sampleMetadata(
            timeUs,
            isKeyFrame ? C.BUFFER_FLAG_KEY_FRAME : 0,
            fragmentedSampleSizeBytes,
            /* offset= */ 0,
            /* cryptoData= */ null);
        fragmentedSampleSizeBytes = C.LENGTH_UNSET;
        gotFirstPacketOfVp8Frame = false;
      }
      previousSequenceNumber = sequenceNumber;
    }
  }

  @Override
  public void seek(long nextRtpTimestamp, long timeUs) {
    firstReceivedTimestamp = nextRtpTimestamp;
    fragmentedSampleSizeBytes = C.LENGTH_UNSET;
    startTimeOffsetUs = timeUs;
  }

  /**
   * Returns {@code true} and sets the {@link ParsableByteArray#getPosition() payload.position} to
   * the end of the descriptor, if a valid VP8 descriptor is present.
   */
  private boolean validateVp8Descriptor(ParsableByteArray payload, int packetSequenceNumber) {
    // VP8 Payload Descriptor is defined in RFC7741 Section 4.2.
    int header = payload.readUnsignedByte();
    if (!gotFirstPacketOfVp8Frame) {
      // TODO(b/198620566) Consider using ParsableBitArray.
      // For start of VP8 partition S=1 and PID=0 as per RFC7741 Section 4.2.
      if ((header & 0x10) != 0x1 || (header & 0x07) != 0) {
        Log.w(TAG, "RTP packet is not the start of a new VP8 partition, skipping.");
        return false;
      }
      gotFirstPacketOfVp8Frame = true;
    } else {
      // Check that this packet is in the sequence of the previous packet.
      int expectedSequenceNumber = RtpPacket.getNextSequenceNumber(previousSequenceNumber);
      if (packetSequenceNumber != expectedSequenceNumber) {
        Log.w(
            TAG,
            Util.formatInvariant(
                "Received RTP packet with unexpected sequence number. Expected: %d; received: %d."
                    + " Dropping packet.",
                expectedSequenceNumber, packetSequenceNumber));
        return false;
      }
    }

    // Check if optional X header is present.
    if ((header & 0x80) != 0) {
      int xHeader = payload.readUnsignedByte();

      // Check if optional I header is present.
      if ((xHeader & 0x80) != 0) {
        int iHeader = payload.readUnsignedByte();
        // Check if I header's M bit is present.
        if ((iHeader & 0x80) != 0) {
          payload.skipBytes(1);
        }
      }

      // Check if optional L header is present.
      if ((xHeader & 0x40) != 0) {
        payload.skipBytes(1);
      }

      // Check if optional T or K header(s) is present.
      if ((xHeader & 0x20) != 0 || (xHeader & 0x10) != 0) {
        payload.skipBytes(1);
      }
    }
    return true;
  }

  private static long toSampleUs(
      long startTimeOffsetUs, long rtpTimestamp, long firstReceivedRtpTimestamp) {
    return startTimeOffsetUs
        + Util.scaleLargeTimestamp(
            (rtpTimestamp - firstReceivedRtpTimestamp),
            /* multiplier= */ C.MICROS_PER_SECOND,
            /* divisor= */ MEDIA_CLOCK_FREQUENCY);
  }
}
