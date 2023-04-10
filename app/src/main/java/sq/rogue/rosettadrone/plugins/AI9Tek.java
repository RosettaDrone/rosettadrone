/**
 * TODO: Reenable and test custom code (if it will be used in the future)
 * TODO: Maybe distribute into different plugins.
 */
package sq.rogue.rosettadrone.plugins;

import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;
import android.widget.Button;

import com.MAVLink.common.msg_rc_channels;
import com.MAVLink.common.msg_statustext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.log.DJILog;
import dji.sdk.media.DownloadListener;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;
import sq.rogue.rosettadrone.DroneModel;
import sq.rogue.rosettadrone.MainActivity;
import sq.rogue.rosettadrone.NotificationHandler;
import sq.rogue.rosettadrone.Plugin;
import sq.rogue.rosettadrone.PluginManager;
import sq.rogue.rosettadrone.R;
import sq.rogue.rosettadrone.RDApplication;
import sq.rogue.rosettadrone.settings.MailReport;

public class AI9Tek extends Plugin {
    private final String TAG = DroneModel.class.getSimpleName();

    private int mAIfunction_activation = 0;
    private String AIaddress = "127.0.0.1";
    private int AIport = 7001;
    private MailReport SendMail;

    protected void init(PluginManager pluginManager) {
        // Set some user values for the AI assist functionallity.
        AIaddress = pluginManager.mainActivity.prefs.getString("pref_ai_ip", "127.0.0.1");
        AIport  = Integer.parseInt(Objects.requireNonNull(pluginManager.mainActivity.prefs.getString("pref_ai_port", "2000")));

        pluginManager.mainActivity.mMavlinkReceiver.AIenabled = pluginManager.mainActivity.prefs.getBoolean("pref_ai_telemetry_enabled", true);

        // Store IP to mission controll...
        pluginManager.mainActivity.mMavlinkReceiver.AIactivityIP = AIaddress; //prefs.getString("pref_gcs_ip", "127.0.0.1");
        pluginManager.mainActivity.mMavlinkReceiver.AIactivityPort = AIport;

        // Prepare mail handler and add the catalog for images
        SendMail = new MailReport(pluginManager.mainActivity, pluginManager.mainActivity.getApplicationContext().getContentResolver());

        // Report button
        /* TODO: Reimplement
        Button mBtnRepport = findViewById(R.id.btn_Report);
        mBtnRepport.setOnClickListener(v -> pluginManager.startPlugin("com.example.sendmail",1));
        */

        /*
        StateListDrawable listDrawable = new StateListDrawable();
        listDrawable.addState(new int[] {android.R.attr.state_pressed},  mBtnRepport.getBackground());
        listDrawable.addState(new int[] {android.R.attr.defaultValue}, mBtnRepport.setBackground("@mipmap/track_report");
        mBtnRepport.setBackground(listDrawable);
        */

        // AI button
        /* TODO: Reimplement
        Button mBtnAI = findViewById(R.id.btn_AI_start);
        mBtnAI.setOnClickListener(v -> pluginManager.setAIMode());
        */
    }

    // Start the AI Pluggin (Developed by the customers...)
    public boolean startActivity(String plugin)
    {
        MainActivity mainActivity = this.pluginManager.mainActivity;
        Intent intent = mainActivity.getPackageManager().getLaunchIntentForPackage(plugin);
        if (intent == null) {
            // Bring user to the market or let them choose an app?
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=" + plugin));
        }
        if (intent != null) {

            if(mainActivity.mMavlinkReceiver.AIenabled == true){
                intent.putExtra("password", "thisisrosettadrone246546101");
                intent.putExtra("ip", AIaddress);
                intent.putExtra("port", AIport);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mainActivity.startActivity(intent);

            } else {
                mainActivity.logMessageDJI("AI not enabled!");
                NotificationHandler.notifySnackbar(mainActivity.findViewById(R.id.snack),R.string.ai_not_active, LENGTH_LONG);
            }
        }
        return true;
    }

    protected void startPlugin(String msg, int type) {
        MainActivity mainActivity = this.pluginManager.mainActivity;
        switch (type){
            case 1:
                //--------------------------------------------------------------
                mainActivity.logMessageDJI("Init media manager and fetch image...");
                NotificationHandler.notifySnackbar(mainActivity.findViewById(R.id.snack),R.string.hold, LENGTH_LONG);

                List<String> address = new ArrayList<String>();
                String Eemail =  mainActivity.sharedPreferences.getString("pref_email_name1", " ");
                address.add(Eemail);
                Eemail = mainActivity.sharedPreferences.getString("pref_email_name2", " ");
                address.add(Eemail);
                Eemail = mainActivity.sharedPreferences.getString("pref_email_name3", " ");
                address.add(Eemail);
                Eemail = mainActivity.sharedPreferences.getString("pref_email_name4", " ");
                address.add(Eemail);

                m_mailToAddress = address;
                DJILog.e(TAG, "Addresses: "+address.toString());

                Runnable runnable = () -> mainActivity.mModel.initMediaManager(address);
                new Thread(runnable).start();
                break;

            case 2:
                Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                Ringtone r = RingtoneManager.getRingtone(mainActivity.getApplicationContext(), notification);
                startActivity(msg);
                break;

            case 3:
                break;
        }
    }

    // Toggle AI mission mode...
    public void setAIMode()
    {
        MainActivity mainActivity = this.pluginManager.mainActivity;
        Button mBtnAI = mainActivity.findViewById(R.id.btn_AI_start);
        Drawable connectedDrawable;

        if (mainActivity.mMavlinkReceiver.AIenabled == true) {
            mainActivity.mMavlinkReceiver.AIstat = !mainActivity.mMavlinkReceiver.AIstat;
            if (mainActivity.mMavlinkReceiver.AIstat) {
                connectedDrawable = mainActivity.getResources().getDrawable(R.drawable.drone_img, null);
            } else {
                connectedDrawable = mainActivity.getResources().getDrawable(R.mipmap.track_right, null);
            }

            mBtnAI.setBackground(connectedDrawable);
        }
        else{
            mainActivity.logMessageDJI("AI not activated in setup menu !!!!!");
            NotificationHandler.notifySnackbar(mainActivity.findViewById(R.id.snack),R.string.ai_not_active, LENGTH_LONG);
            mainActivity.mMavlinkReceiver.AIstat = false;
            connectedDrawable = mainActivity.getResources().getDrawable(R.mipmap.track_right, null);
            mBtnAI.setBackground(connectedDrawable);
        }
    }

    public List<String> m_mailToAddress = null;
    void sendmail(String file_toSend)
    {
        /*
        Log.i(TAG, "File to send: "+file_toSend);
        try {
            Intent email = SendMail.createEmail(
                    m_mailToAddress,
                    "Status report",
                    "There is an issue: ",
                    get_current_lat(),
                    get_current_lon(),
                    get_current_alt(),
                    get_current_head(),
                    file_toSend,
                    m_directory
            );

            if(email != null) {
                try {
                    parent.startActivity(Intent.createChooser(email, "Send mail..."));
                    Log.i(TAG, "Finished sending email...");
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(parent, "There is no email client installed.", Toast.LENGTH_SHORT).show();
                }
            }
        }catch (Exception e) {
            Toast.makeText(parent, "Can not send email: "+ e.toString(), Toast.LENGTH_SHORT).show();
        }
        */
    }

    @Override
    public boolean onMenuItemClick(int itemId) {
        if(itemId == R.id.action_AI) {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            Ringtone r = RingtoneManager.getRingtone(pluginManager.mainActivity.getApplicationContext(), notification);
            startActivity("com.example.remoteconfig4");
            return true;
        } else {
            return false;
        }
    }

    // These are no long used as this is an internal process now, sending data to RemoteConfig...
    void setAiIP(final String ip) {
    }

    void setAiPort(final int port) {
    }

    void setAIenable(final boolean enable) {
        pluginManager.mainActivity.mMavlinkReceiver.AIenabled = enable;
        setAIMode();
    }

    public void setAIfunction(int ai) {
        mAIfunction_activation = ai;
    }

    // Does not work, use RC ch 8...
    void send_AI_Function(int num) {
        msg_statustext msg = new msg_statustext();
        String data = "Mgs: RosettaDrone: AI Fuction " + num + " True";
        byte[] txt = data.getBytes();
        msg.text = txt;
        pluginManager.mainActivity.mModel.sendMessage(msg);
    }

    /* TODO: Reenable
    private void send_rc_channels() {
        msg_rc_channels msg = new msg_rc_channels();
        msg.rssi = (short) mUplinkQuality;
        msg.chan1_raw = mLeftStickVertical;
        msg.chan2_raw = mLeftStickHorizontal;
        msg.chan3_raw = mRightStickVertical;
        msg.chan4_raw = mRightStickHorizontal;
        msg.chan5_raw = mC1 ? 1000 : 2000;
        msg.chan6_raw = mC2 ? 1000 : 2000;
        msg.chan7_raw = mC3 ? 1000 : 2000;
        msg.chan8_raw = (mAIfunction_activation * 100) + 1000;
        msg.chancount = 8;
        pluginManager.mainActivity.mModel.sendMessage(msg);

        // Cancel motion tasks if stick is moved
        if (
                (mLeftStickVertical > 1550 || mLeftStickVertical < 1450) ||
                        (mLeftStickHorizontal > 1550 || mLeftStickHorizontal < 1450) ||
                        (mRightStickVertical > 1550 || mRightStickVertical < 1450) ||
                        (mRightStickHorizontal > 1550 || mRightStickHorizontal < 1450)
        ) {

            if (mAIfunction_activation != 0) {
                parent.logMessageDJI("AI Mode Canceled...");
                mAIfunction_activation = 0;
            }

            mAutonomy = false;   // TODO:: This variable needs to be checked elseware in the code...
        }
    }
    */
}
