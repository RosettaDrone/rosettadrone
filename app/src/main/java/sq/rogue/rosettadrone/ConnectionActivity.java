package sq.rogue.rosettadrone;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
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
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
    };

    private static final int REQUEST_PERMISSION_CODE = 12345;
    private TextView mTextConnectionStatus;
    private TextView mTextProduct;
    private TextView mTextModelAvailable;
    private Button mBtnOpen;
    private Button mBtnSim;
    private Button mBtnTest;
    private int hiddenkey = 0;
    private Handler mUIHandler;

    private KeyListener firmVersionListener = new KeyListener() {
        @Override
        public void onValueChange(@Nullable Object oldValue, @Nullable Object newValue) {
            updateVersion();
        }
    };
    private DJIKey firmkey = ProductKey.create(ProductKey.FIRMWARE_PACKAGE_VERSION);
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private List<String> missingPermission = new ArrayList<>();
    private SharedPreferences sharedPreferences;
    private String CustomName;

    //region Registration n' Permissions Helpers

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
            RDApplication.startLoginApplication();
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
            Toast.makeText(getApplicationContext(), "Missing permissions!!!", Toast.LENGTH_LONG).show();
        }
    }
    
    private void notifyStatusChange() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshSDKRelativeUI();
            }
        });
    }

    //endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAndRequestPermissions();

        //---------------- Force Landscape ether ways...
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        setContentView(R.layout.activity_connection);
        initUI();

        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJISimulatorApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            mTextConnectionStatus.setText(R.string.connection_sim);
            notifyStatusChange();
        }
    };

    @Override
    public void onResume() {
        Log.v(TAG, "onResume");
        super.onResume();
        updateTitleBar();

        // JUST FOR DEBUG MUST BE REMOVED:::::::
/*
        View x = new View(this);
        x.setId(R.id.btn_start);
        onClick(x);
*/
    }

    @Override
    protected void onDestroy() {
        Log.v(TAG, "onDestroy");
        if (KeyManager.getInstance() != null) {
            KeyManager.getInstance().removeListener(firmVersionListener);
        }
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

    private void updateTitleBar() {
        boolean ret = false;
        BaseProduct product;

        if (RDApplication.getSim() == true) {
            product = DJISimulatorApplication.getAircraftInstance();
        } else {
            product = RDApplication.getProductInstance();
        }

        if (product != null) {
            if (product.isConnected()) {
                //The product is connected
                showToast(RDApplication.getProductInstance().getModel() + " Connected");
                ret = true;
            } else {
                if (product instanceof Aircraft) {
                    Aircraft aircraft = (Aircraft) product;
                    if (aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        showToast("only RC Connected");
                        ret = true;
                    }
                }
            }
        }

        if (!ret) {
            // The product or the remote controller are not connected.
            showToast("Disconnected");
        }
    }


    private void updateVersion() {

        if (RDApplication.getProductInstance() != null) {
            final String version = RDApplication.getProductInstance().getFirmwarePackageVersion();
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (TextUtils.isEmpty(version)) {
                        mTextModelAvailable.setText("Model Not Available"); //Firmware version:
                    } else {
                        mTextModelAvailable.setText(version); //"Firmware version: " +
                    }
                }
            });
        }
    }


    private void initUI() {

        mTextConnectionStatus = (TextView) findViewById(R.id.text_connection_status);
        mTextModelAvailable = (TextView) findViewById(R.id.text_model_available);
        mTextProduct = (TextView) findViewById(R.id.text_product_info);

        mBtnOpen = (Button) findViewById(R.id.btn_start);
        mBtnOpen.setOnClickListener(this);
        mBtnOpen.setEnabled(false);

        mBtnSim = (Button) findViewById(R.id.btn_sim);
        mBtnSim.setOnClickListener(this);

        mBtnTest = (Button) findViewById(R.id.btn_test);
        mBtnTest.setOnClickListener(this);

        Context appContext = this.getBaseContext();
        String version = "Version: " + getAppVersion(appContext);
        Log.v(TAG, "" + version);
        ((TextView) findViewById(R.id.textView3)).setText(version);

        sharedPreferences = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        CustomName = sharedPreferences.getString("pref_app_name", "RosettaDrone 2"); //+"RosettaDrone 2";
        if (CustomName.length() > 0)
            ((TextView) findViewById(R.id.textView)).setText(CustomName);

        ((TextView) findViewById(R.id.textView2)).setText(getResources().getString(R.string.sdk_version, DJISDKManager.getInstance().getSDKVersion()));

    }

    private Runnable startApp = new Runnable() {

        @Override
        public void run() {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();

            mBtnOpen.setEnabled(true);
        }
    };


    private void refreshSDKRelativeUI() {

        BaseProduct mProduct = DJISimulatorApplication.getProductInstance();
        Log.d(TAG, "refreshSDKRelativeUI");

        if (null != mProduct && mProduct.isConnected()) {
            Log.d(TAG, "refreshSDK: True");

            mUIHandler = new Handler(Looper.getMainLooper());
            mUIHandler.postDelayed(startApp, 2000);

            String str = mProduct instanceof Aircraft ? "DJIAircraft" : "DJIHandHeld";
            mTextConnectionStatus.setText("Status: " + str + " connected");

            if (null != mProduct.getModel()) {
                mTextProduct.setText("" + mProduct.getModel().getDisplayName());
            } else {
                mTextProduct.setText(R.string.product_information);
            }
            if (KeyManager.getInstance() != null) {
                KeyManager.getInstance().addListener(firmkey, firmVersionListener);
            }
        } else if (RDApplication.getSim() == true) {
            Log.v(TAG, "refreshSDK: Sim");
//            mBtnOpen.setEnabled(true);

            mTextProduct.setText(R.string.product_information);
            //  mTextConnectionStatus.setText(R.string.connection_sim);
        } else {
            Log.v(TAG, "refreshSDK: False");
            mBtnOpen.setEnabled(false);

            mTextProduct.setText(R.string.product_information);
            mTextConnectionStatus.setText(R.string.connection_loose);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            // For debugging we can tap the drone icon 5 times to be able to open the software without a drone connected.
            case R.id.btn_test:
                if (++hiddenkey == 5) {
                    showToast("TestMode enabled...");
                    TextView lTextConnectionStatus = (TextView) findViewById(R.id.text_model_test);
                    lTextConnectionStatus.setText("TestMode");
                    mUIHandler = new Handler(Looper.getMainLooper());
                    mUIHandler.postDelayed(startApp, 50);
                    hiddenkey = 6;
                }
                break;

            case R.id.btn_sim: {
                if (RDApplication.getSim() == true) {
                    TextView lTextConnectionStatus = (TextView) findViewById(R.id.text_model_simulated);
                    lTextConnectionStatus.setText("");

                    showToast("noSimulate...");
                    RDApplication.setSim(false);
                    mTextConnectionStatus.setText(R.string.connection_loose);
                } else {
                    showToast("Simulate...");
                    RDApplication.setSim(true);
                    TextView lTextConnectionStatus = (TextView) findViewById(R.id.text_model_simulated);
                    lTextConnectionStatus.setText("Active");
                }
                break;
            }
            case R.id.btn_start: {
                mBtnOpen.setEnabled(false);
                unregisterReceiver(mReceiver);

                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivityIfNeeded(intent,0);
                break;
            }
            default:
                break;
        }
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
