package sq.rogue.rosettadrone.plugins.WebRTC.websocket;

import okhttp3.Response;

public abstract class OnStateChangeListener {
    /**
     * Invoked when a web socket has been accepted by the remote peer and may begin transmitting
     * messages.
     */
    public void onOpen(Response response) {
    }

    /**
     * Invoked when both peers have indicated that no more messages will be transmitted and the
     * connection has been successfully released. No further calls to this listener will be made.
     */
    public void onClosed(int code, String reason) {
    }

    /**
     * Invoked when a web socket has been closed due to an error reading from or writing to the
     * network. Both outgoing and incoming messages may have been lost.
     */
    public void onFailure(Throwable t) {
    }

    /**
     * Invoked when a web socket has been closed due to an error and reconnection attempt is started.
     */
    public void onReconnect(int attemptsCount, long attemptDelay) {
    }

    /**
     * Invoked when new socket connection status changed.
     *
     * @param status new socket status
     */
    public void onChange(SocketState status) {
    }
}
