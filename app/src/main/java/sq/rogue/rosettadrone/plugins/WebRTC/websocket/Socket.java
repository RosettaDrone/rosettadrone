package sq.rogue.rosettadrone.plugins.WebRTC.websocket;

/*
 * For logging I use `com.orhanobut:logger` Logger
 */

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.ProtocolException;
import java.util.HashMap;
import java.util.Map;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.internal.ws.RealWebSocket;
import okio.ByteString;

/**
 * Websocket class based on OkHttp3 with {event->data} message format to make your life easier.
 *
 * @author Ali Yusuf
 */

public class Socket {

    private final static String TAG = Socket.class.getSimpleName();
    private final static String CLOSE_REASON = "End of session";
    private final static int MAX_COLLISION = 7;

    private static Socket mInstance = null;

    /**
     * Websocket state
     */
    private SocketState mState;
    /**
     * Websocket main request
     */
    private Request mRequest;
    /**
     * Websocket http client
     */
    private OkHttpClient.Builder mHttpClient;
    /**
     * Websocket connection
     */
    private RealWebSocket mRealWebSocket;
    /**
     * Reconnection post delayed handler
     */
    private Handler mHandler;
    /**
     * Stores number of reconnecting attempts
     */
    private int reconnectionAttempts;
    /**
     * Indicate if it's termination to stop reconnecting
     */
    private boolean isForceTermination;
    /**
     * Socket event bus
     */
    private PublishSubject<Object> eventBus = PublishSubject.create();
    /**
     * Map that's help to keep track with hole lifecycle,
     * used to cancel all lifecycle subscriptions.
     *
     * lifecycle -> [events] map
     */
    private Map<Object, CompositeDisposable> sSubscriptionsMap = new HashMap<>();
    /**
     * Map that's help to keep track with lifecycle subscriptions with corresponding
     * event and listener. Used to cancel particular subscription or reset it.
     *
     * lifecycle -> {event -> {listener -> subscription}} map
     */
    private Map<Object, Map<Class, Map<Object, Disposable>>> sListenerBinderMap = new HashMap<>();

    Socket() {}

    @NonNull
    public static Socket getInstance() {
        if(mInstance == null) {
            throw new AssertionError("Make sure to use SocketBuilder before using Socket#getInstance.");
        }
        return mInstance;
    }

    static Socket init(OkHttpClient.Builder httpClient, Request request) {
        mInstance = new Socket();
        mInstance.mHttpClient = httpClient;
        mInstance.mRequest = request;
        mInstance.mState = SocketState.CLOSED;
        mInstance.mHandler = new Handler(Looper.getMainLooper());
        mInstance.isForceTermination = false;
        return mInstance;
    }

    /**
     * Start socket connection if it's not already started
     */
    public void connect() {
        if (mInstance.mHttpClient == null || mInstance.mRequest == null) {
            throw new IllegalStateException("Make sure to use SocketBuilder before using Socket#connect.");
        }
        if (mRealWebSocket == null || mState == SocketState.CLOSED) {
            mRealWebSocket = (RealWebSocket) mHttpClient.build().newWebSocket(mRequest, webSocketListener);
            changeState(SocketState.OPENING);
        }
    }

    /**
     * Send message in {event->data} format
     *
     * @param event event name that you want sent message to
     * @param data message data object
     * @return true if the message send/on socket send quest; false otherwise
     */
    public boolean send(@NonNull String event, @NonNull Object data){
        return send(event, data.toString());
    }

    /**
     * Send message in {event->data} format
     *
     * @param event event name that you want sent message to
     * @param data message data in JSON format
     * @return true if the message send/on socket send quest; false otherwise
     */
    public boolean send(@NonNull String event, @NonNull String data){
        if (mRealWebSocket != null && mState == SocketState.OPEN) {
            try {
                JSONObject text = new JSONObject();
                text.put("event", event);
                text.put("data", new JSONObject(data));
                Log.d(TAG, "Try to send data: \n"+ text.toString());
                return mRealWebSocket.send(text.toString());
            } catch (JSONException e) {
                Log.e(TAG, "Try to send data with wrong JSON format");
            }
        }
        return false;
    }

    /**
     * Set global listener which fired every time message received with contained data.
     *
     * @param listener message on arrive listener
     */
    public Socket addOnEventListener(@NonNull String event, @NonNull OnEventListener listener){
        with(this).addOnEventListener(event, listener);
        return this;
    }

    /**
     * Set global listener which fired every time message received with contained data.
     *
     * @param listener message on arrive listener
     */
    public Socket addOnEventResponseListener(@NonNull String event, @NonNull OnEventResponseListener listener){
        with(this).setOnEventResponseListener(event, listener);
        return this;
    }

    /**
     * Set global state listener which fired every time {@link Socket#mState} changed.
     *
     * @param listener state change listener
     */
    public Socket addOnChangeStateListener(@NonNull OnStateChangeListener listener) {
        with(this).addOnChangeStateListener(listener);
        return this;
    }

    /**
     * Set global message listener which will be called in any message received even if it's not
     * in a {event -> data} format.
     *
     * @param listener message listener
     */
    public Socket addMessageListener(@NonNull OnMessageListener listener) {
        with(this).addOnMessageListener(listener);
        return this;
    }

    /**
     * Send normal close request to the host
     */
    public void close() {
        if (mRealWebSocket != null) {
            mRealWebSocket.close(1000, CLOSE_REASON);
        }
    }

    /**
     * Send close request to the host
     */
    public void close(int code, @NonNull String reason) {
        if (mRealWebSocket != null) {
            mRealWebSocket.close(code, reason);
        }
    }

    /**
     * Terminate the socket connection permanently
     */
    public void terminate() {
        isForceTermination = true; // skip onFailure callback
        if (mRealWebSocket != null) {
            mRealWebSocket.cancel(); // close connection
            mRealWebSocket = null; // clear socket object
        }
        changeState(SocketState.CLOSED);
        postEvent(new SocketEvents.CloseStatusEvent(1006, ""));
    }

    /**
     * Retrieve current socket connection state {@link SocketState}
     */
    public SocketState getState() {
        return mState;
    }

    /**
     * Change current state and call listener method with new state
     * {@link OnStateChangeListener#onChange(SocketState)}
     * @param newState new state
     */
    private void changeState(SocketState newState) {
        mState = newState;
        postEvent(new SocketEvents.ChangeStatusEvent(newState));
    }

    /**
     * Try to reconnect to the websocket after delay time using <i>Exponential backoff</i> method.
     * @see <a href="https://en.wikipedia.org/wiki/Exponential_backoff"></a>
     */
    private void reconnect() {
        if (mState != SocketState.CONNECT_ERROR) // connection not closed !!
            return;

        changeState(SocketState.RECONNECT_ATTEMPT);

        if (mRealWebSocket != null) {
            // Cancel websocket connection
            mRealWebSocket.cancel();
            // Clear websocket object
            mRealWebSocket = null;
        }

        // Calculate delay time
        int collision = reconnectionAttempts > MAX_COLLISION ? MAX_COLLISION : reconnectionAttempts;
        long delayTime = Math.round((Math.pow(2, collision)-1)/2) * 1000;


        postEvent(new SocketEvents.ReconnectStatusEvent(reconnectionAttempts + 1, delayTime));

        // Remove any pending posts of callbacks
        mHandler.removeCallbacksAndMessages(null);
        // Start new post delay
        mHandler.postDelayed(() -> {
            changeState(SocketState.RECONNECTING);
            reconnectionAttempts++; // Increment connections attempts
            connect(); // Establish new connection
        }, delayTime);
    }

    private WebSocketListener webSocketListener = new WebSocketListener() {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.d(TAG, "Socket has been opened successfully.");
            // reset connections attempts counter
            reconnectionAttempts = 0;

            // fire open event listener
            changeState(SocketState.OPEN);
            postEvent(new SocketEvents.OpenStatusEvent(response));
        }

        /**
         * Accept only Json data with format:
         * <b> {"event":"event name","data":{some data ...}} </b>
         */
        @Override
        public void onMessage(WebSocket webSocket, String text) {
            // print received message in log
            Log.d(TAG, "New Message received \n" + text);

            // call message listener
            postEvent(new SocketEvents.BaseMessageEvent(text));

            try {
                // Parse message text
                JSONObject response = new JSONObject(text);
                String event = response.getString("event");
                JSONObject data = response.getJSONObject("data");

                // call event listener with received data
                postEvent(new SocketEvents.ResponseMessageEvent(event, data.toString()));

                // call event listener
                postEvent(new SocketEvents.BaseMessageEvent(event));
            } catch (JSONException e) {
                // Message text not in JSON format or don't have {event}|{data} object
                Log.d(TAG,"Unknown message format.");
                Log.d(TAG,"JSONException:" + e.getMessage());
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            // TODO: some action
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "Close request from server with reason: " + reason);
            changeState(SocketState.CLOSING);
            webSocket.close(1000,reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "Close request from server with reason: " + reason);
            changeState(SocketState.CLOSED);
            postEvent(new SocketEvents.CloseStatusEvent(code, reason));
        }

        /**
         * This method call if:
         * - Fail to verify websocket GET request  => Throwable {@link ProtocolException}
         * - Can't establish websocket connection after upgrade GET request => response null, Throwable {@link Exception}
         * - First GET request had been failed => response null, Throwable {@link java.io.IOException}
         * - Fail to send Ping => response null, Throwable {@link java.io.IOException}
         * - Fail to send data frame => response null, Throwable {@link java.io.IOException}
         * - Fail to read data frame => response null, Throwable {@link java.io.IOException}
         */
        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            if (!isForceTermination) {
                isForceTermination = false; // reset flag
                Log.d(TAG, "Socket connection fail, try to reconnect. (" + reconnectionAttempts + ")");
                changeState(SocketState.CONNECT_ERROR);
                reconnect();
            }
            postEvent(new SocketEvents.FailureStatusEvent(t));
        }
    };

    /**
     * State subscription this lifecycle to socket events and listen for updates on that event.
     *
     * Note: Make sure to call {@link Socket#unsubscribe(Object)} to avoid memory leaks.
     */
    public SocketListenersBinder with(Object lifecycle){
        return new SocketListenersBinder(lifecycle, this);
    }

    /**
     * Post an event for all subscribers of that event.
     */
    private void postEvent(@NonNull Object event) {
        if (eventBus.hasObservers()){
            eventBus.onNext(event);
        }
    }

    /**
     * Unregisters this object from the listeners bus, removing all subscriptions.
     * This should be called when the object is going to go out of memory.
     */
    public void unsubscribe(Object lifecycle){
        CompositeDisposable compositeSubscription = sSubscriptionsMap.remove(lifecycle);
        if (compositeSubscription != null) {
            compositeSubscription.dispose();
            // clear lifecycle subscriptions of event
            sListenerBinderMap.remove(lifecycle);
        }
    }

    /**
     * Get the CompositeDisposable or create it if it's not already in memory.
     */
    @NonNull
    private CompositeDisposable getCompositeSubscription(@NonNull Object object) {
        CompositeDisposable compositeSubscription = sSubscriptionsMap.get(object);
        if (compositeSubscription == null) {
            compositeSubscription = new CompositeDisposable();
            sSubscriptionsMap.put(object, compositeSubscription);
        }
        return compositeSubscription;
    }

    /**
     * Get the event -> disposable map of the specific lifecycle.
     */
    private Map<Class, Map<Object, Disposable>> getListenerBinderMap(Object lifecycle) {
        Map<Class, Map<Object, Disposable>> disposableMap = sListenerBinderMap.get(lifecycle);
        if (disposableMap == null) {
            disposableMap = new HashMap<>();
            sListenerBinderMap.put(lifecycle, disposableMap);
        }
        return disposableMap;
    }

    /**
     * Add event subscription to the specified lifecycle and listen for updates on that event,
     * each listener subscription must be unique one each lifecycle, event.
     * Old subscription of same listener {@code listener} will be disposed if exist.
     */
    <T> void addEventSubscription(Object lifecycle, Class<T> eventClass, @NonNull Consumer<T> consumer, Object listener) {
        Map<Object, Disposable> disposableMap = getListenerBinderMap(lifecycle).get(eventClass);
        if (disposableMap == null) {
            disposableMap = new HashMap<>();
        } else {
            // remove old subscription if exist
            removeEventSubscriptionListener(lifecycle, eventClass, listener);
        }

        // add event subscription to the bus event
        Disposable disposable = eventBus.filter(o -> (o != null)) // Filter out null objects, better safe than sorry
                .filter(eventClass::isInstance)
                .cast(eventClass) // Cast it for easier usage
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(consumer);
        getCompositeSubscription(lifecycle).add(disposable);

        // update lifecycle subscriptions
        disposableMap.put(listener, disposable);
        getListenerBinderMap(lifecycle).put(eventClass, disposableMap);
    }

    /**
     * Remove all event subscriptions of the specified lifecycle.
     */
    <T> void removeEventSubscriptions(Object lifecycle, Class<T> eventClass) {
        // clear lifecycle subscriptions of event
        Map<Object, Disposable> disposableMap = sListenerBinderMap.get(lifecycle).remove(eventClass);
        if (disposableMap != null) {
            for (Disposable disposable : disposableMap.values()) {
                if (disposable != null) {
                    getCompositeSubscription(lifecycle).remove(disposable); // stop subscription
                }
            }
        }
    }

    /**
     * Remove event listener subscription of the specified lifecycle.
     */
    <T> void removeEventSubscriptionListener(Object lifecycle, Class<T> eventClass, Object listener) {
        // get subscription
        Map<Object, Disposable> disposableMap = sListenerBinderMap.get(lifecycle).get(eventClass);
        if (disposableMap != null) {
            Disposable disposable = disposableMap.remove(listener);
            if (disposable != null) {
                getCompositeSubscription(lifecycle).remove(disposable); // stop subscription
            }
        }
    }

}
