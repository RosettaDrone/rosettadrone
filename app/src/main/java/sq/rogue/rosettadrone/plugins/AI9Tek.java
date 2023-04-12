/**
 * TODO: Reenable and test custom code if necessary.
 * TODO: Maybe distribute into multiple plugins.
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
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.MAVLink.common.msg_mission_item_int;
import com.MAVLink.common.msg_rc_channels;
import com.MAVLink.common.msg_statustext;
import com.MAVLink.enums.MAV_CMD;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
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

    public int AIactivityPort = 7001;
    public String AIactivityIP = "127.0.0.1";

    public boolean AIenabled = false;
    public boolean AIstat = false;

    private int mAIfunction_activation = 0;
    private String AIaddress = "127.0.0.1";
    private int AIport = 7001;
    private MailReport SendMail;
    private String[] aiWP = new String[100]; // Max 100 wp in an AI mission for now...

    protected void start() {
        // Set some user values for the AI assist functionallity.
        AIaddress = pluginManager.mainActivity.prefs.getString("pref_ai_ip", "127.0.0.1");
        AIport = Integer.parseInt(Objects.requireNonNull(pluginManager.mainActivity.prefs.getString("pref_ai_port", "2000")));

        AIenabled = pluginManager.mainActivity.prefs.getBoolean("pref_ai_telemetry_enabled", true);

        // Store IP to mission controll...
        AIactivityIP = AIaddress; //prefs.getString("pref_gcs_ip", "127.0.0.1");
        AIactivityPort = AIport;

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

        Button mBtnAI = pluginManager.mainActivity.findViewById(R.id.btn_AI_start);
        mBtnAI.setOnClickListener(v -> setAIMode());
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

            if(AIenabled == true){
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

        if (AIenabled == true) {
            AIstat = !AIstat;
            if (AIstat) {
                connectedDrawable = mainActivity.getResources().getDrawable(R.drawable.drone_img, null);
            } else {
                connectedDrawable = mainActivity.getResources().getDrawable(R.mipmap.track_right, null);
            }

            mBtnAI.setBackground(connectedDrawable);
        }
        else{
            mainActivity.logMessageDJI("AI not activated in setup menu !!!!!");
            NotificationHandler.notifySnackbar(mainActivity.findViewById(R.id.snack),R.string.ai_not_active, LENGTH_LONG);
            AIstat = false;
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

    void setAIenable(final boolean enable) {
        AIenabled = enable;
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

    void onMapReady() {
        /* TODO: Reimplement
        aMap.setOnMapClickListener((point)-> {
            Log.d(TAG, "Goto: " + point.toString());

            AlertDialog.Builder alertDialog2 = new AlertDialog.Builder(this);
            alertDialog2.setIcon(R.mipmap.track_right);
            alertDialog2.setTitle("AI Mavlink/Python function selector!");
            alertDialog2.setMessage("Clicked");
            alertDialog2.setNegativeButton("Cancel",
                    (dialog, which) -> {
                        dialog.cancel();
                    });
            alertDialog2.setNeutralButton("Add to Waypoint list",
                    (dialog, which) -> {
                        markWaypoint(point, true);
                        Waypoint mWaypoint = new Waypoint(point.latitude, point.longitude, mModel.m_alt);
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
            alertDialog2.setPositiveButton("Accept",
                    (dialog, which) -> {
                        // If we are airborn...
                        if (mModel.m_alt > -5.0) {
                            markWaypoint(point, false);
                            mModel.flyTo(point.latitude, point.longitude, mModel.m_alt);
                            dialog.cancel();
                        } else {
                            Log.d(TAG, "Can't Add Waypoint");
                            Toast.makeText(getApplicationContext(), "Can't goto position!", Toast.LENGTH_LONG).show();
                        }
                    });
            ///
            this.runOnUiThread(() -> {
                alertDialog2.show();
            });
        });
        */
    }

    void missionFinished() {
        DroneModel mModel = pluginManager.mainActivity.mModel;
        int num = 0;

        for (msg_mission_item_int m : pluginManager.mainActivity.mMavlinkReceiver.mMissionItemList) {
            Log.d(TAG, "AI Command: " + String.valueOf(m));

            switch (m.command) {
                case MAV_CMD.MAV_CMD_NAV_WAYPOINT:

                    if(num ==0)
                        mModel.mission_alt = m.z;

                    aiWP[num++] = "WP," +
                            String.valueOf(num) + "," +
                            String.valueOf(m.x / 10000000.0) + "," +
                            String.valueOf(m.y / 10000000.0) + "," +
                            String.valueOf(m.z-mModel.mission_alt) + "";
                    break;
            }
        }

        // Send mission to the AIactivityModule
        //Log.d(TAG, aiWP);
        if(num > 0){
            aiWP[num++] = "WP,9999,0,0,0";  // End of mission...

            try {
                InetAddress address = InetAddress.getByName(AIactivityIP);

                try {
                    DatagramSocket mSocketUDP = new DatagramSocket();

                    for(int x=0; x < num; x++){

                        byte[] byteArrray = aiWP[x].getBytes();
                        DatagramPacket p = new DatagramPacket(byteArrray, byteArrray.length, address, AIactivityPort);
                        try {
                            mSocketUDP.send(p);
                            //                 mSocketUDP.close();  // It this needed / wanted...
                        } catch (IOException e) {
                            Log.e(TAG, "Error sending AI datagram socket", e);
                        }
                        aiWP[x]="";
                    }
                } catch (SocketException e) {
                    Log.e(TAG, "Error creating AI datagram socket", e);
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "Error setting address to AI datagram socket", e);
            }
        }

        // TODO: Reimplement
        //stopUpload = true;

        NotificationHandler.notifySnackbar(pluginManager.mainActivity.findViewById(R.id.snack),R.string.ai_uploaded_active, LENGTH_LONG);
    }

    public void settingsChanged() {
        if(MainActivity.changedSetting("pref_ai_telemetry_enabled")) {
            setAIenable(pluginManager.mainActivity.prefs.getBoolean("pref_ai_telemetry_enabled", true));
        }
    }
}
