package sq.rogue.rosettadrone.plugins.WebRTC;

import android.util.Log;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
// https://github.com/square/okhttp/blob/master/samples/guide/src/main/java/okhttp3/recipes/WebSocketEcho.java

// move to https://gist.github.com/AliYusuf95/557af8be5f360c95fdf029795291eddb

public class SocketConnection extends WebSocketListener {
    private static final String TAG = "SocketConnection";
    private static SocketConnection instance;
    private WebSocket webSocket;
//    private WebSocketListener listener;
    private OkHttpClient client;
    private String serverUrl;

    public SocketConnection(String serverUrl) {
        this.serverUrl = serverUrl;
        this.client = new OkHttpClient();
//        this.listener = new DefaultWebSocketListener(); // Initial default listener
        connect();
    }

    public static synchronized SocketConnection getInstance() {
        if (instance == null) {
            instance = new SocketConnection("ws://192.168.1.220:8090");
        }
        return instance;
    }

    private void connect() {
        Request request = new Request.Builder().url(serverUrl).build();
        webSocket = client.newWebSocket(request, this);
        client.dispatcher().executorService().shutdown();
    }

//    public void setWebSocketListener(WebSocketListener newListener) {
//        this.listener = newListener;
//        if (webSocket != null) {
//            webSocket.close(1000, "Reconnecting with new listener");
//        }
//        connect();
//    }

    public WebSocket getWebSocket() {
        return webSocket;
    }

//    private class DefaultWebSocketListener extends WebSocketListener {
    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        Log.d(TAG, "WebSocket connected to " + serverUrl);
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        Log.d(TAG, "Received message: " + text);
        // Default message handling
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        Log.d(TAG, "Received bytes: " + bytes.hex());
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
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.e(TAG, "WebSocket error: " + t.getMessage());
    }
//    }

    public void closeConnection() {
        if (webSocket != null) {
            webSocket.close(1000, "Closing connection");
            webSocket = null;
            instance = null;
            Log.d(TAG, "WebSocket connection closed");
        }
    }
}
