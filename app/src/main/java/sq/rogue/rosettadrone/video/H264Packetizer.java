/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 *
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sq.rogue.rosettadrone.video;

import android.annotation.SuppressLint;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RFC 3984.
 * <p>
 * H.264 streaming over RTP.
 * <p>
 * Must be fed with an InputStream containing H.264 NAL units preceded by their length (4 bytes).
 * The stream must start with mpeg4 or 3gpp header, it will be skipped.
 */
public class H264Packetizer extends AbstractPacketizer implements Runnable {

    public final static String TAG = "H264Packetizer";
    byte[] header = new byte[5];
    private ExecutorService executorService;
    private int naluLength = 0;
    private long delay = 0, oldtime = 0;
    private Statistics stats = new Statistics();
    private byte[] sps = null, pps = null, stapa = null;
    private int count = 0;
    private int streamType = 1;


    public H264Packetizer() {
        super();
        socket.setClockFrequency(90000);
/*
        try {
            socket.setDestination(InetAddress.getByName("127.0.0.1"), 5600, 5000);
            Log.d(TAG, "socket address configured");
        } catch (IOException e) {
            Log.d(TAG, "error setting socket", e);
        }
*/
        executorService = Executors.newSingleThreadExecutor();
    }

    public void start() {
        Log.d(TAG, "start()");
        if (executorService != null) {
            executorService.submit(this);
        }
    }

    public void stop() {
        if (executorService != null) {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            executorService.shutdownNow();
        }
    }

    public void setStreamParameters(byte[] pps, byte[] sps) {
        this.pps = pps;
        this.sps = sps;

        // A STAP-A NAL (NAL type 24) containing the sps and pps of the stream
        if (pps != null && sps != null) {
            // STAP-A NAL header + NALU 1 (SPS) size + NALU 2 (PPS) size = 5 bytes
            stapa = new byte[sps.length + pps.length + 5];

            // STAP-A NAL header is 24
            stapa[0] = 24;

            // Write NALU 1 size into the array (NALU 1 is the SPS).
            stapa[1] = (byte) (sps.length >> 8);
            stapa[2] = (byte) (sps.length & 0xFF);

            // Write NALU 2 size into the array (NALU 2 is the PPS).
            stapa[sps.length + 3] = (byte) (pps.length >> 8);
            stapa[sps.length + 4] = (byte) (pps.length & 0xFF);

            // Write NALU 1 into the array, then write NALU 2 into the array.
            System.arraycopy(sps, 0, stapa, 3, sps.length);
            System.arraycopy(pps, 0, stapa, 5 + sps.length, pps.length);
        }
    }

    public void run() {
        long duration = 0;
        stats.reset();
        count = 0;
        streamType = 1;
        socket.setCacheSize(0);

        try {
            while (is.available() > 0) {
                oldtime = System.nanoTime();
                // We read a NAL units from the input stream and we send them
                send();
                // We measure how long it took to receive NAL units from the phone
                duration = System.nanoTime() - oldtime;

                stats.push(duration);
                // Computes the average duration of a NAL unit
                delay = stats.average();
                //Log.d(TAG,"duration: "+duration/1000000+" delay: "+delay/1000000);
            }
        } catch (IOException | InterruptedException e) {
            Log.d(TAG, "exception", e);
        }
    }

    /**
     * Reads a NAL unit in the FIFO and sends it.
     * If it is too big, we split it in FU-A units (RFC 3984).
     */
    @SuppressLint("NewApi")
    private void send() throws IOException, InterruptedException {
        int sum = 1, len = 0, type;

        if (streamType == 0) {
            // NAL units are preceeded by their length, we parse the length
            fill(header, 0, 5);
            ts += delay;
            naluLength = header[3] & 0xFF | (header[2] & 0xFF) << 8 | (header[1] & 0xFF) << 16 | (header[0] & 0xFF) << 24;
            if (naluLength > 100000 || naluLength < 0) resync();
        } else if (streamType == 1) {
            // NAL units are preceeded with 0x00000001
            fill(header, 0, 5);
            //ts = ((MediaCodecInputStream)is).getLastBufferInfo().presentationTimeUs*1000L;
            ts = System.currentTimeMillis() * 1000L;
            //ts += delay;
            naluLength = is.available() + 1;
            if (!(header[0] == 0 && header[1] == 0 && header[2] == 0)) {
                // Turns out, the NAL units are not preceeded with 0x00000001
                Log.e(TAG, "NAL units are not preceeded by 0x00000001");
                streamType = 2;
                return;
            }
        } else {
            // Nothing preceededs the NAL units
            fill(header, 0, 1);
            header[4] = header[0];
            //ts = ((MediaCodecInputStream)is).getLastBufferInfo().presentationTimeUs*1000L;
            ts = System.currentTimeMillis() * 1000L;
            //ts += delay;
            naluLength = is.available() + 1;
        }

        // Parses the NAL unit type
        type = header[4] & 0x1F;


        // The stream already contains NAL unit type 7 or 8, we don't need
        // to add them to the stream ourselves
        if (type == 7 || type == 8) {
            //      Log.v(TAG, "SPS or PPS present in the stream.");
            count++;
            if (count > 4) {
                sps = null;
                pps = null;
            }
        }

        // We send two packets containing NALU type 7 (SPS) and 8 (PPS)
        // Those should allow the H264 stream to be decoded even if no SDP was sent to the decoder.
        if (type == 5 && sps != null && pps != null) {
            buffer = socket.requestBuffer();
            socket.markNextPacket();
            socket.updateTimestamp(ts);
            System.arraycopy(stapa, 0, buffer, rtphl, stapa.length);
            super.send(rtphl + stapa.length);
            //  Log.e(TAG,"-----  NAL unit - len:"+len+" delay: "+delay);

        }

        //Log.d(TAG,"- Nal unit length: " + naluLength + " delay: "+delay/1000000+" type: "+type);

        // Small NAL unit => Single NAL unit
        if (naluLength <= MAXPACKETSIZE - rtphl - 2) {
            buffer = socket.requestBuffer();
            buffer[rtphl] = header[4];
            len = fill(buffer, rtphl + 1, naluLength - 1);
            socket.updateTimestamp(ts);
            socket.markNextPacket();
            super.send(naluLength + rtphl);
            //  Log.e(TAG,"----- Single NAL unit - len:"+len+" delay: "+delay);
        }
        // Large NAL unit => Split nal unit
        else {

            // Set FU-A header
            header[1] = (byte) (header[4] & 0x1F);  // FU header type
            header[1] += 0x80; // Start bit
            // Set FU-A indicator
            header[0] = (byte) ((header[4] & 0x60) & 0xFF); // FU indicator NRI
            header[0] += 28;

            while (sum < naluLength) {
                buffer = socket.requestBuffer();
                buffer[rtphl] = header[0];
                buffer[rtphl + 1] = header[1];
                socket.updateTimestamp(ts);
                if ((len = fill(buffer, rtphl + 2, naluLength - sum > MAXPACKETSIZE - rtphl - 2 ? MAXPACKETSIZE - rtphl - 2 : naluLength - sum)) < 0)
                    return;
                sum += len;
                // Last packet before next NAL
                if (sum >= naluLength) {
                    // End bit on
                    buffer[rtphl + 1] += 0x40;
                    socket.markNextPacket();
                }
                super.send(len + rtphl + 2);
                // Switch start bit
                header[1] = (byte) (header[1] & 0x7F);
                //    Log.e(TAG,"----- FU-A unit, sum:"+sum);
            }
        }
    }

    private int fill(byte[] buffer, int offset, int length) throws IOException {
        int sum = 0, len;
        while (sum < length) {
            len = is.read(buffer, offset + sum, length - sum);
            if (len < 0) {
                throw new IOException("End of stream");
            } else sum += len;
        }
        return sum;
    }

    private void resync() throws IOException {
        int type;

        Log.e(TAG, "Packetizer out of sync ! Let's try to fix that...(NAL length: " + naluLength + ")");

        while (true) {

            header[0] = header[1];
            header[1] = header[2];
            header[2] = header[3];
            header[3] = header[4];
            header[4] = (byte) is.read();

            type = header[4] & 0x1F;

            if (type == 5 || type == 1) {
                naluLength = header[3] & 0xFF | (header[2] & 0xFF) << 8 | (header[1] & 0xFF) << 16 | (header[0] & 0xFF) << 24;
                if (naluLength > 0 && naluLength < 100000) {
                    oldtime = System.nanoTime();
                    Log.e(TAG, "A NAL unit may have been found in the bit stream !");
                    break;
                }
                if (naluLength == 0) {
                    Log.e(TAG, "NAL unit with NULL size found...");
                } else if (header[3] == 0xFF && header[2] == 0xFF && header[1] == 0xFF && header[0] == 0xFF) {
                    Log.e(TAG, "NAL unit with 0xFFFFFFFF size found...");
                }
            }

        }

    }

}
