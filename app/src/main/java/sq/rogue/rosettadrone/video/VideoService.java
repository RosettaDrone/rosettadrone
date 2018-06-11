package sq.rogue.rosettadrone.video;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.util.Output;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import dji.common.product.Model;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.VideoFeeder;
import dji.thirdparty.sanselan.util.IOUtils;
import sq.rogue.rosettadrone.DroneModel;

import static android.support.v4.app.NotificationCompat.PRIORITY_MIN;

public class VideoService extends Service implements DJIVideoStreamDecoder.IYuvDataListener, DJIVideoStreamDecoder.IFrameDataListener {

    private static final String TAG = VideoService.class.getSimpleName();


    public static final String ACTION_START = "VIDEO.START";
    public static final String ACTION_STOP = "VIDEO.STOP";
    public static final String ACTION_RESTART = "VIDEO.RESTART";
    public static final String ACTION_UPDATE = "VIDEO.UPDATE";

    public static final String ACTION_DRONE_CONNECTED = "VIDEO.DRONE_CONNECTED";
    public static final String ACTION_DRONE_DISCONNECTED = "VIDEO.DRONE_DISCONNECTED";

    public static final String ACTION_SET_MODEL = "VIDEO.SET_MODEL";
    public static final String ACTION_SEND_NAL = "VIDEO.SEND_NAL";



    private boolean isRunning = false;

    protected H264Packetizer mPacketizer;
    protected VideoFeeder.VideoDataCallback mReceivedVideoDataCallBack = null;

    protected Model mModel;
    protected SharedPreferences sharedPreferences;

    protected Thread thread;

    @Override
    public void onYuvDataReceived(byte[] yuvFrame, int width, int height) {

    }

    @Override
    public void onFrameDataReceived(byte[] frame, int width, int height) {
        splitNALs(frame);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "oncreate");
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

    public void setActionDroneConnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            String chan_ID = createNotificationChannel();

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, chan_ID);
            Notification notif = builder.setOngoing(true)
                    .setPriority(PRIORITY_MIN)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build();

            startForeground(1, notif);
        } else {
            startForeground(1, new Notification());
        }
        initVideoStreamDecoder();
        initPacketizer();

        mReceivedVideoDataCallBack = new VideoFeeder.VideoDataCallback() {

            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                //Log.d(TAG, "camera recv video data size: " + size);
                //sendNAL(videoBuffer);
                DJIVideoStreamDecoder.getInstance().parse(videoBuffer, size);
            }
        };
        if (!mModel.equals(Model.UNKNOWN_AIRCRAFT)) {
            if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(mReceivedVideoDataCallBack);
            }
        }

        isRunning = true;
    }

    @TargetApi(Build.VERSION_CODES.O_MR1)
    private String createNotificationChannel() {
        String mChannel_ID = "my_channel";
        String mChannel_Name = "My Foreground Service";

        NotificationChannel chan = new NotificationChannel(mChannel_ID, mChannel_Name, NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager mgr = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

        mgr.createNotificationChannel(chan);
        return mChannel_ID;
    }

    private void setActionDroneDisconnected() {
        stopForeground(true);
        isRunning = false;
        if (mPacketizer != null) {
            mPacketizer.getRtpSocket().close();
            mPacketizer.stop();
        }


        DJIVideoStreamDecoder.getInstance().stop();

        thread.interrupt();
        thread = null;
//        mGCSCommunicator.cancel(true);
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
        //sendPacket(packet);
    }

    private void initVideoStreamDecoder() {
        NativeHelper.getInstance().init();
        DJIVideoStreamDecoder.getInstance().init(getApplicationContext(), null);
        DJIVideoStreamDecoder.getInstance().setYuvDataListener(this);
        DJIVideoStreamDecoder.getInstance().setFrameDataListener(this);
        DJIVideoStreamDecoder.getInstance().resume();

    }

    private void initPacketizer() {
        if (mPacketizer != null && mPacketizer.getRtpSocket() != null)
            mPacketizer.getRtpSocket().close();
        mPacketizer = new H264Packetizer();
        String videoIPString = "127.0.0.1";
        if (sharedPreferences.getBoolean("pref_external_gcs", false))
            if (!sharedPreferences.getBoolean("pref_combined_gcs", false)) {
                videoIPString = sharedPreferences.getString("pref_gcs_ip", "127.0.0.1");
            } else {
                videoIPString = sharedPreferences.getString("pref_video_ip", "127.0.0.1");
            }
        int videoPort = Integer.parseInt(sharedPreferences.getString("pref_video_port", "5600"));
        try {
            mPacketizer.getRtpSocket().setDestination(InetAddress.getByName(videoIPString), videoPort, 5000);
//            logMessageDJI("Starting GCS video link: " + videoIPString + ":" + String.valueOf(videoPort));

        } catch (UnknownHostException e) {
            Log.d(TAG, "exception", e);
//            logMessageDJI("Unknown video host: " + videoIPString + ":" + String.valueOf(videoPort));
        }
    }

    protected void sendNAL(byte[] buffer) {
        // Pack a single NAL for RTP and send
        if (mPacketizer != null) {
            mPacketizer.setInputStream(new ByteArrayInputStream(buffer));
            mPacketizer.run();
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
}
