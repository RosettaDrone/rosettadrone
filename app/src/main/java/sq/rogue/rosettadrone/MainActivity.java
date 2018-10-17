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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.Parser;

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
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.midware.natives.SDKRelativeJNI;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import sq.rogue.rosettadrone.logs.LogFragment;
import sq.rogue.rosettadrone.settings.SettingsActivity;
import sq.rogue.rosettadrone.video.VideoService;

import static android.support.design.widget.Snackbar.LENGTH_LONG;
import static sq.rogue.rosettadrone.util.safeSleep;
import static sq.rogue.rosettadrone.video.VideoService.ACTION_DRONE_CONNECTED;
import static sq.rogue.rosettadrone.video.VideoService.ACTION_DRONE_DISCONNECTED;
import static sq.rogue.rosettadrone.video.VideoService.ACTION_RESTART;
import static sq.rogue.rosettadrone.video.VideoService.ACTION_SET_MODEL;
import static sq.rogue.rosettadrone.video.VideoService.ACTION_START;
import static sq.rogue.rosettadrone.video.VideoService.ACTION_STOP;

public class MainActivity extends AppCompatActivity {

    public static final String FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change";
    private final static int RESULT_SETTINGS = 1001;
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
    private final int GCS_TIMEOUT_mSEC = 2000;
    private boolean permissionGranted = false;
    private Handler mDJIHandler;
    private Handler mUIHandler;
    private CheckBox mSafety;
    private TextView mDroneDetails;
    private FragmentManager fragmentManager;
    private LogFragment logDJI;
    private LogFragment logOutbound;
    private LogFragment logInbound;
    private BottomNavigationView mBottomNavigation;
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

//            invalidateOptionsMenu();
            mUIHandler.postDelayed(this, 500);
        }
    };

    private DJISDKManager.SDKManagerCallback mDJISDKManagerCallback = new DJISDKManager.SDKManagerCallback() {

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
        public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent, BaseComponent newComponent) {
            if (newComponent != null) {
                newComponent.setComponentListener(new BaseComponent.ComponentListener() {
                    @Override
                    public void onConnectivityChange(boolean isConnected) {
                        Log.d(TAG, "onComponentConnectivityChanged: " + isConnected);
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
                        //Toast.makeText(getApplicationContext(), "DJI SDK registered", Toast.LENGTH_LONG).show();
                        logMessageDJI("DJI SDK registered");
                    }
                });
            } else {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        logMessageDJI("DJI SDK registration failed");
                        //Toast.makeText(getApplicationContext(), "DJI SDK registration failed", Toast.LENGTH_LONG).show();
                    }
                });
            }
            if (error != null) {
                Log.e(TAG, error.toString());
            }
        }

    };

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        String action = intent.getAction();
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
            Intent attachedIntent = new Intent();
            attachedIntent.setAction(DJISDKManager.USB_ACCESSORY_ATTACHED);
            sendBroadcast(attachedIntent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String versionName = "";
        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        getSupportActionBar().setTitle("Rosetta Drone " + versionName);

        requestPermissions();

        if (savedInstanceState != null) {
            navState = savedInstanceState.getInt("navigation_state");
        }

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);


        deleteApplicationDirectory();

        if (savedInstanceState != null) {
            navState = savedInstanceState.getInt("navigation_state");
        }

        initLogs();
        initBottomNav();

        mModel = new DroneModel(this, null);
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
    }

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
        navState = mBottomNavigation.getSelectedItemId();
        outState.putInt("navigation_state", navState);
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

    private void requestPermissions() {
        // When the compile and target version is higher than 22, please request the following permission at runtime to ensure the SDK works well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean permissionGrantedForAll = false;
        if (grantResults.length > 0 && permissions.length == grantResults.length) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    permissionGrantedForAll = true;
                } else {
                    permissionGrantedForAll = false;
                }
            }

        } else {
            permissionGrantedForAll = true;
        }
        if (permissionGrantedForAll) {
            permissionGranted = true;
        } else {
            permissionGranted = false;
            finish();
        }

        DJISDKManager.getInstance().registerApp(this, mDJISDKManagerCallback);

//        invalidateOptionsMenu();
    }

    /**
     * onResume is called whenever RosettaDrone takes the foreground. This happens if we open the
     * RosettaDrone application or return from the preferences window. Need to rebind to the AIDL service.
     */
    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");

        if (mDroneDetails != null) {
            String droneID = prefs.getString("pref_drone_id", "1");
            String rtlAlt = prefs.getString("pref_drone_rtl_altitude", "60") + "m";
            String text = "ID:" + "\t" + droneID + System.getProperty("line.separator") + "RTL:" + "\t" + rtlAlt;
            mDroneDetails.setText(text);
        }

        super.onResume();
    }

    /**
     * Called whenever RosettaDrone leaves the foreground, such as when the home button is pressed or
     * we enter the preferences/settings screen. We unbind the AIDL service since we don't need it if we're not
     * currently within RosettaDrone.
     * NOTE: Pressing the back button from the RosettaDrone application also calls onDestroy.
     */
    @Override
    protected void onPause() {
//        Log.d(TAG, "onPause()");
        super.onPause();
        // We have to save text when onPause is called or it will be erased
//        mNewOutbound = logToGCS.getLogText() + mNewOutbound;
//        mNewInbound = logFromGCS.getLogText() + mNewInbound;
//        mNewDJI = logDJI.getLogText() + mNewDJI;
    }

    @Override
    protected void onStop() {
//        Log.i(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
//        Log.i(TAG, "onDestroy");
//        logMessageDJI("onDestroy()");
        sendDroneDisconnected();
        closeGCSCommunicator();

        if (mUIHandler != null) {
            mUIHandler.removeCallbacksAndMessages(null);
        }

        if (mDJIHandler != null) {
            mDJIHandler.removeCallbacksAndMessages(null);
        }

        super.onDestroy();
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.toolbar_menu, menu);

        MenuItem droneItem = menu.findItem(R.id.drone_details);
        mDroneDetails = (TextView) droneItem.getActionView();

        mDroneDetails.setTextSize(12);

        String droneID = prefs.getString("pref_drone_id", "1");
        String rtlAlt = prefs.getString("pref_drone_rtl_altitude", "60") + "m";
        String text = "ID:" + "\t" + droneID + System.getProperty("line.separator") + "RTL:" + "\t" + rtlAlt;
        mDroneDetails.setText(text);

        mDroneDetails.setPadding(mDroneDetails.getPaddingLeft(),
                mDroneDetails.getPaddingTop(),
                mDroneDetails.getPaddingRight() + (int) (15.0f * getResources().getDisplayMetrics().density + 2.0f),
                mDroneDetails.getPaddingBottom());

        MenuItem safetyItem = menu.findItem(R.id.action_safety);
        mSafety = (CheckBox) safetyItem.getActionView();
        mSafety.setButtonDrawable(R.color.safety);
        mSafety.setPadding(mSafety.getPaddingLeft(),
                mSafety.getPaddingTop(),
                mSafety.getPaddingRight() + (int) (10.0f * getResources().getDisplayMetrics().density + 0.5f),
                mSafety.getPaddingBottom());

        //Make sure default is safety enabled
        mModel.setSafetyEnabled(true);
        mSafety.setChecked(true);

        mSafety.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mModel.setSafetyEnabled(isChecked);
                NotificationHandler.notifySnackbar(findViewById(R.id.snack),
                        (mModel.isSafetyEnabled()) ? R.string.safety_on : R.string.safety_off, LENGTH_LONG);
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
//        Log.d(TAG, "menu item selected");
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_safety_switch:
//                Log.d(TAG, "ACTION_SAFETY_SWITCH");
                return true;
            case R.id.action_safety:
//                Log.d(TAG, "ACTION_SAFETY");
//                NotificationHandler.notifySnackbar(bottomNavigationView, R.string.safety, LENGTH_LONG);
                return true;
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
                break;
            default:
//                Log.d(TAG, String.valueOf(item.getItemId()));
                return super.onOptionsItemSelected(item);
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

        if (prefs.getBoolean("pref_enable_video", false)) {
            sendDroneConnected();
        } else {
            sendDroneDisconnected();
        }

        final Drawable connectedDrawable = getResources().getDrawable(R.drawable.ic_baseline_connected_24px);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ImageView djiImageView = findViewById(R.id.dji_conn);
//                imageView.setImageResource(R.drawable.ic_baseline_connected_24px);
                djiImageView.setForeground(connectedDrawable);
                djiImageView.invalidate();
            }
        });

    }

    private void onDroneDisconnected() {
        final Drawable disconnectedDrawable = getResources().getDrawable(R.drawable.ic_outline_disconnected_24px);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ImageView imageView = findViewById(R.id.dji_conn);
//                imageView.setImageResource(R.drawable.ic_baseline_connected_24px);
                imageView.setForeground(disconnectedDrawable);
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

    private void notifyStatusChange() {
        mDJIHandler.removeCallbacks(djiUpdateRunnable);
//        Log.d(TAG, "notifyStatusChange()");
        mDJIHandler.postDelayed(djiUpdateRunnable, 500);
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
    private void sendStartVideoService() {
        Intent intent = setupIntent(ACTION_START);
        sendIntent(intent);
    }

    /**
     *
     */
    private void sendStopVideoService() {
        Intent intent = setupIntent(ACTION_STOP);
        sendIntent(intent);
    }

    /**
     *
     */
    private void sendRestartVideoService() {
        String videoIP = getVideoIP();

        int videoPort = Integer.parseInt(prefs.getString("pref_video_port", "5600"));

        logMessageDJI("Restarting Video link to " + videoIP + ":" + videoPort);
        Intent intent = setupIntent(ACTION_RESTART);
        intent.putExtra("model", mProduct.getModel());
        sendIntent(intent);
    }

    /**
     *
     */
    private void sendSetBackingMode() {
        Intent intent = setupIntent(ACTION_SET_MODEL);
        sendIntent(intent);
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

        Intent intent = setupIntent(ACTION_DRONE_CONNECTED);
        intent.putExtra("model", mProduct.getModel());
        sendIntent(intent);
    }

    /**
     *
     */
    private void sendDroneDisconnected() {
        Intent intent = setupIntent(ACTION_DRONE_DISCONNECTED);
        sendIntent(intent);
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

    /**
     * @param action
     * @param extras
     * @return
     */
    private Intent setupIntent(String action, Object... extras) {
        Intent intent = new Intent(this, VideoService.class);
        intent.setAction(action);

//        for (Object extra : extras) {
////            intent.putExtra()
//        }

        return intent;
    }

    /**
     * @param intent
     */
    private void sendIntent(Intent intent) {
//        Log.d(TAG, "sendIntent");
        if (intent != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    startService(intent);
                } catch (IllegalStateException e) {
                    startForegroundService(intent);
                }
            } else {
                startService(intent);
            }
        }
    }

    //---------------------------------------------------------------------------------------
    //endregion

    @Override
    protected void onStart() {
        super.onStart();
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
                                final Drawable connectedDrawable = mainActivityWeakReference.get().getResources().getDrawable(R.drawable.ic_baseline_connected_24px);

                                mainActivityWeakReference.get().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ImageView imageView = mainActivityWeakReference.get().findViewById(R.id.gcs_conn);
                                        imageView.setForeground(connectedDrawable);
                                        imageView.invalidate();
                                    }
                                });
                            } else {
                                final Drawable disconnectedDrawable = mainActivityWeakReference.get().getResources().getDrawable(R.drawable.ic_outline_disconnected_24px);

                                mainActivityWeakReference.get().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ImageView imageView = mainActivityWeakReference.get().findViewById(R.id.gcs_conn);
                                        imageView.setForeground(disconnectedDrawable);
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
                    imageView.setForeground(disconnectedDrawable);
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
    //endregion

}
