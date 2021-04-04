package sq.rogue.rosettadrone;

// Acknowledgements:
// IP address validation: https://stackoverflow.com/questions/3698034/validating-ip-in-android/11545229#11545229
// Hide keyboard: https://stackoverflow.com/questions/16495440/how-to-hide-keyboard-by-default-and-show-only-when-click-on-edittext
// MenuItemTetColor: RPP @ https://stackoverflow.com/questions/31713628/change-menuitem-text-color-programmatically

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
import com.google.android.gms.maps.model.BitmapDescriptor;
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
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.media.DownloadListener;
import dji.sdk.media.FetchMediaTaskScheduler;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;
import sq.rogue.rosettadrone.logs.LogFragment;
import sq.rogue.rosettadrone.settings.GMailSender;
import sq.rogue.rosettadrone.settings.MailReport;
import sq.rogue.rosettadrone.settings.SettingsActivity;
import sq.rogue.rosettadrone.settings.Waypoint1Activity;
import sq.rogue.rosettadrone.settings.Waypoint2Activity;
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
    private SharedPreferences prefs;
    private String mNewOutbound = "";
    private String mNewInbound = "";
    private String mNewDJI = "";
    private DatagramSocket socket;
    public  DroneModel mModel;
    private Boolean mExtRunning = false;
    private LocationManager locationManager;
    private double m_host_lat = -500;
    private double m_host_lon = -500;

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
    private TextureView videostreamPreviewTtView;
    private TextureView videostreamPreviewTtViewSmall;
    private DJICodecManager mCodecManager;
    private int videoViewWidth;
    private int videoViewHeight;
    protected SharedPreferences sharedPreferences;
    private boolean mIsTranscodedVideoFeedNeeded = false;
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

    //-----------------------------------------------------------------------------//
    private Runnable djiUpdateRunnable = () -> {
        Intent intent = new Intent(DJISimulatorApplication.FLAG_CONNECTION_CHANGE);
        sendBroadcast(intent);
    };

    //-----------------------------------------------------------------------------
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

    //-----------------------------------------------------------------------------
    private void initPacketizer() {
        Log.e(TAG, "initPacketizer");
        String address = "127.0.0.1";

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = new MyLocationListener();

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing right to access GPS location!");
        }else {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
        }

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
            VideoFeeder.getInstance().setTranscodingDataRate(mVideoBitrate);
            logMessageDJI("set rate to " + mVideoBitrate);
        }

        //------------------------------------------------------------
        videostreamPreviewTtView = findViewById(R.id.livestream_preview_ttv);
        videostreamPreviewTtViewSmall = findViewById(R.id.livestream_preview_ttv_small);
        videostreamPreviewTtView.setVisibility(View.VISIBLE);

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

        videostreamPreviewTtView.setSurfaceTextureListener(mSurfaceTextureListener);
        if ( mModel.m_camera != null) {
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
        super.onPause();
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

        aMap.setOnMapClickListener((point)-> {

            Log.d(TAG, "Goto: " + point.toString());
                AlertDialog.Builder alertDialog2 = new AlertDialog.Builder(this);
                alertDialog2.setIcon(R.mipmap.track_right);
                alertDialog2.setTitle("AI Mavlink/Python function selector!");
                alertDialog2.setMessage("Clikked");
                alertDialog2.setNegativeButton("Cancel",
                        (dialog, which) -> {
                            dialog.cancel();
                        });
                alertDialog2.setNeutralButton("Add to Waypoint list",
                        (dialog, which) -> {
                            markWaypoint(point, true);
                            Waypoint mWaypoint = new Waypoint(point.latitude, point.longitude,  mModel.m_alt);
                            //Add Waypoints to Waypoint arraylist;
                            if (waypointMissionBuilder != null) {
                                waypointList.add(mWaypoint);
                                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
                            } else {
                                waypointMissionBuilder = new WaypointMission.Builder();
                                waypointList.add(mWaypoint);
                                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
                            }
                            dialog.cancel();
                        });
                alertDialog2.setPositiveButton ("Accept",
                        (dialog, which) -> {
                            // If we are airborn...
                            if( mModel.m_alt > -5.0) {
                                markWaypoint(point, false);
                                mModel.goto_position(point.latitude, point.longitude, mModel.m_alt, 0);
                                dialog.cancel();
                            }else {
                                Log.d(TAG, "Cannot Add Waypoint");
                                Toast.makeText(getApplicationContext(), "Cannot goto position!!!", Toast.LENGTH_LONG).show();
                            }
                    });
                ///
                this.runOnUiThread(() -> {
                    alertDialog2.show();
                });
         });

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

                if (compare_height == 0) {
                    smallMarker = Bitmap.createScaledBitmap(bitmapdraw.getBitmap(), 32, 32, false);
                }else{
                    smallMarker = Bitmap.createScaledBitmap(bitmapdraw.getBitmap(), 64, 64, false);
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

            if (compare_height == 0) {
                smallMarker = Bitmap.createScaledBitmap(bitmapdraw.getBitmap(), 32, 32, false);
            }else{
                smallMarker = Bitmap.createScaledBitmap(bitmapdraw.getBitmap(), 64, 64, false);
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

    private void initFlightController() {
        if (mModel.mFlightController != null) {
            mModel.mFlightController.setStateCallback(
                djiFlightControllerCurrentState -> {
                    droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                    droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();

                    if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                        // If we got no data from GCS then use the initial Drone location
                        if (m_host_lat == -500 && m_host_lon == -500) {
                            m_host_lat = droneLocationLat;
                            m_host_lon = droneLocationLng+0.00005;
                        }
                        updateDroneLocation();
                    }
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
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        mProduct = RDApplication.getProductInstance(); // Should be set by Connection ...
        if(mProduct != null){
            try {
                mProductModel = mProduct.getModel();
            }catch(Exception e){
                Log.d(TAG, "exception", e);
                mProductModel = Model.MAVIC_PRO; // Just a dummy value should we be in test mode... (No Target)
            }
        }else{
            // We need to make this; if no device or if sin mode...
            // Will support alternative video source...
            Log.d(TAG, "Starting external video input...");
            int port = Integer.parseInt(Objects.requireNonNull(prefs.getString("pref_external_video", "0")));
            if(port > 1000) {
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

        //------------------------------------------------------------
        mModel = new DroneModel(this, null, RDApplication.getSim());
        mModel.setSystemId(Integer.parseInt(Objects.requireNonNull(prefs.getString("pref_drone_id", "1"))));

        //--------------------------------------------------------------
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
        // Make the Report button....
     //   Button mBtnRepport = findViewById(R.id.btn_Report);
     //   mBtnRepport.setOnClickListener(v -> SetMesasageBox("com.example.sendmail",1));
        //--------------------------------------------------------------
        // Make the AI button....
        Button mBtnAI = findViewById(R.id.btn_AI_start);
        mBtnAI.setOnClickListener(v -> SetMesasageBox("com.example.remoteconfig3",2));
        //--------------------------------------------------------------
        // Disable takeoff by default... This however it not how DJI does it, so we must delay this action...
        Handler mTimerHandler = new Handler(Looper.getMainLooper());
        mTimerHandler.postDelayed(enablesafety, 3000);
    }

    // If AI button is pressed then start the AI Pluggin ...
    protected void SetMesasageBox(String msg, int type) {
        switch (type){
            case 1:
                //--------------------------------------------------------------
                logMessageDJI("Init media manager and fetch image...");
                mModel.getfile();
                break;
            case 2:
                Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                startActivity(msg);
                break;
        }
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
            if (m_videoMode == 2) {
                if (mCodecManager != null) {
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                }
                // Send raw H264 to the FFMPEG parser...
                if (mExternalVideoOut == true) {
                    NativeHelper.getInstance().parse(videoBuffer, size, 0);
                }
            } else {
                // Send H.264 to the NAIL generator...
                if (mExternalVideoOut == true) {
                    NativeHelper.getInstance().parse(videoBuffer, size, 1);
                }
            }
        };

        if (null == product || !product.isConnected()) {
            mModel.m_camera = null; // Hmm to be investigated...
        } else {
            // List all models that needs alternative decoding...
            if (validateTranscodingMethod(product.getModel()) == true) {
                m_videoMode = 2;
            }

            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {

                if (mModel.m_camera != null) {

                    mModel.m_camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, djiError -> {
                        if (djiError != null) {
                            Log.e(TAG, "can't change mode of camera, error: " + djiError.getDescription());
                            logMessageDJI("can't change mode of camera, error: " + djiError.getDescription());
                        }
                    });
                    if (product.getModel().equals(Model.MAVIC_AIR_2)){
                        product.getCamera()
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


    // Start the AI Pluggin (Developed by the customers...)
    public boolean startActivity(String pluggin)
    {
        Intent intent = getPackageManager().getLaunchIntentForPackage(pluggin);
        if (intent == null) {
            // Bring user to the market or let them choose an app?
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=" + pluggin));
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
            case MAVIC_AIR_2:       // Verified...
                return true;
        }

        // Mavic 2 Pro              // Verified...
        // Mavic 2 Zoom             // Verified...
        // Matrice 210 V2 RTK       // Verified...
        return false;
    }

    /**
     * Init a fake texture view to for the codec manager, so that the video raw data can be received
     * by the camera needed to get video to the UDP handler...
     */

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "real onSurfaceTextureAvailable: width " + width + " height " + height);
            if (compare_height == 1) {
                height = ((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 86, getResources().getDisplayMetrics()));
                width = ((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 164, getResources().getDisplayMetrics()));

            }
            videoViewWidth = width;
            videoViewHeight = height;

            Log.d(TAG, "real onSurfaceTextureAvailable: width " + videoViewWidth + " height " + videoViewHeight + " Mode: " + compare_height);
            if (mCodecManager == null) {
                mCodecManager = new DJICodecManager(getApplicationContext(), surface, width, height);
            } else {
                mCodecManager.changeOutputSurface(surface);
                mCodecManager.onSurfaceSizeChanged(width, height, 0);
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
            videostreamPreviewTtView.setVisibility(View.GONE);

            //    safeSleep(200);
            videostreamPreviewTtViewSmall.setSurfaceTextureListener(mSurfaceTextureListener);
            videostreamPreviewTtViewSmall.setVisibility(View.VISIBLE);
            video_layout_small.setZ(100.f);

            // MAP ok...
            map_layout.setZ(0.f);
            map_para.height = LayoutParams.WRAP_CONTENT;
            map_para.width = LayoutParams.WRAP_CONTENT;
            map_layout.setLayoutParams(map_para);

            if (mCodecManager != null) {
                mCodecManager.changeOutputSurface(videostreamPreviewTtViewSmall.getSurfaceTexture());
                mCodecManager.onSurfaceSizeChanged(videostreamPreviewTtViewSmall.getWidth(), videostreamPreviewTtViewSmall.getHeight(), 0);
            }

            compare_height = 1;
        } else {
            logMessageDJI("Set Main screen...");
            videostreamPreviewTtViewSmall.clearFocus();
            videostreamPreviewTtViewSmall.setVisibility(View.GONE);

            //         safeSleep(200);
            videostreamPreviewTtView.setSurfaceTextureListener(mSurfaceTextureListener);
            videostreamPreviewTtView.setVisibility(View.VISIBLE);
            video_layout_small.setZ(0.f);

            //MAP OK...
            map_layout.setZ(100.f);
            map_para.height = ((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 86, getResources().getDisplayMetrics()));
            map_para.width = ((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 164, getResources().getDisplayMetrics()));
            map_layout.setLayoutParams(map_para);
            map_layout.setBottom(((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics())));
            map_layout.setLeft(((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, getResources().getDisplayMetrics())));

            if (mCodecManager != null) {
                mCodecManager.changeOutputSurface(videostreamPreviewTtView.getSurfaceTexture());
                mCodecManager.onSurfaceSizeChanged(videostreamPreviewTtView.getWidth(), videostreamPreviewTtView.getHeight(), 0);
            }

            compare_height = 0;
        }
        v.setZ(101.f);
  //      updateDroneLocation();
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
