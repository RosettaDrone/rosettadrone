package sq.rogue.rosettadrone.video;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.freedesktop.gstreamer.GStreamer;

import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

import dji.common.product.Model;
import dji.sdk.camera.VideoFeeder;
import sq.rogue.rosettadrone.R;

import static android.support.v4.app.NotificationCompat.PRIORITY_MIN;

public class VideoService extends Service implements NativeHelper.NativeDataListener {

    public static final String ACTION_START = "VIDEO.START";
    public static final String ACTION_STOP = "VIDEO.STOP";
    public static final String ACTION_RESTART = "VIDEO.RESTART";
    public static final String ACTION_UPDATE = "VIDEO.UPDATE";
    public static final String ACTION_DRONE_CONNECTED = "VIDEO.DRONE_CONNECTED";
    public static final String ACTION_DRONE_DISCONNECTED = "VIDEO.DRONE_DISCONNECTED";
    public static final String ACTION_SET_MODEL = "VIDEO.SET_MODEL";
    public static final String ACTION_SEND_NAL = "VIDEO.SEND_NAL";
    private static final String TAG = VideoService.class.getSimpleName();

    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("VideoEncoderJNI");
        nativeClassInit();
    }

    protected H264Packetizer mPacketizer;
    private Handler handler;
    protected VideoFeeder.VideoDataCallback mReceivedVideoDataCallBack = null;
    protected Model mModel;
    protected SharedPreferences sharedPreferences;
    protected Thread thread;
    private boolean isRunning = false;
    private VideoFeeder.VideoFeed mVideoFeed;
    private DatagramSocket mGstSocket;
    private boolean mGstEnabled = true;
    private long native_custom_data;      // Native code will use this to keep private data

    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks

    private native void nativeInit(String ip, int port, int bitrate, int encodeSpeed);     // Initialize native code, build pipeline, etc

    private native void nativeFinalize(); // Destroy pipeline and shutdown native code

    private native void nativePlay();     // Set pipeline to PLAYING

    private native void nativePause();    // Set pipeline to PAUSED

    private native void nativeSetDestination(String ip, int port);

    private native void nativeSetBitrate(int bitrate);

    @Override
    public void onCreate() {
        Log.d(TAG, "oncreate");
        try {
            GStreamer.init(getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startID) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (intent != null) {
            if (intent.getAction() != null) {
                Log.d(TAG, intent.getAction());
                switch (intent.getAction()) {
                    case ACTION_START:
                        break;
                    case ACTION_STOP:
                        break;
                    case ACTION_RESTART:
                        if (isRunning) {
                            setActionDroneDisconnected();
                            spinThread();
//                            if (mPacketizer != null) {
//                                if (mPacketizer.getRtpSocket() != null) {
//                                    mPacketizer.getRtpSocket().close();
//                                }
//                                mPacketizer.stop();
//                            }
                            initPacketizer();
                        } else {
                            spinThread();
                        }
                        break;
                    case ACTION_UPDATE:
                        break;
                    case ACTION_DRONE_CONNECTED:
                        mModel = (Model) intent.getSerializableExtra("model");
                        spinThread();
                        break;
                    case ACTION_DRONE_DISCONNECTED:
                        setActionDroneDisconnected();
                        break;
                    case ACTION_SET_MODEL:
                        break;
                    case ACTION_SEND_NAL:
                        break;
                    default:
                        break;
                }
            }
        }

        return START_STICKY;
    }

    public void spinThread() {
        thread = new Thread() {
            @Override
            public void run() {
                setActionDroneConnected();
            }
        };
        thread.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void validateTranscodingMethod() {
        switch (mModel) {
            case UNKNOWN_HANDHELD:
            case UNKNOWN_AIRCRAFT:
                return;
            case PHANTOM_3_STANDARD:
            case PHANTOM_3_ADVANCED:
            case PHANTOM_3_PROFESSIONAL:
            case Phantom_3_4K:
            case PHANTOM_4:
            case PHANTOM_4_ADVANCED:
            case PHANTOM_4_PRO:
            case INSPIRE_1:
            case INSPIRE_1_PRO:
            case INSPIRE_1_RAW:
            case INSPIRE_2:
            case Spark:
            case MATRICE_100:
            case MATRICE_600:
            case MATRICE_600_PRO:
                mGstEnabled = true;
                break;
            default:
                mGstEnabled = false;
                break;
        }
    }

    public void setActionDroneConnected() {
        validateTranscodingMethod();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelID = createNotificationChannel();
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelID);
            Notification notification = builder.setOngoing(true)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setPriority(PRIORITY_MIN)
                    .build();
            startForeground(1, notification);
        }
        initVideoStreamDecoder();
        initPacketizer();

        mReceivedVideoDataCallBack = new VideoFeeder.VideoDataCallback() {

            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                NativeHelper.getInstance().parse(videoBuffer, size);
            }
        };

        if (VideoFeeder.getInstance() != null) {
            mVideoFeed = VideoFeeder.getInstance().getPrimaryVideoFeed();
            mVideoFeed.setCallback(mReceivedVideoDataCallBack);
        }


        isRunning = true;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private String createNotificationChannel() {
        String channelID = "video_service";
        String channelName = "RosettaDrone Video Service";
        NotificationChannel chan = new NotificationChannel(channelID,
                channelName, NotificationManager.IMPORTANCE_DEFAULT);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(chan);
        return channelID;
    }

    private void setActionDroneDisconnected() {

//        Log.d(TAG, "DC");
        nativeFinalize();
//        Log.d(TAG, "native finalize");
        stopForeground(true);
//        Log.d(TAG, "stop foreground");

        isRunning = false;


        if (thread != null) {
            thread.interrupt();
            thread = null;
        }

        if (mGstSocket != null) {
            mGstSocket.close();
            Log.d(TAG, "socket close");

        }
        if (mPacketizer != null) {
            if (mPacketizer.getRtpSocket() != null) {
                mPacketizer.getRtpSocket().close();
            }
            mPacketizer.stop();
        }
    }

    private void initVideoStreamDecoder() {
        NativeHelper.getInstance().init();
        NativeHelper.getInstance().setDataListener(this);
    }

    private void initPacketizer() {
//        if (mPacketizer != null && mPacketizer.getRtpSocket() != null)
//            mPacketizer.getRtpSocket().close();
//        mPacketizer = new H264Packetizer();
        String videoIPString = "127.0.0.1";
        if (sharedPreferences.getBoolean("pref_external_gcs", false)) {
            if (!sharedPreferences.getBoolean("pref_separate_gcs", false)) {
                videoIPString = sharedPreferences.getString("pref_gcs_ip", "127.0.0.1");
            } else {
                videoIPString = sharedPreferences.getString("pref_video_ip", "127.0.0.1");
            }
        } else if (sharedPreferences.getBoolean("pref_separate_gcs", false)) {
            videoIPString = sharedPreferences.getString("pref_video_ip", "127.0.0.1");
        }

        int videoPort = Integer.parseInt(sharedPreferences.getString("pref_video_port", "5600"));
        int videoBitrate = Integer.parseInt(sharedPreferences.getString("pref_video_bitrate", "2000"));
        int encodeSpeed = Integer.parseInt((sharedPreferences.getString("pref_encode_speed", "2")));


        if (mGstEnabled) {
            try {
                nativeInit(videoIPString, videoPort, videoBitrate, encodeSpeed);
                mGstSocket = new DatagramSocket();
            } catch (SocketException e) {
                Log.e(TAG, "Error creating Gstreamer datagram socket", e);
            }

        } else {
            try {
                if (mPacketizer != null && mPacketizer.getRtpSocket() != null)
                    mPacketizer.getRtpSocket().close();
                mPacketizer = new H264Packetizer();

                mPacketizer.getRtpSocket().setDestination(InetAddress.getByName(videoIPString), videoPort, 5000);
            } catch (UnknownHostException e) {
                Log.e(TAG, "Error setting destination for RTP packetizer", e);
            }

        }

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
        Log.i("GStreamer", "Gst initialized. ");
        if (mGstEnabled) {
            nativePlay();
        }
    }


    @Override
    public void onDataRecv(byte[] data, int size, int frameNum, boolean isKeyFrame, int width, int height) {

        if (mGstEnabled) {
            try {
                InetAddress address = InetAddress.getByName("127.0.0.1");
                DatagramPacket packet = new DatagramPacket(data, data.length, address, 56994);
                mGstSocket.send(packet);
//                Log.d(TAG, "PACKET SENT");
            } catch (Exception e) {
                Log.e(TAG, "Error sending packet to Gstreamer", e);
            }
        } else {
            splitNALs(data);
        }

    }

    public void splitNALs(byte[] buffer) {
        // One H264 frame can contain multiple NALs
        int packet_start_idx = 0;
        int packet_end_idx = 0;
        if (buffer.length < 4)
            return;
        for (int i = 3; i < buffer.length - 3; i++) {
            // This block handles all but the last NAL in the frame
            if ((buffer[i] & 0xff) == 0 && (buffer[i + 1] & 0xff) == 0 && (buffer[i + 2] & 0xff) == 0 && (buffer[i + 3] & 0xff) == 1) {
                packet_end_idx = i;
                byte[] packet = Arrays.copyOfRange(buffer, packet_start_idx, packet_end_idx);
                sendNAL(packet);
                packet_start_idx = i;
            }

        }
        // This block handles the last NAL in the frame, or the single NAL if only one exists
        packet_end_idx = buffer.length;
        byte[] packet = Arrays.copyOfRange(buffer, packet_start_idx, packet_end_idx);
        sendNAL(packet);
    }
    //
    protected void sendNAL(byte[] buffer) {
        // Pack a single NAL for RTP and send
        if (mPacketizer != null) {
            mPacketizer.setInputStream(new ByteArrayInputStream(buffer));
            mPacketizer.run();
        }
    }
}
