package sq.rogue.rosettadrone.plugins.WebRTC;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import org.json.JSONObject;
import org.webrtc.VideoCapturer;

import java.util.Hashtable;

import dji.common.product.Model;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * The DJIStreamer class will manage all ongoing P2P connections
 * with clients, who desire videofeed.
 */
public class DJIStreamer {
    private static final String TAG = "DJIStreamer";

    private final Context context;
    private final Hashtable<String, WebRTCClient> ongoingConnections = new Hashtable<>();
    private final Model model;
    private WebSocket webSocket;

    public DJIStreamer(Context context, Model model){
        this.context = context;
        this.model = model;
        Log.d(TAG, "Pre SocketEvent");
        setupSocketEvent();
    }

    private WebRTCClient getClient(String socketID){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return ongoingConnections.getOrDefault(socketID, null);
        }
        return null;
    }

    private void removeClient(String socketID){
        // TODO: Any other cleanup necessary?.. Let the client stop the VideoCapturer though.
        ongoingConnections.remove(socketID);
    }

    private WebRTCClient addNewClient(String socketID){
        VideoCapturer videoCapturer = new DJIVideoCapturer(model);
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
        SocketConnection socketConnection = SocketConnection.getInstance();
//        webSocket = socketConnection.getWebSocket();

        // Setting up WebSocket Listener
        socketConnection.setWebSocketListener(new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                Log.d(TAG, "WebSocket connected");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Handler mainHandler = new Handler(context.getMainLooper());
                Runnable myRunnable = () -> {
                    try {
                        JSONObject messageJson = new JSONObject(text);
                        String peerSocketID = messageJson.getString("socketID");
                        Log.d(TAG, "Received WebRTCMessage: " + peerSocketID);

                        WebRTCClient client = getClient(peerSocketID);

                        if (client == null){
                            // A new client wants to establish a P2P
                            client = addNewClient(peerSocketID);
                        }

                        // Then just pass the message to the client
                        client.handleWebRTCMessage(messageJson.getJSONObject("message"));
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing WebRTC message", e);
                    }
                };
                mainHandler.post(myRunnable);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closing: " + reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                Log.e(TAG, "WebSocket error: " + t.getMessage());
            }
        });

        Log.d(TAG, "Socket instantiated");
    }
}

