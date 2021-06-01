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
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

public class VideoService extends Service implements NativeHelper.NativeDataListener {

    private static final String TAG = VideoService.class.getSimpleName();
    protected H264Packetizer mPacketizer;
    protected Thread thread;
    private boolean isRunning = false;

    // Binder given to clients
    private final IBinder binder = new LocalBinder();

    private String mip = "127.0.0.1";
    private int mvideoPort = 5600;
    private int mvideoBitrate = 3000;
    private int mencodeSpeed = 2;

    @Override
    public void onCreate() {
        Log.e(TAG, "oncreate Video ");

        if (mPacketizer != null && mPacketizer.getRtpSocket() != null)
            mPacketizer.getRtpSocket().close();

        mPacketizer = new H264Packetizer();
        initVideoStreamDecoder();
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        setActionDroneDisconnected();
    }

    public class LocalBinder extends Binder {
        public VideoService getInstance() {
            return VideoService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
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

    public void setParameters(String ip, int videoPort, int videoBitrate, int encodeSpeed) {
        Log.e(TAG, "setParameters");
        mip = ip;
        mvideoPort = videoPort;
        mvideoBitrate = videoBitrate;
        mencodeSpeed = encodeSpeed;
        initPacketizer(mip, mvideoPort, mvideoBitrate, mencodeSpeed);
    }

    public void setDualVideo(boolean dualVideo) {
        mPacketizer.socket.UseDualVideo(dualVideo);
    }

    private void setActionDroneDisconnected() {
        stopForeground(true);
        isRunning = false;

        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private void initVideoStreamDecoder() {
        NativeHelper.getInstance().init();
        //NativeHelper.getInstance().setDataListener(this);
    }

    private void initPacketizer(String ip, int videoPort, int videoBitrate, int encodeSpeed) {
        Log.i(TAG, "Gst initPacketizer. ");

        try {
            mPacketizer.getRtpSocket().setDestination(InetAddress.getByName(ip), videoPort, 5000);
        } catch (UnknownHostException e) {
            Log.e(TAG, "Error setting destination for RTP packetizer", e);
        }

        isRunning = true;
    }

    public boolean isRunning() {
        return isRunning;
    }

    // --------------------------------------------------------------------------------------------
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

    protected void sendNAL(byte[] buffer) {
        // Pack a single NAL for RTP and send
        if (mPacketizer != null) {
            mPacketizer.setInputStream(new ByteArrayInputStream(buffer));
            mPacketizer.run();
        }
    }

    //---------------------------------------------------------------------------------------
    @Override
    public void onDataRecv(byte[] data, int size, int frameNum, boolean isKeyFrame, int width, int height) {
        if (size > 0 && isRunning) {
            // Pack the raw H.264 stream...
            try {
                splitNALs(data);
            } catch (Exception e){
                Log.d("VideoService",Log.getStackTraceString(e));
            }
        }
    }
}
