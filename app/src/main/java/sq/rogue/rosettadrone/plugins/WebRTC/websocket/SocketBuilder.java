package sq.rogue.rosettadrone.plugins.WebRTC.websocket;

import androidx.annotation.NonNull;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Builder class to build websocket connection
 */
public class SocketBuilder {

    private Request.Builder request;

    private OkHttpClient.Builder httpClient = new OkHttpClient.Builder();

    private SocketBuilder(Request.Builder request) {
        this.request = request;
    }

    public static SocketBuilder with(@NonNull String url) {
        // Silently replace web socket URLs with HTTP URLs.
        if (!url.regionMatches(true, 0, "ws:", 0, 3) && !url.regionMatches(true, 0, "wss:", 0, 4))
            throw new IllegalArgumentException("web socket url must start with ws or wss, passed url is " + url);

        return new SocketBuilder(new Request.Builder().url(url));
    }

    public SocketBuilder setPingInterval(long interval, @NonNull TimeUnit unit){
        httpClient.pingInterval(interval, unit);
        return this;
    }

    public SocketBuilder addHeader(@NonNull String name, @NonNull String value) {
        request.addHeader(name, value);
        return this;
    }

    public Socket build() {
        return Socket.init(httpClient, request.build());
    }
}
