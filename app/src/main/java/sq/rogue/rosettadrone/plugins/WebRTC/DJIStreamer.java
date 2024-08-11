package sq.rogue.rosettadrone.plugins.WebRTC;

import android.content.Context;
//import android.os.Handler;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.VideoCapturer;

import java.util.Hashtable;

import dji.common.product.Model;
//import dji.sdk.sdkmanager.DJISDKManager;
//import static io.socket.client.Socket.EVENT_DISCONNECT;

//import com.example.SocketConnection;

import sq.rogue.rosettadrone.plugins.WebRTC.websocket.Socket;

/**
 * The DJIStreamer class will manage all ongoing P2P connections
 * with clients, who desire videofeed.
 */
public class DJIStreamer {
    private static final String TAG = "DJIStreamer";

//    private String droneDisplayName = "";
    private final Context context;
    private final Hashtable<String, WebRTCClient> ongoingConnections = new Hashtable<>();
//    private final SocketConnection socket;
    private final Model aircraftModel;

    public DJIStreamer(Context context, Model aircraftModel){
//        this.droneDisplayName = DJISDKManager.getInstance().getProduct().getModel().getDisplayName();
        this.aircraftModel = aircraftModel;
        this.context = context;
        setupSocketEvent();
    }

    private WebRTCClient getClient(String socketID){
        return ongoingConnections.getOrDefault(socketID, null);
    }

    private void removeClient(String socketID){
        // TODO: Any other cleanup necessary?.. Let the client stop the VideoCapturer though.
        ongoingConnections.remove(socketID);
    }

    private WebRTCClient addNewClient(String socketID){
        VideoCapturer videoCapturer = new DJIVideoCapturer(aircraftModel);
        WebRTCClient client = new WebRTCClient(socketID, context, videoCapturer, new WebRTCMediaOptions());
        client.setConnectionChangedListener(new WebRTCClient.PeerConnectionChangedListener() {
            @Override
            public void onDisconnected() {
                removeClient(client.peerSocketID);
                Log.d(TAG, "DJIStreamer has removed connection from table. Remaining active sessions: " + ongoingConnections.size());
            }
        });
        ongoingConnections.put(socketID, client);
        return client;
    }

    private void setupSocketEvent(){
        Socket.getInstance().with(context).setOnEventResponseListener("webrtc_msg", (event, data) -> {

//            Handler mainHandler = new Handler(context.getMainLooper());
//            Runnable myRunnable = new Runnable() {
//                @Override
//                public void run() {
                    try {
                        Log.d(TAG, "Received WebRTCMessage data: " + data);
                        JSONObject jsonData = new JSONObject(data);
                        String peerSocketID = jsonData.getString("socketID"); // The web-client sending a message
                        Log.d(TAG, "Received WebRTCMessage: " + peerSocketID);

                        WebRTCClient client = getClient(peerSocketID);

                        if (client == null){
                            // A new client wants to establish a P2P
                            client = addNewClient(peerSocketID);
                            Log.d(TAG, "New WebRTCClient created");
                        }

                        // Then just pass the message to the client
//                        JSONObject message = (JSONObject) args[1];
                        client.handleWebRTCMessage(jsonData);
                    } catch (JSONException e) {
                        Log.e(TAG, "ERROR: Receiving WebRTCMessage: " + e.getMessage());
                        throw new RuntimeException(e);
                    }
//                }
//            };
//            mainHandler.post(myRunnable);
        });
    }
}
