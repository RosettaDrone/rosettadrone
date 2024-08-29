package sq.rogue.rosettadrone.plugins.WebRTC;

import android.content.Context;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.VideoCapturer;
import java.util.Hashtable;
import dji.common.product.Model;
import sq.rogue.rosettadrone.plugins.WebRTC.websocket.Socket;

/**
 * The DJIStreamer class will manage all ongoing P2P connections
 * with clients, who desire videofeed.
 */
public class DJIStreamer {
    private static final String TAG = DJIStreamer.class.getSimpleName();

    private final Context context;
    private final Hashtable<String, WebRTCClient> ongoingConnections = new Hashtable<>();
    private final Model aircraftModel;

    public DJIStreamer(Context context, Model aircraftModel, String stunServer){
        this.aircraftModel = aircraftModel;
        this.context = context;
        setupSocketEvent(stunServer);
    }

    private WebRTCClient getClient(String socketID){
        return ongoingConnections.getOrDefault(socketID, null);
    }

    private void removeClient(String socketID){
        // TODO: Any other cleanup necessary?.. Let the client stop the VideoCapturer though.
        ongoingConnections.remove(socketID);
    }

    private WebRTCClient addNewClient(String socketID, String stunServer){
        VideoCapturer videoCapturer = new DJIVideoCapturer(aircraftModel);
        WebRTCClient client = new WebRTCClient(socketID, context, videoCapturer, new WebRTCMediaOptions(), stunServer);
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

    private void setupSocketEvent(String stunServer){
        Socket.getInstance().with(context).setOnEventResponseListener("webrtc_msg", (event, data) -> {
            try {
                JSONObject jsonData = new JSONObject(data);
                String peerSocketID = jsonData.getString("socketID"); // The web-client sending a message

                WebRTCClient client = getClient(peerSocketID);

                if (client == null){
                    // A new client wants to establish a P2P
                    client = addNewClient(peerSocketID, stunServer);
                }

                // Then just pass the message to the client
                client.handleWebRTCMessage(jsonData);
            } catch (JSONException e) {
                Log.e(TAG, "JSONException: " + e.getMessage());
            }
        });
    }

    public void closeVideoStream() {
        Log.i(TAG, "closeVideoStream()");
        ongoingConnections.keySet().forEach(socketID -> {
            WebRTCClient client = getClient(socketID);
            client.stopCapture();
            client.close();
            removeClient(socketID);
        });
    }
}
