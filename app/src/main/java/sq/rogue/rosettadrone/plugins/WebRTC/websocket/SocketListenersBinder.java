package sq.rogue.rosettadrone.plugins.WebRTC.websocket;

import androidx.annotation.NonNull;

/**
 * Class to add listeners to specific activity/fragment
 */
public class SocketListenersBinder {

    private Object mLifecycle;
    private Socket mSocket;

    SocketListenersBinder(Object lifecycle, Socket socket) {
        mLifecycle = lifecycle;
        mSocket = socket;
    }

    /**
     * Set state listener which fired every time {@link Socket#mState} changed.
     *
     * @param listener state change listener
     */
    public SocketListenersBinder addOnChangeStateListener(@NonNull OnStateChangeListener listener) {
        // OpenStatusEvent
        mSocket.addEventSubscription(mLifecycle, SocketEvents.OpenStatusEvent.class,
                e -> listener.onOpen(e.response), listener);

        // CloseStatusEvent
        mSocket.addEventSubscription(mLifecycle, SocketEvents.CloseStatusEvent.class,
                e -> listener.onClosed(e.code, e.reason), listener);

        // FailureStatusEvent
        mSocket.addEventSubscription(mLifecycle, SocketEvents.FailureStatusEvent.class,
                e -> listener.onFailure(e.throwable), listener);

        // ReconnectStatusEvent
        mSocket.addEventSubscription(mLifecycle, SocketEvents.ReconnectStatusEvent.class,
                e -> listener.onReconnect(e.attemptsCount, e.attemptDelay), listener);

        // ChangeStatusEvent
        mSocket.addEventSubscription(mLifecycle, SocketEvents.ChangeStatusEvent.class,
                e -> listener.onChange(e.status), listener);
        return this;
    }

    /**
     * Message listener will be called in any message received even if it's not
     * in a {event -> data} format.
     *
     * @param listener message listener
     */
    public SocketListenersBinder addOnMessageListener(@NonNull OnMessageListener listener) {
        mSocket.addEventSubscription(mLifecycle, SocketEvents.MessageEvent.class, e -> listener.onMessage(e.message), listener);
        return this;
    }

    /**
     * Set listener which fired every time message received with contained data.
     *
     * @param listener message on arrive listener
     */
    public SocketListenersBinder addOnEventListener(@NonNull String event, @NonNull OnEventListener listener) {
        mSocket.addEventSubscription(mLifecycle, SocketEvents.BaseMessageEvent.class, e -> {
            if (!event.equals(e.name)) return; // skip if not same event name
            listener.onMessage(e.name);
        }, listener);
        return this;
    }

    /**
     * Set listener which fired every time message received with contained data.
     *
     * @param listener message on arrive listener
     */
    public SocketListenersBinder setOnEventResponseListener(@NonNull String event, @NonNull OnEventResponseListener listener) {
        mSocket.addEventSubscription(mLifecycle, SocketEvents.ResponseMessageEvent.class, e -> {
            if (!event.equals(e.name)) return; // skip if not same event name
            listener.onMessage(e.name, e.data);
        }, listener);
        return this;
    }

    /**
     * Remove listener from being receive new calls.
     *
     * @param listener message on arrive listener
     */
    public void removeListener(@NonNull OnStateChangeListener listener) {
        // remove listeners
        mSocket.removeEventSubscriptionListener(mLifecycle, SocketEvents.OpenStatusEvent.class, listener);
        mSocket.removeEventSubscriptionListener(mLifecycle, SocketEvents.CloseStatusEvent.class, listener);
        mSocket.removeEventSubscriptionListener(mLifecycle, SocketEvents.FailureStatusEvent.class, listener);
        mSocket.removeEventSubscriptionListener(mLifecycle, SocketEvents.ReconnectStatusEvent.class, listener);
        mSocket.removeEventSubscriptionListener(mLifecycle, SocketEvents.ChangeStatusEvent.class, listener);
    }

    /**
     * Remove listener from being receive new calls.
     *
     * @param listener listener to be deleted
     */
    public void removeListener(@NonNull OnMessageListener listener) {
        mSocket.removeEventSubscriptionListener(mLifecycle, SocketEvents.MessageEvent.class, listener);
    }

    /**
     * Remove listener from being receive new calls.
     *
     * @param listener listener to be deleted
     */
    public void removeListener(@NonNull OnEventListener listener) {
        mSocket.removeEventSubscriptionListener(mLifecycle, SocketEvents.BaseMessageEvent.class, listener);
    }

    /**
     * Remove listener from being receive new calls.
     *
     * @param listener listener to be deleted
     */
    public void removeListener(@NonNull OnEventResponseListener listener) {
        mSocket.removeEventSubscriptionListener(mLifecycle, SocketEvents.ResponseMessageEvent.class, listener);
    }
}