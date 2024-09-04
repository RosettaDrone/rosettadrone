package sq.rogue.rosettadrone.plugins.WebRTC.websocket;

public interface OnMessageListener {
    /**
     * Invoked when new message received from websocket
     *
     * @param data Data string received
     */
    void onMessage(String data);
}
