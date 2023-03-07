package sq.rogue.rosettadrone;

import android.app.AlertDialog;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.common.msg_altitude;
import com.MAVLink.common.msg_attitude;
import com.MAVLink.common.msg_autopilot_version;
import com.MAVLink.common.msg_battery_status;
import com.MAVLink.common.msg_command_ack;
import com.MAVLink.common.msg_file_transfer_protocol;
import com.MAVLink.common.msg_global_position_int;
import com.MAVLink.common.msg_gps_raw_int;
import com.MAVLink.common.msg_home_position;
import com.MAVLink.common.msg_mission_ack;
import com.MAVLink.common.msg_mission_count;
import com.MAVLink.common.msg_mission_item_int;
import com.MAVLink.common.msg_mission_item_reached;
import com.MAVLink.common.msg_mission_request_int;
import com.MAVLink.common.msg_mission_request_list;
import com.MAVLink.common.msg_param_value;
import com.MAVLink.common.msg_power_status;
import com.MAVLink.common.msg_radio_status;
import com.MAVLink.common.msg_rc_channels;
import com.MAVLink.common.msg_statustext;
import com.MAVLink.common.msg_sys_status;
import com.MAVLink.common.msg_vfr_hud;
import com.MAVLink.common.msg_vibration;
import com.MAVLink.enums.GPS_FIX_TYPE;
import com.MAVLink.enums.MAV_AUTOPILOT;
import com.MAVLink.enums.MAV_CMD;
import com.MAVLink.enums.MAV_FRAME;
import com.MAVLink.enums.MAV_MISSION_RESULT;
import com.MAVLink.enums.MAV_MISSION_TYPE;
import com.MAVLink.enums.MAV_MODE_FLAG;
import com.MAVLink.enums.MAV_PROTOCOL_CAPABILITY;
import com.MAVLink.enums.MAV_RESULT;
import com.MAVLink.enums.MAV_STATE;
import com.MAVLink.enums.MAV_TYPE;
import com.MAVLink.minimal.msg_heartbeat;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.PortUnreachableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.flightcontroller.Attitude;
import dji.common.flightcontroller.ConnectionFailSafeBehavior;
import dji.common.flightcontroller.ControlMode;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.FlightMode;
import dji.common.flightcontroller.FlightOrientationMode;
import dji.common.flightcontroller.GPSSignalLevel;
import dji.common.flightcontroller.LEDsSettings;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.flightcontroller.simulator.InitializationData;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.mission.MissionState;
import dji.common.mission.followme.FollowMeHeading;
import dji.common.mission.followme.FollowMeMission;
import dji.common.mission.followme.FollowMeMissionState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionState;
import dji.common.model.LocationCoordinate2D;
import dji.common.product.Model;
import dji.common.remotecontroller.HardwareState;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.airlink.AirLink;
import dji.sdk.base.BaseProduct;
import dji.sdk.battery.Battery;
import dji.sdk.camera.Camera;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.media.DownloadListener;
import dji.sdk.media.FetchMediaTaskScheduler;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.followme.FollowMeMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

import static com.MAVLink.enums.MAV_CMD.MAV_CMD_COMPONENT_ARM_DISARM;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_DIGICAM_CONTROL;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_SET_SERVO;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_TAKEOFF;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_START_CAPTURE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_STOP_CAPTURE;
import static com.MAVLink.enums.MAV_COMPONENT.MAV_COMP_ID_AUTOPILOT1;
import static com.MAVLink.enums.MAV_FRAME.*;
import static com.MAVLink.enums.POSITION_TARGET_TYPEMASK.*;
import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;
import static sq.rogue.rosettadrone.Functions.EARTH_RADIUS;
import static sq.rogue.rosettadrone.util.getTimestampMicroseconds;
import static sq.rogue.rosettadrone.util.safeSleep;

enum YawDirection {
    DEST, // Look at destination
    POI,    // Look at POI
    FIXED,  // Use a fixed yaw
    KEEP    // Don't rotate the yaw. Keep current angle.
}

public class DroneModel implements CommonCallbacks.CompletionCallback {
    private final int MOTION_PERIOD_MS = 50;

    private boolean isSimulator;
    private static final int NOT_USING_GCS_COMMANDED_MODE = -1;
    private final String TAG = DroneModel.class.getSimpleName();
    public DatagramSocket socket;
    DatagramSocket secondarySocket;
    private Aircraft djiAircraft;
    private ArrayList<MAVParam> params = new ArrayList<>();
    private long ticks = 0;
    private MainActivity parent;

    private TimeLineMissionControlView TimeLine = new TimeLineMissionControlView();

    private int mSystemId = 1;
    private int gcsFlightMode; // Only set by the GCS. Overwrites the flightMode reported to the GCS (if gcsFlightMode != NOT_USING_GCS_COMMANDED_MODE)

    private int mThrottleSetting = 0;
    private int mLeftStickVertical = 0;
    private int mLeftStickHorisontal = 0;
    private int mRightStickVertical = 0;
    private int mRightStickHorisontal = 0;
    private boolean mC1 = false;
    private boolean mC2 = false;
    private boolean mC3 = false;

    private int mCFullChargeCapacity_mAh = 0;
    private int mCChargeRemaining_mAh = 0;
    private int mCVoltage_mV = 0;
    private int mCVoltage_pr = 0;
    private int mCCurrent_mA = 0;
    private float mCBatteryTemp_C = 0;
    private int mlastState = 100;

    private int mFullChargeCapacity_mAh = 0;
    private int mChargeRemaining_mAh = 0;
    private int mVoltage_mV = 0;
    private int mVoltage_pr = 0;
    private int mCurrent_mA = 0;
    private int[] mCellVoltages = new int[10];
    private int mDownlinkQuality = 0;
    private int mUplinkQuality = 0;
    private int mControllerVoltage_pr = 0;

    private double m_Latitude = 0;
    private double m_Longitude = 0;
    public float m_alt = 0;

    Motion motion = null;

    private FlightMode djiFlightMode = FlightMode.ATTI_HOVER;
    private HardwareState.FlightModeSwitch rcMode = HardwareState.FlightModeSwitch.POSITION_ONE;
    private static HardwareState.FlightModeSwitch forbiddenModeSwitch = HardwareState.FlightModeSwitch.POSITION_ONE; // SwitchMode forbidden to take-off. Depends on each model.
    public msg_home_position home_position = new msg_home_position();

    private MotionTask motionTask = null;
    private Timer motionTimer = null;
    private Rotation m_ServoSet;
    private float m_ServoPos_pitch = 0;
    private float m_ServoPos_yaw = 0;

    private MiniPID miniPIDSide;
    private MiniPID miniPIDFwd;
    private MiniPID miniPIDAlti;
    private MiniPID miniPIDHeading;

    private boolean mSafetyEnabled = true;
    private boolean mMotorsArmed = false;
    private FollowMeMissionOperator fmmo;
    public FlightController mFlightController;
    private Gimbal mGimbal = null;

    public float mission_alt = 0;

    Model m_model;
    Camera m_camera;
    Aircraft m_aircraft;

    File destDir = new File(Environment.getExternalStorageDirectory().getPath() + "/DroneApp/");
    String m_directory;

    private int mAIfunction_activation = 0;
    public boolean mAutonomy = false;   // Sends the MAV_MODE_FLAG_AUTO_ENABLED flag (autonomous mode). TODO: DEPRECATE: Use missionManager.isAutoMode()
    private boolean missionActive = false; // true if started, false if stopped or paused. TODO: DEPRECATE: This flag cannot be trusted, since it is not changed when a mission ends or is interrupted. Use: isMissionActive()

    // FTP...
    // TODO: Move to plugin or FTP class
    public List<MediaFile> mediaFileList = new ArrayList<MediaFile>();
    public int numFiles;
    private MediaManager mMediaManager;
    private MediaManager.FileListState currentFileListState = MediaManager.FileListState.UNKNOWN;
    private FetchMediaTaskScheduler scheduler;
    public String last_downloaded_file;
    public int lastDownloadedIndex = -1;
    public int currentProgress = -1;
    
    public final int MAV_LINK_MULTI = 10000000;

    private boolean useMissionControlClass; // Not supported by Mini series
    public boolean useMissionManager; // Uses VirtualSticks, instead of using onboard logic. May also give better results for taking Photos during Waypoints.
    public MissionManager missionManager = new MissionManager(this);

    enum camera_mode {
        IDLE,
        SUCCSESS,
        DISCONNECTED,
        MEDIAMANAGER,
        DOWNLOADMODESUPPORTED,
        FAILURE,
        TIMEOUT
    }

    public camera_mode switch_camera_mode = camera_mode.IDLE;

    DroneModel(MainActivity parent, DatagramSocket socket, boolean sim) {
        this.parent = parent;
        this.socket = socket;
        this.numFiles = 0;
        this.isSimulator = sim;

        initFlightController();
    }

    /**
     * Sometimes the SDK returns nulls and we need to reconnect or restart the app.
     * TODO: Get back to connection screen.
     */
    private void fatalConnectionError(String msg) {
        Log.e(TAG, msg);
    }

    private void initFlightController() {
        parent.logMessageDJI("Starting FlightController...");

        // PID for position control...

        // TODO: Finetune PID values
        miniPIDFwd = new MiniPID(0.35, 0.0001, 4.0);
        miniPIDSide = new MiniPID(0.35, 0.0001, 4.0);
        miniPIDAlti = new MiniPID(0.35, 0.0001, 4.0);
        miniPIDHeading = new MiniPID(1.0, 0.00001, 2.0);

        m_aircraft = (Aircraft) RDApplication.getProductInstance(); //DJISimulatorApplication.getAircraftInstance();
        if (m_aircraft == null || !m_aircraft.isConnected()) {
            parent.logMessageDJI("No target...");
            mFlightController = null;
            return;

        } else {
            m_model = m_aircraft.getModel();

            if (m_model == null) {
                fatalConnectionError("Couldn't get model. Reconnect or restart app.");
                return;
            }

            if (m_model.equals("INSPIRE_1") || m_model.equals("INSPIRE_1_PRO") || m_model.equals("INSPIRE_1_RAW")) {
                forbiddenModeSwitch = HardwareState.FlightModeSwitch.POSITION_THREE;
            }

            // These models don't support waypoint missions onboard, so we use our MissionManager with VirtualSticks
            useMissionManager = m_model == Model.MAVIC_MINI
                    || m_model == Model.DJI_MINI_SE
                    || m_model == Model.DJI_MINI_2
                    || m_model == Model.DJI_AIR_2S
                    ;

            useMissionControlClass = !useMissionManager;

            mGimbal = m_aircraft.getGimbal();
            m_camera = RDApplication.getCameraInstance();
            mFlightController = m_aircraft.getFlightController();
            if (mFlightController == null) {
                fatalConnectionError("mFlightController == null");
                return;
            }

            setControlModes();

            mFlightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);

            // Not supported by Mavic Mini, DJI Mini 2, DJI Mini SE and Mavic Air 2, DJI Air 2S, so we do it on our own.
            mFlightController.setFlightOrientationMode(FlightOrientationMode.COURSE_LOCK, null);

            if (isSimulator) {
                parent.logMessageDJI("Starting Simulator...");
                mFlightController.getSimulator().setStateCallback(stateData -> new Handler(Looper.getMainLooper()).post(() -> {
                    /*
                    String yaw = String.format("%.2f", stateData.getYaw());
                    String pitch = String.format("%.2f", stateData.getPitch());
                    String roll = String.format("%.2f", stateData.getRoll());
                    String positionX = String.format("%.2f", stateData.getPositionX());
                    String positionY = String.format("%.2f", stateData.getPositionY());
                    String positionZ = String.format("%.2f", stateData.getPositionZ());

                    Log.v("SIM", "Yaw : " + yaw + ", Pitch : " + pitch + ", Roll : " + roll + "\n" + ", PosX : " + positionX +
                            ", PosY : " + positionY +
                            ", PosZ : " + positionZ);

                    */
                }));
            }
        }

        if (mFlightController != null) {
            parent.logMessageDJI("Target found...");

            if (isSimulator) {
                LocationCoordinate2D pos = getSimPos2D();

                mFlightController.getSimulator()
                        .start(InitializationData.createInstance(pos, 10, 10),
                                djiError -> {
                                    if (djiError != null) {
                                        parent.logMessageDJI(djiError.getDescription());
                                    } else {
                                        parent.logMessageDJI("Start Simulator Success");
                                    }
                                });
            }
        }
    }

    LocationCoordinate3D getSimPos3D() {
        LocationCoordinate3D pos = new LocationCoordinate3D(
                Double.parseDouble(Objects.requireNonNull(parent.sharedPreferences.getString("pref_sim_pos_lat", "-1"))),
                Double.parseDouble(Objects.requireNonNull(parent.sharedPreferences.getString("pref_sim_pos_lon", "-1"))),
                Float.parseFloat(Objects.requireNonNull(parent.sharedPreferences.getString("pref_sim_pos_alt", "-1")))
        );

        // If this is the first time the app is running...
        if (pos.getLatitude() == -1) {
            parent.sharedPreferences.getStringSet("pref_sim_pos_lat", Collections.singleton("60.4094"));
            pos.setLatitude(60.4094);
        }
        if (pos.getLongitude() == -1) {
            parent.sharedPreferences.getStringSet("pref_sim_pos_lon", Collections.singleton("10.4911"));
            pos.setLongitude(10.4911);
        }
        if (pos.getAltitude() == -1) {  // Not Used...
            parent.sharedPreferences.getStringSet("pref_sim_pos_alt", Collections.singleton("210.0"));
            pos.setAltitude((float) 210.0);
        }
        return (pos);
    }

    LocationCoordinate2D getSimPos2D() {
        LocationCoordinate3D pos3d = getSimPos3D();
        LocationCoordinate2D pos = new LocationCoordinate2D(pos3d.getLatitude(), pos3d.getLongitude());
        return (pos);
    }

    int getSystemId() {
        return mSystemId;
    }

    void setSystemId(int id) {
        mSystemId = id;
    }

    void setRTLAltitude(final int altitude) {
        djiAircraft.getFlightController().setGoHomeHeightInMeters(altitude, djiError -> {
            if (djiError == null) {
                parent.logMessageDJI("RTL altitude set to " + altitude + "m");

            } else {
                parent.logMessageDJI("Error setting RTL altitude " + djiError.getDescription());
            }
        });
    }

    // These are no long used as this is an internal process now, sending data to RemoteConfig...
    void setAiIP(final String ip) {
    }

    void setAiPort(final int port) {
    }

    void setAIenable(final boolean enable) {
        parent.mMavlinkReceiver.AIenabled = enable;
        parent.pluginManager.setAIMode();
    }

    void setMaxHeight(final int height) {
        djiAircraft.getFlightController().setMaxFlightHeight(height, djiError -> {
            if (djiError == null) {
                parent.logMessageDJI("Max height set to " + height + "m");

            } else {
                parent.logMessageDJI("Error setting max height " + djiError.getDescription());
            }
        });
    }

    void setSmartRTLEnabled(final boolean enabled) {
        djiAircraft.getFlightController().setSmartReturnToHomeEnabled(enabled, djiError -> {
            if (djiError == null) {
                parent.logMessageDJI("Smart RTL set to " + enabled);

            } else {
                parent.logMessageDJI("Error setting smart RTL " + djiError.getDescription());
            }
        });
    }

    void setMultiModeEnabled(final boolean enabled) {
        djiAircraft.getFlightController().setMultipleFlightModeEnabled(enabled, djiError -> {
            if (djiError == null) {
                parent.logMessageDJI("Multi Mode set to " + enabled);

            } else {
                parent.logMessageDJI("Error setting multiple flight modes to  " + enabled + djiError.getDescription());
            }
        });
    }

    void setForwardLEDsEnabled(final boolean enabled) {
        djiAircraft.getFlightController().getLEDsEnabledSettings(new CommonCallbacks.CompletionCallbackWith<LEDsSettings>() {
            @Override
            public void onSuccess(LEDsSettings ledsSettings) {
                LEDsSettings.Builder builder = new LEDsSettings.Builder();
                builder.frontLEDsOn(enabled);
                builder.beaconsOn(ledsSettings.areBeaconsOn());
                builder.rearLEDsOn(ledsSettings.areRearLEDsOn());
                builder.statusIndicatorOn(true);
                djiAircraft.getFlightController().setLEDsEnabledSettings(
                        builder.build(),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError error) {
                                if (error == null) {
                                    Log.d(TAG, "LEDs settings successfully set");
                                } else {
                                    Log.e(TAG, "Failed to set LEDs settings: " + error.getDescription());
                                }
                            }
                        }
                );
            }

            @Override
            public void onFailure(DJIError djiError) {
                parent.logMessageDJI("Error while getting LEDs Settings: " + djiError.getDescription());
            }
        });
    }

    void setCollisionAvoidance(final boolean enabled) {
        if (djiAircraft.getFlightController().getFlightAssistant() != null) {
            djiAircraft.getFlightController().getFlightAssistant().setCollisionAvoidanceEnabled(enabled, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        parent.logMessageDJI("Collision avoidance set to " + enabled);

                    } else {
                        parent.logMessageDJI("Error setting collision avoidance  " + djiError.getDescription());
                    }
                }
            });
        } else {
            parent.logMessageDJI("Error setting collision avoidance to " + enabled);
        }
    }

    void setUpwardCollisionAvoidance(final boolean enabled) {

        if (djiAircraft.getFlightController().getFlightAssistant() != null) {
            djiAircraft.getFlightController().getFlightAssistant().setUpwardVisionObstacleAvoidanceEnabled(enabled, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        parent.logMessageDJI("Upward collision avoidance set to " + enabled);

                    } else {
                        parent.logMessageDJI("Error setting upward collision avoidance  " + djiError.getDescription());
                    }
                }
            });
        } else {
            parent.logMessageDJI("Error setting upward collision avoidance to " + enabled);
        }
    }

    void setLandingProtection(final boolean enabled) {
        if (djiAircraft.getFlightController().getFlightAssistant() != null) {
            djiAircraft.getFlightController().getFlightAssistant().setLandingProtectionEnabled(enabled, djiError -> {
                if (djiError == null) {
                    parent.logMessageDJI("Landing Protection set to " + enabled);

                } else {
                    parent.logMessageDJI("Error setting landing protection " + djiError.getDescription());
                }
            });
        } else {
            parent.logMessageDJI("Error setting landing protection to " + enabled);
        }
    }

    void setGcsFlightMode(int mode) {
        gcsFlightMode = mode;
    }

    // Not used by MissionManager
    void setWaypointMission(final WaypointMission wpMission) {
        DJIError load_error = getWaypointMissionOperator().loadMission(wpMission);
        if (load_error != null)
            parent.logMessageDJI("loadMission() returned error: " + load_error.toString());
        else {
            // We can not wait until we know the results as it takes too long for QGC on large missions...
            send_mission_ack(MAV_MISSION_RESULT.MAV_MISSION_ACCEPTED);

            parent.logMessageDJI("Uploading mission");
            getWaypointMissionOperator().uploadMission(
                    djiError -> {
                        if (djiError == null) {
                            while (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.UPLOADING) {
                                safeSleep(100);
                            }
                            if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.READY_TO_EXECUTE) {
                                send_mission_ack(MAV_MISSION_RESULT.MAV_MISSION_ACCEPTED);
                                send_text("Mission uploaded");
                                parent.logMessageDJI("Mission uploaded and ready to execute!");
                            } else {
                                send_mission_ack(MAV_MISSION_RESULT.MAV_MISSION_ERROR);
                                send_text("Error uploading mission");
                                parent.logMessageDJI("Error uploading waypoint mission to drone.");
                            }
                        } else {
                            send_mission_ack(MAV_MISSION_RESULT.MAV_MISSION_ERROR);
                            send_text("Error uploading mission");
                            parent.logMessageDJI("Error uploading: " + djiError.getDescription());
                        }
                        //parent.logMessageDJI("New state: " + getWaypointMissionOperator().getCurrentState().getName());
                    }
            );
        }
    }

    private Aircraft getDjiAircraft() {
        return djiAircraft;
    }

    boolean isSafetyEnabled() {
        return mSafetyEnabled;
    }

    void setSafetyEnabled(boolean SafetyEnabled) {
        mSafetyEnabled = SafetyEnabled;
    }

    private void SetMesasageBox(String msg) {
        AlertDialog.Builder alertDialog2 = new AlertDialog.Builder(parent);
        alertDialog2.setTitle(msg);
        alertDialog2.setMessage("Please Land !!!");
        alertDialog2.setPositiveButton("Accept",
                (dialog, which) -> {
                    dialog.cancel();
                    //dismiss the dialog
                });

        parent.runOnUiThread(() -> {
            //          Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            //          Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            //          r.play();
            alertDialog2.show();
        });

    }

    boolean setDjiAircraft(Aircraft djiAircraft) {

        if (djiAircraft == null || djiAircraft.getRemoteController() == null)
            return false;

        this.djiAircraft = djiAircraft;

        Arrays.fill(mCellVoltages, 0xffff); // indicates no cell per mavlink definition

        /**************************************************
         * Called whenever RC state changes               *
         **************************************************/

        this.djiAircraft.getRemoteController().setHardwareStateCallback(new HardwareState.HardwareStateCallback() {
            //   boolean lastState = false;

            @Override
            public void onUpdate(@NonNull HardwareState rcHardwareState) {
                // DJI: range [-660,660]
                mThrottleSetting = (Objects.requireNonNull(rcHardwareState.getLeftStick()).getVerticalPosition() + 660) / 1320;

                // Mavlink: 1000 to 2000 with 1500 = 1.5ms as center...
                mLeftStickVertical = (int) (rcHardwareState.getLeftStick().getVerticalPosition() * 0.8) + 1500;
                mLeftStickHorisontal = (int) (rcHardwareState.getLeftStick().getHorizontalPosition() * 0.8) + 1500;
                mRightStickVertical = (int) (rcHardwareState.getRightStick().getVerticalPosition() * 0.8) + 1500;
                mRightStickHorisontal = (int) (rcHardwareState.getRightStick().getHorizontalPosition() * 0.8) + 1500;
                mC1 = Objects.requireNonNull(rcHardwareState.getC1Button()).isClicked();
                mC2 = Objects.requireNonNull(rcHardwareState.getC2Button()).isClicked();
                mC3 = Objects.requireNonNull(rcHardwareState.getC3Button()).isClicked();

                rcMode = Objects.requireNonNull(rcHardwareState.getFlightModeSwitch());
            }
        });

        this.djiAircraft.getRemoteController().setChargeRemainingCallback(new dji.common.remotecontroller.BatteryState.Callback() {
            private int lastState = 100;

            @Override
            public void onUpdate(dji.common.remotecontroller.BatteryState batteryState) {
                mControllerVoltage_pr = (batteryState.getRemainingChargeInPercent());
                if (mControllerVoltage_pr > 90) {
                    lastState = 100;
                } else if (mControllerVoltage_pr < 20 && lastState == 100) {
                    lastState = 20;
                    SetMesasageBox("Controller Battery Warning 20% !!!!!");
                } else if (mControllerVoltage_pr < 10 && lastState == 20) {
                    lastState = 10;
                    SetMesasageBox("Controller Battery Warning 10% !!!!!");
                } else if (mControllerVoltage_pr < 5 && lastState == 10) {
                    lastState = 5;
                    SetMesasageBox("Controller Battery Warning 5% !!!!!");
                }
            }
        });

        /**************************************************
         * Called whenever battery state changes          *
         **************************************************/

        if (this.djiAircraft != null) {
            parent.logMessageDJI("setBatteryCallback");
            this.djiAircraft.getBattery().setStateCallback(batteryState -> {
                mCFullChargeCapacity_mAh = batteryState.getFullChargeCapacity();
                mCChargeRemaining_mAh = batteryState.getChargeRemaining();
                mCVoltage_mV = batteryState.getVoltage();
                mCCurrent_mA = Math.abs(batteryState.getCurrent());
                mCBatteryTemp_C = batteryState.getTemperature();
                mCVoltage_pr = batteryState.getChargeRemainingInPercent();

                if (mCVoltage_pr > 0) {
                    if (mCVoltage_pr > 90) {
                        mlastState = 100;
                    } else if (mCVoltage_pr <= 20 && mlastState == 100) {
                        mlastState = 20;
                        SetMesasageBox("Drone Battery Warning 20% !!!!!");
                    } else if (mCVoltage_pr <= 10 && mlastState == 20) {
                        mlastState = 10;
                        SetMesasageBox("Drone Battery Warning 10% !!!!!");
                    } else if (mCVoltage_pr <= 5 && mlastState == 10) {
                        mlastState = 5;
                        SetMesasageBox("Drone Battery Warning 5% !!!!!");
                    }
                }
            });
            this.djiAircraft.getBattery().getCellVoltages(new CellVoltageCompletionCallback());
        } else {
            Log.e(TAG, "djiAircraft.getBattery() IS NULL");
            return false;
        }

        Battery.setAggregationStateCallback(aggregationState -> {
            mFullChargeCapacity_mAh = aggregationState.getFullChargeCapacity();
            mChargeRemaining_mAh = aggregationState.getChargeRemaining();
            mVoltage_mV = aggregationState.getVoltage();
            mCurrent_mA = aggregationState.getCurrent();
            mVoltage_pr = aggregationState.getChargeRemainingInPercent();
        });

        /**************************************************
         * Called whenever airlink quality changes        *
         **************************************************/

        AirLink airLink = djiAircraft.getAirLink();
        if(airLink != null) {
            airLink.setDownlinkSignalQualityCallback(i -> mDownlinkQuality = i);
            airLink.setUplinkSignalQualityCallback(i -> mUplinkQuality = i);
        } else {
            Log.e(TAG, "Failed to get AirLink");
        }

        initMissionOperator();

        return true;
    }

    WaypointMissionOperator getWaypointMissionOperator() {
        return MissionControl.getInstance().getWaypointMissionOperator();
    }

    private void initMissionOperator() {
        getWaypointMissionOperator().removeListener(null);
        RosettaMissionOperatorListener mMissionOperatorListener = new RosettaMissionOperatorListener();
        mMissionOperatorListener.setMainActivity(parent);
        getWaypointMissionOperator().addListener(mMissionOperatorListener);
    }

    public ArrayList<MAVParam> getParams() {
        return params;
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public void setSocket(DatagramSocket socket) {
        this.socket = socket;
    }

    void setSecondarySocket(DatagramSocket socket) {
        this.secondarySocket = socket;
    }


    void tick() { // Called ever 100ms...
        ticks += 100;

        if (djiAircraft == null)
            return;

        try {
            if (ticks % 100 == 0) {
                send_attitude();
                send_altitude();
                send_vibration();
                send_vfr_hud();
            }
            if (ticks % 200 == 0) {
                send_global_position_int(); // We use this for the AI se need 5Hz...
            }
            if (ticks % 300 == 0) {
                send_gps_raw_int();
                send_radio_status();
                send_rc_channels();
            }
            if (ticks % 1000 == 0) {
                send_heartbeat();
                confirmLanding();
                send_sys_status();
                send_power_status();
                send_battery_status();
            }
            if (ticks % 5000 == 0) {
                send_home_position();
            }

        } catch (Exception e) {
            Log.d(TAG, "exception", e);
        }
    }

    boolean armMotors() {
        if(!checkSafeMode()) return false;
        mMotorsArmed = true;

//
//        djiAircraft.getFlightController().turnOnMotors(new CommonCallbacks.CompletionCallback() {
//            @Override
//            public void onResult(DJIError djiError) {
//                // TODO reattempt if arming/disarming fails
//                if (djiError == null) {
//                    send_command_ack(MAV_CMD_COMPONENT_ARM_DISARM, MAV_RESULT.MAV_RESULT_ACCEPTED);
//                    mSafetyEnabled = false;
//                } else
//                    send_command_ack(MAV_CMD_COMPONENT_ARM_DISARM, MAV_RESULT.MAV_RESULT_FAILED);
//                Log.d(TAG, "onResult()");
//            }
//        });
        return true;
    }

    void disarmMotors(boolean sendResponses) {
        djiAircraft.getFlightController().turnOffMotors(new CommonCallbacks.CompletionCallback() {

            @Override
            public void onResult(DJIError djiError) {
                // TODO reattempt if arming/disarming fails
                if (djiError == null)
                    if(sendResponses) send_command_ack(MAV_CMD_COMPONENT_ARM_DISARM, MAV_RESULT.MAV_RESULT_ACCEPTED);
                else
                    if(sendResponses) send_command_ack(MAV_CMD_COMPONENT_ARM_DISARM, MAV_RESULT.MAV_RESULT_FAILED);
                Log.d(TAG, "onResult()");
                mMotorsArmed = false;
            }
        });
    }

    public void sendMessage(MAVLinkMessage msg) {
        if (socket == null)
            return;

        MAVLinkPacket packet = msg.pack();

        if(msg.msgid == 22 || msg.msgid == 86) {
            int d = 0;
        }

        packet.sysid = mSystemId;
        packet.compid = MAV_COMP_ID_AUTOPILOT1;

        byte[] bytes = packet.encodePacket();

        try {
            DatagramPacket p = new DatagramPacket(bytes, bytes.length, socket.getInetAddress(), socket.getPort());
            socket.send(p);
            parent.logMessageToGCS(msg.toString());

            if (secondarySocket != null) {
                DatagramPacket secondaryPacket = new DatagramPacket(bytes, bytes.length, secondarySocket.getInetAddress(), secondarySocket.getPort());
                secondarySocket.send(secondaryPacket);
            }

        } catch (PortUnreachableException ignored) {

        } catch (IOException e) {

        }
    }

    void send_autopilot_version() {
        msg_autopilot_version msg = new msg_autopilot_version();
        msg.capabilities = MAV_PROTOCOL_CAPABILITY.MAV_PROTOCOL_CAPABILITY_COMMAND_INT;
        msg.capabilities |= MAV_PROTOCOL_CAPABILITY.MAV_PROTOCOL_CAPABILITY_MISSION_INT;
        msg.os_sw_version = 0x040107;
        msg.middleware_sw_version = 0x040107;
        msg.flight_sw_version = 0x040107;
        sendMessage(msg);
    }

    // Cancel all guided and auto tasks (threads)
    public void cancelAllTasks() {
        Log.i(TAG, "cancelAllTasks()");
        pauseWaypointMission();
        cancelMotion();
        cancelLanding();
        mAutonomy = false;
    }

    void cancelLanding() {
        if(djiFlightMode == FlightMode.AUTO_LANDING) {
            djiAircraft.getFlightController().cancelLanding(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    parent.logMessageDJI("Landing confirmed");
                }
            });
        }
    }

    private void confirmLanding() {
        if (djiAircraft.getFlightController().getState().isLandingConfirmationNeeded()) {
            mFlightController.confirmLanding(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    parent.logMessageDJI("Landing confirmed");
                }
            });
        }
    }

    private void send_heartbeat() {
        msg_heartbeat msg = new msg_heartbeat();
        msg.type = MAV_TYPE.MAV_TYPE_QUADROTOR;
        msg.autopilot = MAV_AUTOPILOT.MAV_AUTOPILOT_ARDUPILOTMEGA;

        // For base mode logic, see Copter::sendHeartBeat() in ArduCopter/GCS_Mavlink.cpp
        msg.base_mode = MAV_MODE_FLAG.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED;
        msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_MANUAL_INPUT_ENABLED;

        //parent.logMessageDJI("FlightMode: "+djiAircraft.getFlightController().getState().getFlightMode());

        djiFlightMode = djiAircraft.getFlightController().getState().getFlightMode();
        switch (djiFlightMode) {
            case MANUAL:
                msg.custom_mode = ArduCopterFlightModes.STABILIZE;
                break;

            case ATTI:
                msg.custom_mode = ArduCopterFlightModes.LOITER;
                break;

            case AUTO_TAKEOFF:
                msg.custom_mode = ArduCopterFlightModes.GUIDED;
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;

            case AUTO_LANDING:
                msg.custom_mode = ArduCopterFlightModes.LAND;
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;

            case GPS_WAYPOINT:
                msg.custom_mode = ArduCopterFlightModes.AUTO;
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;

            case GO_HOME:
                msg.custom_mode = ArduCopterFlightModes.RTL;
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;

            case JOYSTICK:
                // JOYSTICK is also used in Auto Mode
                //msg.custom_mode = ArduCopterFlightModes.GUIDED;
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;

            case DRAW:
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;

            case GPS_FOLLOW_ME:
                msg.custom_mode = ArduCopterFlightModes.GUIDED;
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;

            case ACTIVE_TRACK:
                msg.custom_mode = ArduCopterFlightModes.GUIDED;
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;

            case TAP_FLY:
                msg.custom_mode = ArduCopterFlightModes.GUIDED;
                break;

            /*
            case GPS_ATTI:
            case ATTI_HOVER:
            case HOVER:
            case GPS_BLAKE:
            case ATTI_LANDING:
            case CLICK_GO:
            case CINEMATIC:
            case ATTI_LIMITED:
            case PANO:
            case FARMING:
            case FPV:
            case PALM_CONTROL:
            case QUICK_SHOT:
            case DETOUR:
            case TIME_LAPSE:
            case POI2:
            case OMNI_MOVING:
            case ADSB_AVOIDING:
            case ATTI_COURSE_LOCK:
            case GPS_COURSE_LOCK:
            case GPS_HOME_LOCK:
            case GPS_HOT_POINT:
            case ASSISTED_TAKEOFF:
            case GPS_SPORT:
            case GPS_NOVICE:
            case UNKNOWN:
            case CONFIRM_LANDING:
            case TERRAIN_FOLLOW:
            case TRIPOD:
            case TRACK_SPOTLIGHT:
            case MOTORS_JUST_STARTED:
            case GPS_ATTI_WRISTBAND:
            */
        }

        if (mMotorsArmed)
            msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_SAFETY_ARMED;

        if(useMissionManager) {
            // TODO: Consider sending own flightmodes when in gcsFlightMode = AUTO.
            if (missionManager.isAutoMode()) {
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_AUTO_ENABLED;
            }

            // When starting a new mission, the GCS expects us to switch to GUIDED mode. So we only send BRAKE when in AUTO mode.
            if(gcsFlightMode == ArduCopterFlightModes.AUTO && missionManager.paused) {
                msg.custom_mode = ArduCopterFlightModes.BRAKE;
            }

        } else {
	        if (mAutonomy)
		        msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_AUTO_ENABLED;
        }

	    if(gcsFlightMode != NOT_USING_GCS_COMMANDED_MODE) {
			// Overwrites the internal flight mode
		    msg.custom_mode = gcsFlightMode;
	    } else {
            // TODO: DJI flight modes shouldn't be informed to the GCS. Some DJI modes can be used during GUIDED or AUTO mode. In general, we should respect the gcsFlightMode.
            Log.d(TAG, "Sending custom flight mode (not set by GCS): " + msg.custom_mode);
        }

        // Catches manual landings
        // Automatically disarm motors if aircraft is on the ground and a takeoff is not in progress
        if (!getDjiAircraft().getFlightController().getState().isFlying() && gcsFlightMode != ArduCopterFlightModes.GUIDED) {
            if(useMissionManager) {
                missionManager.setLandedStatus(true);
            }
            mMotorsArmed = false;
        }

        // Catches manual takeoffs
        if (getDjiAircraft().getFlightController().getState().areMotorsOn())
            mMotorsArmed = true;

        msg.system_status = MAV_STATE.MAV_STATE_ACTIVE;
        msg.mavlink_version = 3;
        sendMessage(msg);
    }

    private void send_attitude() {
        msg_attitude msg = new msg_attitude();
        // TODO: this next line causes an exception
        //msg.time_boot_ms = getTimestampMilliseconds();
        Attitude att = djiAircraft.getFlightController().getState().getAttitude();
        msg.roll = (float) (att.roll * Math.PI / 180);
        msg.pitch = (float) (att.pitch * Math.PI / 180);
        msg.yaw = (float) (att.yaw * Math.PI / 180);
        // TODO msg.rollspeed = 0;
        // TODO msg.pitchspeed = 0;
        // TODO msg.yawspeed = 0;
        sendMessage(msg);
    }

    private void send_altitude() {
        msg_altitude msg = new msg_altitude();
        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        msg.altitude_relative = coord.getAltitude();
        m_alt = msg.altitude_relative;
        sendMessage(msg);
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
        sendMessage(msg);
    }

    void send_command_ack(int message_id, int result) {
        msg_command_ack msg = new msg_command_ack();
        msg.command = message_id;
        msg.result = (short) result;
        sendMessage(msg);
    }

    void send_command_ftp_string_ack(int message_id, String data, byte[] header) {
        msg_file_transfer_protocol msg = new msg_file_transfer_protocol();
        byte[] byteString = data.getBytes();
        short[] shortArray = new short[251];


        if (header != null) {
            for (int i = 0; i < header.length; i++) {
                short s = (short) (header[i] & 0xFF);
                shortArray[i] = s;
            }
        }

        int sizeCounter = 0;
        for (int i = 0; i < byteString.length; i++) {
            int added = 12 + i;
            short s = byteString[i];
            shortArray[added] = s;
            sizeCounter += 1;
        }
        shortArray[3] = (short) 128;
        shortArray[4] = (short) sizeCounter;
        msg.payload = shortArray;
        msg.msgid = message_id;

        sendMessage(msg);
    }

    void send_command_ftp_bytes_ack(int message_id, byte[] data) {
        msg_file_transfer_protocol msg = new msg_file_transfer_protocol();
        short[] shortArray = new short[data.length];
        for (int i = 0; i < data.length; i++) {
            short s = (short) (data[i] & 0xFF);
            shortArray[i] = s;
        }

        shortArray[3] = (short) 128;

        msg.payload = shortArray;
        msg.msgid = message_id;

        sendMessage(msg);
    }

    void send_command_ftp_nak(int message_id, int errorCode, int failCode) {
        msg_file_transfer_protocol msg = new msg_file_transfer_protocol();
        short[] shortArray = new short[251];

        shortArray[3] = (short) 129; //nak code
        if (failCode != 0) {
            shortArray[4] = (short) 2; //error size
            shortArray[13] = (short) failCode;
        } else {
            shortArray[4] = (short) 1; //error size
        }
        shortArray[12] = (short) errorCode; // the error code

        msg.msgid = message_id;
        sendMessage(msg);
    }

    private void send_global_position_int() {
        msg_global_position_int msg = new msg_global_position_int();

        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        msg.lat = (int) (coord.getLatitude() * MAV_LINK_MULTI);
        msg.lon = (int) (coord.getLongitude() * MAV_LINK_MULTI);

        // This is a bad idea, but the best we can do...
        msg.alt = (int) ((coord.getAltitude()+mission_alt) * Math.pow(10, 3));

        // NOTE: Commented out this field, because msg.relative_alt seems to be intended for altitude above the current terrain,
        // but DJI reports altitude above home point.
        // Mavlink: Millimeters above ground (unspecified: presumably above home point?)
        // DJI: relative altitude of the aircraft relative to take off location, measured by barometer, in meters.
        msg.relative_alt = (int) (coord.getAltitude() * 1000);

        // Mavlink: Millimeters AMSL
        // msg.alt = ??? No method in SDK for obtaining MSL altitude.
        // djiAircraft.getFlightController().getState().getHomePointAltitude()) seems promising, but always returns 0

        // Mavlink: m/s*100
        // DJI: m/s
        msg.vx = (short) (djiAircraft.getFlightController().getState().getVelocityX() * 100); // positive values N
        msg.vy = (short) (djiAircraft.getFlightController().getState().getVelocityY() * 100); // positive values E
        msg.vz = (short) (djiAircraft.getFlightController().getState().getVelocityZ() * 100); // positive values down

        // DJI=[-180,180] where 0 is true north, Mavlink=degrees
        // TODO unspecified in Mavlink documentation whether this heading is true or magnetic
        double yaw = djiAircraft.getFlightController().getState().getAttitude().yaw;
        if (yaw < 0)
            yaw += 360;
        msg.hdg = (int) (yaw * 100);

        sendMessage(msg);
    }

    public double[] getLocation2D() {
        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        return new double[] {coord.getLatitude(), coord.getLongitude()};
    }

    public double[] getLocation3D() {
        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        return new double[] {coord.getLatitude(), coord.getLongitude(), coord.getAltitude()};
    }

    public float getGoHomeHeight() {
        return djiAircraft.getFlightController().getState().getGoHomeHeight();
    }

    // TODO: DEPRECATE: Inefficient

    public double get_current_lat() {
        if (mFlightController == null) {
            Log.d(TAG, "getSimPos3D().getLatitude()="+getSimPos3D().getLatitude());
            return getSimPos3D().getLatitude();
        }

        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        Log.d(TAG, "coord.getLatitude()="+coord.getLatitude());
        return coord.getLatitude();
    }

    public double get_current_lon() {
        if (mFlightController == null)
            return getSimPos3D().getLongitude();

        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        return coord.getLongitude();
    }

    public float get_current_alt() {
        if (mFlightController == null)
            return getSimPos3D().getAltitude();

        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        return coord.getAltitude();
    }

    public double getCurrentYaw() {
        return angleDjiToMav(mFlightController.getState().getAttitude().yaw);
    }

    private void send_gps_raw_int() {
        msg_gps_raw_int msg = new msg_gps_raw_int();

        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();

        msg.time_usec = getTimestampMicroseconds();
        msg.lat = (int) (coord.getLatitude() * MAV_LINK_MULTI);
        msg.lon = (int) (coord.getLongitude() * MAV_LINK_MULTI);
        // TODO msg.alt
        // TODO msg.eph
        // TODO msg.epv
        // TODO msg.vel
        // TODO msg.cog
        msg.satellites_visible = (short) djiAircraft.getFlightController().getState().getSatelliteCount();

        // DJI reports signal quality on a scale of 1-5
        // Mavlink has separate codes for fix type.
        GPSSignalLevel gpsLevel = djiAircraft.getFlightController().getState().getGPSSignalLevel();
        if (gpsLevel == GPSSignalLevel.NONE)
            msg.fix_type = GPS_FIX_TYPE.GPS_FIX_TYPE_NO_FIX;
        if (gpsLevel == GPSSignalLevel.LEVEL_0 || gpsLevel == GPSSignalLevel.LEVEL_1)
            msg.fix_type = GPS_FIX_TYPE.GPS_FIX_TYPE_NO_FIX;
        if (gpsLevel == GPSSignalLevel.LEVEL_2)
            msg.fix_type = GPS_FIX_TYPE.GPS_FIX_TYPE_2D_FIX;
        if (gpsLevel == GPSSignalLevel.LEVEL_3 || gpsLevel == GPSSignalLevel.LEVEL_4 ||
                gpsLevel == GPSSignalLevel.LEVEL_5)
            msg.fix_type = GPS_FIX_TYPE.GPS_FIX_TYPE_3D_FIX;

        sendMessage(msg);
    }

    private void send_sys_status() {
        msg_sys_status msg = new msg_sys_status();

        //     Log.d(TAG, "Full charge capacity: " + String.valueOf(mCFullChargeCapacity_mAh));
        //     Log.d(TAG, "Charge remaining: " + String.valueOf(mCChargeRemaining_mAh));
        //     Log.d(TAG, "Full charge capacity: " + String.valueOf(mCFullChargeCapacity_mAh));

        if (mCFullChargeCapacity_mAh > 0) {
            msg.battery_remaining = (byte) ((float) mCChargeRemaining_mAh / (float) mCFullChargeCapacity_mAh * 100.0);
            //       Log.d(TAG, "calc'ed bat remain: " + String.valueOf(msg.battery_remaining));
        } else {
            Log.d(TAG, "mCFullChargeCapacity_mAh == 0...");
            msg.battery_remaining = 100; // Prevent divide by zero
        }
        msg.voltage_battery = mVoltage_mV;
        msg.current_battery = (short) mCurrent_mA;
        sendMessage(msg);
    }

    private void send_power_status() {
        msg_power_status msg = new msg_power_status();
        sendMessage(msg);
    }

    private void send_radio_status() {
        msg_radio_status msg = new msg_radio_status();
        msg.rssi = 0; // TODO: work out units conversion (see issue #1)
        msg.remrssi = 0; // TODO: work out units conversion (see issue #1)
        sendMessage(msg);
    }

    private void send_rc_channels() {
        msg_rc_channels msg = new msg_rc_channels();
        msg.rssi = (short) mUplinkQuality;
        msg.chan1_raw = mLeftStickVertical;
        msg.chan2_raw = mLeftStickHorisontal;
        msg.chan3_raw = mRightStickVertical;
        msg.chan4_raw = mRightStickHorisontal;
        msg.chan5_raw = mC1 ? 1000 : 2000;
        msg.chan6_raw = mC2 ? 1000 : 2000;
        msg.chan7_raw = mC3 ? 1000 : 2000;

        // Cancel all AI modes if stick is moved...
        if ((mLeftStickVertical > 1550 || mLeftStickVertical < 1450) ||
                (mLeftStickHorisontal > 1550 || mLeftStickHorisontal < 1450) ||
                (mRightStickVertical > 1550 || mRightStickVertical < 1450) ||
                (mRightStickHorisontal > 1550 || mRightStickHorisontal < 1450)) {
            if (mAIfunction_activation != 0) {
                parent.logMessageDJI("AI Mode Canceled...");
                mAIfunction_activation = 0;
            }

            mAutonomy = false;   // TODO:: This variable needs to be checked elseware in the code...

            Log.i(TAG, "Stick moved => cancel all tasks");
            cancelAllTasks();
        }

        msg.chan8_raw = (mAIfunction_activation * 100) + 1000;
        msg.chancount = 8;
        sendMessage(msg);
    }

    private void send_vibration() {
        msg_vibration msg = new msg_vibration();
        sendMessage(msg);
    }

    private void send_battery_status() {
        msg_battery_status msg = new msg_battery_status();
        msg.current_consumed = mCFullChargeCapacity_mAh - mCChargeRemaining_mAh;
        msg.voltages = mCellVoltages;
        float mBatteryTemp_C = 0;
        msg.temperature = (short) (mBatteryTemp_C * 100);
        msg.current_battery = (short) (mCurrent_mA * 10);
        msg.battery_remaining = (byte) ((float) mCChargeRemaining_mAh / (float) mCFullChargeCapacity_mAh * 100.0);
        //     Log.d(TAG, "temp: " + String.valueOf(mBatteryTemp_C));
        //      Log.d(TAG, "send_battery_status() complete");
        // TODO cell voltages
        sendMessage(msg);
    }

    private void send_vfr_hud() {
        msg_vfr_hud msg = new msg_vfr_hud();

        // Mavlink: Current airspeed in m/s
        // DJI: unclear whether getState() returns airspeed or groundspeed
        msg.airspeed = (float) (Math.sqrt(Math.pow(djiAircraft.getFlightController().getState().getVelocityX(), 2) +
                Math.pow(djiAircraft.getFlightController().getState().getVelocityY(), 2)));

        // Mavlink: Current ground speed in m/s. For now, just echoing airspeed.
        msg.groundspeed = msg.airspeed;

        // Mavlink: Current heading in degrees, in compass units (0..360, 0=north)
        // TODO: unspecified in Mavlink documentation whether this heading is true or magnetic
        // DJI=[-180,180] where 0 is true north, Mavlink=degrees
        double yaw = djiAircraft.getFlightController().getState().getAttitude().yaw;
        if (yaw < 0)
            yaw += 360;
        msg.heading = (short) yaw;

        // Mavlink: Current throttle setting in integer percent, 0 to 100
        msg.throttle = mThrottleSetting;

        // Mavlink: Current altitude (MSL), in meters
        // DJI: relative altitude is altitude of the aircraft relative to take off location, measured by barometer, in meters.
        // DJI: home altitude is home point's altitude. Units unspecified in DJI SDK documentation. Presumably meters AMSL.
        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        msg.alt = (int) (coord.getAltitude());

        // Mavlink: Current climb rate in meters/second
        // DJI: m/s, positive values down
        msg.climb = -(short) (djiAircraft.getFlightController().getState().getVelocityZ());

        sendMessage(msg);
    }

    void send_home_position() {
        home_position.latitude = (int) (djiAircraft.getFlightController().getState().getHomeLocation().getLatitude() * MAV_LINK_MULTI);
        home_position.longitude = (int) (djiAircraft.getFlightController().getState().getHomeLocation().getLongitude() * MAV_LINK_MULTI);

        // We are suposed to send the home altitude (MSL), but we don't have it.
        // home_position.altitude = (int) (djiAircraft.getFlightController().getState().getTakeoffLocationAltitude()); // Unsafe, because the TakeOff location altitude could be below the home location altitude.
        home_position.altitude = (int) (djiAircraft.getFlightController().getState().getGoHomeHeight()); // This is the altitude for returning home. This is safe.

        sendMessage(home_position);
    }

    void set_home_position(double lat, double lon) {
        djiAircraft.getFlightController().getState().setHomeLocation(new LocationCoordinate2D(lat, lon));
    }

    public void send_statustext(String text, int severity) {
        msg_statustext msg = new msg_statustext();
        msg.text = text.getBytes();
        msg.severity = (short) severity;
        sendMessage(msg);
    }

    int getParameterIndex(String paramName) {
        for (int i = 0; i < params.size(); i++) {
            MAVParam p = params.get(i);
            if (p.getParamName().equals(paramName)) {
                return i;
            }
        }
        return -1;
    }

    void send_param(int index) {
        MAVParam param = params.get(index);
        send_param(param.getParamName(),
                param.getParamValue(),
                param.getParamType(),
                params.size(),
                index);
    }

    private void send_param(String key, float value, short type, int count, int index) {

        msg_param_value msg = new msg_param_value();
        msg.setParam_Id(key);
        msg.param_value = value;
        msg.param_type = type;
        msg.param_count = count;
        msg.param_index = index;

        Log.d("Rosetta", "Sending param: " + msg.toString());

        sendMessage(msg);
    }

    void send_all_params() {
        for (int i = 0; i < params.size(); i++)
            send_param(i);
    }

    boolean loadParamsFromDJI() {
        if (getDjiAircraft() == null)
            return false;
        for (int i = 0; i < getParams().size(); i++) {
            switch (getParams().get(i).getParamName()) {
                case "DJI_CTRL_MODE":
                    getDjiAircraft().getFlightController().getControlMode(new ParamControlModeCallback(i));
                    break;
                case "DJI_ENBL_LEDS":
//                    getDjiAircraft().getFlightController().getLEDsEnabled(new ParamBooleanCallback(i));
                    break;
                case "DJI_ENBL_QSPIN":
                    getDjiAircraft().getFlightController().getQuickSpinEnabled(new ParamBooleanCallback(i));
                    break;
                case "DJI_ENBL_RADIUS":
                    getDjiAircraft().getFlightController().getMaxFlightRadiusLimitationEnabled(new ParamBooleanCallback(i));
                    break;
                case "DJI_ENBL_TFOLLOW":
                    getDjiAircraft().getFlightController().getTerrainFollowModeEnabled(new ParamBooleanCallback(i));
                    break;
                case "DJI_ENBL_TRIPOD":
                    getDjiAircraft().getFlightController().getTripodModeEnabled(new ParamBooleanCallback(i));
                    break;
                case "DJI_FAILSAFE":
                    getDjiAircraft().getFlightController().getConnectionFailSafeBehavior(new ParamConnectionFailSafeBehaviorCallback(i));
                    break;
                case "DJI_LOW_BAT":
                    getDjiAircraft().getFlightController().getLowBatteryWarningThreshold(new ParamIntegerCallback(i));
                    break;
                case "DJI_MAX_HEIGHT":
                    getDjiAircraft().getFlightController().getMaxFlightHeight(new ParamIntegerCallback(i));
                    break;
                case "DJI_MAX_RADIUS":
                    getDjiAircraft().getFlightController().getMaxFlightRadius(new ParamIntegerCallback(i));
                    break;
                case "DJI_RLPCH_MODE":
                    if (getDjiAircraft().getFlightController().getRollPitchControlMode() == RollPitchControlMode.ANGLE)
                        getParams().get(i).setParamValue(0f);
                    else if (getDjiAircraft().getFlightController().getRollPitchControlMode() == RollPitchControlMode.VELOCITY)
                        getParams().get(i).setParamValue(1f);
                    break;
                case "DJI_RTL_HEIGHT":
                    getDjiAircraft().getFlightController().getGoHomeHeightInMeters(new ParamIntegerCallback(i));
                    break;
                case "DJI_SERIOUS_BAT":
                    getDjiAircraft().getFlightController().getSeriousLowBatteryWarningThreshold(new ParamIntegerCallback(i));
                    break;
                case "DJI_SMART_RTH":
                    getDjiAircraft().getFlightController().getSmartReturnToHomeEnabled(new ParamBooleanCallback(i));
                    break;
                case "DJI_VERT_MODE":
                    if (getDjiAircraft().getFlightController().getVerticalControlMode() == VerticalControlMode.VELOCITY)
                        getParams().get(i).setParamValue(0f);
                    else if (getDjiAircraft().getFlightController().getVerticalControlMode() == VerticalControlMode.POSITION)
                        getParams().get(i).setParamValue(1f);
                    break;
                case "DJI_YAW_MODE":
                    if (getDjiAircraft().getFlightController().getYawControlMode() == YawControlMode.ANGLE)
                        getParams().get(i).setParamValue(0f);
                    else if (getDjiAircraft().getFlightController().getYawControlMode() == YawControlMode.ANGULAR_VELOCITY)
                        getParams().get(i).setParamValue(1f);
                    break;
            }
        }
        return true;
    }

    void changeParam(MAVParam param) {
        for (int i = 0; i < getParams().size(); i++) {
            if (getParams().get(i).getParamName().equals(param.getParamName())) {
                getParams().get(i).setParamValue(param.getParamValue());
                switch (param.getParamName()) {
                    case "DJI_CTRL_MODE":
                        if (param.getParamValue() == 0)
                            getDjiAircraft().getFlightController().setControlMode(ControlMode.MANUAL, new ParamWriteCompletionCallback(i));
                        else if (param.getParamValue() == 2)
                            getDjiAircraft().getFlightController().setControlMode(ControlMode.SMART, new ParamWriteCompletionCallback(i));
                        else if (param.getParamValue() == 255)
                            getDjiAircraft().getFlightController().setControlMode(ControlMode.UNKNOWN, new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_ENBL_LEDS":
//                        getDjiAircraft().getFlightController().setLEDsEnabled(param.getParamValue() > 0, new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_ENBL_QSPIN":
                        getDjiAircraft().getFlightController().setAutoQuickSpinEnabled(param.getParamValue() > 0, new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_ENBL_RADIUS":
                        getDjiAircraft().getFlightController().setMaxFlightRadiusLimitationEnabled((param.getParamValue() > 0), new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_ENBL_TFOLLOW":
                        getDjiAircraft().getFlightController().setTerrainFollowModeEnabled(param.getParamValue() > 0, new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_ENBL_TRIPOD":
                        getDjiAircraft().getFlightController().setTripodModeEnabled(param.getParamValue() > 0, new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_FAILSAFE":
                        if (param.getParamValue() == 0)
                            getDjiAircraft().getFlightController().setConnectionFailSafeBehavior(ConnectionFailSafeBehavior.HOVER, new ParamWriteCompletionCallback(i));
                        else if (param.getParamValue() == 1)
                            getDjiAircraft().getFlightController().setConnectionFailSafeBehavior(ConnectionFailSafeBehavior.LANDING, new ParamWriteCompletionCallback(i));
                        else if (param.getParamValue() == 2)
                            getDjiAircraft().getFlightController().setConnectionFailSafeBehavior(ConnectionFailSafeBehavior.GO_HOME, new ParamWriteCompletionCallback(i));
                        else if (param.getParamValue() == 255)
                            getDjiAircraft().getFlightController().setConnectionFailSafeBehavior(ConnectionFailSafeBehavior.UNKNOWN, new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_LOW_BAT":
                        getDjiAircraft().getFlightController().setLowBatteryWarningThreshold(Math.round(param.getParamValue()), new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_MAX_HEIGHT":
                        getDjiAircraft().getFlightController().setMaxFlightHeight(Math.round(param.getParamValue()), new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_MAX_RADIUS":
                        getDjiAircraft().getFlightController().setMaxFlightRadius(Math.round(param.getParamValue()), new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_RLPCH_MODE":
                        if (param.getParamValue() == 0)
                            getDjiAircraft().getFlightController().setRollPitchControlMode(RollPitchControlMode.ANGLE);
                        else if (param.getParamValue() == 1)
                            getDjiAircraft().getFlightController().setRollPitchControlMode(RollPitchControlMode.VELOCITY);
                        break;
                    case "DJI_RTL_HEIGHT":
                        getDjiAircraft().getFlightController().setGoHomeHeightInMeters(Math.round(param.getParamValue()), new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_SERIOUS_BAT":
                        getDjiAircraft().getFlightController().setSeriousLowBatteryWarningThreshold(Math.round(param.getParamValue()), new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_SMART_RTH":
                        getDjiAircraft().getFlightController().setSmartReturnToHomeEnabled(param.getParamValue() > 0, new ParamWriteCompletionCallback(i));
                        break;
                    case "DJI_VERT_MODE":
                        if (param.getParamValue() == 0)
                            getDjiAircraft().getFlightController().setVerticalControlMode(VerticalControlMode.VELOCITY);
                        else if (param.getParamValue() == 1)
                            getDjiAircraft().getFlightController().setVerticalControlMode(VerticalControlMode.POSITION);
                        break;
                    case "DJI_YAW_MODE":
                        if (param.getParamValue() == 0)
                            getDjiAircraft().getFlightController().setYawControlMode(YawControlMode.ANGLE);
                        else if (param.getParamValue() == 1)
                            getDjiAircraft().getFlightController().setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
                        break;
                    default:
                        parent.logMessageDJI("Unknown parameter name");

                }
                send_param(i);
                break;
            }
        }
        Log.d(TAG, "Request to set param that doesn't exist");

    }

    void send_mission_count() {
        msg_mission_count msg = new msg_mission_count();
        msg.mission_type = MAV_MISSION_TYPE.MAV_MISSION_TYPE_MISSION;
        // Get the full expanded list...
        msg.count = send_mission_item(-1);
        Log.d(TAG, "Mission Count: " + msg.count);
        sendMessage(msg);
    }

    // This is a bit tricky, as DJI and MAVlink pack mission ithems very different...
    // Firste we expand the full mission list and then we pick the one we need...
    int send_mission_item(int i) {
        ArrayList<msg_mission_item_int> msglist = new ArrayList<>();
        WaypointMission x = getWaypointMissionOperator().getLoadedMission();

        // If a mission loaded...
        if (x != null) {
            // For all DJI mission items...
            for (Waypoint wp : Objects.requireNonNull(x).getWaypointList()) {

                msg_mission_item_int msg = new msg_mission_item_int();
                msg.command = MAV_CMD.MAV_CMD_NAV_WAYPOINT;
                msg.seq = i;
                msg.mission_type = MAV_MISSION_TYPE.MAV_MISSION_TYPE_MISSION;
                msg.frame = MAV_FRAME.MAV_FRAME_GLOBAL_RELATIVE_ALT;
                msg.x = (int) (10000000 * (wp.coordinate.getLatitude()));
                msg.y = (int) (10000000 * (wp.coordinate.getLongitude()));
                msg.z = wp.altitude;

                // Assume the first mission point is a takeoff...
                if (i == 0) {
                    Log.d(TAG, "Mission Action : Takeoff");
                    msg.command = MAV_CMD.MAV_CMD_NAV_TAKEOFF;
                    msglist.add(msg);
                }
                // DJI pack the waypoints different than MAVlink, this is a problem...
                else {
                    Log.d(TAG, "Mission Action : WAYPOINT");
                    msglist.add(msg);

                    for (WaypointAction action : wp.waypointActions) {
                        switch (action.actionType) {
                            case STAY:
                                Log.d(TAG, "Mission Action : STAY");
                                msg.command = MAV_CMD.MAV_CMD_NAV_DELAY;
                                msglist.add(msg);
                                break;
                            case START_TAKE_PHOTO:
                                Log.d(TAG, "Mission Action : START_TAKE_PHOTO");
                                msg.command = MAV_CMD.MAV_CMD_IMAGE_START_CAPTURE;
                                msglist.add(msg);
                                break;
                            case START_RECORD:
                                msg.command = MAV_CMD.MAV_CMD_VIDEO_START_CAPTURE;
                                Log.d(TAG, "Mission Action : START_RECORD");
                                msglist.add(msg);
                                break;
                            case STOP_RECORD:
                                msg.command = MAV_CMD.MAV_CMD_VIDEO_STOP_CAPTURE;
                                Log.d(TAG, "Mission Action : STOP_RECORD");
                                msglist.add(msg);
                                break;
                            case ROTATE_AIRCRAFT:
                                msg.command = MAV_CMD.MAV_CMD_CONDITION_YAW;
                                Log.d(TAG, "Mission Action : ROTATE_AIRCRAFT");
                                msglist.add(msg);
                                break;
                            case GIMBAL_PITCH:
                                Log.d(TAG, "Mission Action : GIMBAL_PITCH");
                                msg.command = MAV_CMD.MAV_CMD_DO_DIGICAM_CONTROL;
                                msglist.add(msg);
                                break;
                            case CAMERA_ZOOM:
                                msg.command = MAV_CMD.MAV_CMD_SET_CAMERA_ZOOM;
                                Log.d(TAG, "Mission Action : CAMERA_ZOOM");
                                msglist.add(msg);
                                break;
                            case CAMERA_FOCUS:
                                msg.command = MAV_CMD.MAV_CMD_SET_CAMERA_FOCUS;
                                Log.d(TAG, "Mission Action : CAMERA_FOCUS");
                                msglist.add(msg);
                                break;
                            case PHOTO_GROUPING:
                                msg.command = MAV_CMD.MAV_CMD_VIDEO_START_CAPTURE;
                                msglist.add(msg);
                                break;
                            case FINE_TUNE_GIMBAL_PITCH:
                                msg.command = MAV_CMD.MAV_CMD_DO_DIGICAM_CONTROL;
                                msglist.add(msg);
                                break;
                            case RESET_GIMBAL_YAW:
                                msg.command = MAV_CMD.MAV_CMD_GIMBAL_RESET;
                                Log.d(TAG, "Mission Action : RESET_GIMBAL_YAW");
                                msglist.add(msg);
                                break;
                            default:
                                Log.d(TAG, "Mission Action : Waypoint");
                                msg.command = MAV_CMD.MAV_CMD_NAV_WAYPOINT;
                                msglist.add(msg);
                                break;
                        }
                    }
                }
            }
            if (i >= 0)
                sendMessage(msglist.get(i));
        }
        Log.d(TAG, "Mission return: " + i);
        return (msglist.size());
    }

    public void send_mission_item_reached(int seq) {
        msg_mission_item_reached msg = new msg_mission_item_reached();
        msg.seq = seq;
        sendMessage(msg);
    }

    // Send mission accepted back to Mavlink...
    void send_mission_ack(int status) {
        parent.logMessageDJI("Mission status: " + status);
        msg_mission_ack msg = new msg_mission_ack();
        msg.type = (short) status;
        msg.mission_type = MAV_MISSION_TYPE.MAV_MISSION_TYPE_MISSION;
        sendMessage(msg);
    }

    // Send mission accepted back to Mavlink...
    void send_text(String status) {
        parent.logMessageDJI("Mission status: " + status);
        msg_statustext msg = new msg_statustext();
        int i = 0;
        for( char x:status.toCharArray()){
            msg.text[i++]=(byte)x;
            if(i >= 50) break;
        }
        sendMessage(msg);
    }


    public void fetch_gcs_mission() {
        request_mission_list();
    }

    private void request_mission_list() {
        msg_mission_request_list msg = new msg_mission_request_list();
        sendMessage(msg);
    }

    void request_mission_item(int seq) {
        if(seq == 0) {
            // Uploading new mission => stop current mission
            stopWaypointMission();
        }

//        msg_mission_request msg = new msg_mission_request();
        msg_mission_request_int msg = new msg_mission_request_int();
        msg.seq = seq;
        msg.mission_type = MAV_MISSION_TYPE.MAV_MISSION_TYPE_MISSION;
        sendMessage(msg);
    }

    boolean isMissionActive() {
        if(useMissionManager) {
            return missionManager.isActive();
        } else {
            // This flag cannot be trusted, since it is not changed when a mission ends or is interrupted.
            return missionActive;
        }
    }

    // Prefer this function always
    void startOrResumeWaypointMission() {
        if(useMissionManager) {
            if (missionManager.paused) {
                resumeWaypointMission();
            } else {
                startWaypointMission();
            }

        } else {
            if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTION_PAUSED) {
                resumeWaypointMission();
            } else if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.READY_TO_EXECUTE) {
                startWaypointMission();
            }
        }
    }

    boolean startWaypointMission() {
        if(!checkSafeMode()) return false;

        gcsFlightMode = ArduCopterFlightModes.AUTO;
        mAutonomy = false; // TODO: Why?

        parent.logMessageDJI("start WaypointMission()");

        if(useMissionManager) {
            missionManager.startMission();
            parent.logMessageDJI("Mission started");

        } else {
            missionActive = true;
            if (getWaypointMissionOperator() == null) {
                parent.logMessageDJI("getWaypointMissionOperator() == null");
                return false;
            }

            if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.READY_TO_EXECUTE) {
                parent.logMessageDJI("Ready to execute mission");
            } else {
                parent.logMessageDJI("Not ready to execute mission");
                parent.logMessageDJI(getWaypointMissionOperator().getCurrentState().getName());
                return false;
            }

            // Set default camera angle at 45deg down...
            doSetGimbal(9, (float) ((-45.0 * 5.5) + 1500.0));
            getWaypointMissionOperator().startMission(djiError -> {
                if (djiError != null)
                    parent.logMessageDJI("startWaypointMission(): " + djiError.toString());
                else
                    parent.logMessageDJI("Mission started");
            });
        }
        return true;
    }

    public void stopWaypointMission() {
        mAutonomy = false;

        if(useMissionManager) {
            missionManager.stopMission();
            parent.logMessageDJI("Mission stopped!\n");

        } else {
            missionActive = false;
            if (getWaypointMissionOperator() == null) {
                parent.logMessageDJI("getWaypointMissionOperator() == null");
                return;
            }

            if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTING) {
                parent.logMessageDJI("Stopping mission...\n");
                getWaypointMissionOperator().stopMission(djiError -> {
                    if (djiError != null)
                        Log.e(TAG, "stopWaypointMission(): " + djiError.toString());
                    else
                        parent.logMessageDJI("Mission stopped");
                });
            }
        }
    }

    void pauseWaypointMission() {
        if(!isMissionActive()) return;

        mAutonomy = false;

        if(useMissionManager) {
            missionManager.pauseMission();
            parent.logMessageDJI("Mission paused");

        } else {
            missionActive = false;
            if (getWaypointMissionOperator() == null) {
                parent.logMessageDJI("pauseWaypointMission() - mWaypointMissionOperator null");
                return;
            }

            if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTING) {
                parent.logMessageDJI("Pausing mission...\n");
                getWaypointMissionOperator().pauseMission(djiError -> {
                    if (djiError != null)
                        Log.e(TAG, "pauseWaypointMission(): " + djiError.toString());
                    else
                        parent.logMessageDJI("Mission paused");
                });
            }
        }
    }

    boolean resumeWaypointMission() {
        if(!checkSafeMode()) return false;

        gcsFlightMode = ArduCopterFlightModes.AUTO;
        mAutonomy = false; // TODO: Why?

        if(useMissionManager) {
            missionManager.resumeMission();

            parent.logMessageDJI("Mission resumed!\n");
            gcsFlightMode = NOT_USING_GCS_COMMANDED_MODE;

        } else {
            if (getWaypointMissionOperator() == null) {
                parent.logMessageDJI("resumeWaypointMission() - mWaypointMissionOperator null");
                return false;
            }

            if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTION_PAUSED) {
                parent.logMessageDJI("Resuming mission...\n");
                getWaypointMissionOperator().resumeMission(djiError -> {
                    if (djiError != null)
                        Log.e(TAG, "resumeWaypointMission(): " + djiError.toString());
                    else {
                        parent.logMessageDJI("Mission resumed!\n");
                        missionActive = true;
                        gcsFlightMode = NOT_USING_GCS_COMMANDED_MODE;
                    }
                });
            }
        }
        return true;
    }

    /**
     * Check save mode.
     * Usage: if(!checkSafeMode(MAV_CMD_XXX)) return;
     * @return True if command was accepted
     */
    boolean checkSafeMode() {
        if (mSafetyEnabled && !isSimulator && !RDApplication.isTestMode) {
            parent.logMessageDJI(parent.getResources().getString(R.string.safety_launch));
            send_text(parent.getResources().getString(R.string.safety_launch));
            return false;

        } else if(isForbiddenSwitchMode()) {
            return false;

        } else {
            return true;
        }
    }

    /**
     * @param alt In meters
     */
    boolean doTakeOff(float alt, boolean sendResponses) {
        if(!checkSafeMode()) {
            if(sendResponses) send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_DENIED);
            return false;
        }

        Log.i(TAG, "Starting take off...");

        if(useMissionControlClass) {
            // TODO: Why do we want to start/resume a mission after taking off?. Should we start a mission when setting mode to auto (like Mission Planner)?
            if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTION_PAUSED) {
                resumeWaypointMission();
            }
            else {
                if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.READY_TO_EXECUTE) {
                    Log.d(TAG, "Start Mission...");
                    // But how about takeoff....
                    startWaypointMission();
                    if(sendResponses) send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_ACCEPTED);
                    return true;
                }
            }

            FlightControllerState coord = djiAircraft.getFlightController().getState();
            TimeLine.TimeLinetakeOff(coord.getAircraftLocation().getLatitude(), coord.getAircraftLocation().getLongitude(), alt  * (float) 1000.0, 0);
            TimeLine.startTimeline();

        } else {
            FlightControllerState coord = djiAircraft.getFlightController().getState();
            if(coord.isFlying()) {
                Log.i(TAG, "doTakeOff(): already flying => ignore takeoff and only ascend");
                ascend(alt);

            } else {
                mFlightController.startTakeoff(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError == null) {
                            ascend(alt);
                        } else {
                            Log.e(TAG, "startTakeoff() error: " + djiError.toString());

                            // HACK: DJI sometimes returns "Undefined Error(255)" even when take off was successful (tested on simulator)
                            // So here we check if we are flying for max 10 seconds
                            for(int i = 0; i < 10; i++) {
                                if(getDjiAircraft().getFlightController().getState().isFlying()) {
                                    ascend(alt);
                                    return;
                                }
                                safeSleep(1000);
                            }

                            send_text("ERROR: Couldn't take off");
                            cancelAllTasks();
                            if(sendResponses) send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_DENIED);
                        }
                    }
                });
            }
        }
        if(sendResponses) send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_IN_PROGRESS);
        return true;
    }

    // DJI's TakeOff only goes to 1.2 m approx
    // The elevation is done after
    void ascend(float alt) {
        missionManager.setTakeOffStatus(true);
        Log.i(TAG, "Took off => Flying to altitude " + alt);
        FlightControllerState coord = djiAircraft.getFlightController().getState();
        motion = new Motion(coord.getAircraftLocation().getLatitude(), coord.getAircraftLocation().getLongitude(), alt);
        motion.mask.ignorePosX = true;
        motion.mask.ignorePosY = true;
        motion.mask.ignorePosZ = true;
        motion.mask.ignoreYaw = true;
        motion.yawDirection = YawDirection.KEEP;
        startMotion(motion);
    }

    void doLand() {
        //setMavFlightMode(ArduCopterFlightModes.LAND);
        djiAircraft.getFlightController().startLanding(djiError -> {
            if (djiError == null) {
                parent.logMessageDJI("Landing...");

                if(!useMissionManager) {
                    // TODO: Wait until landed
                    // Keeping old version
                    landed();
                }

            } else {
                Log.e(TAG, "do_land(): " + djiError.toString());
            }
        });
    }

    void landed() {
        parent.logMessageDJI("Landing successful!");
        missionManager.setLandedStatus(true);
        mMotorsArmed = false;
    }

    void doGomeHome() {
        mAutonomy = false;
        missionActive = false;
        parent.logMessageDJI("Initiating Go Home");
        djiAircraft.getFlightController().startGoHome(djiError -> {
            if (djiError != null)
                Log.e(TAG, "do_go_home: " + djiError.toString());
            else
                parent.logMessageDJI("Go home successful!\n");
        });
    }

    void doSetGimbal(float channel, float value) {
        Rotation.Builder builder = new Rotation.Builder().mode(RotationMode.ABSOLUTE_ANGLE).time(2);
        float param = (value - (float) 1500.0) / (float) 5.5;
        if ((int) channel == 9) {
            m_ServoPos_pitch = param;
            builder.pitch(m_ServoPos_pitch);
        } else if ((int) channel == 8) {
            builder.mode(RotationMode.RELATIVE_ANGLE);
            builder.yaw(-m_ServoPos_yaw + param);
            m_ServoPos_yaw = param;
        } else {
            send_command_ack(MAV_CMD_DO_SET_SERVO, MAV_RESULT.MAV_RESULT_UNSUPPORTED);
            return;
        }
        if (mGimbal == null) {
            send_command_ack(MAV_CMD_DO_SET_SERVO, MAV_RESULT.MAV_RESULT_TEMPORARILY_REJECTED);
            return;
        }

        m_ServoSet = builder.build();
        Log.e(TAG, "Set Gimbal Pos: " + value + "   " +m_ServoPos_pitch + "  :  " + m_ServoPos_yaw);
        mGimbal.rotate(m_ServoSet, djiError -> {
            if (djiError != null)
                Log.e(TAG, "do_set_Gimbal(): " + djiError.toString());

            Log.e(TAG, "Gimbal Pos: " + m_ServoSet.getPitch() + "  :  " + m_ServoSet.getYaw());
            send_command_ack(MAV_CMD_DO_SET_SERVO, MAV_RESULT.MAV_RESULT_ACCEPTED);
        });
    }

    enum MotionType {
        FLY_TO, // Run until reaching the destination
        POSITION_TARGET // Run for a given time
    }

    // See: https://mavlink.io/en/messages/common.html#POSITION_TARGET_TYPEMASK
    static class PositionTargetTypeMask {
        boolean ignorePosX;
        boolean ignorePosY;
        boolean ignorePosZ;
        boolean ignoreVelX;
        boolean ignoreVelY;
        boolean ignoreVelZ;
        boolean ignoreAccelX;
        boolean ignoreAccelY;
        boolean ignoreAccelZ;
        boolean useForce;
        boolean ignoreYaw;
        boolean ignoreYawRate;

        PositionTargetTypeMask(int mavLinkMask) {
            // TODO: Reimplement old code. Search for "m_Destination_Mask" here:
            // https://github.com/The1only/rosettadrone/blob/6ebb4f04dac9c284afe26ca5292f0267e62609b6/app/src/main/java/sq/rogue/rosettadrone/DroneModel.java
            // https://github.com/The1only/rosettadrone/blob/6ebb4f04dac9c284afe26ca5292f0267e62609b6/app/src/main/java/sq/rogue/rosettadrone/MAVLinkReceiver.java

            ignorePosX = (mavLinkMask & POSITION_TARGET_TYPEMASK_X_IGNORE) != 0;
            ignorePosY = (mavLinkMask & POSITION_TARGET_TYPEMASK_Y_IGNORE) != 0;
            ignorePosZ = (mavLinkMask & POSITION_TARGET_TYPEMASK_Z_IGNORE) != 0;
            // ...
            ignoreYaw = (mavLinkMask & 	POSITION_TARGET_TYPEMASK_YAW_IGNORE) != 0;
            ignoreYawRate = (mavLinkMask & 	POSITION_TARGET_TYPEMASK_YAW_RATE_IGNORE) != 0;
        }
    }

    public class Motion {
        DroneModel.MotionType type;

        public int command = 0; // If not 0, send MAVLink ACK once the motion has finished

        short coordFrame; // See: https://mavlink.io/en/messages/common.html#MAV_FRAME

        DroneModel.PositionTargetTypeMask mask;

        double x; // Lat
        double y; // Lng
        double z; // Alt
        double yaw;

        double speed = 3; // m/s

        double vx;
        double vy;
        double vz;

        double yawRate;

        YawDirection yawDirection = YawDirection.DEST; // Where to look while moving

        // Used internally (not for MAVLink commands)
        Motion(double lat, double lng, double alt) {
            type = DroneModel.MotionType.FLY_TO;
            coordFrame = MAV_FRAME_GLOBAL;
            this.x = lat;
            this.y = lng;
            this.z = alt;
        }

        // Triggered by a MAVLink commands => sends ACK and does cancelAllTasks()
        // TODO: Implement mask in MotionTask.run()
        Motion(int command, int mavLinkMask) {
            this.command = command;
            cancelAllTasks();
            type = DroneModel.MotionType.POSITION_TARGET;
            this.mask = new DroneModel.PositionTargetTypeMask(mavLinkMask);
            send_command_ack(command, MAV_RESULT.MAV_RESULT_IN_PROGRESS);
        }
    }

    public Motion newMotion(int command, int mavLinkMask) {
        return new Motion(command, mavLinkMask);
    }

    public void flyTo(double lat, double lng, double alt) {
        motion = new Motion(lat, lng, alt);
        motion.yawDirection = YawDirection.DEST;
        startMotion(motion);
    }

    public boolean inMotion() {
        return motionTimer != null;
    }

    public void startMotion(Motion motion) {
        if (motionTimer == null) {
            motionTask = new MotionTask();
            motionTimer = new Timer();
            motionTimer.schedule(motionTask, 100, MOTION_PERIOD_MS);
        } else {
            motionTask.detectionCounter = 0;
        }
    }

    public void cancelMotion() {
        if(motionTimer != null) {
            motionTimer.cancel();
            motionTimer.purge();
            motionTimer = null;
        }
    }

    /**
     * Convert angle from DJI to MAVLink
     * @param djiAngle DJI uses [-180, 180]
     * @return MAVLink uses [0, 360]
     */
    private double angleDjiToMav(double djiAngle) {
        return djiAngle < 0 ? djiAngle + 360 : djiAngle;
    }

    class MotionTask extends TimerTask {
        public int detectionCounter = 0;  // We might fly past the point so we look for consecutive hits...
        private final int requiredDetections = 7;
        private final double detectionDistance = 0.2;
        private final double detectionYawError = 1.25;

        @Override
        synchronized public void run() {
            FlightControllerState coord = djiAircraft.getFlightController().getState();
            double lat = coord.getAircraftLocation().getLatitude();
            double lng = coord.getAircraftLocation().getLongitude();
            double alt = coord.getAircraftLocation().getAltitude();

            double destX = motion.x;
            double destY = motion.y;
            double destZ = motion.z;
            if(motion.coordFrame == MAV_FRAME_BODY_FRD) {
                destX += lat;
                destY += lng;
            } else {
                Log.e(TAG, "Frame not supported");
            }

            if(motion.mask.ignorePosX) destX = lat;
            if(motion.mask.ignorePosY) destX = lng;
            if(motion.mask.ignorePosZ) destX = alt;

            // Distance to destination
            double dist = getRangeBetweenWaypoints_m(destX, destY, destZ, lat, lng, alt);

            // Look at
            boolean lookAt = motion.yawDirection == YawDirection.DEST || motion.yawDirection == YawDirection.POI;
            double lookAtDistance = 0, lookAtLat = 0, lookAtLng = 0;
            if(lookAt) {
                // TODO: Rotate gimbal
                if (motion.yawDirection == YawDirection.DEST) {
                    lookAtLat = destX;
                    lookAtLng = destY;
                } else if (motion.yawDirection == YawDirection.POI) {
                    lookAtLat = missionManager.poiLat;
                    lookAtLng = missionManager.poiLng;
                }
                lookAtDistance = getRangeBetweenWaypoints_m(lookAtLat, lookAtLng, 0, lat, lng,0);
            }

            // Calculate yaw
            double yaw = angleDjiToMav(coord.getAttitude().yaw);
            double yawError; // [-180, 180] (from left to right)

            if(motion.yawDirection == YawDirection.KEEP
                    || lookAt && lookAtDistance < 0.3   // If we are close to the lookAt target => stop looking at it in order to avoid erratic yaw rotations (eg: if we passed the target for 0.00001 mm we would have to turn in 180°)
                    || motion.yawDirection == YawDirection.FIXED
                    || motion.mask.ignoreYaw
                    || motion.mask.ignoreYawRate
            ) {
                yawError = 0;

            } else {
                double targetYaw;

                if(lookAt) {
                    targetYaw = getBearingBetweenWaypoints(lookAtLat, lookAtLng, lat, lng);

                } else if (motion.yawDirection == YawDirection.FIXED) {
                    targetYaw = motion.yaw;

                } else {
                    targetYaw = yaw; // Ignore
                }

                yawError = rotation(targetYaw, yaw);
            }

            // Check if we reached destination point
            boolean onDestination = false;
            if (dist < detectionDistance && Math.abs(yawError) < detectionYawError) {
                if (++detectionCounter > requiredDetections) {
                    onDestination = true;
                }
            } else {
                detectionCounter = 0;
            }

            if(onDestination) {
                parent.logMessageDJI("Motion finished");
                cancelMotion();
                return;
            }

            if(useMissionManager) {
                missionManager.takePhotos();
            }

            // Find the bearing... return 0-360 deg.
            double brng = getBearingBetweenWaypoints(destX, destY, lat, lng);

            // The direct distance to the destination... return distance in meters...
            double hypotenuse = getRangeBetweenWaypoints_m(destX, destY, 0, lat, lng, 0);
            double idealPos[] = get_location_intermediet(destX, destY, hypotenuse, brng - 180);
            // At these two positions is at the same distance from target, the error must be the sideways error....
            double offset = getRangeBetweenWaypoints_m(idealPos[0], idealPos[1], 0, lat, lng, 0);
            double pol = getBearingBetweenWaypoints(idealPos[0], idealPos[1], lat, lng);
            double diff = rotation(brng, pol);
            if (diff > 0) {
                offset *= -1;
            }

            //Log.i(TAG, "diff: " + diff + "brng: " + brng + " hypotenuse: " + hypotenuse + " offset: " + offset + " pol: " + pol);

            // Drone heading - Waypoint bearing... to 0-360 deg...
            double direction = rotation(brng, yaw);

            //Log.i(TAG, "brng: " + brng + ", yaw: " + yaw + ", yawdirection: " + direction);

            // Find the X and Y distance from the hypotenuse and the direction...
            double right_dist = offset; //Math.max(offset,20); // bearing_error; //Math.sin(Math.toRadians(direction)) * hypotenuse + Math.cos(Math.toRadians(direction)) * bearing_error ;
            double fw_dist = hypotenuse; //Math.max(hypotenuse,20); // Math.cos(Math.toRadians(direction)) * hypotenuse + Math.sin(Math.toRadians(direction)) * bearing_error;;

            double speed = 0;

            /* Already set above
            miniPIDFwd.setP(0.35);
            miniPIDSide.setP(0.35);
            miniPIDAlti.setP(0.35);
            miniPIDHeading.setP(1.0);
            */

            final double maxSpeed = 2.5;
            miniPIDFwd.setOutputLimits(maxSpeed);
            miniPIDSide.setOutputLimits(maxSpeed);

            miniPIDHeading.setOutputFilter(0.1);

            miniPIDFwd.setOutputFilter(0.1);
            double fwmotion = miniPIDFwd.getOutput(-fw_dist, 0);

            miniPIDSide.setOutputFilter(0.1);
            double rightmotion = miniPIDSide.getOutput(-right_dist, 0);

            miniPIDAlti.setOutputLimits(3.0f); // m/s
            miniPIDAlti.setOutputFilter(0.1);
            double upVel = miniPIDAlti.getOutput(alt, destZ);

            miniPIDHeading.setOutputLimits(75.0f); // °/s

            double yawVel = miniPIDHeading.getOutput(-yawError, 0);

            double forwardVel = Math.cos(Math.toRadians(direction)) * fwmotion - Math.sin(Math.toRadians(direction)) * rightmotion;
            double rightVel = Math.sin(Math.toRadians(direction)) * fwmotion + Math.cos(Math.toRadians(direction)) * rightmotion;

            setVelocities(forwardVel, rightVel, upVel, yawVel);
        }
    }

    void enableVirtualStickMode() {
        mFlightController.getVirtualStickModeEnabled(new CommonCallbacks.CompletionCallbackWith<Boolean>() {
            @Override
            public void onSuccess(Boolean enabled) {
                if (!enabled) {
                    // After a manual mode change, we might loose the JOYSTICK mode...
                    if (djiFlightMode != FlightMode.JOYSTICK) {
                        mFlightController.setVirtualStickModeEnabled(true, djiError -> {
                            if (djiError != null) {
                                Log.e(TAG, "setVirtualStickModeEnabled() failed: " + djiError.toString());
                            } else {
                                mFlightController.setVirtualStickAdvancedModeEnabled(true);
                            }
                        });
                    }
                }
            }

            @Override
            public void onFailure(DJIError error) {
                Log.e(TAG, "enableVirtualStickMode(): getVirtualStickModeEnabled failed");
            }
        });
    }

    boolean isForbiddenSwitchMode() {
        // Only allow VirtualSticks in P mode
        if (rcMode == forbiddenModeSwitch) {
            parent.logMessageDJI("rcMode == forbiddenModeSwitch == " + rcMode);
            Log.i(TAG, "rcMode == forbiddenModeSwitch == " + rcMode);
            return true;
        }
        return false;
    }

    void setControlModes() {
        mFlightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
        mFlightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
        mFlightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
    }

    int velLogCounter = 0;
    void setVelocities(double roll, double pitch, double throttle, double yaw) {
        if(isForbiddenSwitchMode()) return;

        if(velLogCounter++ >= 1000 / MOTION_PERIOD_MS) {
            velLogCounter = 0;
            Log.i(TAG, "Velocities = roll: " + roll + ", pitch: " + pitch + ", throttle: " + throttle + ", yaw: " + yaw);
        }

        // Just in case...
        enableVirtualStickMode();
        setControlModes();

        /* TODO: Reimplement using motion.mask
        if ((mask & 0b0000100000000000) == 0) {
            mYaw = yaw;
        }
        if ((mask & 0b0000000000001000) == 0) {
            mPitch = y;
        }
        if ((mask & 0b0000000000010000) == 0) {
            mRoll = x;
        }
        if ((mask & 0b0000000000100000) == 0) {
            mThrottle = z;
        }
        */

        mFlightController.sendVirtualStickFlightControlData(new FlightControlData((float)pitch, (float)roll, (float)yaw, (float)throttle), djiError -> {
            if (djiError != null) Log.e(TAG, "SendVelocityDataTask Error: " + djiError.toString());
        });
    }

    // Follow me is not used by Mavlink for now, in the DJI implementation it for a
    // limit to a few meters from current location.
    public void startSimpleFollowMe() {
        if (fmmo == null) {
            fmmo = DJISDKManager.getInstance().getMissionControl().getFollowMeMissionOperator();
        }
        final FollowMeMissionOperator followMeMissionOperator = fmmo;
        if (followMeMissionOperator.getCurrentState().equals(MissionState.READY_TO_EXECUTE)) {
            followMeMissionOperator.startMission(new FollowMeMission(FollowMeHeading.TOWARD_FOLLOW_POSITION, m_Latitude, m_Longitude, m_alt)
                    , djiError -> {
                        if (djiError != null) {
                            parent.logMessageDJI(djiError.getDescription());
                        } else {
                            parent.logMessageDJI("Mission Start: Successfully");
                        }
                    });
        }
    }

    public void updateSimpleFollowMe() {
        if (fmmo == null) {
            fmmo = DJISDKManager.getInstance().getMissionControl().getFollowMeMissionOperator();
        }
        final FollowMeMissionOperator followMeMissionOperator = fmmo;
        if (followMeMissionOperator.getCurrentState().equals(FollowMeMissionState.EXECUTING)) {
            followMeMissionOperator.updateFollowingTarget(new LocationCoordinate2D(m_Latitude, m_Longitude),
                    error -> {
                        if (error != null) {
                            parent.logMessageDJI(followMeMissionOperator.getCurrentState().getName() + " " + error.getDescription());
                        } else {
                            parent.logMessageDJI("Mission Update Successfully");
                        }
                    });
        }
    }

    private int pictureNum = 0;

    void takePhoto(boolean sendResponses) {
        djiAircraft.getCamera().setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, djiError -> {
            if(djiError != null) {
                parent.logMessageDJI("Error Setting PhotoMode: " + djiError.toString());
            }
        });

        // Wait till Gimbal is in Place
        safeSleep(100);

        SettingsDefinitions.ShootPhotoMode photoMode = SettingsDefinitions.ShootPhotoMode.SINGLE;

        if (djiAircraft.getCamera() != null) {
            djiAircraft.getCamera().startShootPhoto(djiError -> {
                if (djiError == null) {
                    parent.logMessageDJI("Requested Photo");
/*
                    msg_camera_image_captured msg = new msg_camera_image_captured();
                    msg.lat = (int)(m_Latitude*10000000);
                    msg.lon = (int)(m_Longitude*10000000);
                    msg.alt = (int)m_alt;
                    msg.camera_id=0;
                    msg.image_index=0;
                    msg.capture_result = 1;
                    sendMessage(msg);
  */
                    if(sendResponses) send_command_ack(MAV_CMD_DO_DIGICAM_CONTROL, MAV_RESULT.MAV_RESULT_ACCEPTED);

                } else {
                    parent.logMessageDJI("Error requesting photo: " + djiError.toString());
                    // try again
                    takePhoto(sendResponses);
                }
            });
        }
    }

    /********************************************
     * CompletionCallback implementation        *
     ********************************************/

    @Override
    public void onResult(DJIError djiError) {

    }

    public void echoLoadedMission() {

        double lat = djiAircraft.getFlightController().getState().getHomeLocation().getLatitude();
        double lon = djiAircraft.getFlightController().getState().getHomeLocation().getLongitude();
        TimeLine.addHome(lat, lon);
        Log.d(TAG, "Init Timeline...");
        TimeLine.initTimeline();
        Log.d(TAG, "Start Timeline...");
        TimeLine.startTimeline();
        Log.d(TAG, "Timeline started...");
/*
        getWaypointMissionOperator().downloadMission(
                djiError -> {
                    if (djiError == null) {
                        parent.logMessageDJI("Waypoint mission successfully downloaded");
                    } else {
                        parent.logMessageDJI("Error downloading: " + djiError.getDescription());
                    }
                });
        WaypointMission wm = getWaypointMissionOperator().getLoadedMission();
        if (wm == null) {
            parent.logMessageDJI("No mission loaded");
            return;
        }
        parent.logMessageDJI("Waypoint count: " + wm.getWaypointCount());
        for (Waypoint w : wm.getWaypointList())
            parent.logMessageDJI(w.coordinate.toString());
        parent.logMessageDJI("State: " + getWaypointMissionOperator().getCurrentState().getName());

 */
    }

    /********************************************
     * Parameter callbacks                      *
     ********************************************/

    public class ParamIntegerCallback implements CommonCallbacks.CompletionCallbackWith<Integer> {
        private int paramIndex;

        ParamIntegerCallback(int paramIndex) {
            this.paramIndex = paramIndex;
        }

        @Override
        public void onSuccess(Integer integer) {
            getParams().get(paramIndex).setParamValue((float) integer);
//            parent.logMessageDJI("Fetched param from DJI: " + getParams().get(paramIndex).getParamName() + "=" + String.valueOf(integer));
        }

        @Override
        public void onFailure(DJIError djiError) {
            getParams().get(paramIndex).setParamValue(-99.0f);
//            parent.logMessageDJI("Param fetch fail: " + getParams().get(paramIndex).getParamName());
        }
    }

    public class ParamBooleanCallback implements CommonCallbacks.CompletionCallbackWith<Boolean> {
        private int paramIndex;

        ParamBooleanCallback(int paramIndex) {
            this.paramIndex = paramIndex;
        }

        @Override
        public void onSuccess(Boolean aBoolean) {
            getParams().get(paramIndex).setParamValue(aBoolean ? 1.0f : 0.0f);
//            parent.logMessageDJI("Fetched param from DJI: " + getParams().get(paramIndex).getParamName() + "=" + String.valueOf(aBoolean));
        }

        @Override
        public void onFailure(DJIError djiError) {
            getParams().get(paramIndex).setParamValue(-99.0f);
//            parent.logMessageDJI("Param fetch fail: " + getParams().get(paramIndex).getParamName());
        }
    }

    public class ParamControlModeCallback implements CommonCallbacks.CompletionCallbackWith<ControlMode> {
        private int paramIndex;

        ParamControlModeCallback(int paramIndex) {
            this.paramIndex = paramIndex;
        }

        @Override
        public void onSuccess(ControlMode cMode) {
            if (cMode == ControlMode.MANUAL)
                getParams().get(paramIndex).setParamValue(0f);
            else if (cMode == ControlMode.SMART)
                getParams().get(paramIndex).setParamValue(2f);
            else if (cMode == ControlMode.UNKNOWN)
                getParams().get(paramIndex).setParamValue(255f);

//            parent.logMessageDJI("Fetched param from DJI: " + getParams().get(paramIndex).getParamName() + "=" + String.valueOf(cMode));
        }

        @Override
        public void onFailure(DJIError djiError) {
            getParams().get(paramIndex).setParamValue(-99.0f);
//            parent.logMessageDJI("Param fetch fail: " + getParams().get(paramIndex).getParamName());
        }
    }

    public class ParamConnectionFailSafeBehaviorCallback implements CommonCallbacks.CompletionCallbackWith<ConnectionFailSafeBehavior> {
        private int paramIndex;

        ParamConnectionFailSafeBehaviorCallback(int paramIndex) {
            this.paramIndex = paramIndex;
        }

        @Override
        public void onSuccess(ConnectionFailSafeBehavior behavior) {
            if (behavior == ConnectionFailSafeBehavior.HOVER)
                getParams().get(paramIndex).setParamValue(0f);
            else if (behavior == ConnectionFailSafeBehavior.LANDING)
                getParams().get(paramIndex).setParamValue(1f);
            else if (behavior == ConnectionFailSafeBehavior.GO_HOME)
                getParams().get(paramIndex).setParamValue(2f);
            else if (behavior == ConnectionFailSafeBehavior.UNKNOWN)
                getParams().get(paramIndex).setParamValue(255f);

            parent.logMessageDJI("Fetched param from DJI: " + getParams().get(paramIndex).getParamName() + "=" + behavior);
        }

        @Override
        public void onFailure(DJIError djiError) {
            getParams().get(paramIndex).setParamValue(-99.0f);
            parent.logMessageDJI("Param fetch fail: " + getParams().get(paramIndex).getParamName());
        }
    }

    public class ParamWriteCompletionCallback implements CommonCallbacks.CompletionCallback {

        private int paramIndex;

        ParamWriteCompletionCallback(int paramIndex) {
            this.paramIndex = paramIndex;
        }

        @Override
        public void onResult(DJIError djiError) {
            if (djiError == null)
                parent.logMessageDJI(("Wrote param to DJI: " + getParams().get(paramIndex).getParamName()));
            else
                parent.logMessageDJI(("Error writing param to DJI: " + getParams().get(paramIndex).getParamName()));
        }
    }

    public class CellVoltageCompletionCallback implements CommonCallbacks.CompletionCallbackWith<Integer[]> {

        @Override
        public void onSuccess(Integer[] integer) {
            for (int i = 0; i < integer.length; i++)
                mCellVoltages[i] = integer[i];
            Log.d(TAG, "got cell voltages, v[0] =" + mCellVoltages[0]);
        }

        @Override
        public void onFailure(DJIError djiError) {

        }

    }

    /********************************************
     * Start Stop  callbacks                      *
     ********************************************/

    void startRecordingVideo(boolean sendResponses) {
        djiAircraft.getCamera().setMode(SettingsDefinitions.CameraMode.RECORD_VIDEO, djiError -> {
            if (djiError != null) {
                parent.logMessageDJI("Error Setting RecordVideo Mode: " + djiError.toString());
                if(sendResponses) send_command_ack(MAV_CMD_VIDEO_START_CAPTURE, MAV_RESULT.MAV_RESULT_FAILED);
            } else {
                djiAircraft.getCamera().startRecordVideo(djiError2 -> {
                    if (djiError2 == null) {
                        parent.logMessageDJI("Started recording video");
                        if(sendResponses) send_command_ack(MAV_CMD_VIDEO_START_CAPTURE, MAV_RESULT.MAV_RESULT_ACCEPTED);
                    } else {
                        parent.logMessageDJI("Error starting video recording: " + djiError2.toString());
                        if(sendResponses) send_command_ack(MAV_CMD_VIDEO_START_CAPTURE, MAV_RESULT.MAV_RESULT_FAILED);
                    }
                });
            }
        });
    }

    void stopRecordingVideo(boolean sendResponses) {
        // If we don't sync it will cause a flightcontroller crash on Mini 2 if we startRecordingVideo immediately after.
        // Happens only after a few minutes of recording footage.
        AtomicBoolean waitStopRecording = new AtomicBoolean(true);

        djiAircraft.getCamera().stopRecordVideo(djiError -> {
            if (djiError == null) {
                parent.logMessageDJI("Stopped recording video");
                if(sendResponses) send_command_ack(MAV_CMD_VIDEO_STOP_CAPTURE, MAV_RESULT.MAV_RESULT_ACCEPTED);
            } else {
                parent.logMessageDJI("Error stopping video recording: " + djiError.toString());
                if(sendResponses) send_command_ack(MAV_CMD_VIDEO_STOP_CAPTURE, MAV_RESULT.MAV_RESULT_FAILED);
            }
            waitStopRecording.set(false);
        });

        while(waitStopRecording.get())
        {
            try
            {
                Thread.sleep(10);
            }
            catch(InterruptedException ex)
            {
                Thread.currentThread().interrupt();
            }
        }

        // Prevent FlightController crash. Additional wait since a sync to the record isn't enough apparently.
        // Min. 1 second, 1.5 just to be safe.
        try
        {
            Thread.sleep(1500);
        }
        catch(InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @param lat2
     * @param lon2
     * @param lat1
     * @param lon1
     * @return Returns angle in [0, 360]
     */
    double getBearingBetweenWaypoints(double lat2, double lon2, double lat1, double lon1) {
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);
        lon1 = Math.toRadians(lon1);
        lon2 = Math.toRadians(lon2);

        double y = Math.sin(lon2 - lon1) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(lon2 - lon1);
        double z = Math.toDegrees(Math.atan2(y, x));
        if (z < 0.0) z += 360.0;
        return z;
    }

    double getRangeBetweenWaypoints_m(double lat1, double lon1, double el1, double lat2, double lon2, double el2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS * c; // convert to meters
        double height = el1 - el2;
        distance = Math.pow(distance, 2) + Math.pow(height, 2);
        return Math.sqrt(distance);
    }

    /**
     * Shortest angle between two angles.
     * @param alpha Angle in range [0, 360]
     * @param beta Angle in range [0, 360]
     * @return Result will be [-180, 180].
     */
    private double rotation(double alpha, double beta) {
        double phi = Math.abs(beta - alpha) % 360;       // This is either the distance or 360 - distance
        double distance = phi > 180 ? 360 - phi : phi;
        double sign = (alpha - beta >= 0 && alpha - beta <= 180) || (alpha - beta <= -180 && alpha - beta >= -360) ? distance : distance * -1;
        return sign;
    }

    public double[] get_location_metres(double dForward, double dRight, double alt) {
        FlightControllerState coord = djiAircraft.getFlightController().getState();
        double ro = Math.toRadians(coord.getAttitude().yaw);
        double lat = coord.getAircraftLocation().getLatitude();
        double lon = coord.getAircraftLocation().getLongitude();
        double newalt = coord.getAircraftLocation().getAltitude() + alt;

        double dNorth = dForward * Math.cos(ro) - dRight * Math.sin(ro);
        double dEast = dForward * Math.sin(ro) + dRight * Math.cos(ro);

        //Coordinate offsets in radians
        double dLat = dNorth / EARTH_RADIUS;
        double dLon = dEast / (EARTH_RADIUS * Math.cos(Math.toRadians(lat)));

        // New position in decimal degrees
        double newlat = lat + Math.toDegrees(dLat);
        double newlon = lon + Math.toDegrees(dLon);

        return new double[]{newlat, newlon, newalt};
    }

    private double[] get_location_intermediet(double lat, double lon, double dist, double yaw) {

        double ro = Math.toRadians(yaw);
        double dNorth = dist * Math.cos(ro);
        double dEast = dist * Math.sin(ro);

        //Coordinate offsets in radians
        double dLat = dNorth / EARTH_RADIUS;
        double dLon = dEast / (EARTH_RADIUS * Math.cos(Math.toRadians(lat)));

        // New position in decimal degrees
        double newlat = lat + Math.toDegrees(dLat);
        double newlon = lon + Math.toDegrees(dLon);

        return new double[]{newlat, newlon};
    }


    // FTP and file related functions
    // TODO: Move to plugin or FTP class
    public void initMediaManager(List<String> address)
    {
        parent.pluginManager.m_mailToAddress = address;
        DJILog.e(TAG, "Addresses: "+address.toString());

        parent.logMessageDJI("Image retrival started...");
        switch_camera_mode = camera_mode.IDLE;

        if (m_aircraft == null)
        {
            mediaFileList.clear();
            DJILog.e(TAG, "Product disconnected");
            switch_camera_mode = camera_mode.DISCONNECTED;
            return;

        } else {
            if (null != m_camera && m_camera.isMediaDownloadModeSupported())
            {
                mMediaManager = m_camera.getMediaManager();

                // Is actually of no interest...
                if(!m_camera.isSSDSupported()){
                    parent.logMessageDJI("Internal ssd is not suported");
                }
                if (null != mMediaManager) {
                    mMediaManager.addUpdateFileListStateListener(this.updateFileListStateListener);
                    //switchCameraMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD);

                    Objects.requireNonNull(RDApplication.getCameraInstance()).setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD, error -> {
                        if (error == null) {
                            parent.logMessageDJI("Set camera to MEDIA_DOWNLOAD success");
                            getFileList();
                        } else {
                            parent.logMessageDJI("Set camera to MEDIA_DOWNLOAD failed");
                            parent.logMessageDJI(error.toString());
                        }
                    });

                    // Is actually of no interest...
                    if (mMediaManager.isVideoPlaybackSupported()) {
                        parent.logMessageDJI("Camera support video playback!");
                    } else {
                        parent.logMessageDJI("Camera does not support video playback!");
                    }

                    scheduler = mMediaManager.getScheduler();
                } else{
                    switch_camera_mode = camera_mode.MEDIAMANAGER;
                }
            } else if (null != m_camera && !m_camera.isMediaDownloadModeSupported()) {
                parent.logMessageDJI("Media Download Mode not Supported");
                switch_camera_mode = camera_mode.DOWNLOADMODESUPPORTED;
            }
        }
        return;
    }

    public void getFileList() {
        BaseProduct product = null;
        if (RDApplication.getSim() == false) product = m_aircraft;

        if (product != null){
            if(product.isConnected()) {
                mMediaManager = m_camera.getMediaManager();
                if (mMediaManager != null) {
                    parent.logMessageDJI(currentFileListState.name());
                    if ((currentFileListState == MediaManager.FileListState.SYNCING) || (currentFileListState == MediaManager.FileListState.DELETING)) {
                        DJILog.e(TAG, "Media Manager is busy.");
                        parent.logMessageDJI("Media Manager is busy.");
                    } else {
                        mMediaManager.refreshFileListOfStorageLocation(SettingsDefinitions.StorageLocation.SDCARD, djiError -> {
                            if (null == djiError) {
                                //Reset data
                                if (currentFileListState != MediaManager.FileListState.INCOMPLETE) {
                                    mediaFileList.clear();
                                }
                                mediaFileList = mMediaManager.getSDCardFileListSnapshot();
                                Collections.sort(mediaFileList, (lhs, rhs) -> {
                                    if (lhs.getTimeCreated() < rhs.getTimeCreated()) {
                                        return -1;
                                    } else if (lhs.getTimeCreated() > rhs.getTimeCreated()) {
                                        return 1;
                                    }
                                    return 0;
                                });
                                scheduler.resume(error -> {
                                    if (error == null) {
                                    }
                                });

                                if(numFiles != mediaFileList.size()){
                                    numFiles = mediaFileList.size();
                                }

                                parent.logMessageDJI("Got Media Files: " + mediaFileList.size());
                                for(int i=0; i<mediaFileList.size(); i++) {
                                    MediaFile file = mediaFileList.get(i);
                                    String fileName = file.getFileName();
                                    parent.logMessageDJI("FileName on Drone: " + fileName);
                                }
                            } else {
                                parent.logMessageDJI("Get Media File List Failed: " + djiError.getDescription());
                            }
                        });
                    }
                } else {
                    parent.logMessageDJI("mMediaManager == null");
                }
            }else {
                parent.logMessageDJI("product == None, not connected...");
            }
        } else {
            parent.logMessageDJI("product == null, Product in SIM mode ?");
        }
    }
    //Listeners
    private MediaManager.FileListStateListener updateFileListStateListener = new MediaManager.FileListStateListener() {
        @Override
        public void onFileListStateChange(MediaManager.FileListState state) {
            currentFileListState = state;
            parent.logMessageDJI("Changed state to " + currentFileListState.name());
            if(currentFileListState == MediaManager.FileListState.UP_TO_DATE){
                if(parent.pluginManager.m_mailToAddress != null) {
                    parent.logMessageDJI("getFile");
                    if (numFiles > 0) {
                        downloadFileByIndex(numFiles - 1);
                    }
                }
            }
        }
    };
    public void downloadFileByName(final String name) {
        for(int i=0; i<mediaFileList.size(); i++) {
            if (mediaFileList.get(i).getFileName() == name) {
                downloadFileByIndex(i);
            }
        }
    }

    public boolean downloadFileByIndex(final int index){
        if ((mediaFileList.get(index).getMediaType() == MediaFile.MediaType.PANORAMA)
                || (mediaFileList.get(index).getMediaType() == MediaFile.MediaType.SHALLOW_FOCUS)) {
            parent.logMessageDJI( "Media type is " + mediaFileList.get(index).getMediaType() + " This is not accaptable.");
            return false;
        }

        mediaFileList.get(index).fetchFileData(destDir, null, new DownloadListener<String>() {
            @Override
            public void onFailure(DJIError error) {
                parent.logMessageDJI( "Download File Failed" + error.getDescription());
            }

            @Override
            public void onProgress(long total, long current) {
            }

            @Override
            public void onRateUpdate(long total, long current, long persize) {
                int tmpProgress = (int) (1.0 * current / total * 100);
                if (tmpProgress != currentProgress) {
                    currentProgress = tmpProgress;
                }
            }

            @Override
            public void onRealtimeDataUpdate(byte[] bytes, long l, boolean b) {
            }

            @Override
            public void onStart() {
                currentProgress = 0;
            }

            @Override
            public void onSuccess(String filePath) {
                m_directory = filePath;
                parent.logMessageDJI( "Download File Success: " + filePath + "/" + mediaFileList.get(index).getFileName());
                //last_downloaded_file = filePath+ "/" + mediaFileList.get(index).getFileName();
                last_downloaded_file = mediaFileList.get(index).getFileName();

                RDApplication.getCameraInstance().setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, error -> {
                    if (error == null) {
                        parent.logMessageDJI("Set camera to SHOOT_PHOTO success");
                        parent.pluginManager.sendmail(last_downloaded_file);
                        NotificationHandler.notifySnackbar(parent.findViewById(R.id.snack),R.string.release, LENGTH_LONG);
                    } else {
                        parent.logMessageDJI("Set camera to SHOOT_PHOTO failed");
                        parent.logMessageDJI(error.toString());
                    }
                });
                lastDownloadedIndex = index;
            }
        });
        return true;
    }

    void onFileListStateChange(MediaManager.FileListState state){
        parent.logMessageDJI( "Files changed?");
    }
}