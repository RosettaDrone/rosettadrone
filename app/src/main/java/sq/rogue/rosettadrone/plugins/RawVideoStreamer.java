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

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

import dji.sdk.codec.DJICodecManager;
import sq.rogue.rosettadrone.Plugin;
import sq.rogue.rosettadrone.PluginManager;

public class RawVideoStreamer extends Plugin implements DJICodecManager.YuvDataCallback {
    PluginManager pluginManager;
    private final int fps = 30;
    Socket socket;
    OutputStream outputStream;

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
            e.printStackTrace();
        }
    }

    public static void test() {
        android.os.AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    RawVideoStreamer streamer = new RawVideoStreamer();
                    streamer.connect();

                    String msg = "This is just a simple test, but here goes the buffer data";
                    Charset charset = Charset.forName("UTF-8");
                    ByteBuffer buffer = charset.encode(msg);
                    streamer.sendYuvData(null, buffer, msg.length(), 10, 10);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void sendInt(int val) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putInt(val);
        byte[] result = byteBuffer.array();
        outputStream.write(result);
    }

    public void sendYuvData(MediaFormat outputFormat, ByteBuffer yuvDataBuf, int size, int width, int height) {
        try {
            final byte[] bytes = new byte[size];
            yuvDataBuf.get(bytes);

            if(outputStream != null) {
                sendInt(width);
                sendInt(height);
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
        pluginManager.mainActivity.mCodecManager.enabledYuvData(true);
        pluginManager.mainActivity.mCodecManager.setYuvDataCallback(this);
    }

    public boolean isEnabled() {
        return true;
    }
}
