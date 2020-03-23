package sq.rogue.rosettadrone;

// Acknowledgements:
// IP address validation: https://stackoverflow.com/questions/3698034/validating-ip-in-android/11545229#11545229
// Hide keyboard: https://stackoverflow.com/questions/16495440/how-to-hide-keyboard-by-default-and-show-only-when-click-on-edittext
// MenuItemTetColor: RPP @ https://stackoverflow.com/questions/31713628/change-menuitem-text-color-programmatically

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.LightingColorFilter;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbManager;
import android.media.MediaFormat;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;
import static com.google.android.material.snackbar.Snackbar.LENGTH_LONG;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.Parser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.product.Model;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.ux.widget.TakeOffWidget;
import sq.rogue.rosettadrone.logs.LogFragment;
import sq.rogue.rosettadrone.settings.SettingsActivity;
import sq.rogue.rosettadrone.settings.HelpActivity;
import sq.rogue.rosettadrone.video.H264Packetizer;

import static com.mapbox.mapboxsdk.Mapbox.getApplicationContext;
import static sq.rogue.rosettadrone.util.safeSleep;

import org.freedesktop.gstreamer.GStreamer;


public class MainActivity extends AppCompatActivity implements DJICodecManager.YuvDataCallback{

    public static final String FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change";
    private final static int RESULT_SETTINGS = 1001;
    private final static int RESULT_HELP = 1002;
    private final static int RESULT_GUI = 1003;

    public static boolean FLAG_PREFS_CHANGED = false;
    public static boolean FLAG_VIDEO_ADDRESS_CHANGED = false;
    public static boolean FLAG_TELEMETRY_ADDRESS_CHANGED = false;
    public static boolean FLAG_DRONE_ID_CHANGED = false;
    public static boolean FLAG_DRONE_RTL_ALTITUDE_CHANGED = false;
    public static boolean FLAG_DRONE_SMART_RTL_CHANGED = false;
    public static boolean FLAG_DRONE_LEDS_CHANGED = false;
    public static boolean FLAG_DRONE_MULTI_MODE_CHANGED = false;
    public static boolean FLAG_DRONE_COLLISION_AVOIDANCE_CHANGED = false;
    public static boolean FLAG_DRONE_UPWARD_AVOIDANCE_CHANGED = false;
    public static boolean FLAG_DRONE_LANDING_PROTECTION_CHANGED = false;
    public static boolean FLAG_DRONE_FLIGHT_PATH_MODE_CHANGED = false;
    public static boolean FLAG_DRONE_MAX_HEIGHT_CHANGED = false;

    private static BaseProduct mProduct;
    private final String TAG = "RosettaDrone";
    private final int GCS_TIMEOUT_mSEC    = 2000;
    private boolean permissionGranted     = false;
    private Handler mDJIHandler;
    private Handler mUIHandler;
    private Handler mTimerHandler;
    private CheckBox mSafety;
    private TextView mDroneDetails;
    private FragmentManager fragmentManager;
    private LogFragment logDJI;
    private LogFragment logOutbound;
    private LogFragment logInbound;
    private BottomNavigationView mBottomNavigation;
    private int navState                   = -1;
    private SharedPreferences prefs;
    private String mNewOutbound            = "";
    private String mNewInbound             = "";
    private String mNewDJI                 = "";
    private DatagramSocket socket;
    private DroneModel mModel;
    private MAVLinkReceiver mMavlinkReceiver;
    private Parser mMavlinkParser;
    private GCSCommunicatorAsyncTask mGCSCommunicator;
    private boolean connectivityHasChanged = false;
    private boolean shouldConnect          = false;
    protected TextureView mVideoSurface    = null;
    private boolean gui_enabled            = true;
    private Button mBtnSafety;
    private Button mBtnAI;
    private boolean stat                   = false; // Is it safe to takeoff....
    private int encodeSpeed                = 2;
    private boolean mGstEnabled            = true;
    private DatagramSocket mGstSocket;
    private InetAddress videoIPString;
    private int videoPort;
    private int videoBitrate;

    private VideoFeeder.VideoFeed standardVideoFeeder;
    protected VideoFeeder.VideoDataListener mReceivedVideoDataListener = null;
    private TextureView videostreamPreviewTtView;
    private Camera mCamera;
    private DJICodecManager mCodecManager;
    private int videoViewWidth;
    private int videoViewHeight;
    protected H264Packetizer mPacketizer;
    private SurfaceHolder.Callback surfaceCallback;
    protected SharedPreferences sharedPreferences;
/*
    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("VideoEncoderJNI");
        nativeClassInit();
    }

    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
    private native void nativeInit(String ip, int port, int bitrate, int encodeSpeed);     // Initialize native code, build pipeline, etc
    private native void nativeFinalize(); // Destroy pipeline and shutdown native code
    private native void nativePlay();     // Set pipeline to PLAYING
    private native void nativePause();    // Set pipeline to PAUSED
    private native void nativeSetDestination(String ip, int port);
    private native void nativeSetBitrate(int bitrate);


 */
    private Runnable djiUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
            sendBroadcast(intent);
        }
    };

    private Runnable RunnableUpdateUI = new Runnable() {
        // We have to update UI from here because we can't update the UI from the
        // drone/GCS handling threads

        @Override
        public void run() {

            if(gui_enabled == false) {
                try {
                    if (!mNewDJI.equals("")) {
                        //                    ((LogFragment) logPagerAdapter.getItem(0)).appendLogText(mNewDJI);
                        logDJI.appendLogText(mNewDJI);
                        mNewDJI = "";
                    }
                    if (!mNewOutbound.equals("")) {
                        //                    ((LogFragment) logPagerAdapter.getItem(1)).appendLogText(mNewOutbound);
                        logOutbound.appendLogText(mNewOutbound);

                        mNewOutbound = "";
                    }
                    if (!mNewInbound.equals("")) {
                        //                    ((LogFragment) logPagerAdapter.getItem(2)).appendLogText(mNewInbound);
                        logInbound.appendLogText(mNewInbound);
                        mNewInbound = "";
                    }

                } catch (Exception e) {
                    Log.d(TAG, "exception", e);
                }
            }
            mUIHandler.postDelayed(this, 500);
        }
    };
    private MenuItem item;


    private void initPacketizer() throws UnknownHostException {
        Log.e(TAG, "initPacketizer");

        String address = "127.0.0.1";

        sharedPreferences = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean("pref_external_gcs", false)) {
            if (!sharedPreferences.getBoolean("pref_separate_gcs", false)) {
                address = sharedPreferences.getString("pref_gcs_ip", "127.0.0.1");
            } else {
                address = sharedPreferences.getString("pref_video_ip", "127.0.0.1");
            }
        } else if (sharedPreferences.getBoolean("pref_separate_gcs", false)) {
            address = sharedPreferences.getString("pref_video_ip", "127.0.0.1");
        }


        //------------------------------------------------------------
        try {
            videoIPString = InetAddress.getByName(address);
        }catch (Exception e) {
            Log.e(TAG, "Ip Address error...", e);
        }
        videoPort = Integer.parseInt(sharedPreferences.getString("pref_video_port", "5600"));
        videoBitrate = Integer.parseInt(sharedPreferences.getString("pref_video_bitrate", "2000"));
        encodeSpeed = Integer.parseInt((sharedPreferences.getString("pref_encode_speed", "5")));

        //------------------------------------------------------------
        if (!isTranscodedVideoFeedNeeded()) {
            try {
   //             nativeInit(videoIPString, videoPort, videoBitrate, encodeSpeed);
                mGstSocket = new DatagramSocket();
            } catch (SocketException e) {
                Log.e(TAG, "Error creating Gstreamer datagram socket", e);
            }
        }
        else {
            try {
                if (mPacketizer != null && mPacketizer.getRtpSocket() != null)
                    mPacketizer.getRtpSocket().close();
                mPacketizer = new H264Packetizer();

                Log.e(TAG, "Receiver: " + videoIPString + ":" + videoPort);
                mPacketizer.getRtpSocket().setDestination(InetAddress.getByName(address), videoPort, 5000);
            } catch (UnknownHostException e) {
                Log.e(TAG, "Error setting destination for RTP packetizer", e);
            }
            // The one were we get transcode data...
            VideoFeeder.getInstance().setTranscodingDataRate(encodeSpeed);
            logMessageDJI("set rate to "+encodeSpeed);
        }

        //------------------------------------------------------------
        videostreamPreviewTtView = (TextureView) findViewById(R.id.livestream_preview_ttv);
        videostreamPreviewTtView.setVisibility(View.VISIBLE);
    }

    /**
     * onResume is called whenever RosettaDrone takes the foreground. This happens if we open the
     * RosettaDrone application or return from the preferences window. Need to rebind to the AIDL service.
     */
    @Override
    protected void onResume() {
        Log.e(TAG, "onResume()");
        super.onResume();

/*
        if (mDroneDetails != null) {
            String droneID = prefs.getString("pref_drone_id", "1");
            String rtlAlt = prefs.getString("pref_drone_rtl_altitude", "60") + "m";

            float dronebattery = mMavlinkReceiver.mModel.get_drone_battery_prosentage();
            float controlerbattery = mMavlinkReceiver.mModel.get_controller_battery_prosentage();

            String text = "Drone Battery:       " + "\t\t" + dronebattery + "%"  + "\t" + "ID: " + "\t\t" + droneID + System.getProperty("line.separator") +
                    "Controller Battery:  " + "\t" + controlerbattery + "%"  + "\t" + "RTL:" + "\t" + rtlAlt;

//            String text = "ID:" + "\t" + droneID + System.getProperty("line.separator") + "RTL:" + "\t" + rtlAlt;
            mDroneDetails.setText(text);
        }

 */

        initPreviewerTextureView();  // Decoded data to UDP...
        //notifyStatusChange();

        // If we use a camera... Remove Listeners if needed...

        if (mCamera != null) {
            if (!prefs.getBoolean("pref_enable_video", true)) {
                if (isTranscodedVideoFeedNeeded()) {
                    if (standardVideoFeeder != null) {
                        standardVideoFeeder.removeVideoDataListener(mReceivedVideoDataListener);
                    }
                } else {
                    if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                        VideoFeeder.getInstance().getPrimaryVideoFeed().removeVideoDataListener(mReceivedVideoDataListener);
                    }
                }
            } else {
                if (isTranscodedVideoFeedNeeded()) {
                    if (standardVideoFeeder != null) {
                        standardVideoFeeder.addVideoDataListener(mReceivedVideoDataListener);
                    }
                } else {
                    if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                        VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(mReceivedVideoDataListener);
                    }
                }
            }
        }
    }


    /**
     * Called whenever RosettaDrone leaves the foreground, such as when the home button is pressed or
     * we enter the preferences/settings screen. We unbind the AIDL service since we don't need it if we're not
     * currently within RosettaDrone.
     * NOTE: Pressing the back button from the RosettaDrone application also calls onDestroy.
     */
    @Override
    protected void onPause() {
        Log.e(TAG, "onPause()");
/*
        // Remove Listeners...
        if (mCamera != null) {
            if (isTranscodedVideoFeedNeeded()) {
                if (standardVideoFeeder != null) {
                    standardVideoFeeder.removeVideoDataListener(mReceivedVideoDataListener);
                }
            } else {
                if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                    VideoFeeder.getInstance().getPrimaryVideoFeed().removeVideoDataListener(mReceivedVideoDataListener);
               }
            }
        }

 */
        super.onPause();
        // We have to save text when onPause is called or it will be erased
//        mNewOutbound = logToGCS.getLogText() + mNewOutbound;
//        mNewInbound = logFromGCS.getLogText() + mNewInbound;
//        mNewDJI = logDJI.getLogText() + mNewDJI;
    }

    @Override
    protected void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
//        logMessageDJI("onDestroy()");
        sendDroneDisconnected();
        closeGCSCommunicator();

        if (mUIHandler != null) {
            mUIHandler.removeCallbacksAndMessages(null);
        }

        if (mDJIHandler != null) {
            mDJIHandler.removeCallbacksAndMessages(null);
        }

        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager.destroyCodec();
        }

        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        //---------------- Hide top bar ---
        getSupportActionBar().hide();
        //---------------- Force Landscape ether ways...
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        //---------------- Hide title (do not know..)
//        requestWindowFeature(Window.FEATURE_NO_TITLE); // for hiding title
        //---------------- Make absolutely full screen...
 //       getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
   //             WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //----------------

        setContentView(R.layout.activity_gui);

        mProduct = RDApplication.getProductInstance(); // Should be set by Connection ...
/*
        FrameLayout wv = findViewById(R.id.compass_container);
        wv.getLayoutParams().height = FrameLayout.LayoutParams.MATCH_PARENT; // LayoutParams: android.view.ViewGroup.LayoutParams
// wv.getLayoutParams().height = LayoutParams.WRAP_CONTENT;
        wv.requestLayout();//It is necesary to refresh the screen

 */


        String versionName = "";
        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        if (savedInstanceState != null) {
            navState = savedInstanceState.getInt("navigation_state");
        }

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        mModel = new DroneModel(this, null, RDApplication.getSim());
        mModel.setSystemId(Integer.parseInt(prefs.getString("pref_drone_id", "1")));

        mMavlinkReceiver = new MAVLinkReceiver(this, mModel);
        loadMockParamFile();

        mDJIHandler = new Handler(Looper.getMainLooper());
        mUIHandler = new Handler(Looper.getMainLooper());
        mUIHandler.postDelayed(RunnableUpdateUI, 1000);

        Intent aoaIntent = getIntent();
        if (aoaIntent != null) {
            String action = aoaIntent.getAction();
            if (action == (UsbManager.ACTION_USB_ACCESSORY_ATTACHED)) {
                Intent attachedIntent = new Intent();

                attachedIntent.setAction(DJISDKManager.USB_ACCESSORY_ATTACHED);
                sendBroadcast(attachedIntent);
            }
        }

        deleteApplicationDirectory();
        initLogs();
        try {
            initPacketizer();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        DJISDKManager.getInstance().registerApp(this, mDJISDKManagerCallback);

        //--------------------------------------------------------------
        // Make the safety switch....
        mBtnSafety = (Button) findViewById(R.id.btn_safety);
        mBtnSafety.setOnClickListener(new Button.OnClickListener(){

            @Override
            public void onClick(View v) {
                Drawable connectedDrawable;
                stat = !stat;
                if(stat) {
                    connectedDrawable = getResources().getDrawable(R.drawable.ic_lock_outline_secondary_24dp,null);
                    mBtnSafety.setBackground(connectedDrawable);
                    findViewById(R.id.Takeoff).setVisibility(View.INVISIBLE);
                }else {
                    connectedDrawable = getResources().getDrawable(R.drawable.ic_lock_open_black_24dp,null);
                    mBtnSafety.setBackground(connectedDrawable);
                    findViewById(R.id.Takeoff).setVisibility(View.VISIBLE);
                }
                mModel.setSafetyEnabled(stat);
                NotificationHandler.notifySnackbar(findViewById(R.id.snack),
                        (mModel.isSafetyEnabled()) ? R.string.safety_on : R.string.safety_off, LENGTH_LONG);
            }

        });


        //--------------------------------------------------------------
        // Make the AI button....
        mBtnAI = (Button) findViewById(R.id.btn_AI_start);
        mBtnAI.setOnClickListener(new Button.OnClickListener(){

            @Override
            public void onClick(View v) {
                SetMesasageBox("Hit A to go Left and B to go right...");
            }
        });
        //--------------------------------------------------------------
        // Disable takeoff by default... This however it not how DJI does it, so we must delay this action...
        mTimerHandler = new Handler(Looper.getMainLooper());
        mTimerHandler.postDelayed(enablesafety, 3000);
        //--------------------------------------------------------------
    }

    protected void SetMesasageBox(String msg) {
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);

        AlertDialog.Builder alertDialog2 = new AlertDialog.Builder(this);
        alertDialog2.setIcon(R.mipmap.track_right);
        alertDialog2.setTitle("AI Mavlink/Python function selector!");
        alertDialog2.setMessage(msg);
        alertDialog2.setNegativeButton("Function B",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mModel.send_AI_Function(2);
                        r.stop();
                        dialog.cancel();
                    }
                });
        alertDialog2.setNeutralButton ("Function A",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mModel.send_AI_Function(1);
                        r.stop();
                        dialog.cancel();
                    }
                });
        alertDialog2.setPositiveButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mModel.send_AI_Function(0);
                        r.stop();
                        dialog.cancel();
                    }
                });

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                r.play();
                alertDialog2.show();
            }
        });
    }


    // By default disable takeoff...
    private Runnable enablesafety = new Runnable() {
        @Override
        public void run() {
            stat = false;
            mBtnSafety.callOnClick();
        }
    };

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateTitleBar();
            logMessageDJI("Simulator state change...");
        }
    };

    private void updateTitleBar() {
        boolean ret = false;
        BaseProduct product = DJISimulatorApplication.getProductInstance();
        if (product != null) {
            if(product.isConnected()) {
                //The product is connected
                logMessageDJI(DJISimulatorApplication.getProductInstance().getModel() + " Connected");
                ret = true;
            } else {
                if(product instanceof Aircraft) {
                    Aircraft aircraft = (Aircraft)product;
                    if(aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        logMessageDJI("only RC Connected");
                        ret = true;
                    }
                }
            }
        }

        if(!ret) {
            // The product or the remote controller are not connected.
            logMessageDJI("Disconnected");
        }
    }


    @Override
    protected void onNewIntent(@NonNull Intent intent) {

        super.onNewIntent(intent);

        String action = intent.getAction();
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
            Intent attachedIntent = new Intent();
            attachedIntent.setAction(DJISDKManager.USB_ACCESSORY_ATTACHED);
            sendBroadcast(attachedIntent);
        }
    }

    private void notifyStatusChange()
    {
        mDJIHandler.removeCallbacks(djiUpdateRunnable);
        mDJIHandler.postDelayed(djiUpdateRunnable, 500);
        final BaseProduct product = RDApplication.getProductInstance();
        if (product != null && product.isConnected() && product.getModel() != null) {
            logMessageDJI(product.getModel().name() + " Connected " );
        } else {
            logMessageDJI("Disconnected");
        }

        // The callback for receiving the raw H264 video data for camera live view
        // For newer drones...
        mReceivedVideoDataListener = new VideoFeeder.VideoDataListener() {
            int desimate = 0;

            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                if (mGstEnabled) {
                    if(mCodecManager != null){
                        mCodecManager.sendDataToDecoder(videoBuffer, size);
                    }
                    try {
                        DatagramPacket packet = new DatagramPacket(videoBuffer, size, videoIPString, videoPort);
                        mGstSocket.send(packet);
                        if(++desimate >= 30) {
                            logMessageDJI( "Sending: " + size);
//                            Log.e(TAG, "Sending: " + size);
                            desimate = 0;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending packet to Gstreamer", e);
                    }
                } else {
                    // Wait 5 sec.
                    if(++desimate >= 30*3) {
                        splitNALs(videoBuffer);
                        desimate = 30*100;
                    }
                }
            }
        };

        if (null == product || !product.isConnected()) {
            mCamera = null;
        } else {
            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                mCamera = product.getCamera();
                mCamera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError != null) {
                            Log.e(TAG, "can't change mode of camera, error: "+djiError.getDescription());
                            logMessageDJI("can't change mode of camera, error: "+djiError.getDescription());
                        }
                    }
                });

                //When calibration is needed or the fetch key frame is required by SDK, should use the provideTranscodedVideoFeed
                //to receive the transcoded video feed from main camera.
                if (isTranscodedVideoFeedNeeded()) {
                    standardVideoFeeder = VideoFeeder.getInstance().provideTranscodedVideoFeed();
                    if (prefs.getBoolean("pref_enable_video", true)) {
                        standardVideoFeeder.addVideoDataListener(mReceivedVideoDataListener);
                        mGstEnabled = false;
                        logMessageDJI("Transcode Video !!!!!!!");
                    }
                }else{
                    if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                        if (prefs.getBoolean("pref_enable_video", true)) {
                            VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(mReceivedVideoDataListener);
                            mGstEnabled = true;
                            logMessageDJI("Do NOT Transcode Video !!!!!!!");
                        }
                    }
                }
            }
        }
    }

    /**
     * Init a fake texture view to for the codec manager, so that the video raw data can be received
     * by the camera needed to get video to the UDP handler...
     */
    private void initPreviewerTextureView() {
        Log.e(TAG, "initPreviewerTextureView");

        videostreamPreviewTtView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                videoViewWidth = width;
                videoViewHeight = height;
                Log.d(TAG, "real onSurfaceTextureAvailable: width " + videoViewWidth + " height " + videoViewHeight);
                if (mCodecManager == null) {
                    mCodecManager = new DJICodecManager(getApplicationContext(), surface, width, height);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                videoViewWidth = width;
                videoViewHeight = height;
                Log.d(TAG, "real onSurfaceTextureAvailable2: width " + videoViewWidth + " height " + videoViewHeight);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                Log.e(TAG, "onSurfaceTextureDestroyed");
                if (mCodecManager != null) {
                    mCodecManager.cleanSurface();
                }
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
    }
/*
    @Override
    public void onYuvDataReceived(MediaFormat format, final ByteBuffer yuvFrame, int dataSize, final int width, final int height) {
//    public void onYuvDataReceived(final ByteBuffer yuvFrame, int dataSize, final int width, final int height) {
        //In this demo, we test the YUV data by saving it into JPG files.
 //       Log.e(TAG, "onYuvDataReceived " + dataSize + "  " + format);
//        Log.e(TAG, "onYuvDataReceived " + dataSize );
        Log.e(TAG, "onYuvDataReceived");

    }
*/

    //---------------------------------------------------------------------------------------
    //---------------------------------------------------------------------------------------
    //---------------------------------------------------------------------------------------
    //---------------------------------------------------------------------------------------

    private DJISDKManager.SDKManagerCallback mDJISDKManagerCallback = new DJISDKManager.SDKManagerCallback() {

        @Override
        public void onDatabaseDownloadProgress(long x, long y){
        }

        @Override
        public void onInitProcess(DJISDKInitEvent a, int x){
        }

        @Override
        public void onProductDisconnect() {
            //notifyStatusChange();
        }

        @Override
        public void onProductConnect(BaseProduct newProduct) {
            mProduct = newProduct;

            if (mProduct == null) {
                logMessageDJI("No DJI drone detected");
                onDroneDisconnected();
            } else {
                if (mProduct instanceof Aircraft) {
                    logMessageDJI("DJI aircraft detected");
                    onDroneConnected();
                } else {
                    logMessageDJI("DJI non-aircraft product detected");
                    onDroneDisconnected();
                }
            }
            notifyStatusChange();
        }

        @Override
        public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent, BaseComponent newComponent) {
            if (newComponent != null) {
                newComponent.setComponentListener(new BaseComponent.ComponentListener() {
                    @Override
                    public void onConnectivityChange(boolean isConnected) {
                        notifyStatusChange();
                    }
                });
            }
        }

        @Override
        public void onRegister(DJIError error) {
            Log.d(TAG, error == null ? "success" : error.getDescription());
            if (error == DJISDKError.REGISTRATION_SUCCESS) {
                DJISDKManager.getInstance().startConnectionToProduct();
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        logMessageDJI("DJI SDK registered");
                    }
                });
            } else {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        logMessageDJI("DJI SDK registration failed");
                    }
                });
            }
            if (error != null) {
                Log.e(TAG, error.toString());
            }
        }

    };

    //---------------------------------------------------------------------------------------
    //---------------------------------------------------------------------------------------
    //endregion

    /**
     *
     */
    private void initLogs() {

        fragmentManager = getSupportFragmentManager();

        //Adapters in order: DJI, Outbound to GCS, Inbound to GCS

        logDJI = new LogFragment();
        logOutbound = new LogFragment();
        logInbound = new LogFragment();

        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        fragmentTransaction.add(R.id.fragment_container, logDJI);
        fragmentTransaction.add(R.id.fragment_container, logOutbound);
        fragmentTransaction.add(R.id.fragment_container, logInbound);

//        Log.d(TAG, "initLOGS navState : " + navState);
        switch (navState) {
            case R.id.action_gcs_up:
                fragmentTransaction.hide(logDJI);
                fragmentTransaction.hide(logInbound);
                break;
            case R.id.action_gcs_down:
                fragmentTransaction.hide(logDJI);
                fragmentTransaction.hide(logOutbound);
                break;
            default:
                fragmentTransaction.hide(logOutbound);
                fragmentTransaction.hide(logInbound);
                break;
        }
        fragmentTransaction.commit();
    }

    /**
     *
     */
    /*
    private void initBottomNav() {
        mBottomNavigation = findViewById(R.id.navigationView);
        mBottomNavigation.setOnNavigationItemSelectedListener(
                new BottomNavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                        switch (item.getItemId()) {
                            case R.id.action_dji:
                                fragmentTransaction.show(logDJI);
                                fragmentTransaction.hide(logOutbound);
                                fragmentTransaction.hide(logInbound);
                                break;
                            case R.id.action_gcs_up:
                                fragmentTransaction.hide(logDJI);
                                fragmentTransaction.show(logOutbound);
                                fragmentTransaction.hide(logInbound);
                                break;
                            case R.id.action_gcs_down:
                                fragmentTransaction.hide(logDJI);
                                fragmentTransaction.hide(logOutbound);
                                fragmentTransaction.show(logInbound);
                                break;
                        }
                        fragmentTransaction.commit();
                        return true;
                    }
                }
        );

        ViewGroup navigationMenuView = (ViewGroup) mBottomNavigation.getChildAt(0);
        ViewGroup gcsUpMenuItem = (ViewGroup) navigationMenuView.getChildAt(1);
        ViewGroup gcsDownMenuItem = (ViewGroup) navigationMenuView.getChildAt(2);


        gcsUpMenuItem.setLongClickable(true);
        gcsUpMenuItem.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                onLongClickGCSUp();
                return true;
            }
        });

        gcsDownMenuItem.setLongClickable(true);
        gcsDownMenuItem.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                onLongClickGCSDown();
                return true;
            }
        });


        if (navState != -1) {
            mBottomNavigation.setSelectedItemId(navState);
        }
    }
*/
    private void downloadLogs() {
        BufferedWriter bufferedWriter = null;

        try {
            final int BUF_LEN = 2048;
            byte[] buffer = new byte[BUF_LEN];

            String zipName = "RD_LOG_" + android.text.format.DateFormat.format("yyyy-MM-dd-hh:mm:ss", new java.util.Date());

            String[] fileNames = {"DJI_LOG", "OUTBOUND_LOG", "INBOUND_LOG"};

            File directory = new File(Environment.getExternalStorageDirectory().getPath()
                    + File.separator + "RosettaDrone");
            File dataFile = new File(directory, zipName);

            if (!directory.exists()) {
                directory.mkdir();
            }

            ArrayList<File> files = new ArrayList<>(fileNames.length);
            for (String fileName : fileNames) {
                files.add(new File(directory, fileName));
            }

            try {
                for (File file : files) {
                    bufferedWriter = new BufferedWriter(new FileWriter(file, false));
                    if (file.getName().equals("DJI_LOG")) {
                        bufferedWriter.write(logDJI.getLogText());
                    } else if (file.getName().equals("OUTBOUND_LOG")) {
                        bufferedWriter.write(logOutbound.getLogText());

                    } else {
                        bufferedWriter.write(logInbound.getLogText());
                    }
                    bufferedWriter.flush();
                }
            } finally {
                try {
                    if (bufferedWriter != null) {
                        bufferedWriter.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }


            BufferedInputStream origin;
            FileOutputStream dest = new FileOutputStream(dataFile);

            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));

            for (int i = 0; i < files.size(); i++) {
                Log.v("Compress", "Adding: " + files.get(i));
                FileInputStream fi = new FileInputStream(files.get(i));
                origin = new BufferedInputStream(fi, BUF_LEN);
                ZipEntry entry = null;
                if (i == 0) {
                    entry = new ZipEntry("DJI_LOG");
                } else if (i == 1) {
                    entry = new ZipEntry("OUTBOUND_LOG");
                } else {
                    entry = new ZipEntry("INBOUND_LOG");
                }
                out.putNextEntry(entry);
                int count;
                while ((count = origin.read(buffer, 0, BUF_LEN)) != -1) {
                    out.write(buffer, 0, count);
                }
                origin.close();
                files.get(i).delete();
            }
            out.finish();
            out.close();


        } catch (IOException e) {
            Log.e(TAG, "ERROR ZIPPING LOGS", e);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
//        navState = mBottomNavigation.getSelectedItemId();
//        outState.putInt("navigation_state", navState);
//        Log.d(TAG, "SAVED NAVSTATE: " + navState);

    }

    private void deleteApplicationDirectory() {
        Log.d("RosettaDrone", "deleteApplicationDirectory()");
        try {
            PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0);
            String s = p.applicationInfo.dataDir;
            Log.d(TAG, s);
            File dir = new File(s);
            if (dir.isDirectory()) {
                Log.d("RosettaDrone", "yes, is directory");
                String[] children = dir.list();
                for (int i = 0; i < children.length; i++) {
                    new File(dir, children[i]).delete();
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "exception", e);
        }
        //File dir = new File(Environment.getExternalStorageDirectory()+"DJI/sq.rogue.rosettadrone");

    }

    /**
     * Called when we start GCSAsyncTask which is used for comms. If we're opening a comms channel
     * we assume we also need to start the Video Service as well.
     *
     * @param reqCode Identifier for who sent the request
     * @param resCode Result sent by the child we are starting
     * @param data    Extra data we attached to the returned intent
     */
    @Override
    protected void onActivityResult(int reqCode, int resCode, Intent data) {
//        Log.d(TAG, "onActivityResult");
        super.onActivityResult(reqCode, resCode, data);
        if (reqCode == RESULT_SETTINGS && mGCSCommunicator != null && FLAG_PREFS_CHANGED) {

            if (FLAG_TELEMETRY_ADDRESS_CHANGED) {
                mGCSCommunicator.renewDatalinks();

                if (prefs.getBoolean("pref_enable_video", false)) {
                    if (!prefs.getBoolean("pref_separate_gcs", false)) {
                        sendRestartVideoService();
                    }
                }
//                FLAG_TELEMETRY_ADDRESS_CHANGED = false;
            }
            if (prefs.getBoolean("pref_enable_video", false)) {
                if (FLAG_VIDEO_ADDRESS_CHANGED) {
                    sendRestartVideoService();
                    FLAG_VIDEO_ADDRESS_CHANGED = false;
                }
            } else {
                sendDroneDisconnected();
                FLAG_VIDEO_ADDRESS_CHANGED = false;
            }
            setDroneParameters();

            FLAG_PREFS_CHANGED = false;
        }
    }

    public void showPopup(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.toolbar_menu, popup.getMenu());
        logMessageDJI("Config Click...");
        popup.show();
    }

    public boolean onMenuItemClick(MenuItem item) {
        this.item = item;
        switch (item.getItemId()) {
            case R.id.action_clear_logs:
                onClickClearLogs();
                break;
            case R.id.action_download_logs:
                onClickDownloadLogs();
                break;
            case R.id.action_settings:
                onClickSettings();
                break;
            case R.id.action_help:
                onClickHelp();
                break;
            case R.id.action_gui:
                onClickGUI();
                break;
            default:
                return false;
        }
        return true;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        logMessageDJI("Config ..."+item);
        switch (item.getItemId()) {
            case R.id.action_clear_logs:
                onClickClearLogs();
                break;
            case R.id.action_download_logs:
                onClickDownloadLogs();
                break;
            case R.id.action_settings:
                onClickSettings();
                break;
            case R.id.action_help:
                onClickHelp();
                break;
            case R.id.action_gui:
                onClickGUI();
                break;
            default:
                return super.onContextItemSelected(item);
        }
        return true;
    }


    private void onLongClickGCSUp() {
//        Log.d(TAG, "onLongClickGCSUp()");
        mModel.startWaypointMission();
    }

    private void onLongClickGCSDown() {
//        Log.d(TAG, "onLongClickGCSDown()");
        mModel.echoLoadedMission();
    }

    private void onClickSettings() {
//        Log.d(TAG, "onClickSettings()");
        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
        startActivityForResult(intent, RESULT_SETTINGS);
    }

    private void onClickHelp() {
//        Log.d(TAG, "onClickSettings()");
        Intent intent = new Intent(MainActivity.this, HelpActivity.class);
        startActivityForResult(intent, RESULT_HELP);
    }

    private void onClickGUI() {
        if(gui_enabled == false){
            gui_enabled = true;
            logDJI.clearLogText();
            logOutbound.clearLogText();
            logInbound.clearLogText();
        }
        else{
            gui_enabled = false;
        }
    }


    private void onClickClearLogs() {
        logDJI.clearLogText();
        logOutbound.clearLogText();
        logInbound.clearLogText();
    }

    private void onClickDownloadLogs() {
        downloadLogs();
    }

    /**
     *
     */
    private void onDroneConnected() {

        if (mProduct.getModel() == null) {
            logMessageDJI("Aircraft is not on!");
            return;
        }

        if (mProduct.getBattery() == null) {
            logMessageDJI("Reconnect your android device to the RC for full functionality.");
            return;
        }

        mGCSCommunicator = new GCSCommunicatorAsyncTask(this);
        mGCSCommunicator.execute();

        // Multiple tries and a timeout are necessary because of a bug that causes all the
        // components of mProduct to be null sometimes.
        int tries = 0;
        while (!mModel.setDjiAircraft((Aircraft) mProduct)) {
            safeSleep(1000);
            logMessageDJI("Connecting to drone...");
            tries++;
            if (tries == 5) {
                Toast.makeText(this, "Oops, DJI's SDK just glitched. Please restart the app.",
                        Toast.LENGTH_LONG).show();

                Intent intent = getIntent();
                finish();
                startActivity(intent);
                return;
            }
        }
        while (!mModel.loadParamsFromDJI()) {

        }

        sendDroneConnected();

        final Drawable connectedDrawable = getResources().getDrawable(R.drawable.ic_baseline_connected_24px,null);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ImageView djiImageView = findViewById(R.id.dji_conn);
                djiImageView.setBackground(connectedDrawable);
                djiImageView.invalidate();
            }
        });

    }

    private void onDroneDisconnected() {
        final Drawable disconnectedDrawable = getResources().getDrawable(R.drawable.ic_outline_disconnected_24px,null);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ImageView imageView = findViewById(R.id.dji_conn);
                imageView.setBackground(disconnectedDrawable);
                imageView.invalidate();
            }
        });


        logMessageDJI("Drone disconnected");
        mModel.setDjiAircraft(null);
        closeGCSCommunicator();

        sendDroneDisconnected();

    }

    private void closeGCSCommunicator() {
        if (mGCSCommunicator != null) {
            mGCSCommunicator.cancel(true);
            mGCSCommunicator = null;
        }
    }

    private void loadMockParamFile() {
        mModel.getParams().clear();
        try {

            AssetManager am = getAssets();
            InputStream is = am.open("DJIMock.txt");
            InputStreamReader inputStreamReader = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(inputStreamReader);

            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#"))
                    continue;
                String[] paramData = line.split("\t");
                String paramName = paramData[2];
                Float paramValue = Float.valueOf(paramData[3]);
                short paramType = Short.valueOf(paramData[4]);

                mModel.getParams().add(new MAVParam(paramName, paramValue, paramType));
            }
        } catch (IOException e) {
            Log.d(TAG, "exception", e);
        }
    }

    public void logMessageToGCS(String msg) {
        if (prefs.getBoolean("pref_log_mavlink", false))
            mNewOutbound += "\n" + msg;
    }

    //region Interface to VideoService
    //---------------------------------------------------------------------------------------

    public void logMessageFromGCS(String msg) {
        if (prefs.getBoolean("pref_log_mavlink", false))
            mNewInbound += "\n" + msg;
    }

    public void logMessageDJI(String msg) {
        mNewDJI += "\n" + msg;
    }

    /**
     *
     */
    private void sendRestartVideoService() {
        String videoIP = getVideoIP();
        int videoPort = Integer.parseInt(prefs.getString("pref_video_port", "5600"));
        logMessageDJI("Restarting Video link to " + videoIP + ":" + videoPort);
    }

    /**
     *
     */
    private void sendDroneConnected() {
        String videoIP = getVideoIP();

        int videoPort = Integer.parseInt(prefs.getString("pref_video_port", "5600"));

        // Going to reset all the flags so everything is set.
        // Assume its a new drone.

        FLAG_DRONE_RTL_ALTITUDE_CHANGED = true;
        FLAG_DRONE_SMART_RTL_CHANGED = true;
        FLAG_DRONE_MULTI_MODE_CHANGED = true;
        FLAG_DRONE_COLLISION_AVOIDANCE_CHANGED = true;
        FLAG_DRONE_UPWARD_AVOIDANCE_CHANGED = true;
        FLAG_DRONE_LANDING_PROTECTION_CHANGED = true;
        FLAG_DRONE_LEDS_CHANGED = true;
        FLAG_DRONE_ID_CHANGED = true;
        FLAG_DRONE_FLIGHT_PATH_MODE_CHANGED = true;
        FLAG_DRONE_MAX_HEIGHT_CHANGED = true;

        setDroneParameters();

        logMessageDJI("Starting Video link to " + videoIP + ":" + videoPort);

    }

    /**
     *
     */
    private void sendDroneDisconnected() {

    }

    private String getVideoIP() {
        String videoIP = "127.0.0.1";

        if (prefs.getBoolean("pref_external_gcs", false)) {
            if (!prefs.getBoolean("pref_separate_gcs", false)) {
                videoIP = prefs.getString("pref_gcs_ip", "127.0.0.1");
            } else {
                videoIP = prefs.getString("pref_video_ip", "127.0.0.1");
            }
        } else if (prefs.getBoolean("pref_separate_gcs", false)) {
            videoIP = prefs.getString("pref_video_ip", "127.0.0.1");
        }

        return videoIP;
    }

    private void setDroneParameters() {
        logMessageDJI("setDroneParameters");

        if (FLAG_DRONE_FLIGHT_PATH_MODE_CHANGED) {
            if (Integer.parseInt((prefs.getString("pref_drone_flight_path_mode", "2"))) == 0) {
                mMavlinkReceiver.curvedFlightPath = true;
            } else {
                mMavlinkReceiver.curvedFlightPath = false;
            }

            mMavlinkReceiver.flightPathRadius = Float.parseFloat(prefs.getString("pref_drone_flight_path_radius", ".2"));
        }

        if (FLAG_DRONE_ID_CHANGED) {
            mModel.setSystemId(Integer.parseInt(prefs.getString("pref_drone_id", "1")));
            FLAG_DRONE_ID_CHANGED = false;
        }

        if (FLAG_DRONE_RTL_ALTITUDE_CHANGED) {
            mModel.setRTLAltitude(Integer.parseInt(prefs.getString("pref_drone_rtl_altitude", "60")));
            FLAG_DRONE_RTL_ALTITUDE_CHANGED = false;
        }

        if (FLAG_DRONE_MAX_HEIGHT_CHANGED) {
            mModel.setMaxHeight(Integer.parseInt(prefs.getString("pref_drone_max_height", "500")));
            FLAG_DRONE_MAX_HEIGHT_CHANGED = false;
        }

        if (FLAG_DRONE_SMART_RTL_CHANGED) {
            mModel.setSmartRTLEnabled(prefs.getBoolean("pref_drone_smart_rtl", true));
            FLAG_DRONE_SMART_RTL_CHANGED = false;
        }

        if (FLAG_DRONE_MULTI_MODE_CHANGED) {
            mModel.setMultiModeEnabled(prefs.getBoolean("pref_drone_multi_mode", false));
            FLAG_DRONE_MULTI_MODE_CHANGED = false;
        }

        if (FLAG_DRONE_LEDS_CHANGED) {
            mModel.setForwardLEDsEnabled(prefs.getBoolean("pref_drone_leds", true));
            FLAG_DRONE_LEDS_CHANGED = false;
        }

        //Flight assistant modules
        if (FLAG_DRONE_COLLISION_AVOIDANCE_CHANGED) {
            mModel.setCollisionAvoidance(prefs.getBoolean("pref_drone_collision_avoidance", true));
            FLAG_DRONE_COLLISION_AVOIDANCE_CHANGED = false;
        }

        if (FLAG_DRONE_UPWARD_AVOIDANCE_CHANGED) {
            mModel.setUpwardCollisionAvoidance(prefs.getBoolean("pref_drone_upward_avoidance", true));
            FLAG_DRONE_UPWARD_AVOIDANCE_CHANGED = false;
        }

        if (FLAG_DRONE_LANDING_PROTECTION_CHANGED) {
            mModel.setLandingProtection(prefs.getBoolean("pref_drone_landing_protection", true));
            FLAG_DRONE_LANDING_PROTECTION_CHANGED = false;
        }

    }

    //---------------------------------------------------------------------------------------
    //endregion

    //region GCS Timer Task
    //---------------------------------------------------------------------------------------

    //---------------------------------------------------------------------------------------
    //endregion

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        Log.e(TAG, "onPointerCaptureChanged");
    }

    @Override
    public void onYuvDataReceived(MediaFormat mediaFormat, ByteBuffer byteBuffer, int i, int i1, int i2) {
        Log.e(TAG, "onYuvDataReceived");

    }

    //region GCS Timer Task
    //---------------------------------------------------------------------------------------

    private static class GCSSenderTimerTask extends TimerTask {

        private WeakReference<MainActivity> mainActivityWeakReference;

        GCSSenderTimerTask(WeakReference<MainActivity> mainActivityWeakReference) {
            this.mainActivityWeakReference = mainActivityWeakReference;

        }

        @Override
        public void run() {
            mainActivityWeakReference.get().mModel.tick();
        }
    }


    private static class GCSCommunicatorAsyncTask extends AsyncTask<Integer, Integer, Integer> {

        private static final String TAG = GCSSenderTimerTask.class.getSimpleName();
        public boolean request_renew_datalinks = false;
        private Timer timer;
        private WeakReference<MainActivity> mainActivityWeakReference;

        GCSCommunicatorAsyncTask(MainActivity mainActivity) {
            mainActivityWeakReference = new WeakReference<>(mainActivity);
        }

        public void renewDatalinks() {
//            Log.d(TAG, "renewDataLinks");
            request_renew_datalinks = true;
            FLAG_TELEMETRY_ADDRESS_CHANGED = false;
        }

        private void onRenewDatalinks() {
//            Log.d(TAG, "onRenewDataLinks");
            createTelemetrySocket();
//            mainActivityWeakReference.get().sendRestartVideoService();
        }

        @Override
        protected Integer doInBackground(Integer... ints2) {
//            Log.d("RDTHREADS", "doInBackground()");

            try {
                createTelemetrySocket();
                mainActivityWeakReference.get().mMavlinkParser = new Parser();

                GCSSenderTimerTask gcsSender = new GCSSenderTimerTask(mainActivityWeakReference);
                timer = new Timer(true);
                timer.scheduleAtFixedRate(gcsSender, 0, 100);

                while (!isCancelled()) {
                    // Listen for packets
                    try {
                        if (request_renew_datalinks) {
                            request_renew_datalinks = false;
                            onRenewDatalinks();
                        }

                        if (System.currentTimeMillis() - mainActivityWeakReference.get().mMavlinkReceiver.getTimestampLastGCSHeartbeat() <= mainActivityWeakReference.get().GCS_TIMEOUT_mSEC) {
                            if (!mainActivityWeakReference.get().shouldConnect) {
                                mainActivityWeakReference.get().shouldConnect = true;
                                mainActivityWeakReference.get().connectivityHasChanged = true;
                            }
                        } else {
                            if (mainActivityWeakReference.get().shouldConnect) {
                                mainActivityWeakReference.get().shouldConnect = false;
                                mainActivityWeakReference.get().connectivityHasChanged = true;
                            }
                        }

                        if (mainActivityWeakReference.get().connectivityHasChanged) {

                            if (mainActivityWeakReference.get().shouldConnect) {
                                final Drawable connectedDrawable = mainActivityWeakReference.get().getResources().getDrawable(R.drawable.ic_baseline_connected_24px,null);

                                mainActivityWeakReference.get().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ImageView imageView = mainActivityWeakReference.get().findViewById(R.id.gcs_conn);
                                        imageView.setBackground(connectedDrawable);
                                        imageView.invalidate();
                                    }
                                });
                            } else {
                                final Drawable disconnectedDrawable = mainActivityWeakReference.get().getResources().getDrawable(R.drawable.ic_outline_disconnected_24px,null);

                                mainActivityWeakReference.get().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ImageView imageView = mainActivityWeakReference.get().findViewById(R.id.gcs_conn);
                                        imageView.setBackground(disconnectedDrawable);
                                        imageView.invalidate();
                                    }
                                });
                            }

                            mainActivityWeakReference.get().connectivityHasChanged = false;
                        }

                        byte[] buf = new byte[1000];
                        DatagramPacket dp = new DatagramPacket(buf, buf.length);
                        mainActivityWeakReference.get().socket.receive(dp);

                        byte[] bytes = dp.getData();
                        int[] ints = new int[bytes.length];
                        for (int i = 0; i < bytes.length; i++)
                            ints[i] = bytes[i] & 0xff;

                        for (int i = 0; i < bytes.length; i++) {
                            MAVLinkPacket packet = mainActivityWeakReference.get().mMavlinkParser.mavlink_parse_char(ints[i]);

                            if (packet != null) {
                                MAVLinkMessage msg = packet.unpack();
                                if (mainActivityWeakReference.get().prefs.getBoolean("pref_log_mavlink", false))
                                    mainActivityWeakReference.get().logMessageFromGCS(msg.toString());
                                mainActivityWeakReference.get().mMavlinkReceiver.process(msg);
                            }
                        }
                    } catch (IOException e) {
                        //logMessageDJI("IOException: " + e.toString());
                    }
                }

            } catch (Exception e) {
                Log.d(TAG, "exception", e);
            } finally {
                if (mainActivityWeakReference.get().socket.isConnected()) {
                    mainActivityWeakReference.get().socket.disconnect();
                }
                if (timer != null) {
                    timer.cancel();
                }
//                Log.d("RDTHREADS", "doInBackground() complete");

            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            /*
            TODO Not sure what to do here...
             */
            if (mainActivityWeakReference.get() == null || mainActivityWeakReference.get().isFinishing())
                return;

            mainActivityWeakReference.clear();

        }

        @Override
        protected void onCancelled(Integer result) {
            super.onCancelled();

            close();

            final Drawable disconnectedDrawable = mainActivityWeakReference.get().getResources().getDrawable(R.drawable.ic_outline_disconnected_24px);

            mainActivityWeakReference.get().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ImageView imageView = mainActivityWeakReference.get().findViewById(R.id.gcs_conn);
                    imageView.setBackground(disconnectedDrawable);
                    imageView.invalidate();
                }
            });
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {

        }

        private void createTelemetrySocket() {
            close();

            String gcsIPString = "127.0.0.1";

            if (mainActivityWeakReference.get().prefs.getBoolean("pref_external_gcs", false))
                gcsIPString = mainActivityWeakReference.get().prefs.getString("pref_gcs_ip", "127.0.0.1");


            int telemIPPort = Integer.parseInt(mainActivityWeakReference.get().prefs.getString("pref_telem_port", "14550"));

            try {
                mainActivityWeakReference.get().socket = new DatagramSocket();
                mainActivityWeakReference.get().socket.connect(InetAddress.getByName(gcsIPString), telemIPPort);
                mainActivityWeakReference.get().socket.setSoTimeout(10);

                mainActivityWeakReference.get().logMessageDJI("Starting GCS telemetry link: " + gcsIPString + ":" + String.valueOf(telemIPPort));
            } catch (SocketException e) {
                Log.d(TAG, "createTelemetrySocket() - socket exception");
                Log.d(TAG, "exception", e);
                mainActivityWeakReference.get().logMessageDJI("Telemetry socket exception: " + gcsIPString + ":" + String.valueOf(telemIPPort));
            } // TODO
            catch (UnknownHostException e) {
                Log.d(TAG, "createTelemetrySocket() - unknown host exception");
                Log.d(TAG, "exception", e);
                mainActivityWeakReference.get().logMessageDJI("Unknown telemetry host: " + gcsIPString + ":" + String.valueOf(telemIPPort));
            } // TODO

//            Log.d(TAG, mainActivityWeakReference.get().socket.getInetAddress().toString());
//            Log.d(TAG, mainActivityWeakReference.get().socket.getLocalAddress().toString());
//            Log.d(TAG, String.valueOf(mainActivityWeakReference.get().socket.getPort()));
//            Log.d(TAG, String.valueOf(mainActivityWeakReference.get().socket.getLocalPort()));

            if (mainActivityWeakReference.get() != null) {
                mainActivityWeakReference.get().mModel.setSocket(mainActivityWeakReference.get().socket);
                if (mainActivityWeakReference.get().prefs.getBoolean("pref_secondary_telemetry_enabled", false)) {
                    String secondaryIP = mainActivityWeakReference.get().prefs.getString("pref_secondary_telemetry_ip", "127.0.0.1");
                    int secondaryPort = Integer.parseInt(mainActivityWeakReference.get().prefs.getString("pref_secondary_telemetry_port", "18990"));
                    try {
                        DatagramSocket secondarySocket = new DatagramSocket();
                        secondarySocket.connect(InetAddress.getByName(secondaryIP), secondaryPort);
                        mainActivityWeakReference.get().logMessageDJI("Starting secondary telemetry link: " + secondaryIP + ":" + String.valueOf(secondaryPort));

//                        mainActivityWeakReference.get().logMessageDJI(secondaryIP + ":" + secondaryPort);
                        mainActivityWeakReference.get().mModel.setSecondarySocket(secondarySocket);
                    } catch (SocketException | UnknownHostException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        protected void close() {
            if (mainActivityWeakReference.get().socket != null) {
                mainActivityWeakReference.get().socket.disconnect();
                mainActivityWeakReference.get().socket.close();
            }
            if (mainActivityWeakReference.get().mModel != null) {
                if (mainActivityWeakReference.get().mModel.secondarySocket != null) {
                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            mainActivityWeakReference.get().mModel.secondarySocket.disconnect();
                            mainActivityWeakReference.get().mModel.secondarySocket.close();
                        }
                    });
                    thread.start();

                }

            }
        }
    }

    //---------------------------------------------------------------------------------------
    // --------------------------------------------------------------------------------------------
    // To be moved later for better structure....

    private boolean isTranscodedVideoFeedNeeded() {
        if (VideoFeeder.getInstance() == null) {
            return false;
        }

        return VideoFeeder.getInstance().isFetchKeyFrameNeeded() || VideoFeeder.getInstance()
                .isLensDistortionCalibrationNeeded();
    }

    //---------------------------------------------------------------------------------------
    // --------------------------------------------------------------------------------------------
    public void splitNALs(byte[] buffer) {

        // One H264 frame can contain multiple NALs
        int packet_start_idx = 0;
        int packet_end_idx = 0;
        if (buffer.length < 4)
            return;

        for (int i = 3; i < buffer.length - 3; i++) {
            // This block handles all but the last NAL in the frame
            if ((buffer[i] & 0xff) == 0 && (buffer[i + 1] & 0xff) == 0 && (buffer[i + 2] & 0xff) == 0 && (buffer[i + 3] & 0xff) == 1) {
                packet_end_idx = i;
                byte[] packet = Arrays.copyOfRange(buffer, packet_start_idx, packet_end_idx);
                sendNAL(packet);
                packet_start_idx = i;
            }
        }
        // This block handles the last NAL in the frame, or the single NAL if only one exists
        packet_end_idx = buffer.length;
        byte[] packet = Arrays.copyOfRange(buffer, packet_start_idx, packet_end_idx);
        sendNAL(packet);
    }

    protected void sendNAL(byte[] buffer) {
        // Pack a single NAL for RTP and send
        if (mPacketizer != null) {
            mPacketizer.setInputStream(new ByteArrayInputStream(buffer));
            mPacketizer.run();
        }
    }

    //---------------------------------------------------------------------------------------
    //endregion

}

