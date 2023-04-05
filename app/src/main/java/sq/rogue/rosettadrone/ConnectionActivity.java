package sq.rogue.rosettadrone;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import dji.keysdk.DJIKey;
import dji.keysdk.KeyManager;
import dji.keysdk.ProductKey;
import dji.keysdk.callback.KeyListener;
import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public class ConnectionActivity extends Activity implements View.OnClickListener {

    String modelString = "";
    String firmwareString = "";

    enum AppMode {
        REAL_DRONE,
        SIMULATOR,
        TEST_MODE,
    }

    private static final String TAG = MainActivity.class.getName();
    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.READ_PHONE_STATE,
//            Manifest.permission.SYSTEM_ALERT_WINDOW,
//            Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
//    Manifest.permission.WRITE_EXTERNAL_STORAGE,
//    Manifest.permission.READ_EXTERNAL_STORAGE,
    };

    private static final int REQUEST_PERMISSION_CODE = 12345;
    private TextView mStatusBig;
    private TextView mStatusSmall;
    private Button mBtnOpen;
    private Button mBtnSim;
    private Button mBtnTest;

    private Handler mUIHandler;
    private static boolean running = false;

    private KeyListener firmVersionListener = new KeyListener() {
        @Override
        public void onValueChange(@Nullable Object oldValue, @Nullable Object newValue) {
            refreshProductInfo();
        }
    };
    private DJIKey firmkey = ProductKey.create(ProductKey.FIRMWARE_PACKAGE_VERSION);
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private List<String> missingPermission = new ArrayList<>();
    private SharedPreferences sharedPreferences;
    private String appName;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
            Intent attachedIntent = new Intent();
            attachedIntent.setAction(DJISDKManager.USB_ACCESSORY_ATTACHED);
            sendBroadcast(attachedIntent);
        }
    }

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        Log.d(TAG, "checkAndRequestPermissions");

        // Check the permissions...
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (!missingPermission.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }

        if (missingPermission.isEmpty()) {
            Log.d(TAG, "No missingPermission");
            RDApplication.startLoginApplication();
        }
        else{
            String[] x = missingPermission.toArray(new String[missingPermission.size()]);
            for (int i = 0; i < x.length; i++) {
                Log.d(TAG, x[i]);
            }
        }
    }

    /**
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult");
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            RDApplication.startLoginApplication();
        } else {
            Toast.makeText(getApplicationContext(), "Missing permissions!", Toast.LENGTH_LONG).show();
        }
    }
    
    //endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(this.running == false){
            Log.v(TAG, "First time ... Register Receiver");
            this.running = true;
        }

        checkAndRequestPermissions();

        setContentView(R.layout.activity_connection);
        initUI();

        registerBroadcast();

        if (KeyManager.getInstance() != null) {
            KeyManager.getInstance().addListener(firmkey, firmVersionListener);
        }

        // Process extras passed to intent
        Bundle extras = this.getIntent().getExtras();
        if (extras != null) {
            // Provide mode argument via command line. Set:
            // [Run > Edit Configurations > General tab > Launch Flags] = “--es mode test”
            String mode = extras.getString("mode");
            if(mode != null) {
                if(mode.equals("test")) {
                    openMainActivity(AppMode.TEST_MODE);
                }
            }
        }
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    playSound();
                    refreshProductInfo();
                }
            });
        }
    };

    @Override
    public void onResume() {
        Log.v(TAG, "onResume");
        super.onResume();
        refreshProductInfo();
        registerBroadcast();
    }

    void registerBroadcast() {
        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJISimulatorApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);
    }

    void safeUnregisterBroadcast() {
        try {
            unregisterReceiver(mReceiver);
        } catch(java.lang.IllegalArgumentException e) {
            // Just ignore. Happens when using TestMode
        }
    }

    @Override
    protected void onDestroy() {
        Log.v(TAG, "onDestroy");
        if (KeyManager.getInstance() != null) {
            KeyManager.getInstance().removeListener(firmVersionListener);
        }
        safeUnregisterBroadcast();
        super.onDestroy();
    }

    public static String getAppVersion(Context context) {
        PackageManager localPackageManager = context.getPackageManager();
        try {
            String str = localPackageManager.getPackageInfo(context.getPackageName(), 0).versionName;
            return str;
        } catch (PackageManager.NameNotFoundException e) {
            Log.v(TAG, "getAppVersion error" + e.getMessage());
            e.printStackTrace();
        }
        return "";
    }

    private void refreshProductInfo() {
        boolean somethingConnected = false;
        BaseProduct realProduct = DJISDKManager.getInstance().getProduct();
        boolean productConnected = false;

        if (realProduct != null) {
            if (realProduct.isConnected()) {
                productConnected = true;
                somethingConnected = true;

                if (realProduct.getModel() != null) {
                    mStatusBig.setText(realProduct.getModel().getDisplayName());
                } else {
                    String str = realProduct instanceof Aircraft ? "Aircraft" : "Handheld";
                    mStatusBig.setText(str);
                }

                final String version = realProduct.getFirmwarePackageVersion();
                mStatusSmall.setText(version);

            } else {
                if (realProduct instanceof Aircraft) {
                    Aircraft aircraft = (Aircraft) realProduct;
                    if (aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        mStatusBig.setText("RC connected");
                        somethingConnected = true;
                    }
                }

                mStatusBig.setText(R.string.connection_loose);
            }
        }

        mBtnOpen.setEnabled(productConnected);
        mBtnSim.setEnabled(productConnected);

        if (!somethingConnected) {
            // The product nor the remote controller are connected.
            mStatusBig.setText("RC not connected");
            mStatusSmall.setText("");
        }
    }

    private void initUI() {
        mStatusBig = (TextView) findViewById(R.id.textConnectionStatus);
        mStatusSmall = (TextView) findViewById(R.id.textConnectedModel);

        mBtnOpen = (Button) findViewById(R.id.btn_start);
        mBtnOpen.setOnClickListener(this);
        mBtnOpen.setEnabled(false);

        mBtnSim = (Button) findViewById(R.id.btn_sim);
        mBtnSim.setOnClickListener(this);
        mBtnSim.setEnabled(false);

        mBtnTest = (Button) findViewById(R.id.btn_test);
        mBtnTest.setOnClickListener(this);

        Context appContext = this.getBaseContext();
        String version = "Version: " + getAppVersion(appContext);
        Log.v(TAG, "" + version);
        ((TextView) findViewById(R.id.textAppVersion)).setText(version);

        sharedPreferences = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        appName = "RosettaDrone"; // Use PluginManager to customize

        if (appName.length() > 0)
            ((TextView) findViewById(R.id.textView)).setText(appName);

        ((TextView) findViewById(R.id.textSdkInfo)).setText(getResources().getString(R.string.sdk_version, DJISDKManager.getInstance().getSDKVersion()));
    }

    void playSound() {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager. getRingtone(getApplicationContext(), notification);
            r.play();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_sim:
                openMainActivity(AppMode.SIMULATOR);
                break;

            case R.id.btn_start:
                openMainActivity(AppMode.REAL_DRONE);
                break;

            case R.id.btn_test:
                openMainActivity(AppMode.TEST_MODE);
                break;

            default:
                break;
                }
            }

    void openMainActivity(AppMode mode) {
        RDApplication.isTestMode = mode == AppMode.TEST_MODE;
        RDApplication.setSim(mode == AppMode.SIMULATOR);
        safeUnregisterBroadcast();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityIfNeeded(intent,0);
    }

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ConnectionActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
