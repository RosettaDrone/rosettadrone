package sq.rogue.rosettadrone.plugins;

import android.util.Log;

//import java.io.IOException;
//import java.nio.ByteBuffer;

import dji.common.product.Model;

// import io.socket.client.Socket;

// import sq.rogue.rosettadrone.DroneModel;
import sq.rogue.rosettadrone.Plugin;
import sq.rogue.rosettadrone.RDApplication;
//import sq.rogue.rosettadrone.plugins.WebRTC.DJIStreamer;

import sq.rogue.rosettadrone.plugins.WebRTC.SocketConnection;

public class WebRTCStreaming extends Plugin {
    private static final String TAG = "WebRTCStreaming";
    //private DJIStreamer djiStreamer;
//    private SocketConnection socket;
    private Model aircraftModel;
//    WebRTCStreaming.TestSender testSender;
    private static final boolean TEST = false; // Send a testing stream

//    public static class TestSender extends Thread {
//        public Socket instance;
//        public boolean stop = false;
//
//        @Override
//        public void run() {
//            int offset = 0;
//            while (!stop) {
//                try {
//                    int w = 1280;
//                    int h = 720;
//
//                    int bufferSize = w * h * 3 / 2;
//                    byte[] bytes = new byte[bufferSize];
//
//                    for (int i = 0; i < bytes.length; i++) {
//                        bytes[i] = (byte) ((i + offset) % 256);
//                    }
//                    offset++;
//
//                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
////                    streamer.sendYuvData(null, buffer, bufferSize, w, h);
//                    instance.send()
//
//                    Thread.sleep(1000 / fps);
//
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//    }

    public void start() {
        pluginManager.mainActivity.useCustomDecoder = false; // Messes up the buffer received by onYuvDataReceived()
        pluginManager.mainActivity.useOutputSurface = false; // Avoid crash when clicking on minimap
        Log.e(TAG, "SocketConnection() call");
        SocketConnection socket = SocketConnection.getInstance();
        Log.e(TAG, "start() call");

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
//                djiStreamer = new DJIStreamer(pluginManager.mainActivity, aircraftModel);
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

//        if(djiStreamer != null) {
//            socket.getSocket().close();
//        }
    }

    public boolean isEnabled() {
        return true;
    }


}
