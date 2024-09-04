package sq.rogue.rosettadrone;

import android.Manifest;
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
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuInflater;
import android.view.MenuItem;
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
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
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
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;
import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.model.LocationCoordinate2D;
import dji.common.product.Model;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.media.FetchMediaTaskScheduler;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;
import sq.rogue.rosettadrone.logs.LogFragment;
import sq.rogue.rosettadrone.settings.SettingsActivity;
import sq.rogue.rosettadrone.settings.Waypoint1Activity;
import sq.rogue.rosettadrone.settings.Waypoint2Activity;
import sq.rogue.rosettadrone.video.NativeHelper;
import sq.rogue.rosettadrone.video.VideoService;

import static com.google.android.material.snackbar.Snackbar.LENGTH_LONG;
import static sq.rogue.rosettadrone.util.safeSleep;

//public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMapClickListener {
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private final static int RESULT_SETTINGS = 1001;
    private final static int RESULT_HELP = 1002;

    private enum FocusedView {
        VideoFeed,
        Map,
    }
    private static FocusedView focusedView = FocusedView.VideoFeed;

    public static boolean FLAG_PREFS_CHANGED = false;
    public static List<String> changedSettings = new ArrayList<String>();

    // TODO: DEPRECATE: Use changedSettings
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
    public static boolean FLAG_APP_REPORT_EMAIL = false;
    public static boolean FLAG_MAPS_CHANGED = false;

    private GoogleMap aMap;
    private double droneLocationLat, droneLocationLng;
    private Marker droneMarker = null;
    private Marker gcsMarker = null;
    private Marker gotoMarker = null;

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
    private String mNewOutbound = "";
    private String mNewInbound = "";
    private String mNewDJI = "";
    public  DroneModel mModel;
    private Boolean mExtRunning = false;
    private LocationManager locationManager;
    private double m_host_lat = -500;
    private double m_host_lon = -500;

    public MAVLinkReceiver mMavlinkReceiver;
    private Parser mMavlinkParser;
    private GCSCommunicatorAsyncTask mGCSCommunicator;
    private boolean connectivityHasChanged = false;
    private boolean shouldConnect = false;
    private boolean gui_enabled = true;
    private Button mBtnSafety;
    private boolean stat = false; // Is it safe to takeoff...

    public SharedPreferences prefs;
    public SharedPreferences sharedPreferences;

    // TODO: Move to VideoService
    public boolean useCustomDecoder = true; // Can be disabled by a video plugin
    public boolean useOutputSurface = true; // Can be disabled by a video plugin
    private boolean mExternalVideoOut = true;
    private String mvideoIPString;
    private int videoPort;
    private int mVideoBitrate = 2;
    private int mEncodeSpeed = 2;
    private VideoService videoService = null;
    private boolean mIsBound;
    private int m_videoMode = 1;
    private int mPrevVideoBufferSize = 0;

    private VideoFeeder.VideoFeed standardVideoFeeder;
    protected VideoFeeder.VideoDataListener mReceivedVideoDataListener;
    private TextureView videostreamPreviewTtView;
    public DJICodecManager mCodecManager;
    private boolean mIsTranscodedVideoFeedNeeded = false;

    private int mMaptype = GoogleMap.MAP_TYPE_HYBRID;

    public static WaypointMission.Builder waypointMissionBuilder;
    private List<Waypoint> waypointList = new ArrayList<>();
    private List<Polyline> m_line = new ArrayList<>();
    private LatLng m_lastPos;

    public List<MediaFile> mediaFileList = new ArrayList<MediaFile>();
    private MediaManager mMediaManager;
    private MediaManager.FileListState currentFileListState = MediaManager.FileListState.UNKNOWN;
    private FetchMediaTaskScheduler scheduler;
    public String last_downloaded_file;
    public boolean downloadError = false;
    public int lastDownloadedIndex = -1;

    public PluginManager pluginManager = new PluginManager(this);

    public static boolean changedSetting(String settingId) {
        return changedSettings.indexOf(settingId) != -1;
    }

    private Runnable djiUpdateRunnable = () -> {
        Intent intent = new Intent(DJISimulatorApplication.FLAG_CONNECTION_CHANGE);
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
                        logDJI.appendLogText(mNewDJI);
                        mNewDJI = "";
                    }
                    if (!mNewOutbound.equals("")) {
                        logOutbound.appendLogText(mNewOutbound);
                        mNewOutbound = "";
                    }
                    if (!mNewInbound.equals("")) {
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

    private void initLocationManager() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = new MyLocationListener();

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing right to access GPS location!");
        } else {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
        }
    }

    public String getGCSAddress() {
        String gcsAddress;
        if (sharedPreferences.getBoolean("pref_external_gcs", false)) {
            gcsAddress = sharedPreferences.getString("pref_gcs_ip", "127.0.0.1");
        } else {
            gcsAddress = "127.0.0.1";
        }

        return gcsAddress;
    }

    /**
     * Reads the settings and binds the VideoService
     */
    private void initVideoService() {
        if(!useCustomDecoder) return;

        Log.e(TAG, "initVideoService");

        mvideoIPString = getVideoIP();
        Log.e(TAG, "IP Address: " + mvideoIPString);

        videoPort = Integer.parseInt(Objects.requireNonNull(sharedPreferences.getString("pref_video_port", "5600")));
        mVideoBitrate = Integer.parseInt(Objects.requireNonNull(sharedPreferences.getString("pref_video_bitrate", "2")));
        mEncodeSpeed = Integer.parseInt(Objects.requireNonNull(sharedPreferences.getString("pref_encode_speed", "2")));

        mMaptype = Integer.parseInt(Objects.requireNonNull(prefs.getString("pref_maptype_mode", "2")));
        logMessageDJI("Mapmode: " + mMaptype);

        Intent intent = new Intent(this, VideoService.class);

        // BUG: "Not allowed to start service Intent" "app is in background"
        this.startService(intent);
        //this.startForegroundService(intent); // For API level 26

        safeSleep(500);

        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        Log.e(TAG, "doBindService");
        bindService(new Intent(this, VideoService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;

        mIsTranscodedVideoFeedNeeded = isTranscodedVideoFeedNeeded();
        if (mIsTranscodedVideoFeedNeeded) {
            // The one were we get transcode data...
            VideoFeeder.getInstance().setTranscodingDataRate(mVideoBitrate);
            logMessageDJI("set rate to " + mVideoBitrate);
        }
    }

    // After the service has started...
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.e(TAG, "onServiceConnected  " + mvideoIPString);
            videoService = ((VideoService.LocalBinder) iBinder).getInstance();
            videoService.setParameters(mvideoIPString, videoPort, mVideoBitrate, mEncodeSpeed);

            if (prefs.getBoolean("pref_enable_dualvideo", true)) {
                videoService.setDualVideo(true);
            } else {
                videoService.setDualVideo(false);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.e(TAG, "onServiceDisconnected");
            videoService = null;
        }
    };

    /**
     * onResume is called whenever RosettaDrone takes the foreground. This happens if we open the
     * RosettaDrone application or return from the preferences window. Need to rebind to the AIDL service.
     */
    @Override
    protected void onResume() {
        Log.e(TAG, "onResume()");
        super.onResume();

        setDroneParameters();

        /*
        if (mDroneDetails != null) {
            String droneID = prefs.getString("pref_drone_id", "1");
            String rtlAlt = prefs.getString("pref_drone_rtl_altitude", "60") + "m";

            float dronebattery = mMavlinkReceiver.mModel.get_drone_battery_prosentage();
            float controlerbattery = mMavlinkReceiver.mModel.get_controller_battery_prosentage();

            String text = "Drone Battery:       " + "\t\t" + dronebattery + "%"  + "\t" + "ID: " + "\t\t" + droneID + System.getProperty("line.separator") +
                    "Controller Battery:  " + "\t" + controlerbattery + "%"  + "\t" + "RTL:" + "\t" + rtlAlt;

            mDroneDetails.setText(text);
        }
        */

        resumeVideo();
        pluginManager.resume();
    }

    void resumeVideo() {
        mExternalVideoOut = prefs.getBoolean("pref_enable_video", true);

        // We should rather use multicast...
        if (videoService != null) {
            if (prefs.getBoolean("pref_enable_dualvideo", true)) {
                videoService.setDualVideo(true);
            } else {
                videoService.setDualVideo(false);
            }
        }

        videostreamPreviewTtView.setSurfaceTextureListener(mSurfaceTextureListener);
        if ( mModel.m_camera != null) {
            if (mExternalVideoOut) {
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
            } else {
                if (mIsTranscodedVideoFeedNeeded) {
                    if (standardVideoFeeder != null) {
                        standardVideoFeeder.removeVideoDataListener(mReceivedVideoDataListener);
                    }
                } else {
                    VideoFeeder.getInstance().getPrimaryVideoFeed().removeVideoDataListener(mReceivedVideoDataListener);
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
        super.onPause();
        pluginManager.pause();
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

        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager.destroyCodec();
        }
        if (mediaFileList != null) {
            mediaFileList.clear();
        }

        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }

        pluginManager.stop();

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
                LayoutParams map_para = map_layout.getLayoutParams();
                map_layout.setZ(0.f);
                map_para.height = LayoutParams.WRAP_CONTENT;
                map_para.width = LayoutParams.WRAP_CONTENT;
                map_layout.setLayoutParams(map_para);
            });
        }
        aMap.getUiSettings().setZoomControlsEnabled(false);
        aMap.setMapType(mMaptype);
        LatLng marker = new LatLng(m_host_lat, m_host_lon);
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(marker, 14F));

        updateDroneLocation();
    }

    private void markWaypoint(LatLng point, boolean keep) {
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));

        if(keep) {
            if(m_line.size() == 0){ m_lastPos = new LatLng(mModel.get_current_lat(), mModel.get_current_lat()); }
            m_line.add(aMap.addPolyline(new PolylineOptions()
                    .add(m_lastPos, point)
                    .width(5)
                    .color(Color.RED)));

            m_lastPos = point;

        }else{
            for( Polyline line : m_line){
                line.remove();
            }
            m_line.clear();
        }

        if (gotoMarker != null && keep == false) {
            gotoMarker.remove();
        }
        gotoMarker = aMap.addMarker(markerOptions);
    }

    // Update the drone location based on states from MCU.
    private void updateDroneLocation() {

        // We initialize the default map location to the same as the default SIM location...
        if (aMap != null) {

            // Add GCS Location to map...
            if (checkGpsCoordination(m_host_lat, m_host_lon)) {

                final MarkerOptions GCS_markerOptions = new MarkerOptions();
                GCS_markerOptions.position(new LatLng(m_host_lat, m_host_lon));
                BitmapDrawable bitmapdraw = (BitmapDrawable)getResources().getDrawable(R.drawable.pilot, null);
                Bitmap smallMarker;

                switch (focusedView) {
                    case Map: {
                        smallMarker = Bitmap.createScaledBitmap(bitmapdraw.getBitmap(), 64, 64, false);
                        break;
                    }
                    case VideoFeed:
                    default: {
                        smallMarker = Bitmap.createScaledBitmap(bitmapdraw.getBitmap(), 32, 32, false);
                        break;
                    }
                }

                GCS_markerOptions.icon(BitmapDescriptorFactory.fromBitmap(smallMarker));

                runOnUiThread(() -> {
                    if (gcsMarker != null) { gcsMarker.remove(); }
                    gcsMarker = aMap.addMarker(GCS_markerOptions);
//                aMap.moveCamera(CameraUpdateFactory.newLatLng(pos));
                });

            }

            // Find/define GCS Host location...
            LatLng pos;
            if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                pos = new LatLng(droneLocationLat, droneLocationLng);
            } else {
                LocationCoordinate2D loc = mModel.getSimPos2D();
                if(checkGpsCoordination(loc.getLatitude(),loc.getLongitude())) {
                    pos = new LatLng(loc.getLatitude(), loc.getLongitude());
                }
                else{
                    pos = new LatLng(62,12);
                }
            }
            // Add Drone location to map...
            final MarkerOptions markerOptions = new MarkerOptions();
            markerOptions.position(pos);
            BitmapDrawable bitmapdraw = (BitmapDrawable)getResources().getDrawable(R.drawable.drone_img, null);
            Bitmap smallMarker;

            switch (focusedView) {
                case Map: {
                    smallMarker = Bitmap.createScaledBitmap(bitmapdraw.getBitmap(), 64, 64, false);
                    break;
                }
                case VideoFeed:
                default: {
                    smallMarker = Bitmap.createScaledBitmap(bitmapdraw.getBitmap(), 32, 32, false);
                    break;
                }
            }
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(smallMarker));

            runOnUiThread(() -> {
                if (droneMarker != null) { droneMarker.remove(); }
                if (checkGpsCoordination(pos.latitude, pos.longitude)) {
                    droneMarker = aMap.addMarker(markerOptions);
                    aMap.moveCamera(CameraUpdateFactory.newLatLng(pos));
                }
            });
        }
    }

    /*---------- GCS Location changed... ------------- */
    private class MyLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location loc) {

            m_host_lat = loc.getLatitude();
            m_host_lon = loc.getLongitude();
            updateDroneLocation();

            /*------- To get city name from coordinates -------- */
/*
            String cityName = null;
            Geocoder gcd = new Geocoder(getBaseContext(), Locale.getDefault());
            List<Address> addresses;
            try {
                addresses = gcd.getFromLocation(loc.getLatitude(),
                        loc.getLongitude(), 1);
                if (addresses.size() > 0) {
                    System.out.println(addresses.get(0).getLocality());
                    cityName = addresses.get(0).getLocality();
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            Log.d(TAG, m_host_lat + "\n" + m_host_lon + "\n\nMy Current City is: "+ cityName);
            */
        }

        @Override
        public void onProviderDisabled(String provider) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    }

    private void requestDroneLocation() {
        if (mModel.mFlightController != null) {
            mModel.mFlightController.setStateCallback(djiFlightControllerCurrentState -> {
                LocationCoordinate3D loc = djiFlightControllerCurrentState.getAircraftLocation();
                droneLocationLat = loc.getLatitude();
                droneLocationLng = loc.getLongitude();

                if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                    // If we got no data from GCS then use the initial Drone location
                    if (m_host_lat == -500 && m_host_lon == -500) {
                        m_host_lat = droneLocationLat;
                        m_host_lon = droneLocationLng + 0.00005;
                    }
                    updateDroneLocation();
                }
            });
        }
    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    public static BaseProduct createDummyProduct() {
        return DummyProduct.getProductInstance();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        mProduct = RDApplication.getProductOrDummy();

        // Hide top bar
        Objects.requireNonNull(getSupportActionBar()).hide();

        // Force Landscape
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        // Hide title
        //requestWindowFeature(Window.FEATURE_NO_TITLE); // for hiding title

        // Make fullscreen
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_gui);

        // Load preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mProduct = RDApplication.getProductOrDummy();

        if (mProduct != null) {
            try {
                mProductModel = mProduct.getModel();
            } catch (Exception e) {
                Log.d(TAG, "exception", e);
                mProductModel = Model.MAVIC_PRO; // Just a dummy value should we be in test mode... (No Target)
            }
        } else {
            // We need to make this; if no device or if sin mode...
            // Will support alternative video source...
            Log.d(TAG, "Starting external video input...");
            int port = Integer.parseInt(Objects.requireNonNull(prefs.getString("pref_external_video", "0")));
            if (port > 1000) {
                startVideoLoop(port);
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

        mModel = new DroneModel(this, null, RDApplication.getSim());
        if(mModel.mFlightController == null) {
            // We should never have a MainActivity with a DroneModel without a FlightController since it may crash everywhere.
            // TODO: DOC: How does this happen? Maybe when running the DJI FlyApp in background?
            Log.e(TAG, "Tried to create MainActivity without a FlightController!");
            finish();
            return;
        }

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

        videostreamPreviewTtView = findViewById(R.id.livestream_preview_ttv);

        deleteApplicationDirectory();
        initLogs();
        initLocationManager();
        initVideoService();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        requestDroneLocation();

        DJISDKManager.getInstance().registerApp(this, mDJISDKManagerCallback);

        // SafeMode button
        mBtnSafety = findViewById(R.id.btn_safety);
        mBtnSafety.setOnClickListener(v -> {
            Drawable connectedDrawable;
            stat = !stat;
            if (stat) {
                connectedDrawable = getResources().getDrawable(R.drawable.ic_lock_outline_secondary_24dp, null);
                mBtnSafety.setBackground(connectedDrawable);
                findViewById(R.id.btn_takeoff).setVisibility(View.INVISIBLE);
            } else {
                connectedDrawable = getResources().getDrawable(R.drawable.ic_lock_open_black_24dp, null);
                mBtnSafety.setBackground(connectedDrawable);
                findViewById(R.id.btn_takeoff).setVisibility(View.VISIBLE);
            }
            mModel.setSafetyEnabled(stat);
            NotificationHandler.notifySnackbar(findViewById(R.id.snack),
                    (mModel.isSafetyEnabled()) ? R.string.safety_on : R.string.safety_off, LENGTH_LONG);
        });

        // Takeoff button
        Button mBtnTakeoff = findViewById(R.id.btn_takeoff);
        mBtnTakeoff.setOnClickListener(v -> mModel.doTakeOff(5, false));

        // RTH button
        Button mBtnRTH = findViewById(R.id.btn_rth);
        //mBtnRTH.setOnClickListener(v -> mModel.doGomeHome());
        mBtnRTH.setOnClickListener(v -> mModel.doReturnToLaunch());

        pluginManager.start();

        if(RDApplication.isTestMode) {
            mModel.mMotorsArmed = true;
            onDroneConnected();
        } else {
            // Click on SafeMode button (enable SafeMode)
            Handler mTimerHandler = new Handler(Looper.getMainLooper());
            mTimerHandler.postDelayed(enableSafeMode, 3000);
        }
    }

    private Runnable enableSafeMode = new Runnable() {
        @Override
        public void run() {
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

        if (mProduct != null && mProduct.isConnected() && mProduct.getModel() != null) {
            logMessageDJI(mProduct.getModel().name() + " Connected ");
        } else {
            logMessageDJI("Disconnected");
            Log.e(TAG, "Video out 2: ");
        }

        // Set callback for receiving the raw H264 video data from the camera
        mReceivedVideoDataListener = (videoBuffer, size) -> {
            int parserMode;
            if (m_videoMode == 2) {
                parserMode = 0;

                if (mCodecManager != null) {
                    // Render on screen
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                    if (mPrevVideoBufferSize != size && videostreamPreviewTtView.getSurfaceTexture() != null) {
                        mPrevVideoBufferSize = size;
                        mSurfaceTextureListener.onSurfaceTextureSizeChanged(videostreamPreviewTtView.getSurfaceTexture(), videostreamPreviewTtView.getWidth(), videostreamPreviewTtView.getHeight());
                    }
                }

            } else {
                parserMode = 1;
            }

            // Send the raw DJI H264 data to the JNI ffmpeg decoder --> NAL splitter --> RtpSocket
            if (mExternalVideoOut == true) {
                NativeHelper.getInstance().parse(videoBuffer, size, parserMode);
            }
        };

        if (mProduct == null || !mProduct.isConnected()) {
            mModel.m_camera = null;

        } else {
            m_videoMode = getVideoMode(mProduct.getModel());

            if (!mProduct.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                if (mModel.m_camera != null) {
                    mModel.m_camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, djiError -> {
                        if (djiError != null) {
                            Log.e(TAG, "can't change mode of camera, error: " + djiError.getDescription());
                            logMessageDJI("can't change mode of camera, error: " + djiError.getDescription());
                        }
                    });
                    if (mProduct.getModel().equals(Model.MAVIC_AIR_2)){
                        mProduct.getCamera()
                                .setFlatMode(SettingsDefinitions.FlatCameraMode.PHOTO_SINGLE, djiError -> {
                                    if (djiError != null) {
                                        Log.e(TAG, "can't change mode of camera, error: " + djiError.getDescription());
                                        logMessageDJI("can't change mode of camera, error: " + djiError.getDescription());
                                    }
                                });
                    }
                    else {
                        mModel.m_camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, djiError -> {
                            if (djiError != null) {
                                Log.e(TAG, "can't change mode of camera, error: " + djiError.getDescription());
                                logMessageDJI("can't change mode of camera, error: " + djiError.getDescription());
                            }
                            else{
                                logMessageDJI("Camera Mode set OK...");
                            }
                        });
                    }
                }

                // When calibration is needed or the fetch key frame is required by SDK, should use the provideTranscodedVideoFeed
                // to receive the transcoded video feed from main camera.
                if (mIsTranscodedVideoFeedNeeded) {
                    if (standardVideoFeeder == null) {
                        standardVideoFeeder = VideoFeeder.getInstance().provideTranscodedVideoFeed();
                    }
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

    private int getVideoMode(Model model) {
        switch (model) {
            case UNKNOWN_HANDHELD:
            case UNKNOWN_AIRCRAFT:
            case PHANTOM_3_STANDARD:
            case PHANTOM_3_ADVANCED:
            case PHANTOM_3_PROFESSIONAL:
            case Phantom_3_4K:
            case PHANTOM_4:         // https://github.com/The1only/rosettadrone/issues/61
            case MAVIC_MINI:        // Confirmed
            case DJI_MINI_SE:       // Confirmed
            case DJI_MINI_2:
            case DJI_AIR_2S:        // To be confirmed
            case MAVIC_PRO:
            case INSPIRE_1:
            case Spark:
            case MAVIC_AIR_2:       // Confirmed. https://github.com/The1only/rosettadrone/issues/108
            case INSPIRE_1_PRO:
            case INSPIRE_1_RAW:     // Confirmed
            case MAVIC_AIR:         // Confirmed
                return 2;

            case MAVIC_2_PRO:       // Confirmed
            case MAVIC_2_ZOOM:      // Confirmed
            case MATRICE_210_RTK_V2:    // Confirmed
                return 1;
        }
        return 1;
    }

    /**
     * Init a fake texture view to for the codec manager, so that the video raw data can be received
     * by the camera needed to get video to the UDP handler...
     */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            if (mCodecManager == null) {
                mCodecManager = new DJICodecManager(getApplicationContext(), surface, width, height);
                pluginManager.onVideoChange();
            }

            Log.d(TAG, "real onSurfaceTextureAvailable: width " + width + " height " + height);

            onSurfaceTextureSizeChanged(surface, width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            double aspectRatio = (double) mCodecManager.getVideoHeight() / mCodecManager.getVideoWidth();
            int newWidth, newHeight;
            if (height > (int) (width * aspectRatio)) {
                newWidth = width;
                newHeight = (int) (width * aspectRatio);
            } else {
                newWidth = (int) (height / aspectRatio);
                newHeight = height;
            }
            int xOffset = (width - newWidth) / 2;
            int yOffset = (height - newHeight) / 2;

            Matrix transform = new Matrix();
            TextureView textureView = videostreamPreviewTtView;
            textureView.getTransform(transform);
            transform.setScale((float) newWidth / width, (float) newHeight / height);
            transform.postTranslate(xOffset, yOffset);
            textureView.setTransform(transform);

            Log.d(TAG, "real onSurfaceTextureAvailable2: width " + width + " height " + height);

            if (useOutputSurface) {
                mCodecManager.changeOutputSurface(surface);
                mCodecManager.onSurfaceSizeChanged(width, height, 0);
            }
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
    };

    //---------------------------------------------------------------------------------------
    //---------------------------------------------------------------------------------------

    private DJISDKManager.SDKManagerCallback mDJISDKManagerCallback = new DJISDKManager.SDKManagerCallback() {

        @Override
        public void onDatabaseDownloadProgress(long x, long y) {}

        @Override
        public void onInitProcess(DJISDKInitEvent a, int x) {}

        @Override
        public void onProductDisconnect() {
            notifyStatusChange();
        }

        @Override
        public void onProductConnect(BaseProduct newProduct) {
            // Keep same product (real drone or dummy) selected in the ConnectionActivity
            //mProduct = newProduct;

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
            // Keep same product (real drone or dummy) selected in the ConnectionActivity
            //mProduct = baseProduct;

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

            String zipName = "RD_LOG_" + DateFormat.format("yyyy-MM-dd-hh:mm:ss", new Date());
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
                return this.pluginManager.onMenuItemClick(item.getItemId());
        }
        return true;
    }

    public void onSmallMapClick(View v) {
        LinearLayout map_layout = findViewById(R.id.map_view);
        FrameLayout video_layout = findViewById(R.id.fragment_container);

        ViewGroup focusedLayout;
        ViewGroup previewLayout;
        switch (focusedView) {
            case Map: {
                focusedLayout = map_layout;
                previewLayout = video_layout;
                focusedView = FocusedView.VideoFeed;
                break;
            }
            case VideoFeed:
            default: {
                focusedLayout = video_layout;
                previewLayout = map_layout;
                focusedView = FocusedView.Map;
                break;
            }
        }

        logMessageDJI("Swap the map and the video feed...");

        int previewWidth = previewLayout.getWidth();
        int previewHeight = previewLayout.getHeight();

        LayoutParams focusedLayoutParams = focusedLayout.getLayoutParams();
        focusedLayoutParams.width = previewWidth;
        focusedLayoutParams.height = previewHeight;
        focusedLayout.setLayoutParams(focusedLayoutParams);
        focusedLayout.setZ(100.f);

        LayoutParams previewLayoutParams = previewLayout.getLayoutParams();
        previewLayoutParams.height = LayoutParams.WRAP_CONTENT;
        previewLayoutParams.width = LayoutParams.WRAP_CONTENT;
        previewLayout.setLayoutParams(previewLayoutParams);
        previewLayout.setZ(0.f);

        v.setZ(101.f);
        //updateDroneLocation();
    }

    // Hmm is this ever called...
    @Override
    public boolean onContextItemSelected(MenuItem item) {
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
        String videoIP;
        if (sharedPreferences.getBoolean("pref_separate_gcs", false)) {
            // Separate video from GCS IP
            videoIP = sharedPreferences.getString("pref_video_ip", "127.0.0.1");
        } else {
            videoIP = getGCSAddress();
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

        pluginManager.settingsChanged();
        changedSettings.clear();
    }

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

    public static class GCSCommunicatorAsyncTask extends AsyncTask<Integer, Integer, Integer> {

        private static final String TAG = GCSSenderTimerTask.class.getSimpleName();
        boolean request_renew_datalinks = false;
        private Timer timer;
        private WeakReference<MainActivity> mainActivityWeakReference;

        GCSCommunicatorAsyncTask(MainActivity mainActivity) {
            mainActivityWeakReference = new WeakReference<>(mainActivity);
        }

        void renewDatalinks() {
            request_renew_datalinks = true;
            FLAG_TELEMETRY_ADDRESS_CHANGED = false;
        }

        public class Listener extends Thread {
            private final MainActivity mainActivity;
            private final MAVLinkConnection connection;
            public boolean close = false;

            Listener(MAVLinkConnection connection, MainActivity mainActivity) {
                this.mainActivity = mainActivity;
                this.connection = connection;
            }

            @Override
            public void run() {
                while (!isCancelled()) {
                    // Listen for packets
                    try {
                        // TODO: Process request_renew_datalinks only in main Listener thread
                        if (request_renew_datalinks) {
                            request_renew_datalinks = false;
                            createTelemetrySockets(); // renew
                        }

                        if (System.currentTimeMillis() - this.mainActivity.mMavlinkReceiver.getTimestampLastGCSHeartbeat() <= this.mainActivity.GCS_TIMEOUT_mSEC) {
                            if (!this.mainActivity.shouldConnect) {
                                this.mainActivity.shouldConnect = true;
                                this.mainActivity.connectivityHasChanged = true;
                            }
                        } else {
                            // Timed out
                            if (this.mainActivity.shouldConnect) {
                                this.mainActivity.shouldConnect = false;
                                this.mainActivity.connectivityHasChanged = true;
                            }
                        }

                        if (this.mainActivity.connectivityHasChanged) {
                            if (this.mainActivity.shouldConnect) {
                                final Drawable connectedDrawable = this.mainActivity.getResources().getDrawable(R.drawable.ic_baseline_connected_24px, null);

                                this.mainActivity.runOnUiThread(() -> {
                                    ImageView imageView = this.mainActivity.findViewById(R.id.gcs_conn);
                                    imageView.setBackground(connectedDrawable);
                                    imageView.invalidate();
                                });
                            } else {
                                final Drawable disconnectedDrawable = this.mainActivity.getResources().getDrawable(R.drawable.ic_outline_disconnected_24px, null);

                                this.mainActivity.runOnUiThread(() -> {
                                    ImageView imageView = this.mainActivity.findViewById(R.id.gcs_conn);
                                    imageView.setBackground(disconnectedDrawable);
                                    imageView.invalidate();
                                });
                            }

                            this.mainActivity.connectivityHasChanged = false;
                        }

                        byte[] buf = new byte[1000];
                        DatagramPacket dp = new DatagramPacket(buf, buf.length);

                        // Listen on random src port
                        //this.mainActivity.mMavlinkReceiver.mavLinkConnections.get(0).socket.receive(dp);
                        connection.socket.receive(dp);

                        if(close) {
                            Log.i(TAG, "Listener closed");
                            return;
                        }

                        byte[] bytes = dp.getData();
                        int[] ints = new int[bytes.length];
                        for (int i = 0; i < bytes.length; i++)
                            ints[i] = bytes[i] & 0xff;

                        synchronized (this.mainActivity) { // this.mainActivity.mMavlinkParser crashes if used concurrently by two threads. TODO: Instantiate multiple parsers?
                            for (int i = 0; i < bytes.length; i++) {
                                MAVLinkPacket packet = this.mainActivity.mMavlinkParser.mavlink_parse_char(ints[i]);

                                if (packet != null) {
                                    MAVLinkMessage msg = packet.unpack();
                                    if (this.mainActivity.prefs.getBoolean("pref_log_mavlink", false))
                                        this.mainActivity.logMessageFromGCS(msg.toString());

                                    this.mainActivity.mMavlinkReceiver.process(msg);
                                }
                            }
                        }

                    } catch(SocketTimeoutException e) {
                        // Timeout
                    } catch(PortUnreachableException e) {
                        // Remote side is not receiving telemetry
                    } catch (IOException e) {
                        Log.e(TAG, "IOException: " + e.toString());
                        //logMessageDJI("IOException: " + e.toString());
                    }
                }
            }
        }

        @Override
        protected Integer doInBackground(Integer... ints2) {
            try {
                createTelemetrySockets();

                MainActivity mainActivityRef = mainActivityWeakReference.get();
                mainActivityRef.mMavlinkParser = new Parser();

                GCSSenderTimerTask gcsSender = new GCSSenderTimerTask(mainActivityWeakReference);
                timer = new Timer(true);
                timer.scheduleAtFixedRate(gcsSender, 0, 100);

                for(MAVLinkConnection mavLinkConnection : mainActivityRef.mMavlinkReceiver.mavLinkConnections) {
                    Listener listener = new Listener(mavLinkConnection, mainActivityRef);
                    mavLinkConnection.listen(listener);
                }

                for(MAVLinkConnection mavLinkConnection : mainActivityRef.mMavlinkReceiver.mavLinkConnections) {
                    mavLinkConnection.listener.join();
                }

            } catch (Exception e) {
                Log.d(TAG, "exception", e);
            } finally {
                /*
                if (mainActivityWeakReference.get().socket.isConnected()) {
                    mainActivityWeakReference.get().socket.disconnect();
                }
                */
                if (timer != null) {
                    timer.cancel();
                }
            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            // TODO: Not sure what to do here...
            if (mainActivityWeakReference.get() == null || mainActivityWeakReference.get().isFinishing()) return;
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

        private void createTelemetrySockets() {
            MainActivity mainActivityRef = mainActivityWeakReference.get();
            close();

            String gcsIPString = mainActivityWeakReference.get().getGCSAddress();

            int telemIPPort = Integer.parseInt(Objects.requireNonNull(mainActivityWeakReference.get().prefs.getString("pref_telem_port", "14550")));
            mainActivityRef.mMavlinkReceiver.mavLinkConnections.add(new MAVLinkConnection(gcsIPString, telemIPPort));

            if (mainActivityRef.prefs.getBoolean("pref_secondary_telemetry_enabled", false)) {
                String secondaryIP = mainActivityRef.prefs.getString("pref_secondary_telemetry_ip", "127.0.0.1");
                int secondaryPort = Integer.parseInt(Objects.requireNonNull(mainActivityRef.prefs.getString("pref_secondary_telemetry_port", "18990")));

                mainActivityRef.mMavlinkReceiver.mavLinkConnections.add(new MAVLinkConnection(secondaryIP, secondaryPort));
            }

            if (mainActivityRef.prefs.getBoolean("pref_telemetry_3_enabled", false)) {
                String host = mainActivityRef.prefs.getString("pref_telemetry_3_host", "");
                int port = Integer.parseInt(Objects.requireNonNull(mainActivityRef.prefs.getString("pref_telemetry_3_port", "")));
                mainActivityRef.mMavlinkReceiver.mavLinkConnections.add(new MAVLinkConnection(host, port));
            }

            if (mainActivityRef.prefs.getBoolean("pref_telemetry_4_enabled", false)) {
                String host = mainActivityRef.prefs.getString("pref_telemetry_4_host", "");
                int port = Integer.parseInt(Objects.requireNonNull(mainActivityRef.prefs.getString("pref_telemetry_4_port", "")));
                mainActivityRef.mMavlinkReceiver.mavLinkConnections.add(new MAVLinkConnection(host, port));
            }
        }

        protected void close() {
            MainActivity mainActivityRef = mainActivityWeakReference.get();
            if (mainActivityRef.mModel != null) {
                for(MAVLinkConnection mavLinkConnection : mainActivityRef.mMavlinkReceiver.mavLinkConnections) {
                    Thread thread = new Thread(() -> {
                        mavLinkConnection.close();
                        mainActivityRef.mMavlinkReceiver.mavLinkConnections.remove(mavLinkConnection);
                    });
                    thread.start();
                }
            }
        }
    }

    //---------------------------------------------------------------------------------------
    // Receive alternative H.264 video data, mostly for debugging or simulation...
    private void startVideoLoop(int port){
        if(!mExtRunning) {
            mExtRunning = true;
            runOnUiThread(() -> {
                try {
                    DatagramSocket clientsocket = new DatagramSocket(port);
                    byte[] receivedata = new byte[1024 * 64];
                    while (true) {
                        DatagramPacket recv_packet = new DatagramPacket(receivedata, receivedata.length);
                        clientsocket.receive(recv_packet);
                        if (mCodecManager != null) {
                            mCodecManager.sendDataToDecoder(recv_packet.getData(), recv_packet.getData().length);
                        }
                        // Send raw H264 to the FFMPEG parser...
                        NativeHelper.getInstance().parse(recv_packet.getData(), recv_packet.getData().length, 0);
                    }
                } catch (Exception e) {
                    Log.e("UDP", "S: Error", e);
                }
            });
            mExtRunning = false;
        }
    }
    // --------------------------------------------------------------------------------------------
    // To be moved later for better structure....
    private boolean isTranscodedVideoFeedNeeded() {
        if (VideoFeeder.getInstance() == null) {
            return false;
        }
        return VideoFeeder.getInstance().isFetchKeyFrameNeeded() || VideoFeeder.getInstance()
                .isLensDistortionCalibrationNeeded();
    }
}
