package sq.rogue.rosettadrone.plugins.WebRTC.websocket;

public interface OnEventResponseListener {
    /**
     * Invoked when new message received from websocket with {event, data} structure
     *
     * @param event message event
     * @param data data string received
     */
    void onMessage(String event, String data);
}
