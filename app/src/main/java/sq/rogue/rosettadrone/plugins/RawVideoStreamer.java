/**
 * This is a RosettaDrone plugin for sending raw video frames via TCP for Computer Vision.
 * This avoids re-encoding the video and using a video buffer, decreases latency and provides better image quality.
 *
 * TODO: Reenable video preview in Rosetta
 * TODO: Add a timestamp of the time the frame was grabbed (not received) or, *only if the video latency is low* (I don't think so), add the telemetry info directly into the image packet to make the absolute pose estimation.
 *
 * Author: Christopher Pereira (rosetta@imatronix.com)
 */

package sq.rogue.rosettadrone.plugins;

import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import dji.sdk.codec.DJICodecManager;
import sq.rogue.rosettadrone.Plugin;
import sq.rogue.rosettadrone.PluginManager;
import sq.rogue.rosettadrone.RDApplication;

public class RawVideoStreamer extends Plugin implements DJICodecManager.YuvDataCallback {
    private static final boolean TEST = false; // Send a testing stream

    PluginManager pluginManager;
    private final int fps = 15; // Must be a divisor of 30 (eg. 1, 3, 5, 6, 10, 15, 30)
    Socket socket;
    OutputStream outputStream;
    TestSender testSender;

    public void init(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
        pluginManager.mainActivity.useCustomDecoder = false; // Messes up the buffer received by onYuvDataReceived()
        pluginManager.mainActivity.useOutputSurface = false; // Avoid crash when clicking on minimap
    }

    private void connect() {
        try {
            socket = new Socket("localhost", 6000);
            outputStream = socket.getOutputStream();
        } catch (IOException e) {
            //e.printStackTrace();
        }
    }

    public class TestSender extends Thread {
        public RawVideoStreamer streamer;
        public boolean stop = false;

        @Override
        public void run() {
            int offset = 0;
            while (!stop) {
                try {
                    int w = 1280;
                    int h = 720;

                    int bufferSize = w * h * 3 / 2;
                    byte[] bytes = new byte[bufferSize];

                    for (int i = 0; i < bytes.length; i++) {
                        bytes[i] = (byte) ((i + offset) % 256);
                    }
                    offset++;

                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    streamer.sendYuvData(null, buffer, bufferSize, w, h);

                    Thread.sleep(1000 / fps);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendInt(int val) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putInt(val);
        byte[] result = byteBuffer.array();
        outputStream.write(result);
    }

    private void sendLong(long val) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(8);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putLong(val);
        byte[] result = byteBuffer.array();
        outputStream.write(result);
    }

    private void sendFloat(float val) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byte[] bytes = byteBuffer.putFloat(val).array();
        outputStream.write(bytes);
    }

    public synchronized void sendYuvData(MediaFormat outputFormat, ByteBuffer yuvDataBuf, int size, int width, int height) {
        try {
            final byte[] bytes = new byte[size];
            yuvDataBuf.get(bytes);

            if(outputStream != null) {
                sendInt(width);
                sendInt(height);
                sendLong(System.nanoTime());
                sendFloat((float)pluginManager.mainActivity.mModel.getCurrentYaw());
                sendInt(size);
                outputStream.write(bytes);
                outputStream.flush();
            } else {
                connect();
            }
        } catch (SocketException e) {
            connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int count = 0;
    @Override
    public void onYuvDataReceived(MediaFormat mediaFormat, ByteBuffer yuvFrame, int dataSize, int width, int height) {
        if (count++ % (30 / fps) == 0 && yuvFrame != null) {
            sendYuvData(mediaFormat, yuvFrame, dataSize, width, height);
        }
    }

    public void onVideoChange() {
        if(TEST || RDApplication.isTestMode) {
            testSender = new TestSender();
            testSender.streamer = this;
            testSender.start();
        } else {
            pluginManager.mainActivity.mCodecManager.enabledYuvData(true);
            pluginManager.mainActivity.mCodecManager.setYuvDataCallback(this);
        }
    }

    public void stop() {
        if(TEST || RDApplication.isTestMode) {
            testSender.stop = true;
        } else {
            pluginManager.mainActivity.mCodecManager.enabledYuvData(false);
        }

        if(socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    public boolean isEnabled() {
        return true;
    }
}
