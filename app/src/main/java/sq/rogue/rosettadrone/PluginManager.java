/**
 * TODO: Move here all code not directly related with Rosetta Drone.
 * TODO: Code should be moved to different plugins.
 */
package sq.rogue.rosettadrone;

import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.widget.Button;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import sq.rogue.rosettadrone.settings.MailReport;

public class PluginManager {
    private final String TAG = DroneModel.class.getSimpleName();
    private String AIaddress = "127.0.0.1";
    private int AIport = 7001;
    private MailReport SendMail;

    public MainActivity mainActivity;

    List<Plugin> plugins = new ArrayList<Plugin>();
    List<String> classNames = Arrays.asList("OpenCVStreamer"); // TODO: Obtain dynamically

    PluginManager(MainActivity mainActivity) {
        this.mainActivity = mainActivity;

        for (String className : classNames) {
            try {
                Class<?> myClass = Class.forName("sq.rogue.rosettadrone.plugins." + className);
                Plugin plugin = (Plugin) myClass.newInstance();
                if(plugin.isEnabled()) {
                    plugin.init(this);
                    plugins.add(plugin);
                }

            } catch (Exception e) {
            }
        }
    }

    public void init() {
        // Set some user values for the AI assist functionallity.
        AIaddress = mainActivity.prefs.getString("pref_ai_ip", "127.0.0.1");
        AIport  = Integer.parseInt(Objects.requireNonNull(mainActivity.prefs.getString("pref_ai_port", "2000")));

        mainActivity.mMavlinkReceiver.AIenabled = mainActivity.prefs.getBoolean("pref_ai_telemetry_enabled", true);

        // Store IP to mission controll...
        mainActivity.mMavlinkReceiver.AIactivityIP = AIaddress; //prefs.getString("pref_gcs_ip", "127.0.0.1");
        mainActivity.mMavlinkReceiver.AIactivityPort = AIport;

        // Prepare mail handler and add the catalog for images
        SendMail = new MailReport(mainActivity, mainActivity.getApplicationContext().getContentResolver());
    }

    // Start the AI Pluggin (Developed by the customers...)
    public boolean startActivity(String pluggin)
    {
        Intent intent = mainActivity.getPackageManager().getLaunchIntentForPackage(pluggin);
        if (intent == null) {
            // Bring user to the market or let them choose an app?
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=" + pluggin));
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

    public void onVideoChange() {
        for (Plugin plugin : plugins) {
            plugin.onVideoChange();
        }
    }
}
