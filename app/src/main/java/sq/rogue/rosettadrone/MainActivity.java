package sq.rogue.rosettadrone;

// Acknowledgements:
// IP address validation: https://stackoverflow.com/questions/3698034/validating-ip-in-android/11545229#11545229
// Hide keyboard: https://stackoverflow.com/questions/16495440/how-to-hide-keyboard-by-default-and-show-only-when-click-on-edittext
// MenuItemTetColor: RPP @ https://stackoverflow.com/questions/31713628/change-menuitem-text-color-programmatically

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.Parser;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
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
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import dji.common.camera.ResolutionAndFrameRate;
import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.model.LocationCoordinate2D;
import dji.common.product.Model;
import dji.midware.usb.P3.UsbAccessoryService;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

import sq.rogue.rosettadrone.logs.LogFragment;
import sq.rogue.rosettadrone.settings.SettingsActivity;
import sq.rogue.rosettadrone.settings.Waypoint1Activity;
import sq.rogue.rosettadrone.settings.Waypoint2Activity;
import sq.rogue.rosettadrone.video.DJIVideoStreamDecoder;
import sq.rogue.rosettadrone.video.NativeHelper;
import sq.rogue.rosettadrone.video.VideoService;

import static com.google.android.material.snackbar.Snackbar.LENGTH_LONG;
import static sq.rogue.rosettadrone.util.safeSleep;

//public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMapClickListener {
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    //    public static final String FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change";
    private final static int RESULT_SETTINGS = 1001;
    private final static int RESULT_HELP = 1002;
    private static int compare_height = 0;

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
    public static boolean FLAG_APP_NAME_CHANGED = false;
    public static boolean FLAG_MAPS_CHANGED = false;

    private GoogleMap  aMap;
    private double droneLocationLat, droneLocationLng;
    private Marker droneMarker = null;

    private static BaseProduct mProduct;
    private Model mProductModel;
    private final String TAG = MainActivity.class.getSimpleName();
    private final int GCS_TIMEOUT_mSEC = 2000;
    private Handler mDJIHandler;
    private Handler mUIHandler;
    private LogFragment logDJI;
    private LogFragment logOutbound;
    private LogFragment logInbound;
    private int navState = -1;
    private SharedPreferences prefs;
    private String mNewOutbound = "";
    private String mNewInbound = "";
    private String mNewDJI = "";
    private DatagramSocket socket;
    private DroneModel mModel;

    private MAVLinkReceiver mMavlinkReceiver;
    private Parser mMavlinkParser;
    private GCSCommunicatorAsyncTask mGCSCommunicator;
    private boolean connectivityHasChanged = false;
    private boolean shouldConnect = false;
    private boolean gui_enabled = true;
    private Button mBtnSafety;
    private boolean stat = false; // Is it safe to takeoff....

    private boolean mExternalVideoOut = true;
    private String mvideoIPString;
    private int videoPort;
    private int mVideoBitrate = 2;
    private int mEncodeSpeed = 2;
    private VideoService mService = null;
    private boolean mIsBound;
    private int m_videoMode = 1;
    private int mMaptype = GoogleMap.MAP_TYPE_HYBRID;

    private VideoFeeder.VideoFeed standardVideoFeeder;
    protected VideoFeeder.VideoDataListener mReceivedVideoDataListener;
    private SurfaceView videostreamPreviewTtView;
    private SurfaceView videostreamPreviewTtViewSmall;
    private CodecOutputSurface mCodecOutputSurface;
    private CodecOutputSurface mTranscodeOutputSurface;

    private Camera mCamera;
    private DJICodecManager mCodecManager;
    private int videoViewWidth;
    private int videoViewHeight;
    protected SharedPreferences sharedPreferences;
    private boolean mIsTranscodedVideoFeedNeeded = false;

    private Runnable djiUpdateRunnable = () -> {
        Intent intent = new Intent(DJISimulatorApplication.FLAG_CONNECTION_CHANGE);
//        Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
        sendBroadcast(intent);
    };

    private Runnable RunnableUpdateUI = new Runnable() {
        // We have to update UI from here because we can't update the UI from the
        // drone/GCS handling threads

        @Override
        public void run() {
            if (!gui_enabled) {
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


    @RequiresApi(api = Build.VERSION_CODES.M)
    private void initPacketizer() {
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
        mvideoIPString = address;
        Log.e(TAG, "Ip Address: " + mvideoIPString);
        //------------------------------------------------------------
        videoPort = Integer.parseInt(Objects.requireNonNull(sharedPreferences.getString("pref_video_port", "5600")));
        mVideoBitrate = Integer.parseInt(Objects.requireNonNull(sharedPreferences.getString("pref_video_bitrate", "2")));
        mEncodeSpeed = Integer.parseInt(Objects.requireNonNull(sharedPreferences.getString("pref_encode_speed", "2")));
        //------------------------------------------------------------
        mMaptype = Integer.parseInt(Objects.requireNonNull(prefs.getString("pref_maptype_mode", "2")));
        logMessageDJI("Mapmode: " + mMaptype);
        //------------------------------------------------------------

        Intent intent = new Intent(this, VideoService.class);
        this.startService(intent);
        safeSleep(500);
        doBindService();

        mIsTranscodedVideoFeedNeeded = isTranscodedVideoFeedNeeded();
        if (mIsTranscodedVideoFeedNeeded) {
            // The one were we get transcode data...
            mVideoBitrate = 10;
            VideoFeeder.getInstance().setTranscodingDataRate(mVideoBitrate);
            logMessageDJI("set rate to " + mVideoBitrate);
        }

        //------------------------------------------------------------
        videostreamPreviewTtView = findViewById(R.id.livestream_preview_ttv);
        videostreamPreviewTtViewSmall = findViewById(R.id.livestream_preview_ttv_small);

        videostreamPreviewTtView.getHolder().addCallback(mSurfaceCallback);
        videostreamPreviewTtView.setVisibility(View.VISIBLE);

        // Dehardcode
        mCodecOutputSurface = new CodecOutputSurface(1280, 720);
        mTranscodeOutputSurface = new CodecOutputSurface(1280, 720);

        // Create decoder with off-screen buf
        DJIVideoStreamDecoder.getInstance().init(getApplicationContext(), mCodecOutputSurface.getSurface(), mCodecOutputSurface);
        DJIVideoStreamDecoder.getInstance().resume();

        mCodecManager = new DJICodecManager(getApplicationContext(), mTranscodeOutputSurface.getSurfaceTexture(), 1280, 720, UsbAccessoryService.VideoStreamSource.Camera);

        /*mCodecManager.enabledYuvData(true);
        mCodecManager.setYuvDataCallback((mediaFormat, byteBuffer, i, i1, i2) -> {
            mTranscodeOutputSurface.awaitNewImage();
            mTranscodeOutputSurface.drawImage(true);
            return;
        });*/
    }

    // After the service have started...
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.e(TAG, "onServiceConnected  " + mvideoIPString);
            mService = ((VideoService.LocalBinder) iBinder).getInstance();
            mService.setParameters(mvideoIPString, videoPort, mVideoBitrate, mEncodeSpeed);

            if (prefs.getBoolean("pref_enable_dualvideo", true)) {
                mService.setDualVideo(true);
            } else {
                mService.setDualVideo(false);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.e(TAG, "onServiceDisconnected");
            mService = null;
        }
    };

    private void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        Log.e(TAG, "doBindService");
        bindService(new Intent(this, VideoService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    private void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    /**
     * onResume is called whenever RosettaDrone takes the foreground. This happens if we open the
     * RosettaDrone application or return from the preferences window. Need to rebind to the AIDL service.
     */
    @Override
    protected void onResume() {
        Log.e(TAG, "onResume()");
        super.onResume();

        setDroneParameters();

        if (prefs.getBoolean("pref_enable_video", true)) {
            mExternalVideoOut = true;
        } else {
            mExternalVideoOut = false;
        }

        // Wew should rather use multicast...
        if (mService != null) {
            if (prefs.getBoolean("pref_enable_dualvideo", true)) {
                mService.setDualVideo(true);
            } else {
                mService.setDualVideo(false);
            }
        }

        videostreamPreviewTtView.getHolder().addCallback(mSurfaceCallback);

        if (mCamera != null) {
            if (!mExternalVideoOut) {
                if (mIsTranscodedVideoFeedNeeded) {
                    if (standardVideoFeeder != null) {
                        standardVideoFeeder.removeVideoDataListener(mReceivedVideoDataListener);
                    }
                } else {
                    VideoFeeder.getInstance().getPrimaryVideoFeed().removeVideoDataListener(mReceivedVideoDataListener);
                }
            } else {
                if (mIsTranscodedVideoFeedNeeded) {
                    if (standardVideoFeeder != null) {
                        for (VideoFeeder.VideoDataListener listener : standardVideoFeeder.getListeners()) {
                            standardVideoFeeder.removeVideoDataListener(listener);
                        }
                        standardVideoFeeder.addVideoDataListener(mReceivedVideoDataListener);
                    }
                } else {
                    final VideoFeeder.VideoFeed videoFeed = VideoFeeder.getInstance().getPrimaryVideoFeed();
                    for (VideoFeeder.VideoDataListener listener : videoFeed.getListeners()) {
                        videoFeed.removeVideoDataListener(listener);
                    }
                    videoFeed.addVideoDataListener(mReceivedVideoDataListener);
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
            if (mIsTranscodedVideoFeedNeeded) {
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

        sendDroneDisconnected();
        closeGCSCommunicator();

        if (mUIHandler != null) {
            mUIHandler.removeCallbacksAndMessages(null);
        }

        if (mDJIHandler != null) {
            mDJIHandler.removeCallbacksAndMessages(null);
        }

        /*if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager.destroyCodec();
        }*/
        doUnbindService();

        //Intent intent = getIntent();
        finish();
        super.onDestroy();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        Log.d(TAG, "onMapReady()");

        if (aMap == null) {
            aMap = googleMap;
            LinearLayout map_layout = findViewById(R.id.map_view);
            map_layout.setClickable(true);
            map_layout.setOnClickListener((map) -> {
                ViewGroup.LayoutParams map_para = map_layout.getLayoutParams();
                map_layout.setZ(0.f);
                map_para.height = LayoutParams.WRAP_CONTENT;
                map_para.width = LayoutParams.WRAP_CONTENT;
                map_layout.setLayoutParams(map_para);
            });
        }
        aMap.getUiSettings().setZoomControlsEnabled(false);
        aMap.setMapType(mMaptype);

        updateDroneLocation();
    }

    // Update the drone location based on states from MCU.
    private void updateDroneLocation() {

        if (aMap != null) {

            // We initialize the default map location to the same as the default SIM location...
            LatLng pos;

            if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                pos = new LatLng(droneLocationLat, droneLocationLng);
            } else {
                LocationCoordinate2D loc = mModel.getSimPos2D();
                if(checkGpsCoordination(loc.getLongitude(),loc.getLongitude())) {
                    pos = new LatLng(loc.getLatitude(), loc.getLongitude());
                }
                else{
                    pos = new LatLng(62,12);
                }
            }

            //Create MarkerOptions object
            final MarkerOptions markerOptions = new MarkerOptions();
            markerOptions.position(pos);
            markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));

            runOnUiThread(() -> {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordination(pos.latitude, pos.longitude)) {
                    droneMarker = aMap.addMarker(markerOptions);
                    aMap.moveCamera(CameraUpdateFactory.newLatLng(pos));
                }
            });
        }
    }

    private void initFlightController() {
//        setResultToToast(droneLocationLat+"----"+droneLocationLng);

        if (mModel.mFlightController != null) {
            mModel.mFlightController.setStateCallback(
                    djiFlightControllerCurrentState -> {
                        droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                        droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                        updateDroneLocation();
                    });
        }
    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        //---------------- Hide top bar ---
        Objects.requireNonNull(getSupportActionBar()).hide();
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

        if(mProduct != null){
            try {
                mProductModel = mProduct.getModel();
            }catch(Exception e){
                Log.d(TAG, "exception", e);
                mProductModel = Model.MAVIC_PRO; // Just a dummy value should we be in test mode... (No Target)
            }
        }
/*
        FrameLayout wv = findViewById(R.id.compass_container);
        wv.getLayoutParams().height = FrameLayout.LayoutParams.MATCH_PARENT; // LayoutParams: android.view.ViewGroup.LayoutParams
// wv.getLayoutParams().height = LayoutParams.WRAP_CONTENT;
        wv.requestLayout();//It is necesary to refresh the screen

 */

        if (savedInstanceState != null) {
            navState = savedInstanceState.getInt("navigation_state");
        }

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        mModel = new DroneModel(this, null, RDApplication.getSim());
        mModel.setSystemId(Integer.parseInt(Objects.requireNonNull(prefs.getString("pref_drone_id", "1"))));

        mMavlinkReceiver = new MAVLinkReceiver(this, mModel);
        loadMockParamFile();

        mDJIHandler = new Handler(Looper.getMainLooper());
        mUIHandler = new Handler(Looper.getMainLooper());
        mUIHandler.postDelayed(RunnableUpdateUI, 1000);

        Intent aoaIntent = getIntent();
        if (aoaIntent != null) {
            String action = aoaIntent.getAction();
            // assert action != null;
            if (action == (UsbManager.ACTION_USB_ACCESSORY_ATTACHED)) {
                Intent attachedIntent = new Intent();

                attachedIntent.setAction(DJISDKManager.USB_ACCESSORY_ATTACHED);
                sendBroadcast(attachedIntent);
            }
        }
        deleteApplicationDirectory();
        initLogs();
        initPacketizer();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        initFlightController();

        DJISDKManager.getInstance().registerApp(this, mDJISDKManagerCallback);

        //--------------------------------------------------------------
        // Make the safety switch....
        mBtnSafety = findViewById(R.id.btn_safety);
        mBtnSafety.setOnClickListener(v -> {
            Drawable connectedDrawable;
            stat = !stat;
            if (stat) {
                connectedDrawable = getResources().getDrawable(R.drawable.ic_lock_outline_secondary_24dp, null);
                mBtnSafety.setBackground(connectedDrawable);
                findViewById(R.id.Takeoff).setVisibility(View.INVISIBLE);
            } else {
                connectedDrawable = getResources().getDrawable(R.drawable.ic_lock_open_black_24dp, null);
                mBtnSafety.setBackground(connectedDrawable);
                findViewById(R.id.Takeoff).setVisibility(View.VISIBLE);
            }
            mModel.setSafetyEnabled(stat);
            NotificationHandler.notifySnackbar(findViewById(R.id.snack),
                    (mModel.isSafetyEnabled()) ? R.string.safety_on : R.string.safety_off, LENGTH_LONG);
        });


        //--------------------------------------------------------------
        // Make the AI button....
        Button mBtnAI = findViewById(R.id.btn_AI_start);
        mBtnAI.setOnClickListener(v -> SetMesasageBox("Hit A to go Left and B to go right..."));
        //--------------------------------------------------------------
        // Disable takeoff by default... This however it not how DJI does it, so we must delay this action...
        Handler mTimerHandler = new Handler(Looper.getMainLooper());
        mTimerHandler.postDelayed(enablesafety, 3000);
        //--------------------------------------------------------------
    }

    private Thread customWaypointTask = new Thread() {
        @Override
        public void run() {
            String waypointCsv = "";
            BufferedReader br = null;

            try {
                br = new BufferedReader(new FileReader("/sdcard/waypoints.csv"));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            }

            try {
                StringBuilder sb = new StringBuilder();
                String line = br.readLine();

                while (line != null) {
                    sb.append(line);
                    sb.append(System.lineSeparator());
                    line = br.readLine();
                }
                waypointCsv = sb.toString();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } finally {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }

            String[] waypointRows = waypointCsv.split("\n", 0);

            for(int i = 0; i < waypointRows.length; i++)
            {
                String row = waypointRows[i].trim();

                Log.e(TAG, "Waypoints: Row: " + row);

                if(row.startsWith(";"))
                    continue;
                
                // TODO: check isNumeric / if a header is present
                String[] columns = row.split(",", 0);
                //;latitude,longitude,altitude(m),heading(deg),curvesize(m),rotationdir,gimbalmode,gimbalpitchangle,actiontype1,actionparam1,actiontype2,actionparam2,actiontype3,actionparam3,actiontype4,actionparam4,actiontype5,actionparam5,actiontype6,actionparam6,actiontype7,actionparam7,actiontype8,actionparam8,actiontype9,actionparam9,actiontype10,actionparam10,actiontype11,actionparam11,actiontype12,actionparam12,actiontype13,actionparam13,actiontype14,actionparam14,actiontype15,actionparam15,altitudemode,speed(m/s),poi_latitude,poi_longitude,poi_altitude(m),poi_altitudemode,photo_timeinterval,photo_distinterval
                String strGimbalPitch = columns[7];
                float gimbalPitch = Float.parseFloat(strGimbalPitch);

                // Absolute pitch
                //if(gimbalPitch < 0.0)
                mModel.do_set_Gimbal(9, gimbalPitch);

                String strWantedSpeed = columns[39];
                String strPOILatitude = columns[40];
                String strPOILongitude = columns[41];
                // Disabled is 0 / 0
                double wantedSpeed = Double.parseDouble(strWantedSpeed);
                double poiLatitude = Double.parseDouble(strPOILatitude);
                double poiLongitude = Double.parseDouble(strPOILongitude);

                String strLatitude = columns[0];
                String strLongitude = columns[1];
                double latitude = Double.parseDouble(strLatitude);
                double longitude = Double.parseDouble(strLongitude);

                String strAltitude = columns[2];
                float altitude = Float.parseFloat(strAltitude);

                String strHeading = columns[3];
                float heading = Float.parseFloat(strHeading);

                String strCurveSize = columns[4];
                double curveSize = Double.parseDouble(strCurveSize);

                // Min dist to target
                // Useful for fly-by
                mModel.m_Curvesize = Math.max(curveSize, 0.5);

                // Enable Cruising Mode (early handoff to next waypoint via curvesize rudimentary implementation)
                mModel.m_CruisingMode = wantedSpeed <= 3.0;

                mModel.gotoNoPhoto = true;
                mModel.m_Stay = false;

                // Handle some actions first, reset them so 2nd pass doesnt execute them
                for(int x = 0; x < 15; x += 2)
                {
                    String strActionType = columns[8 + x];
                    int actionType = Integer.parseInt(strActionType);

                    String strActionParam = columns[8 + x + 1];
                    int actionParam = Integer.parseInt(strActionParam);

                    // Disabled or in 2nd Pass
                    if(actionType == -1 || actionType == 2 || actionType == 3)
                        continue;
                    
                    switch(actionType)
                    {
                        // Stay for, Early Pass
                        case 0:
                            // Set velocity zero after reaching the target
                            mModel.m_Stay = true;
                            // Dont erase
                            continue;
                        // Take Photo
                        case 1:
                            mModel.gotoNoPhoto = false;
                            break;
                        default:
                            break;
                    }

                    // Block 2nd execution
                    columns[8 + x] = "-1";
                    columns[8 + x + 1] = "-1";
                }
                
                mModel.m_POI_Lat = poiLatitude;
                mModel.m_POI_Lon = poiLongitude;

                Log.d(TAG, "Waypoints: m_POI_Lon: " + poiLongitude + " m_POI_Lat: " + poiLatitude);

                mModel.do_set_motion_absolute(latitude, longitude, altitude, heading <= 180 ? heading : -180 + ((heading) - 180), 2.5f, 2.5f, 2.5f, 2.5f, 0);
                while(mModel.mMoveToDataTimer != null ||  mModel.photoTaken != true)
                {
                    ;
                }
                
                // 2nd Pass
                for(int x = 0; x < 15; x += 2)
                {
                    String strActionType = columns[8 + x];
                    int actionType = Integer.parseInt(strActionType);

                    String strActionParam = columns[8 + x + 1];
                    int actionParam = Integer.parseInt(strActionParam);

                    if(actionType == -1)
                        continue;
                    
                    switch(actionType)
                    {
                        // Start Recording
                        case 2:
                            mModel.startRecordingVideo();
                            break;
                        // Stop Recording
                        case 3:
                            mModel.stopRecordingVideo();
                            break;
                        // Stay for
                        case 0:
                            try
                            {
                                Thread.sleep(actionParam);
                            }
                            catch(InterruptedException ex)
                            {
                                Thread.currentThread().interrupt();
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
           
            mModel.m_CruisingMode = false;
            mModel.m_POI_Lat = 0.0;
            mModel.m_POI_Lon = 0.0;

            mModel.do_go_home();
        }
    };

    // Start the AI Pluggin (Developed by the customers...)
    protected boolean startActivity(String pluggin) {

        Intent intent = getPackageManager().getLaunchIntentForPackage(pluggin);
        if (intent == null) {
            // Start our Waypoint routine
            if(!customWaypointTask.isAlive())
                customWaypointTask.start();
        }
        if (intent != null) {
            intent.putExtra("password", "thisisrosettadrone246546101");
            intent.putExtra("ip", "127.0.0.1");
            intent.putExtra("port", 4000);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        return true;
    }

    // If AI button is pressed then start the AI Pluggin ...
    protected void SetMesasageBox(String msg) {
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
        startActivity("com.example.remoteconfig3");
    }

    // By default disable takeoff...
    private Runnable enablesafety = new Runnable() {
        @Override
        public void run() {
            stat = false;
            mBtnSafety.callOnClick();
        }
    };

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

    private void notifyStatusChange() {
        mDJIHandler.removeCallbacks(djiUpdateRunnable);
        mDJIHandler.postDelayed(djiUpdateRunnable, 500);
        final BaseProduct product = RDApplication.getProductInstance();

        if (product != null && product.isConnected() && product.getModel() != null) {
            logMessageDJI(product.getModel().name() + " Connected ");
        } else {
            logMessageDJI("Disconnected");
            Log.e(TAG, "Video out 2: ");

        }

        // The callback for receiving the raw H264 video data for camera live view
        // For newer drones...
        mReceivedVideoDataListener = (videoBuffer, size) -> {
            DJIVideoStreamDecoder.getInstance().parse(videoBuffer, size);
        };

        if (null == product || !product.isConnected()) {
            mCamera = null;
        } else {
            // List all models that needs alternative decoding...
            if (validateTranscodingMethod(product.getModel()) == true) {
                m_videoMode = 2;
            }

            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                mCamera = product.getCamera();
                if (mCamera != null) {
                    mCamera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, djiError -> {
                        if (djiError != null) {
                            Log.e(TAG, "can't change mode of camera, error: " + djiError.getDescription());
                            logMessageDJI("can't change mode of camera, error: " + djiError.getDescription());
                        }
                    });

                    mCamera.setHDLiveViewEnabled(true, djiError -> {});
                }

                /*mCamera.setVideoResolutionAndFrameRate(new ResolutionAndFrameRate(SettingsDefinitions.VideoResolution.RESOLUTION_3840x2160,SettingsDefinitions.VideoFrameRate.FRAME_RATE_29_DOT_970_FPS) , djiError -> {
                    if (djiError != null) {
                        Log.e(TAG, "can't change mode of camera, error: "+djiError);
                        logMessageDJI("can't change mode of camera, error: "+djiError);
                    }
                });*/

                NativeHelper.getInstance().init();
                //When calibration is needed or the fetch key frame is required by SDK, should use the provideTranscodedVideoFeed
                //to receive the transcoded video feed from main camera.
                if (mIsTranscodedVideoFeedNeeded) {
                    if (standardVideoFeeder == null)
                        standardVideoFeeder = VideoFeeder.getInstance().provideTranscodedVideoFeed();
                    if (mExternalVideoOut) {
                        for (VideoFeeder.VideoDataListener listener : standardVideoFeeder.getListeners()) {
                            standardVideoFeeder.removeVideoDataListener(listener);
                        }
                        standardVideoFeeder.addVideoDataListener(mReceivedVideoDataListener);
                        logMessageDJI("Transcode Video !!!!!!!");
                    }
                } else {
                    final VideoFeeder.VideoFeed videoFeeder = VideoFeeder.getInstance().getPrimaryVideoFeed();
                    if (mExternalVideoOut && videoFeeder != null) {
                        for (VideoFeeder.VideoDataListener listener : videoFeeder.getListeners()) {
                            videoFeeder.removeVideoDataListener(listener);
                        }
                        videoFeeder.addVideoDataListener(mReceivedVideoDataListener);
                        logMessageDJI("Do NOT Transcode Video !!!!!!!");
                    }
                }
            }
        }
    }

    private boolean validateTranscodingMethod(Model model) {
        // If the drone requires the old handling...
        switch (model) {
            case UNKNOWN_HANDHELD:
            case UNKNOWN_AIRCRAFT:
            case PHANTOM_3_STANDARD:
            case PHANTOM_3_ADVANCED:
            case PHANTOM_3_PROFESSIONAL:
            case Phantom_3_4K:
            case MAVIC_MINI:
            case MAVIC_PRO:
            case INSPIRE_1:
            case Spark:
            case INSPIRE_1_PRO:
            case INSPIRE_1_RAW:     // Verified...
            case MAVIC_AIR:         // Verified...
            case MAVIC_AIR_2:        // Verified...
                return true;
        }

        // Mavic 2 Pro              // Verified...
        // Mavic 2 Zoom             // Verified...
        // Matrice 210 V2 RTK       // Verified...
        return false;
    }

    private SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            // Inits decoder or changes surface
            // Change to on-screen surface if not already initialized
            DJIVideoStreamDecoder.getInstance().init(getApplicationContext(), holder.getSurface(), mCodecOutputSurface);
            DJIVideoStreamDecoder.getInstance().resume();

            //mCodecManager = new DJICodecManager(getApplicationContext(), null, 1280, 720, UsbAccessoryService.VideoStreamSource.Camera);
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            videoViewWidth = width;
            videoViewHeight = height;
            Log.d(TAG, "surfaceChanged: width " + videoViewWidth + " height " + videoViewHeight);
            DJIVideoStreamDecoder.getInstance().changeSurface(holder.getSurface());

        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // Switch to off-screen surface
            DJIVideoStreamDecoder.getInstance().changeSurface(mCodecOutputSurface.getSurface());
            /*if(mCodecManager != null)
            {
                mCodecManager = null;
            }*/
        }
    };

    //---------------------------------------------------------------------------------------
    //---------------------------------------------------------------------------------------
    //---------------------------------------------------------------------------------------

    private DJISDKManager.SDKManagerCallback mDJISDKManagerCallback = new DJISDKManager.SDKManagerCallback() {

        @Override
        public void onDatabaseDownloadProgress(long x, long y) {
        }

        @Override
        public void onInitProcess(DJISDKInitEvent a, int x) {
        }

        @Override
        public void onProductDisconnect() {
            notifyStatusChange();
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
        public void onProductChanged(BaseProduct baseProduct) {
            mProduct = baseProduct;

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
                newComponent.setComponentListener(isConnected -> notifyStatusChange());
            }
        }

        @Override
        public void onRegister(DJIError error) {
            Log.d(TAG, error == null ? "success" : error.getDescription());
            if (error == DJISDKError.REGISTRATION_SUCCESS) {
                DJISDKManager.getInstance().startConnectionToProduct();
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(() -> logMessageDJI("DJI SDK registered"));
            } else {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(() -> logMessageDJI("DJI SDK registration failed"));
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

        FragmentManager fragmentManager = getSupportFragmentManager();

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
                ZipEntry entry;
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
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
                for (String child : children) {
                    new File(dir, child).delete();
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
                if (mExternalVideoOut == false) {
                    if (!prefs.getBoolean("pref_separate_gcs", false)) {
                        sendRestartVideoService();
                    }
                }
//                FLAG_TELEMETRY_ADDRESS_CHANGED = false;
            }
            if (mExternalVideoOut == false) {
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
            case R.id.action_waypoint2:
                onClickWaypoint1();
                break;
            case R.id.action_waypoint1:
                onClickWaypoint2();
                break;
            case R.id.action_gui:
                onClickGUI();
                break;
            default:
                return false;
        }
        return true;
    }

    public void onSmallMapClick(View v) {

        LinearLayout map_layout = findViewById(R.id.map_view);
        FrameLayout video_layout_small = findViewById(R.id.fragment_container_small);
        ViewGroup.LayoutParams map_para = map_layout.getLayoutParams();

        if (compare_height == 0) {
            logMessageDJI("Set Small screen...");
            videostreamPreviewTtView.clearFocus();
            // Hide, switch surface off-screen
            videostreamPreviewTtView.setVisibility(View.GONE);
            // Add new surface callback, will eventually update codec surface to on-screen
            videostreamPreviewTtViewSmall.getHolder().addCallback(mSurfaceCallback);
            // Set Visible
            videostreamPreviewTtViewSmall.setVisibility(View.VISIBLE);
            // Wait a bit
            safeSleep(200);

            video_layout_small.setZ(100.f);
            map_layout.setZ(0.f);

            map_para.height = LayoutParams.WRAP_CONTENT;
            map_para.width = LayoutParams.WRAP_CONTENT;
            map_layout.setLayoutParams(map_para);

            compare_height = 1;
        } else {
            logMessageDJI("Set Main screen...");

            videostreamPreviewTtViewSmall.clearFocus();
            // Hide, switch surface off-screen
            videostreamPreviewTtViewSmall.setVisibility(View.GONE);
            // Add new surface callback, will eventually update codec surface to on-screen
            videostreamPreviewTtView.getHolder().addCallback(mSurfaceCallback);
            // Set Visible
            videostreamPreviewTtView.setVisibility(View.VISIBLE);;
            // Wait a bit
            safeSleep(200);

            video_layout_small.setZ(0.f);
            map_layout.setZ(100.f);

            map_para.height = ((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 86, getResources().getDisplayMetrics()));
            map_para.width = ((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 164, getResources().getDisplayMetrics()));
            map_layout.setLayoutParams(map_para);
            map_layout.setBottom(((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics())));
            map_layout.setLeft(((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics())));

//            com.amap.api.maps.MapView map = findViewById(R.id.map);
//            map.setAlpha((float)0.99);
            compare_height = 0;
        }

//        v.bringToFront();
        v.setZ(101.f);


    }

    // Hmm is this ever called...
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        //     AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        logMessageDJI("Config ..." + item);
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
            case R.id.action_waypoint2:
                onClickWaypoint1();
                break;
            case R.id.action_waypoint1:
                onClickWaypoint2();
                break;
            case R.id.action_gui:
                onClickGUI();
                break;
            default:
                return super.onContextItemSelected(item);
        }
        return true;
    }

    private void onClickSettings() {
        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
        startActivityForResult(intent, RESULT_SETTINGS);
    }

    private void onClickWaypoint1() {
        Intent intent = new Intent(MainActivity.this, Waypoint1Activity.class);
        startActivityForResult(intent, RESULT_HELP);
    }

    private void onClickWaypoint2() {
        Intent intent = new Intent(MainActivity.this, Waypoint2Activity.class);
        startActivityForResult(intent, RESULT_HELP);
    }


    private void onClickGUI() {
        if (!gui_enabled) {
            gui_enabled = true;
            logDJI.clearLogText();
            logOutbound.clearLogText();
            logInbound.clearLogText();
        } else {
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
            safeSleep(100);
        }

        sendDroneConnected();

        final Drawable connectedDrawable = getResources().getDrawable(R.drawable.ic_baseline_connected_24px, null);

        runOnUiThread(() -> {
            ImageView djiImageView = findViewById(R.id.dji_conn);
            djiImageView.setBackground(connectedDrawable);
            djiImageView.invalidate();
        });

    }

    private void onDroneDisconnected() {
        final Drawable disconnectedDrawable = getResources().getDrawable(R.drawable.ic_outline_disconnected_24px, null);

        runOnUiThread(() -> {
            ImageView imageView = findViewById(R.id.dji_conn);
            imageView.setBackground(disconnectedDrawable);
            imageView.invalidate();
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
                float paramValue = Float.parseFloat(paramData[3]);
                short paramType = Short.parseShort(paramData[4]);

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
        Log.d(TAG, msg);
        if (mNewDJI.length() > 1000)
            mNewDJI = mNewDJI.substring(500, 1000);

        mNewDJI += "\n" + msg;
    }

    private void sendRestartVideoService() {
        String videoIP = getVideoIP();
        int videoPort = Integer.parseInt(Objects.requireNonNull(prefs.getString("pref_video_port", "5600")));
        logMessageDJI("Restarting Video link to " + videoIP + ":" + videoPort);
    }

    private void sendDroneConnected() {
        String videoIP = getVideoIP();

        int videoPort = Integer.parseInt(Objects.requireNonNull(prefs.getString("pref_video_port", "5600")));

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
            mMavlinkReceiver.curvedFlightPath = Integer.parseInt((Objects.requireNonNull(prefs.getString("pref_drone_flight_path_mode", "2")))) == 0;
            mMavlinkReceiver.flightPathRadius = Float.parseFloat(Objects.requireNonNull(prefs.getString("pref_drone_flight_path_radius", ".2")));
        }

        if (FLAG_DRONE_ID_CHANGED) {
            mModel.setSystemId(Integer.parseInt(Objects.requireNonNull(prefs.getString("pref_drone_id", "1"))));
            FLAG_DRONE_ID_CHANGED = false;
        }

        if (FLAG_DRONE_RTL_ALTITUDE_CHANGED) {
            mModel.setRTLAltitude(Integer.parseInt(Objects.requireNonNull(prefs.getString("pref_drone_rtl_altitude", "30"))));
            FLAG_DRONE_RTL_ALTITUDE_CHANGED = false;
        }

        if (FLAG_DRONE_MAX_HEIGHT_CHANGED) {
            mModel.setMaxHeight(Integer.parseInt(Objects.requireNonNull(prefs.getString("pref_drone_max_height", "500"))));
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

        if(FLAG_MAPS_CHANGED){
            mMaptype = Integer.parseInt(Objects.requireNonNull(prefs.getString("pref_maptype_mode", "0")));
            aMap.setMapType(mMaptype);
            FLAG_MAPS_CHANGED = false;
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
        boolean request_renew_datalinks = false;
        private Timer timer;
        private WeakReference<MainActivity> mainActivityWeakReference;

        GCSCommunicatorAsyncTask(MainActivity mainActivity) {
            mainActivityWeakReference = new WeakReference<>(mainActivity);
        }

        void renewDatalinks() {
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
                                final Drawable connectedDrawable = mainActivityWeakReference.get().getResources().getDrawable(R.drawable.ic_baseline_connected_24px, null);

                                mainActivityWeakReference.get().runOnUiThread(() -> {
                                    ImageView imageView = mainActivityWeakReference.get().findViewById(R.id.gcs_conn);
                                    imageView.setBackground(connectedDrawable);
                                    imageView.invalidate();
                                });
                            } else {
                                final Drawable disconnectedDrawable = mainActivityWeakReference.get().getResources().getDrawable(R.drawable.ic_outline_disconnected_24px, null);

                                mainActivityWeakReference.get().runOnUiThread(() -> {
                                    ImageView imageView = mainActivityWeakReference.get().findViewById(R.id.gcs_conn);
                                    imageView.setBackground(disconnectedDrawable);
                                    imageView.invalidate();
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

            final Drawable disconnectedDrawable = mainActivityWeakReference.get().getResources().getDrawable(R.drawable.ic_outline_disconnected_24px, null);

            mainActivityWeakReference.get().runOnUiThread(() -> {
                ImageView imageView = mainActivityWeakReference.get().findViewById(R.id.gcs_conn);
                imageView.setBackground(disconnectedDrawable);
                imageView.invalidate();
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


            int telemIPPort = Integer.parseInt(Objects.requireNonNull(mainActivityWeakReference.get().prefs.getString("pref_telem_port", "14550")));

            try {
                mainActivityWeakReference.get().socket = new DatagramSocket();
                mainActivityWeakReference.get().socket.connect(InetAddress.getByName(gcsIPString), telemIPPort);
                mainActivityWeakReference.get().socket.setSoTimeout(10);

                mainActivityWeakReference.get().logMessageDJI("Starting GCS telemetry link: " + gcsIPString + ":" + telemIPPort);
            } catch (SocketException e) {
                Log.d(TAG, "createTelemetrySocket() - socket exception");
                Log.d(TAG, "exception", e);
                mainActivityWeakReference.get().logMessageDJI("Telemetry socket exception: " + gcsIPString + ":" + telemIPPort);
            } // TODO
            catch (UnknownHostException e) {
                Log.d(TAG, "createTelemetrySocket() - unknown host exception");
                Log.d(TAG, "exception", e);
                mainActivityWeakReference.get().logMessageDJI("Unknown telemetry host: " + gcsIPString + ":" + telemIPPort);
            } // TODO

            if (mainActivityWeakReference.get() != null) {
                mainActivityWeakReference.get().mModel.setSocket(mainActivityWeakReference.get().socket);
                if (mainActivityWeakReference.get().prefs.getBoolean("pref_secondary_telemetry_enabled", false)) {
                    String secondaryIP = mainActivityWeakReference.get().prefs.getString("pref_secondary_telemetry_ip", "127.0.0.1");
                    int secondaryPort = Integer.parseInt(Objects.requireNonNull(mainActivityWeakReference.get().prefs.getString("pref_secondary_telemetry_port", "18990")));
                    try {
                        DatagramSocket secondarySocket = new DatagramSocket();
                        secondarySocket.connect(InetAddress.getByName(secondaryIP), secondaryPort);
                        mainActivityWeakReference.get().logMessageDJI("Starting secondary telemetry link: " + secondaryIP + ":" + secondaryPort);

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
                    Thread thread = new Thread(() -> {
                        mainActivityWeakReference.get().mModel.secondarySocket.disconnect();
                        mainActivityWeakReference.get().mModel.secondarySocket.close();
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

        return true;
        //return VideoFeeder.getInstance().isFetchKeyFrameNeeded() || VideoFeeder.getInstance().isLensDistortionCalibrationNeeded();
    }

    //---------------------------------------------------------------------------------------
    //endregion
    // TODO: Move to own file, credit sources
    /**
     * Holds state associated with a Surface used for MediaCodec decoder output.
     * <p>
     * The constructor for this class will prepare GL, create a SurfaceTexture,
     * and then create a Surface for that SurfaceTexture.  The Surface can be passed to
     * MediaCodec.configure() to receive decoder output.  When a frame arrives, we latch the
     * texture with updateTexImage(), then render the texture with GL to a pbuffer.
     * <p>
     * By default, the Surface will be using a BufferQueue in asynchronous mode, so we
     * can potentially drop frames.
     */
    public static class CodecOutputSurface
            implements SurfaceTexture.OnFrameAvailableListener {
        private STextureRender mTextureRender;
        private SurfaceTexture mSurfaceTexture;
        private Surface mSurface;
        private EGL10 mEgl;

        private EGLDisplay mEGLDisplay = EGL10.EGL_NO_DISPLAY;
        private EGLContext mEGLContext = EGL10.EGL_NO_CONTEXT;
        private EGLSurface mEGLSurface = EGL10.EGL_NO_SURFACE;
        int mWidth;
        int mHeight;

        private Object mFrameSyncObject = new Object();     // guards mFrameAvailable
        private boolean mFrameAvailable;

        private ByteBuffer mPixelBuf;                       // used by saveFrame()

        /**
         * Creates a CodecOutputSurface backed by a pbuffer with the specified dimensions.  The
         * new EGL context and surface will be made current.  Creates a Surface that can be passed
         * to MediaCodec.configure().
         */
        public CodecOutputSurface(int width, int height) {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException();
            }
            mEgl = (EGL10) EGLContext.getEGL();
            mWidth = width;
            mHeight = height;

            eglSetup();
            makeCurrent();
            setup();
        }

        /**
         * Creates interconnected instances of TextureRender, SurfaceTexture, and Surface.
         */
        private void setup() {
            mTextureRender = new STextureRender();
            mTextureRender.surfaceCreated();

            mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());

            // This doesn't work if this object is created on the thread that CTS started for
            // these test cases.
            //
            // The CTS-created thread has a Looper, and the SurfaceTexture constructor will
            // create a Handler that uses it.  The "frame available" message is delivered
            // there, but since we're not a Looper-based thread we'll never see it.  For
            // this to do anything useful, CodecOutputSurface must be created on a thread without
            // a Looper, so that SurfaceTexture uses the main application Looper instead.
            //
            // Java language note: passing "this" out of a constructor is generally unwise,
            // but we should be able to get away with it here.
            mSurfaceTexture.setOnFrameAvailableListener(this);

            mSurface = new Surface(mSurfaceTexture);

            mPixelBuf = ByteBuffer.allocateDirect(mWidth * mHeight * 4);
            mPixelBuf.order(ByteOrder.LITTLE_ENDIAN);
        }

        /**
         * Prepares EGL.  We want a GLES 2.0 context and a surface that supports pbuffer.
         */
        private void eglSetup() {
            final int EGL_OPENGL_ES2_BIT = 0x0004;
            final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

            mEGLDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (mEGLDisplay == EGL10.EGL_NO_DISPLAY) {
                throw new RuntimeException("unable to get EGL14 display");
            }
            int[] version = new int[2];
            if (!mEgl.eglInitialize(mEGLDisplay, version)) {
                mEGLDisplay = null;
                throw new RuntimeException("unable to initialize EGL14");
            }

            // Configure EGL for pbuffer and OpenGL ES 2.0, 24-bit RGB.
            int[] attribList = {
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                    EGL10.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!mEgl.eglChooseConfig(mEGLDisplay, attribList, configs, configs.length,
                    numConfigs)) {
                throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
            }

            // Configure context for OpenGL ES 2.0.
            int[] attrib_list = {
                    EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL10.EGL_NONE
            };
            mEGLContext = mEgl.eglCreateContext(mEGLDisplay, configs[0], EGL10.EGL_NO_CONTEXT,
                    attrib_list);
            checkEglError("eglCreateContext");
            if (mEGLContext == null) {
                throw new RuntimeException("null context");
            }

            // Create a pbuffer surface.
            int[] surfaceAttribs = {
                    EGL10.EGL_WIDTH, mWidth,
                    EGL10.EGL_HEIGHT, mHeight,
                    EGL10.EGL_NONE
            };
            mEGLSurface = mEgl.eglCreatePbufferSurface(mEGLDisplay, configs[0], surfaceAttribs);
            checkEglError("eglCreatePbufferSurface");
            if (mEGLSurface == null) {
                throw new RuntimeException("surface was null");
            }
        }

        public SurfaceTexture getSurfaceTexture()
        {
            return this.mSurfaceTexture;
        }
        
        /**
         * Discard all resources held by this class, notably the EGL context.
         */
        public void release() {
            if (mEGLDisplay != EGL10.EGL_NO_DISPLAY) {
                mEgl.eglDestroySurface(mEGLDisplay, mEGLSurface);
                mEgl.eglDestroyContext(mEGLDisplay, mEGLContext);
                //mEgl.eglReleaseThread();
                mEgl.eglMakeCurrent(mEGLDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
                        EGL10.EGL_NO_CONTEXT);
                mEgl.eglTerminate(mEGLDisplay);
            }
            mEGLDisplay = EGL10.EGL_NO_DISPLAY;
            mEGLContext = EGL10.EGL_NO_CONTEXT;
            mEGLSurface = EGL10.EGL_NO_SURFACE;

            mSurface.release();

            // this causes a bunch of warnings that appear harmless but might confuse someone:
            //  W BufferQueue: [unnamed-3997-2] cancelBuffer: BufferQueue has been abandoned!
            //mSurfaceTexture.release();

            mTextureRender = null;
            mSurface = null;
            mSurfaceTexture = null;
        }

        /**
         * Makes our EGL context and surface current.
         */
        public void makeCurrent() {
            if (!mEgl.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
                throw new RuntimeException("eglMakeCurrent failed");
            }
        }

        /**
         * Returns the Surface.
         */
        public Surface getSurface() {
            return mSurface;
        }

        /**
         * Latches the next buffer into the texture.  Must be called from the thread that created
         * the CodecOutputSurface object.  (More specifically, it must be called on the thread
         * with the EGLContext that contains the GL texture object used by SurfaceTexture.)
         */
        public void awaitNewImage() {
            final int TIMEOUT_MS = 2500;

            synchronized (mFrameSyncObject) {
                while (!mFrameAvailable) {
                    try {
                        // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
                        // stalling the test if it doesn't arrive.
                        mFrameSyncObject.wait(TIMEOUT_MS);
                        if (!mFrameAvailable) {
                            // TODO: if "spurious wakeup", continue while loop
                            throw new RuntimeException("frame wait timed out");
                        }
                    } catch (InterruptedException ie) {
                        // shouldn't happen
                        throw new RuntimeException(ie);
                    }
                }
                mFrameAvailable = false;
            }

            // Latch the data.
            mTextureRender.checkGlError("before updateTexImage");
            mSurfaceTexture.updateTexImage();
        }

        /**
         * Draws the data from SurfaceTexture onto the current EGL surface.
         *
         * @param invert if set, render the image with Y inverted (0,0 in top left)
         */
        public void drawImage(boolean invert) {
            mTextureRender.drawFrame(mSurfaceTexture, invert);
        }

        // SurfaceTexture callback
        @Override
        public void onFrameAvailable(SurfaceTexture st) {
            Log.d(MainActivity.class.getSimpleName(), "new frame available");
            synchronized (mFrameSyncObject) {
                if (mFrameAvailable) {
                    throw new RuntimeException("mFrameAvailable already set, frame could be dropped");
                }
                mFrameAvailable = true;
                mFrameSyncObject.notifyAll();
            }
        }

        /**
         * Saves the current frame to disk as a PNG image.
         */
        public void saveFrame(String filename) throws IOException {
            // glReadPixels gives us a ByteBuffer filled with what is essentially big-endian RGBA
            // data (i.e. a byte of red, followed by a byte of green...).  To use the Bitmap
            // constructor that takes an int[] array with pixel data, we need an int[] filled
            // with little-endian ARGB data.
            //
            // If we implement this as a series of buf.get() calls, we can spend 2.5 seconds just
            // copying data around for a 720p frame.  It's better to do a bulk get() and then
            // rearrange the data in memory.  (For comparison, the PNG compress takes about 500ms
            // for a trivial frame.)
            //
            // So... we set the ByteBuffer to little-endian, which should turn the bulk IntBuffer
            // get() into a straight memcpy on most Android devices.  Our ints will hold ABGR data.
            // Swapping B and R gives us ARGB.  We need about 30ms for the bulk get(), and another
            // 270ms for the color swap.
            //
            // We can avoid the costly B/R swap here if we do it in the fragment shader (see
            // http://stackoverflow.com/questions/21634450/ ).
            //
            // Having said all that... it turns out that the Bitmap#copyPixelsFromBuffer()
            // method wants RGBA pixels, not ARGB, so if we create an empty bitmap and then
            // copy pixel data in we can avoid the swap issue entirely, and just copy straight
            // into the Bitmap from the ByteBuffer.
            //
            // Making this even more interesting is the upside-down nature of GL, which means
            // our output will look upside-down relative to what appears on screen if the
            // typical GL conventions are used.  (For ExtractMpegFrameTest, we avoid the issue
            // by inverting the frame when we render it.)
            //
            // Allocating large buffers is expensive, so we really want mPixelBuf to be
            // allocated ahead of time if possible.  We still get some allocations from the
            // Bitmap / PNG creation.

            mPixelBuf.rewind();
            GLES20.glReadPixels(0, 0, mWidth, mHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
                mPixelBuf);

            BufferedOutputStream bos = null;
            try {
                bos = new BufferedOutputStream(new FileOutputStream(filename));
                Bitmap bmp = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
                mPixelBuf.rewind();
                bmp.copyPixelsFromBuffer(mPixelBuf);
                bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
                bmp.recycle();
            } finally {
                if (bos != null) bos.close();
            }
            Log.d(MainActivity.class.getSimpleName(), "Saved " + mWidth + "x" + mHeight + " frame as '" + filename + "'");
        }

        /**
         * Checks for EGL errors.
         */
        private void checkEglError(String msg) {
            int error;
            if ((error = mEgl.eglGetError()) != EGL10.EGL_SUCCESS) {
                throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
            }
        }
    }


    /**
     * Code for rendering a texture onto a surface using OpenGL ES 2.0.
     */
    private static class STextureRender {
        private static final int FLOAT_SIZE_BYTES = 4;
        private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
        private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
        private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
        private final float[] mTriangleVerticesData = {
                // X, Y, Z, U, V
                -1.0f, -1.0f, 0, 0.f, 0.f,
                 1.0f, -1.0f, 0, 1.f, 0.f,
                -1.0f,  1.0f, 0, 0.f, 1.f,
                 1.0f,  1.0f, 0, 1.f, 1.f,
        };

        private FloatBuffer mTriangleVertices;

        private static final String VERTEX_SHADER =
                "uniform mat4 uMVPMatrix;\n" +
                "uniform mat4 uSTMatrix;\n" +
                "attribute vec4 aPosition;\n" +
                "attribute vec4 aTextureCoord;\n" +
                "varying vec2 vTextureCoord;\n" +
                "void main() {\n" +
                "    gl_Position = uMVPMatrix * aPosition;\n" +
                "    vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                "}\n";

        private static final String FRAGMENT_SHADER =
                "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +      // highp here doesn't seem to matter
                "varying vec2 vTextureCoord;\n" +
                "uniform samplerExternalOES sTexture;\n" +
                "void main() {\n" +
                "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                "}\n";

        private float[] mMVPMatrix = new float[16];
        private float[] mSTMatrix = new float[16];

        private int mProgram;
        private int mTextureID = -12345;
        private int muMVPMatrixHandle;
        private int muSTMatrixHandle;
        private int maPositionHandle;
        private int maTextureHandle;

        public STextureRender() {
            mTriangleVertices = ByteBuffer.allocateDirect(
                    mTriangleVerticesData.length * FLOAT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            mTriangleVertices.put(mTriangleVerticesData).position(0);

            Matrix.setIdentityM(mSTMatrix, 0);
        }

        public int getTextureId() {
            return mTextureID;
        }

        /**
         * Draws the external texture in SurfaceTexture onto the current EGL surface.
         */
        public void drawFrame(SurfaceTexture st, boolean invert) {
            checkGlError("onDrawFrame start");
            st.getTransformMatrix(mSTMatrix);
            if (invert) {
                mSTMatrix[5] = -mSTMatrix[5];
                mSTMatrix[13] = 1.0f - mSTMatrix[13];
            }

            // (optional) clear to green so we can see if we're failing to set pixels
            GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            GLES20.glUseProgram(mProgram);
            checkGlError("glUseProgram");

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);

            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
            GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
            checkGlError("glVertexAttribPointer maPosition");
            GLES20.glEnableVertexAttribArray(maPositionHandle);
            checkGlError("glEnableVertexAttribArray maPositionHandle");

            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
            GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
            checkGlError("glVertexAttribPointer maTextureHandle");
            GLES20.glEnableVertexAttribArray(maTextureHandle);
            checkGlError("glEnableVertexAttribArray maTextureHandle");

            Matrix.setIdentityM(mMVPMatrix, 0);
            GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
            GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            checkGlError("glDrawArrays");

            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        }

        /**
         * Initializes GL state.  Call this after the EGL surface has been created and made current.
         */
        public void surfaceCreated() {
            mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            if (mProgram == 0) {
                throw new RuntimeException("failed creating program");
            }

            maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
            checkLocation(maPositionHandle, "aPosition");
            maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
            checkLocation(maTextureHandle, "aTextureCoord");

            muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
            checkLocation(muMVPMatrixHandle, "uMVPMatrix");
            muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
            checkLocation(muSTMatrixHandle, "uSTMatrix");

            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);

            mTextureID = textures[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
            checkGlError("glBindTexture mTextureID");

            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_NEAREST);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
            checkGlError("glTexParameter");
        }

        /**
         * Replaces the fragment shader.  Pass in null to reset to default.
         */
        public void changeFragmentShader(String fragmentShader) {
            if (fragmentShader == null) {
                fragmentShader = FRAGMENT_SHADER;
            }
            GLES20.glDeleteProgram(mProgram);
            mProgram = createProgram(VERTEX_SHADER, fragmentShader);
            if (mProgram == 0) {
                throw new RuntimeException("failed creating program");
            }
        }

        private int loadShader(int shaderType, String source) {
            int shader = GLES20.glCreateShader(shaderType);
            checkGlError("glCreateShader type=" + shaderType);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(MainActivity.class.getSimpleName(), "Could not compile shader " + shaderType + ":");
                Log.e(MainActivity.class.getSimpleName(), " " + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
            return shader;
        }

        private int createProgram(String vertexSource, String fragmentSource) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            if (vertexShader == 0) {
                return 0;
            }
            int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            if (pixelShader == 0) {
                return 0;
            }

            int program = GLES20.glCreateProgram();
            if (program == 0) {
                Log.e(MainActivity.class.getSimpleName(), "Could not create program");
            }
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            GLES20.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(MainActivity.class.getSimpleName(), "Could not link program: ");
                Log.e(MainActivity.class.getSimpleName(), GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
            return program;
        }

        public void checkGlError(String op) {
            int error;
            while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
                Log.e(MainActivity.class.getSimpleName(), op + ": glError " + error);
                throw new RuntimeException(op + ": glError " + error);
            }
        }

        public static void checkLocation(int location, String label) {
            if (location < 0) {
                throw new RuntimeException("Unable to locate '" + label + "' in program");
            }
        }
    }
}
