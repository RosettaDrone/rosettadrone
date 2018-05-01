package sq.rogue.rosettadrone;

// Acknowledgements:
// IP address validation: https://stackoverflow.com/questions/3698034/validating-ip-in-android/11545229#11545229
// Hide keyboard: https://stackoverflow.com/questions/16495440/how-to-hide-keyboard-by-default-and-show-only-when-click-on-edittext
// MenuItemTetColor: RPP @ https://stackoverflow.com/questions/31713628/change-menuitem-text-color-programmatically

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.Parser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.product.Model;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import sq.rogue.rosettadrone.logs.LogFragment;
import sq.rogue.rosettadrone.logs.LogPagerAdapter;
import sq.rogue.rosettadrone.settings.SettingsActivity;
import sq.rogue.rosettadrone.video.DJIVideoStreamDecoder;
import sq.rogue.rosettadrone.video.H264Packetizer;
import sq.rogue.rosettadrone.video.NativeHelper;
import sq.rogue.rosettadrone.video.VideoService;
import static sq.rogue.rosettadrone.util.safeSleep;
import static sq.rogue.rosettadrone.video.VideoService.ACTION_DRONE_CONNECTED;
import static sq.rogue.rosettadrone.video.VideoService.ACTION_DRONE_DISCONNECTED;
import static sq.rogue.rosettadrone.video.VideoService.ACTION_RESTART;
import static sq.rogue.rosettadrone.video.VideoService.ACTION_SEND_NAL;
import static sq.rogue.rosettadrone.video.VideoService.ACTION_SET_MODEL;
import static sq.rogue.rosettadrone.video.VideoService.ACTION_START;
import static sq.rogue.rosettadrone.video.VideoService.ACTION_STOP;

public class MainActivity extends AppCompatActivity {

    public static final String FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change";
    private final static int RESULT_SETTINGS = 1001;
    private static BaseProduct mProduct;
    private final String TAG = "RosettaDrone";
    private final int GCS_TIMEOUT_mSEC = 2000;
    private Handler mDJIHandler;
    private Handler mUIHandler;
    private Button mButtonClear;
    private ToggleButton toggleBtnArming;

    private LogFragment logDJI;
    private LogFragment logToGCS;
    private LogFragment logFromGCS;
    private TabLayout tabLayout;
    private ViewPager viewPager;
    private LogPagerAdapter adapter;

    private SharedPreferences prefs;


    private String mNewOutbound = "";
    private String mNewInbound = "";
    private String mNewDJI = "";

    private DatagramSocket socket;

    private DroneModel mModel;
    private MAVLinkReceiver mMavlinkReceiver;
    private Parser mMavlinkParser;
    private GCSCommunicatorAsyncTask mGCSCommunicator;

    private Runnable RunnableUpdateUI = new Runnable() {
        // We have to update UI from here because we can't update the UI from the
        // drone/GCS handling threads

        @Override
        public void run() {

            try {
                if (!mNewOutbound.equals("")) {
                    //((LogFragment)adapter.getItem(0)).appendLogText(mNewOutbound);
                    logToGCS.appendLogText(mNewOutbound);
                    mNewOutbound = "";
                }
                if (!mNewInbound.equals("")) {
                    //((LogFragment)adapter.getItem(1)).appendLogText(mNewOutbound);
                    logFromGCS.appendLogText(mNewInbound);
                    mNewInbound = "";
                }
                if (!mNewDJI.equals("")) {
                    //((LogFragment)adapter.getItem(2)).appendLogText(mNewOutbound);
                    logDJI.appendLogText(mNewDJI);
                    mNewDJI = "";
                }
                if (mModel != null) {
                    if (mModel.isSafetyEnabled())
                        toggleBtnArming.setChecked(true);
                    else
                        toggleBtnArming.setChecked(false);
                } else
                    toggleBtnArming.setChecked(false);
                invalidateOptionsMenu();
            } catch (Exception e) {
                Log.d(TAG, "exception", e);
            }
            mUIHandler.postDelayed(this, 100);
        }
    };
    private Runnable djiUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
            sendBroadcast(intent);
        }
    };
    private BaseComponent.ComponentListener mDJIComponentListener = new BaseComponent.ComponentListener() {
        @Override
        public void onConnectivityChange(boolean isConnected) {
            notifyStatusChange();
        }
    };
    private BaseProduct.BaseProductListener mDJIBaseProductListener = new BaseProduct.BaseProductListener() {
        @Override
        public void onComponentChange(BaseProduct.ComponentKey key, BaseComponent oldComponent, BaseComponent newComponent) {
            Log.d(TAG, "onComponentChange()");
            if (newComponent != null) {
                newComponent.setComponentListener(mDJIComponentListener);
            }
            notifyStatusChange();
        }

        @Override
        public void onConnectivityChange(boolean isConnected) {
            Log.d(TAG, "onConnectivityChange()");
            logMessageDJI("onConnectivityChange()");
            if (isConnected)
                onDroneConnected();
            else
                onDroneDisconnected();

            notifyStatusChange();
        }
    };
    private DJISDKManager.SDKManagerCallback mDJISDKManagerCallback = new DJISDKManager.SDKManagerCallback() {
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

        @Override
        public void onProductChange(BaseProduct oldProduct, BaseProduct newProduct) {
            Log.d(TAG, "onProductChange()");
            logMessageDJI("onProductChange()");

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
                mProduct.setBaseProductListener(mDJIBaseProductListener);
            }

            notifyStatusChange();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String versionName = "";
        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        getSupportActionBar().setTitle("Rosetta Drone v" + versionName);


        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        requestPermissions();

        mButtonClear = (Button) findViewById(R.id.button_clear);
        viewPager = (ViewPager) findViewById(R.id.pager);
        toggleBtnArming = (ToggleButton) findViewById(R.id.toggBtnSafety);
        tabLayout = (TabLayout) findViewById(R.id.tab_layout);

        mButtonClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                logDJI.setLogText("");
                logFromGCS.setLogText("");
                logToGCS.setLogText("");
            }
        });

        deleteApplicationDirectory();


        toggleBtnArming.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mModel.setSafetyEnabled(true);
                    toggleBtnArming.setTextColor(Color.RED);
                } else {
                    mModel.setSafetyEnabled(false);
                    toggleBtnArming.setTextColor(Color.GREEN);
                }
            }
        });

        tabLayout.removeAllTabs();
        tabLayout.addTab(tabLayout.newTab().setText("DJI"));
        tabLayout.addTab(tabLayout.newTab().setText("To GCS"));
        tabLayout.addTab(tabLayout.newTab().setText("From GCS"));
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        LogFragment[] fragments = new LogFragment[3];
        if (savedInstanceState == null) {
            for (int i = 0; i < 3; i++)
                fragments[i] = new LogFragment();
        } else {
            for (int i = 0; i < 3; i++)
                fragments[i] = (LogFragment) getSupportFragmentManager().getFragments().get(i);
        }
        adapter = new LogPagerAdapter(fragments, getSupportFragmentManager());
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        viewPager.setOffscreenPageLimit(2);


        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        logDJI = (LogFragment) adapter.getItem(0);
        logToGCS = (LogFragment) adapter.getItem(1);
        logFromGCS = (LogFragment) adapter.getItem(2);

        mModel = new DroneModel(this, null);
        mMavlinkReceiver = new MAVLinkReceiver(this, mModel);
        loadMockParamFile();

        mDJIHandler = new Handler(Looper.getMainLooper());
        mUIHandler = new Handler(Looper.getMainLooper());
        mUIHandler.postDelayed(RunnableUpdateUI, 1000);

        //NativeHelper.getInstance().init();
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
        DJISDKManager.getInstance().registerApp(this, mDJISDKManagerCallback);
        invalidateOptionsMenu();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();
        // We have to save text when onPause is called or it will be erased
//        mNewOutbound = logToGCS.getLogText() + mNewOutbound;
//        mNewInbound = logFromGCS.getLogText() + mNewInbound;
//        mNewDJI = logDJI.getLogText() + mNewDJI;
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        logMessageDJI("onDestroy()");
        closeGCSCommunicator();

        mUIHandler.removeCallbacksAndMessages(null);
        mDJIHandler.removeCallbacksAndMessages(null);

//        if(VideoFeeder.getInstance() != null) {
//            if(VideoFeeder.getInstance().getPrimaryVideoFeed() != null)
//                VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(null);
//        }
//        mModel = null;

//        try { mModel.getDjiAircraft().getRemoteController().setHardwareStateCallback(null); }
//        catch(Exception e) {}
//        try { mModel.getDjiAircraft().getBattery().setStateCallback(null); }
//        catch(Exception e) {}
//        try { Battery.setAggregationStateCallback(null); }
//        catch(Exception e) {}
//        try { mModel.getDjiAircraft().getBattery().getCellVoltages(null); }
//        catch(Exception e) {}

        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int reqCode, int resCode, Intent data) {
        Log.d(TAG, "onActivityResult");
        super.onActivityResult(reqCode, resCode, data);
        if (reqCode == RESULT_SETTINGS && mGCSCommunicator != null) {
            mGCSCommunicator.renewDatalinks();
            sendRestartVideoService();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_toolbar, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);

        View view_status_dji = findViewById(R.id.action_dji);
        View view_status_gcs = findViewById(R.id.action_gcs);

        if (view_status_dji != null && view_status_dji instanceof TextView) {
            if (mProduct instanceof Aircraft)
                ((TextView) view_status_dji).setTextColor(Color.GREEN);
            else
                ((TextView) view_status_dji).setTextColor(Color.RED);
        }

        if (view_status_gcs != null && view_status_gcs instanceof TextView) {
            if (System.currentTimeMillis() - mMavlinkReceiver.getTimestampLastGCSHeartbeat() <= GCS_TIMEOUT_mSEC)
                ((TextView) view_status_gcs).setTextColor(Color.GREEN);
            else
                ((TextView) view_status_gcs).setTextColor(Color.RED);
        }
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "menu item selected");
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_dji:
                onClickDJIStatus();
                return true;
            case R.id.action_gcs:
                onClickGCSStatus();
                return true;
            case R.id.action_settings:
                onClickSettings();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onClickDJIStatus() {

        Log.d(TAG, "onClickDJIStatus()");
        mModel.startWaypointMission();
    }

    private void onClickGCSStatus() {
        Log.d(TAG, "onClickGCSStatus()");
        mModel.echoLoadedMission();
    }

    private void onClickSettings() {
        Log.d(TAG, "onClickSettings()");
        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
        startActivityForResult(intent, RESULT_SETTINGS);
    }

    private void onDroneConnected() {
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
                return;
            }
        }
        while (!mModel.loadParamsFromDJI()) {

        }

        sendDroneConnected();

    }

    private void onDroneDisconnected() {
        logMessageDJI("onDroneDisconnected()");
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
        Log.d(TAG, "notifyStatusChange()");
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

    public void logMessageFromGCS(String msg) {
        if (prefs.getBoolean("pref_log_mavlink", false))
            mNewInbound += "\n" + msg;
    }

    public void logMessageDJI(String msg) {
        mNewDJI += "\n" + msg;
    }

    //region Interface to VideoService
    //---------------------------------------------------------------------------------------

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
            if (!prefs.getBoolean("pref_combined_gcs", false)) {
                videoIP = prefs.getString("pref_gcs_ip", "127.0.0.1");
            } else {
                videoIP = prefs.getString("pref_video_ip", "127.0.0.1");
            }
        }

        return videoIP;
    }
    /**
     *
     * @param action
     * @param extras
     * @return
     */
    private Intent setupIntent(String action, Object... extras) {
        Intent intent = new Intent(this, VideoService.class);
        intent.setAction(action);

        for (Object extra: extras) {
//            intent.putExtra()
        }

        return intent;
    }

    /**
     *
     * @param intent
     */
    private void sendIntent(Intent intent) {
        Log.d(TAG, "sendIntent");
        if (intent != null) {
            startService(intent);
        }
    }

    //---------------------------------------------------------------------------------------
    //endregion

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
            Log.d(TAG, "renewDataLinks");
            request_renew_datalinks = true;
        }

        private void onRenewDatalinks() {
            Log.d(TAG, "onRenewDataLinks");
            createTelemetrySocket();
//            mainActivityWeakReference.get().sendRestartVideoService();
        }

        @Override
        protected Integer doInBackground(Integer... ints2) {
            Log.d("RDTHREADS", "doInBackground()");

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
                Log.d("RDTHREADS", "doInBackground() complete");

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

            Log.d(TAG, mainActivityWeakReference.get().socket.getInetAddress().toString());
            Log.d(TAG, mainActivityWeakReference.get().socket.getLocalAddress().toString());
            Log.d(TAG, String.valueOf(mainActivityWeakReference.get().socket.getPort()));
            Log.d(TAG, String.valueOf(mainActivityWeakReference.get().socket.getLocalPort()));

            mainActivityWeakReference.get().mModel.setSocket(mainActivityWeakReference.get().socket);
        }

        protected void close() {
            if (mainActivityWeakReference.get().socket != null) {
                mainActivityWeakReference.get().socket.disconnect();
                mainActivityWeakReference.get().socket.close();
            }
        }
    }

    //---------------------------------------------------------------------------------------
    //endregion

}
