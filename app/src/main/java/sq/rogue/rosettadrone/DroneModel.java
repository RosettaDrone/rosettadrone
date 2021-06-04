package sq.rogue.rosettadrone;

import android.app.AlertDialog;
import android.content.SharedPreferences;
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
import com.MAVLink.common.msg_global_position_int;
import com.MAVLink.common.msg_gps_raw_int;
import com.MAVLink.common.msg_heartbeat;
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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.PortUnreachableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import dji.sdk.battery.Battery;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.followme.FollowMeMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

import static com.MAVLink.common.msg_set_position_target_global_int.MAVLINK_MSG_ID_SET_POSITION_TARGET_GLOBAL_INT;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_COMPONENT_ARM_DISARM;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_DIGICAM_CONTROL;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_SET_SERVO;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_TAKEOFF;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_START_CAPTURE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_STOP_CAPTURE;
import static com.MAVLink.enums.MAV_COMPONENT.MAV_COMP_ID_AUTOPILOT1;
import static sq.rogue.rosettadrone.util.getTimestampMicroseconds;
import static sq.rogue.rosettadrone.util.safeSleep;

// TENI import dji.common.remotecontroller.ChargeRemaining;
// import dji.sdksharedlib.keycatalog.extension.InternalKey;


public class DroneModel implements CommonCallbacks.CompletionCallback {
    private static final int NOT_USING_GCS_COMMANDED_MODE = -1;
    private final String TAG = DroneModel.class.getSimpleName();
    public DatagramSocket socket;
    DatagramSocket secondarySocket;
    private Aircraft djiAircraft;
    private ArrayList<MAVParam> params = new ArrayList<>();
    private long ticks = 0;
    private MainActivity parent;
    protected SharedPreferences sharedPreferences;

    private TimeLineMissionControlView TimeLine = new TimeLineMissionControlView();

    private int mSystemId = 1;
    private int mGCSCommandedMode;

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

    private float mPitch = 0;
    private float mRoll = 0;
    private float mYaw = 0;
    private float mThrottle = 0;
    private double m_Latitude = 0;
    private double m_Longitude = 0;
    public float m_alt = 0;

    private double m_Destination_Lat = 0;
    private double m_Destination_Lon = 0;
    private float m_Destination_Alt = 0;
    private double m_Destination_Yaw = 0;
    private double m_Destination_Yaw_rate = 0;
    private float m_Destination_Set_vx = -1;
    private float m_Destination_Set_vy = -1;
    private float m_Destination_Set_vz = -1;
    private int m_Destination_Mask = 0;
    private double m_Destination_brng = 0;
    private double m_Destination_hypotenuse = 0;
    private int m_lastCommand = 0;

    private FlightMode lastMode = FlightMode.ATTI_HOVER;
    private HardwareState.FlightModeSwitch rcmode = HardwareState.FlightModeSwitch.POSITION_ONE;
    private static HardwareState.FlightModeSwitch avtivemode = HardwareState.FlightModeSwitch.POSITION_ONE; // Change this to decide what position the mode switch should be inn.

    private SendVelocityDataTask mSendVirtualStickDataTask = null;
    private Timer mSendVirtualStickDataTimer = null;
    public MoveTo mMoveToDataTask = null;
    public Timer mMoveToDataTimer = null;
    private Rotation m_ServoSet;
    private float m_ServoPos_pitch = 0;
    private float m_ServoPos_yaw = 0;
    public boolean photoTaken = false;
    public boolean gotoNoPhoto = false;
    public double m_Curvesize = 0.0;
    public double m_POI_Lat = 0;
    public double m_POI_Lon = 0;
    public boolean m_CruisingMode = false;
    public boolean m_Stay = false;
    public AtomicBoolean gimbalReady = null;
    private MiniPID miniPIDSide;
    private MiniPID miniPIDFwd;
    private MiniPID miniPIDAlti;
    private MiniPID miniPIDHeading;

    private boolean mSafetyEnabled = true;
    private boolean mMotorsArmed = false;
    private FollowMeMissionOperator fmmo;
    public FlightController mFlightController;
    private Gimbal mGimbal = null;
    private Rotation mRotation = null;

    private Model m_model;

    private int mAIfunction_activation = 0;
    public boolean mAutonomy = false;
    public int mAirBorn = 0;
    int mission_loaded = -1;

    DroneModel(MainActivity parent, DatagramSocket socket, boolean sim) {
        this.parent = parent;
        this.socket = socket;
        initFlightController(sim);
    }

    public float get_battery_status() {
        if (mCFullChargeCapacity_mAh > 0) {
            return (mCVoltage_pr); //mCChargeRemaining_mAh * 100 / mCFullChargeCapacity_mAh);
        }
        return 0;
    }

    public float get_drone_battery_prosentage() {
        return mCVoltage_pr;
    }

    public float get_controller_battery_prosentage() {
        return mControllerVoltage_pr;
    }


    private void initFlightController(boolean sim) {
        parent.logMessageDJI("Starting FlightController...");

        // Default to true to avoid issues with set gimbal never called before taking a pic
        gimbalReady = new AtomicBoolean(true);

        // PID for position control...
        miniPIDSide = new MiniPID(0.35, 0.00008, 4.0);
        miniPIDFwd = new MiniPID(0.35, 0.00008, 4.0);
        miniPIDAlti = new MiniPID(0.35, 0.00008, 4.0);
        miniPIDHeading = new MiniPID(1.0, 0.00001, 2.0);
        
        //miniPIDSide.setOutputRampRate(0.3);
        //miniPIDFwd.setOutputRampRate(0.3);

        Aircraft aircraft = (Aircraft) RDApplication.getProductInstance(); //DJISimulatorApplication.getAircraftInstance();
        if (aircraft == null || !aircraft.isConnected()) {
            parent.logMessageDJI("No target...");
            mFlightController = null;
            return;
        } else {
            m_model = aircraft.getModel();
            if (m_model.equals("INSPIRE_1") || m_model.equals("INSPIRE_1_PRO") || m_model.equals("INSPIRE_1_RAW")) {
                avtivemode = HardwareState.FlightModeSwitch.POSITION_THREE;
            }

            mGimbal = aircraft.getGimbals().get(0);
            mFlightController = aircraft.getFlightController();
            mFlightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
            mFlightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
            mFlightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
            mFlightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
            mFlightController.setFlightOrientationMode(FlightOrientationMode.COURSE_LOCK, null);

            if (sim) {
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

            if (sim) {
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
        //  SetMesasageBox("Controller Ready!!!!!");
    }

    LocationCoordinate3D getSimPos3D(){
        sharedPreferences = android.preference.PreferenceManager.getDefaultSharedPreferences(parent.getApplicationContext());
        LocationCoordinate3D pos = new LocationCoordinate3D(
                Double.parseDouble(Objects.requireNonNull(sharedPreferences.getString("pref_sim_pos_lat", "-1"))),
                Double.parseDouble(Objects.requireNonNull(sharedPreferences.getString("pref_sim_pos_lon", "-1"))),
                Float.parseFloat(Objects.requireNonNull(sharedPreferences.getString("pref_sim_pos_alt", "-1")))
        );

        // If this is the first time the app is running...
        if(pos.getLatitude() == -1){
            sharedPreferences.getStringSet("pref_sim_pos_lat", Collections.singleton("60.4094"));
            pos.setLatitude(60.4094);
        }
        if(pos.getLongitude() == -1){
            sharedPreferences.getStringSet("pref_sim_pos_lon", Collections.singleton("10.4911"));
            pos.setLongitude(10.4911);
        }
        if(pos.getAltitude() == -1){  // Not Used...
            sharedPreferences.getStringSet("pref_sim_pos_alt", Collections.singleton("210.0"));
            pos.setAltitude((float)210.0);
        }
        return (pos);
    }

    LocationCoordinate2D getSimPos2D(){
        LocationCoordinate3D pos3d = getSimPos3D();
        LocationCoordinate2D pos = new LocationCoordinate2D(pos3d.getLatitude(),pos3d.getLongitude());
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
        LEDsSettings.Builder Tmp = new LEDsSettings.Builder().frontLEDsOn(enabled);
        assert Tmp != null;
        djiAircraft.getFlightController().setLEDsEnabledSettings(Tmp.build(), djiError -> {
            if (djiError == null) {
                parent.logMessageDJI("Front LEDs set to " + enabled);

            } else {
                parent.logMessageDJI("Error setting front LEDs" + djiError.getDescription());
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

    public void setHeadingMode(int headingValue) {
        switch (headingValue) {
            case 0:
                break;
            case 1:
                break;
            case 2:
                break;
            case 3:
                break;
            case 4:
                break;
            default:
                parent.logMessageDJI("Invalid heading mode.");
        }
    }

    public boolean isMotorsArmed() {
        return mMotorsArmed;
    }

    public void setMotorsArmed(boolean motorsArmed) {
        mMotorsArmed = motorsArmed;
    }

    public int getGCSCommandedMode() {
        return mGCSCommandedMode;
    }

    void setGCSCommandedMode(int GCSCommandedMode) {
        mGCSCommandedMode = GCSCommandedMode;
    }

    void setWaypointMission(final WaypointMission wpMission) {
        mission_loaded = -1;
        DJIError load_error = getWaypointMissionOperator().loadMission(wpMission);
        if (load_error != null)
            parent.logMessageDJI("loadMission() returned error: " + load_error.toString());
        else {
            parent.logMessageDJI("Uploading mission");
            getWaypointMissionOperator().uploadMission(
                    djiError -> {
                        if (djiError == null) {
                            while (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.UPLOADING) {
                                safeSleep(200);
                            }
                            if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.READY_TO_EXECUTE) {
                                parent.logMessageDJI("Mission uploaded and ready to execute!");
                                mission_loaded = MAV_MISSION_RESULT.MAV_MISSION_ACCEPTED;
                                send_mission_ack(mission_loaded);
                            } else {
                                parent.logMessageDJI("Error uploading waypoint mission to drone.");
                                mission_loaded = MAV_MISSION_RESULT.MAV_MISSION_ERROR;
                                send_mission_ack(mission_loaded);
                            }

                        } else {
                            parent.logMessageDJI("Error uploading: " + djiError.getDescription());
                            parent.logMessageDJI(("Please try re-uploading"));
                            mission_loaded = MAV_MISSION_RESULT.MAV_MISSION_ERROR;
                            send_mission_ack(mission_loaded);
                        }
                        //parent.logMessageDJI("New state: " + getWaypointMissionOperator().getCurrentState().getName());
                    });
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

                rcmode = Objects.requireNonNull(rcHardwareState.getFlightModeSwitch());
//                parent.logMessageDJI("FlighMode switch : "+ rcmode);

                // If C3 is pressed...
                /*
                if(mC3 == true && !lastState) {
                    parent.logMessageDJI("DoTakeoff");
                    do_takeoff();
                    lastState = true;
                }
                else if(mC3 == false && lastState){
                    lastState = false;
                }
                 */
            }
        });

        this.djiAircraft.getRemoteController().setChargeRemainingCallback(new dji.common.remotecontroller.BatteryState.Callback() {
            private int lastState = 100;

            @Override
            public void onUpdate(dji.common.remotecontroller.BatteryState batteryState) {
                mControllerVoltage_pr = (batteryState.getRemainingChargeInPercent());
                if (mControllerVoltage_pr > 90) {
                    lastState = 100;
                }
                else if (mControllerVoltage_pr < 20 && lastState == 100) {
                    lastState = 20;
                    SetMesasageBox("Controller Battery Warning 20% !!!!!");
                }
                else if (mControllerVoltage_pr < 10 && lastState == 20) {
                    lastState = 10;
                    SetMesasageBox("Controller Battery Warning 10% !!!!!");
                }
                else if (mControllerVoltage_pr < 5 && lastState == 10) {
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
                //     Log.d(TAG, "Battery State callback");
                mCFullChargeCapacity_mAh = batteryState.getFullChargeCapacity();
                mCChargeRemaining_mAh = batteryState.getChargeRemaining();
                mCVoltage_mV = batteryState.getVoltage();
                mCCurrent_mA = Math.abs(batteryState.getCurrent());
                mCBatteryTemp_C = batteryState.getTemperature();
                mCVoltage_pr = batteryState.getChargeRemainingInPercent();

                if (mCVoltage_pr > 0) {
//                        Log.d(TAG, "Voltage %: " + mCVoltage_pr);
//                      Log.d(TAG, "mlastState %: " + mlastState);

                    if (mCVoltage_pr > 90) {
                        mlastState = 100;
                    }
                    else if (mCVoltage_pr <= 20 && mlastState == 100) {
                        mlastState = 20;
                        SetMesasageBox("Drone Battery Warning 20% !!!!!");
                    }
                    else if (mCVoltage_pr <= 10 && mlastState == 20) {
                        mlastState = 10;
                        SetMesasageBox("Drone Battery Warning 10% !!!!!");
                    }
                    else if (mCVoltage_pr <= 5 && mlastState == 10) {
                        mlastState = 5;
                        SetMesasageBox("Drone Battery Warning 5% !!!!!");
                    }
                }
                //       Log.d(TAG, "Voltage %: " + mCVoltage_pr);
            });
            this.djiAircraft.getBattery().getCellVoltages(new CellVoltageCompletionCallback());
        } else {
            Log.e(TAG, "djiAircraft.getBattery() IS NULL");
            return false;
        }

        Battery.setAggregationStateCallback(aggregationState -> {
            //       Log.d(TAG, "Aggregation State callback");
            mFullChargeCapacity_mAh = aggregationState.getFullChargeCapacity();
            mChargeRemaining_mAh = aggregationState.getChargeRemaining();
            mVoltage_mV = aggregationState.getVoltage();
            mCurrent_mA = aggregationState.getCurrent();
            mVoltage_pr = aggregationState.getChargeRemainingInPercent();
            //        Log.d(TAG, "Aggregated voltage: " + String.valueOf(aggregationState.getVoltage()));
        });

        /**************************************************
         * Called whenever airlink quality changes        *
         **************************************************/

        djiAircraft.getAirLink().setDownlinkSignalQualityCallback(i -> mDownlinkQuality = i);

        djiAircraft.getAirLink().setUplinkSignalQualityCallback(i -> mUplinkQuality = i);

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

    void armMotors() {
        if (mSafetyEnabled) {
            parent.logMessageDJI(parent.getResources().getString(R.string.safety_launch));
            send_command_ack(MAV_CMD_COMPONENT_ARM_DISARM, MAV_RESULT.MAV_RESULT_DENIED);
        } else {
            send_command_ack(MAV_CMD_COMPONENT_ARM_DISARM, MAV_RESULT.MAV_RESULT_ACCEPTED);
            mMotorsArmed = true;
        }

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
    }

    void disarmMotors() {
        djiAircraft.getFlightController().turnOffMotors(new CommonCallbacks.CompletionCallback() {

            @Override
            public void onResult(DJIError djiError) {
                // TODO reattempt if arming/disarming fails
                if (djiError == null)
                    send_command_ack(MAV_CMD_COMPONENT_ARM_DISARM, MAV_RESULT.MAV_RESULT_ACCEPTED);
                else
                    send_command_ack(MAV_CMD_COMPONENT_ARM_DISARM, MAV_RESULT.MAV_RESULT_FAILED);
                Log.d(TAG, "onResult()");
                mMotorsArmed = false;
            }
        });
    }

    public void sendMessage(MAVLinkMessage msg) {
        if (socket == null)
            return;

        MAVLinkPacket packet = msg.pack();

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
//                parent.logMessageDJI("SECONDARY PACKET SENT");
            }
//            if(msg.msgid != MAVLINK_MSG_ID_POWER_STATUS &&
//                    msg.msgid != MAVLINK_MSG_ID_SYS_STATUS &&
//                    msg.msgid != MAVLINK_MSG_ID_VIBRATION &&
//                    msg.msgid != MAVLINK_MSG_ID_ATTITUDE &&
//                    msg.msgid != MAVLINK_MSG_ID_VFR_HUD &&
//                    msg.msgid != MAVLINK_MSG_ID_GLOBAL_POSITION_INT &&
//                    msg.msgid != MAVLINK_MSG_ID_GPS_RAW_INT &&
//                    msg.msgid != MAVLINK_MSG_ID_RADIO_STATUS)
//                parent.logMessageToGCS(msg.toString());

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
        msg.compid = 0;
        
        sendMessage(msg);
    }

    private void send_heartbeat() {
        msg_heartbeat msg = new msg_heartbeat();
        msg.type = MAV_TYPE.MAV_TYPE_QUADROTOR;
        msg.autopilot = MAV_AUTOPILOT.MAV_AUTOPILOT_ARDUPILOTMEGA;

        // For base mode logic, see Copter::sendHeartBeat() in ArduCopter/GCS_Mavlink.cpp
        msg.base_mode = MAV_MODE_FLAG.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED;
        msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_MANUAL_INPUT_ENABLED;

        //parent.logMessageDJI("FlightMode: "+djiAircraft.getFlightController().getState().getFlightMode());

        lastMode = djiAircraft.getFlightController().getState().getFlightMode();
        switch (lastMode) {
            case MANUAL:
                msg.custom_mode = ArduCopterFlightModes.STABILIZE;
                break;
            case ATTI_HOVER:
                break;
            case HOVER:
                break;
            case GPS_BLAKE:
                break;
            case ATTI_LANDING:
                break;
            case CLICK_GO:
                break;
            case CINEMATIC:
                break;
            case ATTI_LIMITED:
                break;
            case PANO:
                break;
            case FARMING:
                break;
            case FPV:
                break;
            case PALM_CONTROL:
                break;
            case QUICK_SHOT:
                break;
            case DETOUR:
                break;
            case TIME_LAPSE:
                break;
            case POI2:
                break;
            case OMNI_MOVING:
                break;
            case ADSB_AVOIDING:
                break;
            case ATTI:
                msg.custom_mode = ArduCopterFlightModes.LOITER;
                break;
            case ATTI_COURSE_LOCK:
                break;
            case GPS_ATTI:
                msg.custom_mode = ArduCopterFlightModes.STABILIZE;
                break;
            case GPS_COURSE_LOCK:
                break;
            case GPS_HOME_LOCK:
                break;
            case GPS_HOT_POINT:
                break;
            case ASSISTED_TAKEOFF:
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
                msg.custom_mode = ArduCopterFlightModes.GUIDED;
                msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_GUIDED_ENABLED;
                break;
            case GPS_ATTI_WRISTBAND:
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
            case GPS_SPORT:
                break;
            case GPS_NOVICE:
                break;
            case UNKNOWN:
                break;
            case CONFIRM_LANDING:
                break;
            case TERRAIN_FOLLOW:
                break;
            case TRIPOD:
                break;
            case TRACK_SPOTLIGHT:
                break;
            case MOTORS_JUST_STARTED:
                break;
        }

        if (mGCSCommandedMode == ArduCopterFlightModes.AUTO)
            msg.custom_mode = ArduCopterFlightModes.AUTO;
        if (mGCSCommandedMode == ArduCopterFlightModes.GUIDED)
            msg.custom_mode = ArduCopterFlightModes.GUIDED;
        if (mGCSCommandedMode == ArduCopterFlightModes.BRAKE)
            msg.custom_mode = ArduCopterFlightModes.BRAKE;

        if (mMotorsArmed)
            msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_SAFETY_ARMED;

        if (mAutonomy)
            msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_AUTO_ENABLED;

        // Catches manual landings
        // Automatically disarm motors if aircraft is on the ground and a takeoff is not in progress
        if (!getDjiAircraft().getFlightController().getState().isFlying() && mGCSCommandedMode != ArduCopterFlightModes.GUIDED)
            mMotorsArmed = false;

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

    private void send_global_position_int() {
        msg_global_position_int msg = new msg_global_position_int();

        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        msg.lat = (int) (coord.getLatitude() * Math.pow(10, 7));
        msg.lon = (int) (coord.getLongitude() * Math.pow(10, 7));

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

    public double get_current_lat() {
        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        return coord.getLatitude();
    }

    public double get_current_lon() {
        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        return coord.getLongitude();
    }

    public float get_current_alt() {
        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        return coord.getAltitude();
    }

    private void send_gps_raw_int() {
        msg_gps_raw_int msg = new msg_gps_raw_int();

        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();


        msg.time_usec = getTimestampMicroseconds();
        msg.lat = (int) (coord.getLatitude() * Math.pow(10, 7));
        msg.lon = (int) (coord.getLongitude() * Math.pow(10, 7));
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

            mAutonomy = false;
        //    pauseWaypointMission();  // TODO:: halt mission for safety...
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
        msg_home_position msg = new msg_home_position();

        msg.latitude = (int) (djiAircraft.getFlightController().getState().getHomeLocation().getLatitude() * Math.pow(10, 7));
        msg.longitude = (int) (djiAircraft.getFlightController().getState().getHomeLocation().getLongitude() * Math.pow(10, 7));
        // msg.altitude = (int) (djiAircraft.getFlightController().getState().getHomePointAltitude());
        msg.altitude = (int) (djiAircraft.getFlightController().getState().getGoHomeHeight());

        // msg.x = 0;
        // msg.y = 0;
        // msg.z = 0;
        // msg.approach_x = 0;
        // msg.approach_y = 0;
        // msg.approach_z = 0;
        sendMessage(msg);
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
        Log.d(TAG, "Mission Count: "+msg.count);
        sendMessage(msg);
    }

    // This is a bit tricky, as DJI and MAVlink pack mission ithems very different...
    // Firste we expand the full mission list and then we pick the one we need...
    int send_mission_item(int i)
    {
        ArrayList<msg_mission_item_int> msglist = new ArrayList<>();
        WaypointMission x = getWaypointMissionOperator().getLoadedMission();

        // If a mission loaded...
        if(x!=null) {
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
            if(i>=0)
                sendMessage(msglist.get(i));
        }
        Log.d(TAG, "Mission return: "+i);
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

    public void fetch_gcs_mission() {
        request_mission_list();
    }

    //--------------------------------------------------------------------------------------
    //--------------------------------------------------------------------------------------

    private void request_mission_list() {
        msg_mission_request_list msg = new msg_mission_request_list();
        sendMessage(msg);
    }

    void request_mission_item(int seq) {
//        msg_mission_request msg = new msg_mission_request();
        msg_mission_request_int msg = new msg_mission_request_int();
        msg.seq = seq;
        msg.mission_type = MAV_MISSION_TYPE.MAV_MISSION_TYPE_MISSION;
        sendMessage(msg);
    }

    void startWaypointMission() {
        mAutonomy = false;
        parent.logMessageDJI("start WaypointMission()");

        if (getWaypointMissionOperator() == null) {
            parent.logMessageDJI("start WaypointMission() - WaypointMissionOperator null");
            return;
        }
        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.READY_TO_EXECUTE) {
            parent.logMessageDJI("Ready to execute mission!\n");
        } else {
            parent.logMessageDJI("Not ready to execute mission\n");
            parent.logMessageDJI(getWaypointMissionOperator().getCurrentState().getName());
            return;
        }
        if (mSafetyEnabled) {
            parent.logMessageDJI("You must turn off the safety to start mission");
        } else {
            getWaypointMissionOperator().startMission(djiError -> {
                if (djiError != null)
                    parent.logMessageDJI("Error: " + djiError.toString());
                else
                    parent.logMessageDJI("Mission started!");
            });
        }
    }

    public void stopWaypointMission() {
        mAutonomy = false;
        if (getWaypointMissionOperator() == null) {
            parent.logMessageDJI("stopWaypointMission() - mWaypointMissionOperator null");
            return;
        }

        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTING) {
            parent.logMessageDJI("Stopping mission...\n");
            getWaypointMissionOperator().stopMission(djiError -> {
                if (djiError != null)
                    parent.logMessageDJI("Error: " + djiError.toString());
                else
                    parent.logMessageDJI("Mission stopped!\n");
            });
        }
    }

    void pauseWaypointMission() {
        mAutonomy = false;
        if (getWaypointMissionOperator() == null) {
            parent.logMessageDJI("pauseWaypointMission() - mWaypointMissionOperator null");
            return;
        }

        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTING) {
            parent.logMessageDJI("Pausing mission...\n");
            getWaypointMissionOperator().pauseMission(djiError -> {
                if (djiError != null)
                    parent.logMessageDJI("Error: " + djiError.toString());
                else
                    parent.logMessageDJI("Mission paused!\n");
            });
        }
    }

    void resumeWaypointMission() {
        mAutonomy = false;
        if (isSafetyEnabled()) {
            parent.logMessageDJI(parent.getResources().getString(R.string.safety_launch));
            send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if (getWaypointMissionOperator() == null) {
            parent.logMessageDJI("resumeWaypointMission() - mWaypointMissionOperator null");
            return;
        }

        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTION_PAUSED) {
            parent.logMessageDJI("Resuming mission...\n");
            getWaypointMissionOperator().resumeMission(djiError -> {
                if (djiError != null)
                    parent.logMessageDJI("Error: " + djiError.toString());
                else {
                    parent.logMessageDJI("Mission resumed!\n");
                    mGCSCommandedMode = NOT_USING_GCS_COMMANDED_MODE;
                }
            });
        }
    }
    //--------------------------------------------------------------------------------------
    //--------------------------------------------------------------------------------------

    void do_takeoff(float alt) {
        mAutonomy = false;

        if (mSafetyEnabled) {
            parent.logMessageDJI(parent.getResources().getString(R.string.safety_launch));
            send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        // Only allow takeoff in P mode...
        if (rcmode == avtivemode) {
            parent.logMessageDJI(":rcmode != avtivemode " + rcmode + "   " + avtivemode);
            Log.i(TAG, ":rcmode != avtivemode " + rcmode + "   " + avtivemode);
            send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }
/*
        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.READY_TO_EXECUTE) {
            startWaypointMission();
            send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_ACCEPTED);
        } else {
            parent.logMessageDJI("Initiating takeoff");
            djiAircraft.getFlightController().startTakeoff(djiError -> {
                if (djiError != null) {
                    parent.logMessageDJI("Error: " + djiError.toString());
                    //  send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_FAILED);
                    mAirBorn = 1;
                } else {
                    parent.logMessageDJI("Takeoff successful!\n");
                    //  send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_ACCEPTED);
                    mAirBorn = 2;
                }
                mGCSCommandedMode = NOT_USING_GCS_COMMANDED_MODE;
            });
        }

        // Wait for command to be executed...
        Log.d(TAG,"Wait for takeoff...");
        while( mAirBorn == 0) {
            safeSleep(250);
        }

        // Check if ok or failed...
        if(mAirBorn == 1) {
            Log.d(TAG,"Takeoff failed...");
            send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_FAILED);
        }
        else{
            send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_IN_PROGRESS);

            int timeout = 0;

            // If we took off, wait for command co complete...
            Log.d(TAG,"Wait for first altitude...");
            while (m_alt < 1000.0 || timeout > (20*(1/0.250))) {
                timeout+= 1;
                safeSleep(250);
            }

            Log.d(TAG,"Takeoff success...");
            safeSleep(500);

            timeout = 0;
            // If we are airborne, or was airborne from the start...
            if (m_alt >= 800) {
                float desired_alt = alt - m_alt;
                Log.d(TAG, "Climb out...from: "+m_alt+" to: "+desired_alt+" set: "+alt);
                // Climb out to 99% of requested altitude, however 100% is set, just to avoid deadlock...
                while (m_alt < desired_alt || timeout > (30*(1/0.250))){
                    do_set_motion_relative(MAV_CMD_NAV_TAKEOFF, 0, 0, (alt - m_alt) / (float) 1000.0, 0, 0, 0, 0, 0, 0b00011111111000);
                    timeout+= 1;
                    safeSleep(250);
                }
                Log.d(TAG, "Climb out completed..."+m_alt);
            }else{
                Log.d(TAG, "Climb out failed...");
            }
        }

 */
        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.READY_TO_EXECUTE) {
            // But how about takeoff....
            startWaypointMission();
            send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_ACCEPTED);
        } else {
            FlightControllerState coord = djiAircraft.getFlightController().getState();
            TimeLine.TimeLinetakeOff(coord.getAircraftLocation().getLatitude(), coord.getAircraftLocation().getLongitude(), alt, 0);
            TimeLine.startTimeline();
            Log.d(TAG, "Takeoff started...");
        }
    }

    void do_land() {
        mAutonomy = false;
        parent.logMessageDJI("Initiating landing");
        djiAircraft.getFlightController().startLanding(djiError -> {
            if (djiError != null)
                parent.logMessageDJI("Error: " + djiError.toString());
            else {
                parent.logMessageDJI("Landing successful!\n");
                mMotorsArmed = false;
            }
            mAirBorn = 0;
        });
    }

    void do_go_home() {
        mAutonomy = false;
        parent.logMessageDJI("Initiating Go Home");
        djiAircraft.getFlightController().startGoHome(djiError -> {
            if (djiError != null)
                parent.logMessageDJI("Error: " + djiError.toString());
            else
                parent.logMessageDJI("Go home successful!\n");
        });
    }

    /********************************************
     * Motion implementation                    *
     ********************************************/

    void do_set_Gimbal(float channel, float value) {
        Rotation.Builder builder = new Rotation.Builder().mode(RotationMode.ABSOLUTE_ANGLE).time(1.25);
        float param = value;
        if ((int) channel == 9) {
            m_ServoPos_pitch = param;
            builder.mode(RotationMode.ABSOLUTE_ANGLE);
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

        gimbalReady.set(false);
        mGimbal.rotate(m_ServoSet, djiError -> {
            if (djiError != null)
                parent.logMessageDJI("Error: " + djiError.toString());

            Log.e(TAG, "Gimbal Pos: " + m_ServoSet.getPitch() + "  :  " + m_ServoSet.getYaw());
            send_command_ack(MAV_CMD_DO_SET_SERVO, MAV_RESULT.MAV_RESULT_ACCEPTED);

            gimbalReady.set(true);
        });
    }

    // --------------------------------------------------------------------------------
    // We want to move to a lat,lon,alt position, this has no support by DJI...
    public void do_set_motion_relative(int command, double forward, double right, float up, float head, float vx, float vy, float vz, float yaw_rate, int mask) {
        //   Log.i(TAG, "do_set_motion_relative");

        // Find the global lat/lon from the local reference fram (the drone is origo)...
        double[] pos = get_location_metres(forward, right, up);
        m_Destination_Lat = pos[0];
        m_Destination_Lon = pos[1];
        m_Destination_Alt = (float) pos[2];
        m_Destination_Yaw = head;
        m_Destination_Yaw_rate = yaw_rate;
        m_Destination_Set_vx = vx;
        m_Destination_Set_vy = vy;
        m_Destination_Set_vz = vz;
        m_Destination_Mask = mask;
        m_lastCommand = command;
        //------------------------------------------------
        // To be able to folow a stright line...
        FlightControllerState coord = djiAircraft.getFlightController().getState();

        // Find the heading difference compared to bearing... return 0-360 deg.
        m_Destination_brng = getBearingBetweenWaypoints(m_Destination_Lat, m_Destination_Lon, coord.getAircraftLocation().getLatitude(), coord.getAircraftLocation().getLongitude()) - 180;
        if (m_Destination_brng < 0) m_Destination_brng = m_Destination_brng + 360;

        // The direct distance to the destination... return distance in meters...
        m_Destination_hypotenuse = getRangeBetweenWaypoints_m(m_Destination_Lat, m_Destination_Lon, 0, coord.getAircraftLocation().getLatitude(), coord.getAircraftLocation().getLongitude(), 0);
        do_start_absolute_motion();
    }

    // --------------------------------------------------------------------------------
    public void goto_position(double Lat, double Lon, float alt, float head) {
        //TimeLine.TimeLineGoTo(Lat, Lon, alt, (float) 2.0, head);
        //TimeLine.startTimeline();
        gotoNoPhoto = true;
        do_set_motion_absolute(Lat, Lon, alt, head, 2.5f, 2.5f, 2.5f, 2.5f, 0);
    }

    // We want to move to a lat,lon,alt position, this has no support by DJI...
    public void do_set_motion_absolute(double Lat, double Lon, float alt, float head, float vx, float vy, float vz, float yaw_rate, int mask) {
        //    Log.i(TAG, "do_set_motion_absolute");
        photoTaken = false;

        // Set our new destination...
        m_Destination_Lat = Lat;
        m_Destination_Lon = Lon;
        m_Destination_Alt = alt;
        m_Destination_Yaw = head;
        m_Destination_Yaw_rate = yaw_rate;
        m_Destination_Set_vx = vx;
        m_Destination_Set_vy = vy;
        m_Destination_Set_vz = vz;
        m_Destination_Mask = mask;
        m_lastCommand = MAVLINK_MSG_ID_SET_POSITION_TARGET_GLOBAL_INT;

        //------------------------------------------------
        // To be able to follow a straight line...
        FlightControllerState coord = djiAircraft.getFlightController().getState();
        double local_lat = coord.getAircraftLocation().getLatitude();
        double local_lon = coord.getAircraftLocation().getLongitude();

        // Find the bearing to wp... return 0-360 deg.
        m_Destination_brng = getBearingBetweenWaypoints(m_Destination_Lat, m_Destination_Lon, local_lat, local_lon) - 180;
        if (m_Destination_brng < 0) m_Destination_brng = m_Destination_brng + 360;

        // The direct distance to the wp... return distance in meters...
        m_Destination_hypotenuse = getRangeBetweenWaypoints_m(m_Destination_Lat, m_Destination_Lon, 0, local_lat, local_lon, 0);
        Log.i(TAG, "m_Destination_hypotenuse: " + m_Destination_hypotenuse + " m_Destination_brng: " + m_Destination_brng);

        do_start_absolute_motion();
    }

    private void do_start_absolute_motion() {
        //    Log.i(TAG, "do_start_absolute_motion");

        // Start a task to do the job... if not already running...
        if (mMoveToDataTimer == null) {
            parent.logMessageDJI("Starting Absolution Mission!");

            if(!m_CruisingMode)
            {
                miniPIDFwd.reset();
                miniPIDSide.reset();
                miniPIDAlti.reset();
                miniPIDHeading.reset();
            }

            // This is a non standard trick, but we would like to know exactly when we have reached the position...
            // So we move to AUTO mode while flying to position, and then go back to GUIDED...
            // If there is another way let me know...
            mMoveToDataTask = new MoveTo();
            mMoveToDataTimer = new Timer();
            mMoveToDataTimer.schedule(mMoveToDataTask, 0, 25);
            mAutonomy = true;
        } else {
            mMoveToDataTask.detection = 0;
        }
    }

    class MoveTo extends TimerTask {
        public int detection = 0;  // Wi might fly past the point so we look for consecutive hits...
        public int detectionTriggerLimit = 5;
        public double detectionYawErrorLimit = 2.0;

        @Override
        public void run() {
            //          Log.i(TAG, "TimerTask");

            FlightControllerState coord = djiAircraft.getFlightController().getState();
            double local_lat = coord.getAircraftLocation().getLatitude();
            double local_lon = coord.getAircraftLocation().getLongitude();

            double dist = getRangeBetweenWaypoints_m(m_Destination_Lat, m_Destination_Lon, m_Destination_Alt,
                    local_lat, local_lon, coord.getAircraftLocation().getAltitude());

            //------------------------------------------------
            // Mavlink uses 0-360 while DJI uses +-180... We use 0-360 for math...
            double yaw = coord.getAttitude().yaw;
            if (yaw < 0) yaw = yaw + 360.0;

            double brngPoi = m_Destination_Yaw;
            if(m_POI_Lon != 0.0 && m_POI_Lat != 0.0)
            {
                brngPoi = getBearingBetweenWaypoints(m_POI_Lat, m_POI_Lon, local_lat, local_lon);
            }

            // If yaw is masked then do not change yaw...
            double yawerror;
            if ((m_Destination_Mask & 0b0000010000000000) > 0) yawerror = 0;
                // Make the error +-180 deg. error  + is we need to turn more right...mAutonomy
            else yawerror = rotation(brngPoi, yaw);
            
            //yawerror = 0;

            // Do we move og not...
            if ((m_Destination_Mask & 0b0000000000111111) == 0x3F) dist = 0;

            // If we are there...
            if ((m_Curvesize > 0.0 ? (dist < m_Curvesize) : (dist < 0.5)) && (m_CruisingMode ? (Math.abs(yawerror) < 5.0) : (Math.abs(yawerror) < detectionYawErrorLimit)) || mAutonomy == false) {
                // If we got x consecutive hits at the right spot....
                detection++;
                if((m_CruisingMode && detection > 1) || detection > detectionTriggerLimit || mAutonomy == false) {
                    mMoveToDataTimer.cancel();
                    mMoveToDataTimer.purge();
                    if(!m_CruisingMode || m_Stay)
                    {
                        do_set_motion_velocity(0, 0, 0, 0, 0b1111011111000111);
                        mAutonomy = false;
                    }
                    parent.logMessageDJI("At Position...");
//                    setGCSCommandedMode(ArduCopterFlightModes.GUIDED);
                    send_command_ack(m_lastCommand, MAV_RESULT.MAV_RESULT_ACCEPTED);
                    // Take Photo
                    // Hack
                    if(!gotoNoPhoto)
                    {
                        takePhoto();
                    } else {
                        gotoNoPhoto = false;
                        photoTaken = true;
                    }
                    mMoveToDataTimer = null;

                    return;
                }
            } else {
                detection = 0;
            }

            //------------------------------------------------
            // Find the bearing... return 0-360 deg.
            double brng = getBearingBetweenWaypoints(m_Destination_Lat, m_Destination_Lon, local_lat, local_lon);
            // The direct distance to the destination... return distance in meters...
            double hypotenuse = getRangeBetweenWaypoints_m(m_Destination_Lat, m_Destination_Lon, 0, local_lat, local_lon, 0);
            double idealPos[] = get_location_intermediet(m_Destination_Lat, m_Destination_Lon, hypotenuse, m_Destination_brng);
            // At these two positions is at the same distance from target, the error must be the sideways error....
            double offset = getRangeBetweenWaypoints_m(idealPos[0], idealPos[1], 0, local_lat, local_lon, 0);
            double pol = getBearingBetweenWaypoints(idealPos[0], idealPos[1], local_lat, local_lon);
            double diff = rotation(brng, pol);
            if (diff > 0) {
                offset *= -1;
            }

            Log.i(TAG, "diff: " + diff + "brng: " + brng + " hypotenuse: " + hypotenuse + " offset: " + offset + " pol: " + pol);

            // Drone heading - Waypoint bearing... to 0-360 deg...
            double direction = rotation(brng, yaw);

            // Find the X and Y distance from the hypotenuse and the direction...
            double right_dist = offset; //Math.max(offset,20); // bearing_error; //Math.sin(Math.toRadians(direction)) * hypotenuse + Math.cos(Math.toRadians(direction)) * bearing_error ;
            double fw_dist = hypotenuse; //Math.max(hypotenuse,20); // Math.cos(Math.toRadians(direction)) * hypotenuse + Math.sin(Math.toRadians(direction)) * bearing_error;
            Log.i(TAG, "direction: " + direction);

            if(m_CruisingMode)
            {
                miniPIDFwd.setP(1.0);
                miniPIDSide.setP(1.0);
                miniPIDAlti.setP(1.0);
                miniPIDHeading.setP(1.0);

                miniPIDFwd.setOutputLimits(-3.0f, 3.0f); // m/s
                miniPIDSide.setOutputLimits(-3.0f, 3.0f); // m/s
                miniPIDHeading.setOutputFilter(0.7);
            } else {
                miniPIDFwd.setP(0.35);
                miniPIDSide.setP(0.35);
                miniPIDAlti.setP(0.35);
                miniPIDHeading.setP(1.0);

                miniPIDFwd.setOutputLimits(-10.0f, 10.0f); // m/s
                miniPIDSide.setOutputLimits(-10.0f, 10.0f); // m/s
                miniPIDHeading.setOutputFilter(0.1);
            }
            
            miniPIDFwd.setOutputFilter(0.1);
            double fwmotion = miniPIDFwd.getOutput(-fw_dist, 0);

            miniPIDSide.setOutputFilter(0.1);
            double rightmotion = miniPIDSide.getOutput(-right_dist, 0);

            miniPIDAlti.setOutputLimits(-3.0f, 3.0f); // m/s
            miniPIDAlti.setOutputFilter(0.1);
            double upmotion = miniPIDAlti.getOutput(coord.getAircraftLocation().getAltitude(), m_Destination_Alt);

            miniPIDHeading.setOutputLimits(-75.0f, 75.0f); // °/s

            double clockmotion = 0.0;
            clockmotion = miniPIDHeading.getOutput(-yawerror, 0);

            //------------------------------------------------
            if ((m_Destination_Mask & 0b0000000000111111) == 0x3F) {
                //         parent.logMessageDJI("To Yaw...");
                do_set_motion_velocity((float) 0, (float) 0, (float) 0, (float) clockmotion, 0b1111011111111111);
            } else {

                Log.i(TAG, "fwmotion: " + fwmotion + " rightmotion: " + rightmotion);
                double fmove = Math.cos(Math.toRadians(direction)) * fwmotion - Math.sin(Math.toRadians(direction)) * rightmotion;
                double rmove = Math.sin(Math.toRadians(direction)) * fwmotion + Math.cos(Math.toRadians(direction)) * rightmotion;
                Log.i(TAG, "rmove: " + rmove + " fmove: " + fmove);

                do_set_motion_velocity((float) fmove, (float) rmove, (float) upmotion, (float) clockmotion, 0b1111011111000111);
                //do_set_motion_velocity((float) fwmotion, (float) rightmotion, (float) upmotion, (float) clockmotion, 0b1111011111000111);
            }
        }
    }

    // --------------------------------------------------------------------------------
    void do_set_motion_velocity_NED(float dNorth, float dEast, float D, float yaw, int mask) {
        // If we use yaw rate...

        //   parent.logMessageDJI(":Velocity NED" );

        FlightControllerState coord = djiAircraft.getFlightController().getState();
        double ro = Math.toRadians(coord.getAttitude().yaw);
        double dForward = dNorth * Math.cos(ro) + dEast * Math.sin(ro);
        double dRight = -dNorth * Math.sin(ro) + dEast * Math.cos(ro);
        do_set_motion_velocity((float) dForward, (float) dRight, D, yaw, mask);
    }

    // --------------------------------------------------------------------------------
    void do_set_motion_velocity(float x, float y, float z, float yaw, int mask) {
        //    Log.i(TAG, "do_set_motion_velocity");

        mFlightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
        mFlightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
        mFlightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
        
        // If we use yaw rate...
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

        // Only allow velocity movement in P mode...
        if (rcmode == avtivemode) {
            parent.logMessageDJI(":rcmode != avtivemode " + rcmode + "   " + avtivemode);
            Log.i(TAG, ":rcmode != avtivemode " + rcmode + "   " + avtivemode);
            return;
        }

        mFlightController.getVirtualStickModeEnabled(new CommonCallbacks.CompletionCallbackWith<Boolean>() {
            @Override
            public void onSuccess(Boolean aBoolean) {
                if (aBoolean == false) {
                    // After a manual mode change, we might loose the JOYSTICK mode...
                    if (lastMode != FlightMode.JOYSTICK) {
                        mFlightController.setVirtualStickModeEnabled(true, djiError -> {
                                    if (djiError != null) {
                                        Log.i(TAG, "Velocity Mode not enabled Error: " + djiError.toString());
                                        parent.logMessageDJI("Velocity Mode not enabled Error: " + djiError.toString());
                                    } else {
                                        mFlightController.setVirtualStickAdvancedModeEnabled(true);
                                    }
                                }
                        );
                    }
                }
            }

            @Override
            public void onFailure(DJIError error) {
                Log.e(TAG, "Can Not get VirtualStick mode...");
            }
        });

        // If first time...
        if (null == mSendVirtualStickDataTimer) {
            mSendVirtualStickDataTask = new SendVelocityDataTask();
            mSendVirtualStickDataTimer = new Timer();
            mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 50);
        } else {
            mSendVirtualStickDataTask.repeat = 2;
        }
    }

    // Run the velocity command for 2 seconds...
    class SendVelocityDataTask extends TimerTask {
        public int repeat = 2;
        private int report = 0;

        @Override
        public void run() {
            if (mFlightController != null) {
                if (--repeat <= 0) {
                    mSendVirtualStickDataTimer.cancel();
                    mSendVirtualStickDataTimer.purge();
                    mSendVirtualStickDataTimer = null;

                    if(!m_CruisingMode)
                    {
                        mFlightController.setVirtualStickModeEnabled(false, djiError -> {
                                    if (djiError != null)
                                        parent.logMessageDJI("Velocity Mode not disabled Error: " + djiError.toString());
                                }
                        );

                        lastMode = FlightMode.GPS_ATTI;
                    }
                    
                    parent.logMessageDJI("Motion done!\n");
                    return;
                }
                if (++report > 1) {
                    report = 0;
                    Log.e(TAG, ":" + mPitch + " " + mRoll + " " + mYaw + " " + mThrottle);
//                parent.logMessageDJI(":"+mPitch+" "+ mRoll+" "+ mYaw+" "+ mThrottle);
                }

                mFlightController.getVirtualStickModeEnabled(new CommonCallbacks.CompletionCallbackWith<Boolean>() {
                    @Override
                    public void onSuccess(Boolean aBoolean) {
                        if (aBoolean == false) {
                            // After a manual mode change, we might loose the JOYSTICK mode...
                            if (lastMode != FlightMode.JOYSTICK) {
                                mFlightController.setVirtualStickModeEnabled(true, djiError -> {
                                            if (djiError != null) {
                                                Log.i(TAG, "Velocity Mode not enabled Error: " + djiError.toString());
                                                parent.logMessageDJI("Velocity Mode not enabled Error: " + djiError.toString());
                                            } else {
                                                mFlightController.setVirtualStickAdvancedModeEnabled(true);
                                            }
                                        }
                                );
                            }
                        }
                    }
        
                    @Override
                    public void onFailure(DJIError error) {
                        Log.e(TAG, "Can Not get VirtualStick mode...");
                    }
                });
        
                mFlightController.sendVirtualStickFlightControlData(
                        new FlightControlData(mPitch, mRoll, mYaw, mThrottle)
                        , djiError -> {
                            if (djiError != null)
                                parent.logMessageDJI("Motion Error: " + djiError.toString());
                        }
                );
            }
        }
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

    void takePhoto() {
        // Wait till Gimbal is in Place
        while(!gimbalReady.get())
            ; // Block Thread

        djiAircraft.getCamera().setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, djiError -> {
            if(djiError != null) {
                parent.logMessageDJI("Error Setting PhotoMode: " + djiError.toString());
            }
        });

        SettingsDefinitions.ShootPhotoMode photoMode = SettingsDefinitions.ShootPhotoMode.SINGLE;
        photoTaken = false;

        if (djiAircraft.getCamera() != null) {
            djiAircraft.getCamera().startShootPhoto(djiError -> {
                if (djiError == null) {
                    parent.logMessageDJI("Took photo");
                    photoTaken = true;
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
                    //send_command_ack(MAV_CMD_DO_SET_PARAMETER, MAV_RESULT.MAV_RESULT_ACCEPTED);
                    send_command_ack(MAV_CMD_DO_DIGICAM_CONTROL, MAV_RESULT.MAV_RESULT_ACCEPTED);
                } else {
                    parent.logMessageDJI("Error taking photo: " + djiError.toString());
                    // resume
                    takePhoto();
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

    void startRecordingVideo() {
        djiAircraft.getCamera().setMode(SettingsDefinitions.CameraMode.RECORD_VIDEO, djiError -> {
            if (djiError != null) {
                parent.logMessageDJI("Error Setting RecordVideo Mode: " + djiError.toString());
                send_command_ack(MAV_CMD_VIDEO_START_CAPTURE, MAV_RESULT.MAV_RESULT_FAILED);
            } else {
                djiAircraft.getCamera().startRecordVideo(djiError2 -> {
                    if (djiError2 == null) {
                        parent.logMessageDJI("Started recording video");
                        send_command_ack(MAV_CMD_VIDEO_START_CAPTURE, MAV_RESULT.MAV_RESULT_ACCEPTED);
                    } else {
                        parent.logMessageDJI("Error starting video recording: " + djiError2.toString());
                        send_command_ack(MAV_CMD_VIDEO_START_CAPTURE, MAV_RESULT.MAV_RESULT_FAILED);
                    }
                });
            }
        });
    }

    void stopRecordingVideo() {
        // If we dont sync it will cause a flightcontroller crash on Mini 2 if we startRecordingVideo instantly after
        // Happens only after a few minutes of recording footage
        AtomicBoolean waitStopRecording = new AtomicBoolean(true);

        djiAircraft.getCamera().stopRecordVideo(djiError -> {
            if (djiError == null) {
                parent.logMessageDJI("Stopped recording video");
                send_command_ack(MAV_CMD_VIDEO_STOP_CAPTURE, MAV_RESULT.MAV_RESULT_ACCEPTED);
            } else {
                parent.logMessageDJI("Error stopping video recording: " + djiError.toString());
                send_command_ack(MAV_CMD_VIDEO_STOP_CAPTURE, MAV_RESULT.MAV_RESULT_FAILED);
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

        // Stupid FC Crash... additional wait since a sync to the record isn't enough apparently
        // Min 1 second, 1.5 just to be safe..
        try
        {
            Thread.sleep(1500);
        }
        catch(InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }

    double getBearingBetweenWaypoints(double lat2, double lon2, double lat1, double lon1) {
        // (all angles in degrees 0-360)
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);
        lon1 = Math.toRadians(lon1);
        lon2 = Math.toRadians(lon2);

        double y = Math.sin(lon2 - lon1) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(lon2 - lon1);
        double z = Math.toDegrees(Math.atan2(y, x));
        if (z < 0.0) z += 360.0;
        return z;
    }

    // --------------------------------------------------------------------------------
    double getRangeBetweenWaypoints_m(double lat1, double lon1, float el1, double lat2, double lon2, float el2) {
        final int R = 6371; // Radius of the earth
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters
        double height = el1 - el2;
        distance = Math.pow(distance, 2) + Math.pow(height, 2);
        return Math.sqrt(distance);
    }

    /**
     * Shortest distance (angular) between two angles.
     * It will be in range [0, +-180].
     */
    private double rotation(double alpha, double beta) {
        double phi = Math.abs(beta - alpha) % 360;       // This is either the distance or 360 - distance
        double distance = phi > 180 ? 360 - phi : phi;
        double sign = (alpha - beta >= 0 && alpha - beta <= 180) || (alpha - beta <= -180 && alpha - beta >= -360) ? distance : distance * -1;
        return sign;
    }


    private double[] get_location_metres(double dForward, double dRight, double alt) {
        FlightControllerState coord = djiAircraft.getFlightController().getState();
        double ro = Math.toRadians(coord.getAttitude().yaw);
        double lat = coord.getAircraftLocation().getLatitude();
        double lon = coord.getAircraftLocation().getLongitude();
        double newalt = coord.getAircraftLocation().getAltitude() + alt;

        double dNorth = dForward * Math.cos(ro) - dRight * Math.sin(ro);
        double dEast = dForward * Math.sin(ro) + dRight * Math.cos(ro);
        double earth_radius = 6371e3;  // Radius of "spherical" earth

        //Coordinate offsets in radians
        double dLat = dNorth / earth_radius;
        double dLon = dEast / (earth_radius * Math.cos(Math.toRadians(lat)));

        // New position in decimal degrees
        double newlat = lat + Math.toDegrees(dLat);
        double newlon = lon + Math.toDegrees(dLon);

        //parent.logMessageDJI("Wants: lat " + lat + " lon " + lon + " newlat " + newlat + " newlon " + newlon);
        return new double[]{newlat, newlon, newalt};
    }

    private double[] get_location_intermediet(double lat, double lon, double dist, double yaw) {

        double ro = Math.toRadians(yaw);
        double dNorth = dist * Math.cos(ro);
        double dEast = dist * Math.sin(ro);
        double earth_radius = 6371e3;  // Radius of "spherical" earth

        //Coordinate offsets in radians
        double dLat = dNorth / earth_radius;
        double dLon = dEast / (earth_radius * Math.cos(Math.toRadians(lat)));

        // New position in decimal degrees
        double newlat = lat + Math.toDegrees(dLat);
        double newlon = lon + Math.toDegrees(dLon);

        //parent.logMessageDJI("Was: lat " + lat + " lon " + lon + " newlat " + newlat + " newlon " + newlon);
        return new double[]{newlat, newlon};
    }
}