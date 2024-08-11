package sq.rogue.rosettadrone.plugins;

//import android.os.Handler;
import android.util.Log;
import java.util.concurrent.TimeUnit;
//import java.io.IOException;
//import java.nio.ByteBuffer;

import dji.common.product.Model;

// import io.socket.client.Socket;

// import sq.rogue.rosettadrone.DroneModel;
//import okhttp3.WebSocket;
import sq.rogue.rosettadrone.Plugin;
import sq.rogue.rosettadrone.RDApplication;
import sq.rogue.rosettadrone.plugins.WebRTC.DJIStreamer;

//import sq.rogue.rosettadrone.plugins.WebRTC.SocketConnection;
import sq.rogue.rosettadrone.plugins.WebRTC.WebRTCMediaOptions;
import  sq.rogue.rosettadrone.plugins.WebRTC.websocket.Socket;
import  sq.rogue.rosettadrone.plugins.WebRTC.websocket.SocketBuilder;
import  sq.rogue.rosettadrone.plugins.WebRTC.websocket.OnStateChangeListener;
import  sq.rogue.rosettadrone.plugins.WebRTC.websocket.SocketState;


public class WebRTCStreaming extends Plugin {
    private static final String TAG = "WebRTCStreaming";
    private final String WEBSOCKET_URL = "ws://192.168.1.220:8090";
    private DJIStreamer djiStreamer;
    private Socket mSocket;
    private Model aircraftModel;
//    WebRTCStreaming.TestSender testSender;
    private static final boolean TEST = false; // Send a testing stream

    public void start() {
        pluginManager.mainActivity.useCustomDecoder = false; // Messes up the buffer received by onYuvDataReceived()
        pluginManager.mainActivity.useOutputSurface = false; // Avoid crash when clicking on minimap
        // Handler mainHandler = new Handler(pluginManager.mainActivity.getMainLooper());
//        Log.e(TAG, "Socket start");

        // init websocket
        mSocket = SocketBuilder.with(WEBSOCKET_URL)
                .setPingInterval(5, TimeUnit.SECONDS).build();

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

        if(TEST || RDApplication.isTestMode) {
            // TODO
//            testSender = new WebRTCStreaming.TestSender();
//            testSender.streamer = this;
//            testSender.start();
        }
        else {
            aircraftModel = pluginManager.mainActivity.mModel.m_model;
            if (aircraftModel == null) {
                String msg = "Couldn't get model. Reconnect or restart app.";
                Log.e(TAG, msg);
                pluginManager.mainActivity.logMessageDJI(msg);
                pluginManager.mainActivity.finish();
                return;
            }
            else {
                Log.d(TAG, " djiStreamer start");
                djiStreamer = new DJIStreamer(pluginManager.mainActivity, aircraftModel);
                Log.d(TAG, " djiStreamer started");
            }
        }

    }

    public void onVideoChange() {
//        pluginManager.mainActivity.mCodecManager.enabledYuvData(true);
//        pluginManager.mainActivity.mCodecManager.setYuvDataCallback(this);
        Log.d(TAG, "onVideoChange");
    }

    public void stop() {
        if(TEST || RDApplication.isTestMode) {
            // TODO
//            testSender.stop = true;
        } else {
            Log.d(TAG, "CALLstop");
//            pluginManager.mainActivity.mCodecManager.enabledYuvData(false);
        }

        if(djiStreamer != null) {
            mSocket.close();
        }
    }

    public boolean isEnabled() {
        return true;
    }


}
