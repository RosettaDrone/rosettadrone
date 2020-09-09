/*
    gStreamer interface output, FFMPEG on the input side, is a service, from Native Helper....
    We send data to UDP port 56994, that is received by the gStreamer module...
*/

package sq.rogue.rosettadrone.video;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import android.util.Log;

import org.freedesktop.gstreamer.GStreamer;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class VideoService extends Service implements NativeHelper.NativeDataListener {

    public static final String ACTION_START = "VIDEO.START";
    public static final String ACTION_STOP = "VIDEO.STOP";
    public static final String ACTION_RESTART = "VIDEO.RESTART";
    public static final String ACTION_UPDATE = "VIDEO.UPDATE";
    public static final String ACTION_DRONE_CONNECTED = "VIDEO.DRONE_CONNECTED";
    public static final String ACTION_DRONE_DISCONNECTED = "VIDEO.DRONE_DISCONNECTED";
    public static final String ACTION_SET_MODEL = "VIDEO.SET_MODEL";
    public static final String ACTION_SEND_NAL = "VIDEO.SEND_NAL";
    private static final String TAG = "VideoService";  //.class.getSimpleName();

    private native String nativeGetGStreamerInfo();

    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("VideoEncoderJNI");
        nativeClassInit();
    }

    protected Thread thread;
    private boolean isRunning = false;
    private DatagramSocket mGstSocket;

    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
    private native void nativeInit(String ip, int port, int bitrate, int encodeSpeed);     // Initialize native code, build pipeline, etc
    private native void nativeFinalize(); // Destroy pipeline and shutdown native code
    private native void nativePlay();     // Set pipeline to PLAYING
    private native void nativePause();    // Set pipeline to PAUSED
    private native void nativeSetDestination(String ip, int port);
    private native void nativeSetBitrate(int bitrate);

    private long native_custom_data;      // Native code will use this to keep private data

    private String mip = "192.168.0.174";
    private int mvideoPort = 5600;
    private int mvideoBitrate = 3000;
    private int mencodeSpeed = 2;

    @Override
    public void onCreate() {
        Log.e(TAG, "oncreate Video ");

        try {
            GStreamer.init(getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            mGstSocket = new DatagramSocket();
        } catch (SocketException e) {
            Log.e(TAG, "Error creating Gstreamer datagram socket", e);
        }
        initVideoStreamDecoder();
        initPacketizer(mip,mvideoPort, mvideoBitrate,mencodeSpeed);

        Log.e(TAG,"Welcome to " + nativeGetGStreamerInfo() + " !");

        super.onCreate();
    }
    @Override
    public void onDestroy() {
        //setActionDroneDisconnected();
    }

    public class LocalBinder extends Binder {
        public VideoService getInstance() {
            return VideoService.this;
        }
    }

    public void setParameters(String ip, int videoPort, int videoBitrate, int encodeSpeed){
        Log.e(TAG, "setParameters");
        mip = ip;
        mvideoPort= videoPort;
        mvideoBitrate = videoBitrate;
        mencodeSpeed = encodeSpeed;
        initPacketizer(mip, mvideoPort, mvideoBitrate, mencodeSpeed);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private String createNotificationChannel() {
        String channelID = "video_service";
        String channelName = "RosettaDrone 2 Video Service";
        NotificationChannel chan = new NotificationChannel(channelID, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(chan);
        return channelID;
    }

    private void setActionDroneDisconnected() {
        nativeFinalize();
        stopForeground(true);
        isRunning = false;

        if (thread != null) {
            thread.interrupt();
            thread = null;
        }

        if (mGstSocket != null) {
            mGstSocket.close();
            Log.d(TAG, "socket close");
        }
    }

    private void initVideoStreamDecoder() {
        NativeHelper.getInstance().init();
        NativeHelper.getInstance().setDataListener(this);
    }

    private void initPacketizer(String ip, int videoPort, int videoBitrate, int encodeSpeed) {
        Log.e(TAG, "Video initPacketizer");
        nativeInit(ip, videoPort, videoBitrate, encodeSpeed);
    }

    public boolean isRunning() {
        return isRunning;
    }

    // Called from native code. This sets the content of the TextView from the UI thread.
    private void setMessage(final String message) {
    }

    // Called from native code. Native code calls this once it has created its pipeline and
    // the main loop is running, so it is ready to accept commands.
    private void onGStreamerInitialized() {
        Log.i(TAG, "Gst initialized. ");
     //   nativePlay();
    }

    @Override
    public void onDataRecv(byte[] data, int size, int frameNum, boolean isKeyFrame, int width, int height) {
    //    Log.i(TAG, "onDataRecv");
        if(size > 0) {
      //      Log.i(TAG, "With->"+width+" Height->"+height);
            try {
                // Raw H.264 stream...
                // Can be decoded with:  sudo gst-launch-1.0 -vvv udpsrc port=5601 ! queue ! h264parse ! decodebin ! videoconvert ! autovideosink
                // Tested OK....
                InetAddress address_remote = InetAddress.getByName(mip);
                DatagramPacket packet_remote = new DatagramPacket(data, size, address_remote, mvideoPort);
                mGstSocket.send(packet_remote);

                // Add RTP Header...
//                InetAddress address = InetAddress.getByName("127.0.0.1");
  //              DatagramPacket packet = new DatagramPacket(data, size, address, 56994);
    //            mGstSocket.send(packet);

                } catch (Exception e) {
                Log.e(TAG, "Error sending packet to Gstreamer", e);
            }
        }
    }
}
