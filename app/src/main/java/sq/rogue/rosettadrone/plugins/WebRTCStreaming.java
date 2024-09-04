package sq.rogue.rosettadrone.plugins;

import android.util.Log;
import java.util.concurrent.TimeUnit;

import sq.rogue.rosettadrone.Plugin;
import sq.rogue.rosettadrone.RDApplication;
import sq.rogue.rosettadrone.plugins.WebRTC.DJIStreamer;
import sq.rogue.rosettadrone.plugins.WebRTC.websocket.Socket;
import sq.rogue.rosettadrone.plugins.WebRTC.websocket.SocketBuilder;
import sq.rogue.rosettadrone.plugins.WebRTC.websocket.OnStateChangeListener;
import sq.rogue.rosettadrone.plugins.WebRTC.websocket.SocketState;

public class WebRTCStreaming extends Plugin {
    private static final String TAG = WebRTCStreaming.class.getSimpleName();
    private DJIStreamer djiStreamer;
    private Socket mSocket;
    private static final boolean TEST = false; // Send a testing stream
    private String stunServer;

    public void initWebSocket() {
        Log.d(TAG, "initWebSocket");
        stunServer = getPrefString("pref_webrtc_stun_server", "stun:stun.l.google.com:19302");
        // init websocket
        mSocket = SocketBuilder
                .with(getPrefString("pref_webrtc_signaling_server", "ws://192.168.1.220:8090"))
                .setPingInterval(5, TimeUnit.SECONDS)
                .build();

        // add ws states listeners
        mSocket.addOnChangeStateListener(new OnStateChangeListener() {
            // Socket connection events
            @Override
            public void onChange(SocketState status) {
                switch (status) {
                    case OPEN:
                        // new OnlineEvent();
                        break;
                    case CLOSING: case CLOSED: case RECONNECTING:
                    case RECONNECT_ATTEMPT: case CONNECT_ERROR:
                        // new OfflineEvent();
                        break;
                }
            }
            @Override
            public void onClosed(int code, String reason) {
                // socket should be always connected
                // Even it's closed, open the connection again
                mSocket.connect();
            }
        });
        mSocket.connect();
    }

    public void initStreaming() {
        if(TEST || RDApplication.isTestMode) {
            // TODO: fake video streaming?
        }
        else {
            if (pluginManager.mainActivity.mModel.m_model == null) {
                String msg = "Couldn't get model. Reconnect or restart app.";
                Log.e(TAG, msg);
                pluginManager.mainActivity.logMessageDJI(msg);
                pluginManager.mainActivity.finish();
            }
            else if (djiStreamer == null) {
                djiStreamer = new DJIStreamer(pluginManager.mainActivity,
                        pluginManager.mainActivity.mModel.m_model, stunServer);
            }
        }
    }

    public void start() {
        if (getPrefBoolean("pref_enable_webrtc", false)) {
            pluginManager.mainActivity.useCustomDecoder = false; // Messes up the buffer received by onYuvDataReceived()
            pluginManager.mainActivity.useOutputSurface = false; // Avoid crash when clicking on minimap
            initWebSocket();
        }
    }

    public void resume() {
        if (getPrefBoolean("pref_enable_webrtc", false)) {
            initStreaming();
        }
    }

    public void pause() {
        if (getPrefBoolean("pref_enable_webrtc", false) && djiStreamer != null) {
            djiStreamer.closeVideoStream();
        }
    }

    public void onVideoChange() {
        // TODO: stop/start connections?
        Log.d(TAG, "onVideoChange");
    }

    public void stop() {
        if(TEST || RDApplication.isTestMode) {
            // TODO: stop fake video stream;
        } else {
            pause();
        }

        if (getPrefBoolean("pref_enable_webrtc", false) && mSocket != null)
            mSocket.terminate();
    }

    public boolean isEnabled() {
        return true;
    }
}
