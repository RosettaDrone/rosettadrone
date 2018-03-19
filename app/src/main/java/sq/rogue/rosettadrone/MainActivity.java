package sq.rogue.rosettadrone;

// Acknowledgements:
// IP address validation: https://stackoverflow.com/questions/3698034/validating-ip-in-android/11545229#11545229
// Hide keyboard: https://stackoverflow.com/questions/16495440/how-to-hide-keyboard-by-default-and-show-only-when-click-on-edittext
// MenuItemTetColor: RPP @ https://stackoverflow.com/questions/31713628/change-menuitem-text-color-programmatically

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.Parser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.product.Model;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import sq.rogue.rosettadrone.video.DJIVideoStreamDecoder;
import sq.rogue.rosettadrone.video.H264Packetizer;
import sq.rogue.rosettadrone.video.NativeHelper;

public class MainActivity extends AppCompatActivity implements DJIVideoStreamDecoder.IYuvDataListener, DJIVideoStreamDecoder.IFrameDataListener {

    private Handler mDJIHandler;
    private Handler mUIHandler;

    private final static int RESULT_SETTINGS = 1001;

    private final String TAG = "RosettaDrone";
    private final int GCS_TIMEOUT_mSEC = 2000;
    public static final String FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change";
    private static BaseProduct mProduct;

    private Button mButtonClear;
    private ToggleButton toggleBtnArming;
    protected VideoFeeder.VideoDataCallback mReceivedVideoDataCallBack = null;
    private H264Packetizer packetizer;

    private LogFragment logDJI;
    private LogFragment logToGCS;
    private LogFragment logFromGCS;
    private TabLayout tabLayout;
    private ViewPager viewPager;
    private LogPagerAdapter adapter;


    private String mNewOutbound = "";
    private String mNewInbound = "";
    private String mNewDJI = "";

    private DatagramSocket socket;

    private DroneModel mModel;
    private MAVLinkReceiver mMavlinkReceiver;
    private Parser mMavlinkParser;
    private GCSCommunicatorAsyncTask mGCSCommunicator;

    private static final Pattern PARTIAl_IP_ADDRESS =
            Pattern.compile("^((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9])\\.){0,3}" +
                    "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9])){0,1}$");


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
                if(mModel != null) {
                    if (mModel.isRSArmingEnabled())
                        toggleBtnArming.setChecked(true);
                    else
                        toggleBtnArming.setChecked(false);
                }
                else
                    toggleBtnArming.setChecked(false);
                invalidateOptionsMenu();
            } catch (Exception e) {
                Log.d(TAG, "exception", e);
            }
            mUIHandler.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        requestPermissions();

        mButtonClear = (Button) findViewById(R.id.button_clear);
        viewPager = (ViewPager) findViewById(R.id.pager);
        toggleBtnArming = (ToggleButton) findViewById(R.id.toggBtnEnablerArming);
        tabLayout = (TabLayout) findViewById(R.id.tab_layout);

        mButtonClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                logDJI.setLogText("");
                logFromGCS.setLogText("");
                logToGCS.setLogText("");
            }
        });


        toggleBtnArming.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mModel.setRSArmingEnabled(true);
                    toggleBtnArming.setTextColor(Color.GREEN);
                } else {
                    mModel.setRSArmingEnabled(false);
                    toggleBtnArming.setTextColor(Color.RED);
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


        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
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
        mGCSCommunicator = new GCSCommunicatorAsyncTask();
        mGCSCommunicator.execute();

        //NativeHelper.getInstance().init();
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

    private void initVideoStreamDecoder() {
        NativeHelper.getInstance().init();
        DJIVideoStreamDecoder.getInstance().init(getApplicationContext(), null);
        DJIVideoStreamDecoder.getInstance().setYuvDataListener(MainActivity.this);
        DJIVideoStreamDecoder.getInstance().setFrameDataListener(MainActivity.this);
        DJIVideoStreamDecoder.getInstance().resume();
    }

    private void initPacketizer() {
        if(packetizer != null && packetizer.getRtpSocket() != null)
            packetizer.getRtpSocket().close();
        packetizer = new H264Packetizer();
        packetizer.setInputStream(new ByteArrayInputStream("".getBytes()));
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String videoIPString = "127.0.0.1";
        if (prefs.getBoolean("pref_external_gcs", false))
            videoIPString = prefs.getString("pref_gcs_ip", null);
        int videoPort = Integer.parseInt(prefs.getString("pref_video_port", "-1"));
        try {
            packetizer.getRtpSocket().setDestination(InetAddress.getByName(videoIPString), videoPort, 5000);
            logMessageDJI("Starting GCS video link: " + videoIPString + ":" + String.valueOf(videoPort));

        } catch (UnknownHostException e) {
            Log.d(TAG, "exception", e);
            logMessageDJI("Unknown video host: " + videoIPString + ":" + String.valueOf(videoPort));
        }
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
        mGCSCommunicator.cancel(true);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int reqCode, int resCode, Intent data) {
        super.onActivityResult(reqCode, resCode, data);
        if (reqCode == RESULT_SETTINGS)
            mGCSCommunicator.renewDatalinks();
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
    }

    private void onClickSettings() {
        Log.d(TAG, "onClickSettings()");
        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
        startActivityForResult(intent, RESULT_SETTINGS);
    }

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

    private void onDroneConnected() {
        mModel.setDjiAircraft((Aircraft) mProduct);
        mModel.loadParamsFromDJI();
        initVideoStreamDecoder();
        initPacketizer();
        mReceivedVideoDataCallBack = new VideoFeeder.VideoDataCallback() {

            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                //Log.d(TAG, "camera recv video data size: " + size);
                //sendNAL(videoBuffer);
                DJIVideoStreamDecoder.getInstance().parse(videoBuffer, size);
            }
        };
        if (!mProduct.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
            if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(mReceivedVideoDataCallBack);
            }
        }
    }

    private void onDroneDisconnected() {
        mModel.setDjiAircraft(null);
    }


    @Override
    public void onYuvDataReceived(byte[] yuvFrame, int width, int height) {
    }

    @Override
    public void onFrameDataReceived(byte[] frame, int width, int height) {
        // Called whenever a new H264 frame is received from the DJI video decoder
        splitNALs(frame);
    }

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
        //sendPacket(packet);
    }

    protected void sendNAL(byte[] buffer) {
        // Pack a single NAL for RTP and send
        if (packetizer != null) {
            ByteArrayInputStream is = new ByteArrayInputStream(buffer);
            packetizer.setInputStream(is);
            packetizer.run2();
        }
    }

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
            if (!isConnected) {
                onDroneDisconnected();
            }
            notifyStatusChange();
        }
    };
    private BaseComponent.ComponentListener mDJIComponentListener = new BaseComponent.ComponentListener() {
        @Override
        public void onConnectivityChange(boolean isConnected) {
            notifyStatusChange();
        }
    };

    private void notifyStatusChange() {
        mDJIHandler.removeCallbacks(djiUpdateRunnable);
        Log.d(TAG, "notifyStatusChange()");
        mDJIHandler.postDelayed(djiUpdateRunnable, 500);
    }

    private Runnable djiUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
            sendBroadcast(intent);
        }
    };

    private class GCSSenderTimerTask extends TimerTask {
        @Override
        public void run() {
            mModel.tick();
        }
    }


    private class GCSCommunicatorAsyncTask extends AsyncTask<Integer, Integer, Integer> {

        public boolean request_renew_datalinks = false;
        private Timer timer;

        public void renewDatalinks() {
            request_renew_datalinks = true;
        }

        private void onRenewDatalinks() {
            createTelemetrySocket();
            initPacketizer();
        }

        protected Integer doInBackground(Integer... ints2) {
            Log.d("RDTHREADS", "doInBackground()");

            try {
                onRenewDatalinks();
                mMavlinkParser = new Parser();

                GCSSenderTimerTask gcsSender = new GCSSenderTimerTask();
                timer = new Timer(true);
                timer.scheduleAtFixedRate(gcsSender, 0, 100);

                while (!isCancelled()) {
                    // Listen for packets
                    try {
                        if (request_renew_datalinks == true) {
                            request_renew_datalinks = false;
                            onRenewDatalinks();

                        }
                        byte[] buf = new byte[1000];
                        DatagramPacket dp = new DatagramPacket(buf, buf.length);
                        socket.receive(dp);

                        byte[] bytes = dp.getData();
                        int[] ints = new int[bytes.length];
                        for (int i = 0; i < bytes.length; i++)
                            ints[i] = bytes[i] & 0xff;

                        for (int i = 0; i < bytes.length; i++) {
                            MAVLinkPacket packet = mMavlinkParser.mavlink_parse_char(ints[i]);

                            if (packet != null) {
                                MAVLinkMessage msg = packet.unpack();
                                logMessageFromGCS(msg.toString());
                                mMavlinkReceiver.process(msg);
                            }
                        }
                    } catch (PortUnreachableException e) {
                        //logMessageDJI("Port unreachable: " + e.toString());
                    } catch (SocketTimeoutException e) {
                    } catch (IOException e) {
                        //logMessageDJI("IOException: " + e.toString());
                    }
                }

            } catch (Exception e) {
                Log.d(TAG, "exception", e);
            }
            socket.disconnect();
            timer.cancel();
            Log.d("RDTHREADS", "doInBackground() complete");
            return 0;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        private void createTelemetrySocket() {
            if (socket != null) {
                socket.disconnect();
                socket.close();
            }

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            String gcsIPString = "127.0.0.1";
            if (prefs.getBoolean("pref_external_gcs", false) == true)
                gcsIPString = prefs.getString("pref_gcs_ip", null);
            int telemIPPort = Integer.parseInt(prefs.getString("pref_telem_port", "-1"));

            try {
                socket = new DatagramSocket();
                socket.connect(InetAddress.getByName(gcsIPString), telemIPPort);
                socket.setSoTimeout(10);
                logMessageDJI("Starting GCS telemetry link: " + gcsIPString + ":" + String.valueOf(telemIPPort));
            } catch (SocketException e) {
                Log.d(TAG, "createTelemetrySocket() - socket exception");
                Log.d(TAG, "exception", e);
                logMessageDJI("Telemetry socket exception: " + gcsIPString + ":" + String.valueOf(telemIPPort));
            } // TODO
            catch (UnknownHostException e) {
                Log.d(TAG, "createTelemetrySocket() - unknown host exception");
                Log.d(TAG, "exception", e);
                logMessageDJI("Unknown telemetry host: " + gcsIPString + ":" + String.valueOf(telemIPPort));
            } // TODO

            Log.d(TAG, socket.getInetAddress().toString());
            Log.d(TAG, socket.getLocalAddress().toString());
            Log.d(TAG, String.valueOf(socket.getPort()));
            Log.d(TAG, String.valueOf(socket.getLocalPort()));

            mModel.setSocket(socket);
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
        } catch (FileNotFoundException e) {
            Log.d(TAG, "exception", e);
        } catch (IOException e) {
            Log.d(TAG, "exception", e);
        }
    }

    public void logMessageToGCS(String msg) {
        mNewOutbound += "\n" + msg;
    }

    public void logMessageFromGCS(String msg) {
        mNewInbound += "\n" + msg;
    }

    public void logMessageDJI(String msg) {
        mNewDJI += "\n" + msg;
    }
}
