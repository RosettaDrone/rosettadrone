package sq.rogue.rosettadrone;

import android.support.annotation.NonNull;
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
import com.MAVLink.common.msg_mission_item;
import com.MAVLink.common.msg_mission_item_reached;
import com.MAVLink.common.msg_mission_request;
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

import dji.common.airlink.SignalQualityCallback;
import dji.common.battery.AggregationState;
import dji.common.battery.BatteryState;
import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.flightcontroller.Attitude;
import dji.common.flightcontroller.ConnectionFailSafeBehavior;
import dji.common.flightcontroller.ControlMode;
import dji.common.flightcontroller.FlightControlState;
import dji.common.flightcontroller.GPSSignalLevel;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionState;
import dji.common.remotecontroller.HardwareState;
import dji.common.util.CommonCallbacks;
import dji.sdk.battery.Battery;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.products.Aircraft;

import static com.MAVLink.enums.MAV_CMD.MAV_CMD_COMPONENT_ARM_DISARM;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_DIGICAM_CONTROL;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_TAKEOFF;
import static com.MAVLink.enums.MAV_COMPONENT.MAV_COMP_ID_AUTOPILOT1;
import static sq.rogue.rosettadrone.util.getTimestampMicroseconds;


public class DroneModel implements CommonCallbacks.CompletionCallback {
    private static final int NOT_USING_GCS_COMMANDED_MODE = -1;
    private final String TAG = "RosettaDrone";
    private Aircraft djiAircraft;
    private ArrayList<MAVParam> params = new ArrayList<MAVParam>();
    private DatagramSocket socket;
    private long ticks = 0;
    private MainActivity parent;
    private int mSystemId = 1;
    private int mComponentId = MAV_COMP_ID_AUTOPILOT1;
    private int mGCSCommandedMode;
    private int mThrottleSetting;
    private int mFullChargeCapacity_mAh;
    private int mChargeRemaining_mAh;
    private int mVoltage_mV;
    private int mCurrent_mA;
    private float mBatteryTemp_C;
    private int[] mCellVoltages = new int[10];
    private int mDownlinkQuality = 0;
    private int mUplinkQuality = 0;

    private boolean mSafetyEnabled = true;
    private boolean mMotorsArmed = false;
    private RosettaMissionOperatorListener mMissionOperatorListener;

    public DroneModel(MainActivity parent, DatagramSocket socket) {
        this.parent = parent;
        this.socket = socket;
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

    public void setGCSCommandedMode(int GCSCommandedMode) {
        mGCSCommandedMode = GCSCommandedMode;
    }

    public void setWaypointMission(WaypointMission wpMission) {
        DJIError load_error = getWaypointMissionOperator().loadMission(wpMission);
        if (load_error != null)
            parent.logMessageDJI("loadMission() returned error: " + load_error.toString());
        else {
            parent.logMessageDJI("Uploading mission");
            getWaypointMissionOperator().uploadMission(
                    new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError == null) {
                                while (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.UPLOADING) {
                                    // Do nothing
                                }
                                if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.READY_TO_EXECUTE)
                                    parent.logMessageDJI("Mission uploaded and ready to execute!");
                                else
                                    parent.logMessageDJI("Error uploading waypoint mission to drone");

                            } else {
                                parent.logMessageDJI("Error uploading: " + djiError.getDescription());
                                parent.logMessageDJI(("Please try re-uploading"));
                            }
                            //parent.logMessageDJI("New state: " + getWaypointMissionOperator().getCurrentState().getName());
                        }
                    });
        }
    }

    public Aircraft getDjiAircraft() {
        return djiAircraft;
    }

    public boolean isSafetyEnabled() {
        return mSafetyEnabled;
    }

    public void setSafetyEnabled(boolean SafetyEnabled) {
        mSafetyEnabled = SafetyEnabled;
    }

    public boolean setDjiAircraft(Aircraft djiAircraft) {

        if (djiAircraft == null || djiAircraft.getRemoteController() == null)
            return false;
        this.djiAircraft = djiAircraft;

        Arrays.fill(mCellVoltages, 0xffff); // indicates no cell per mavlink definition

        /**************************************************
         * Called whenever RC state changes               *
         **************************************************/

        this.djiAircraft.getRemoteController().setHardwareStateCallback(new HardwareState.HardwareStateCallback() {
            @Override
            public void onUpdate(@NonNull HardwareState rcHardwareState) {
                // DJI: range [-660,660]
                mThrottleSetting = (rcHardwareState.getLeftStick().getVerticalPosition() + 660) / 1320;
            }
        });

        /**************************************************
         * Called whenever battery state changes          *
         **************************************************/


        if (this.djiAircraft != null) {
            this.djiAircraft.getBattery().setStateCallback(new BatteryState.Callback() {

                @Override
                public void onUpdate(BatteryState batteryState) {
                    Log.d(TAG, "Battery State callback");
                    mFullChargeCapacity_mAh = batteryState.getFullChargeCapacity();
                    mChargeRemaining_mAh = batteryState.getChargeRemaining();
                    mVoltage_mV = batteryState.getVoltage();
                    mCurrent_mA = Math.abs(batteryState.getCurrent());
                    mBatteryTemp_C = batteryState.getTemperature();
                    Log.d(TAG, "Current: " + String.valueOf(batteryState.getCurrent()));
                }
            });

            this.djiAircraft.getBattery().getCellVoltages(new CellVoltageCompletionCallback());
        } else {
            Log.e(TAG, "djiAircraft.getBattery() IS NULL");
            return false;
        }

        Battery.setAggregationStateCallback(new AggregationState.Callback() {
            @Override
            public void onUpdate(AggregationState aggregationState) {
                Log.d(TAG, "Aggregation State callback");
                mFullChargeCapacity_mAh = aggregationState.getFullChargeCapacity();
                mChargeRemaining_mAh = aggregationState.getChargeRemaining();
                mVoltage_mV = aggregationState.getVoltage();
                mCurrent_mA = aggregationState.getCurrent();
                Log.d(TAG, "Aggregated voltage: " + String.valueOf(aggregationState.getVoltage()));
            }
        });

        /**************************************************
         * Called whenever airlink quality changes        *
         **************************************************/

        djiAircraft.getAirLink().setDownlinkSignalQualityCallback(new SignalQualityCallback() {
            @Override
            public void onUpdate(int i) {
                mDownlinkQuality = i;
            }
        });

        djiAircraft.getAirLink().setUplinkSignalQualityCallback(new SignalQualityCallback() {
            @Override
            public void onUpdate(int i) {
                mUplinkQuality = i;
            }
        });

        initMissionOperator();

        return true;
    }

    public WaypointMissionOperator getWaypointMissionOperator() {
        return MissionControl.getInstance().getWaypointMissionOperator();
    }

    public void initMissionOperator() {
        getWaypointMissionOperator().removeListener(null);
        mMissionOperatorListener = new RosettaMissionOperatorListener();
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

    public void tick() {
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
            if (ticks % 300 == 0) {
                send_global_position_int();
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

    public void armMotors() {
        if (mSafetyEnabled) {
            parent.logMessageDJI("You must turn off safety_layout to arm motors");
            send_command_ack(MAV_CMD_COMPONENT_ARM_DISARM, MAV_RESULT.MAV_RESULT_DENIED);
            return;
        } else {
            send_command_ack(MAV_CMD_COMPONENT_ARM_DISARM, MAV_RESULT.MAV_RESULT_ACCEPTED);
            mMotorsArmed = true;
        }
        return;

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

    public void disarmMotors() {
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

    private void sendMessage(MAVLinkMessage msg) {
        if (socket == null)
            return;

        MAVLinkPacket packet = msg.pack();

        packet.sysid = mSystemId;
        packet.compid = mComponentId;

        byte[] bytes = packet.encodePacket();

        try {
            DatagramPacket p = new DatagramPacket(bytes, bytes.length, socket.getInetAddress(), socket.getPort());
            socket.send(p);
            parent.logMessageToGCS(msg.toString());
//            if(msg.msgid != MAVLINK_MSG_ID_POWER_STATUS &&
//                    msg.msgid != MAVLINK_MSG_ID_SYS_STATUS &&
//                    msg.msgid != MAVLINK_MSG_ID_VIBRATION &&
//                    msg.msgid != MAVLINK_MSG_ID_ATTITUDE &&
//                    msg.msgid != MAVLINK_MSG_ID_VFR_HUD &&
//                    msg.msgid != MAVLINK_MSG_ID_GLOBAL_POSITION_INT &&
//                    msg.msgid != MAVLINK_MSG_ID_GPS_RAW_INT &&
//                    msg.msgid != MAVLINK_MSG_ID_RADIO_STATUS)
//                parent.logMessageToGCS(msg.toString());

        } catch (PortUnreachableException e) {
        } catch (IOException e) {
        }
    }

    public void send_autopilot_version() {
        msg_autopilot_version msg = new msg_autopilot_version();
        msg.capabilities = MAV_PROTOCOL_CAPABILITY.MAV_PROTOCOL_CAPABILITY_COMMAND_INT;
        msg.capabilities |= MAV_PROTOCOL_CAPABILITY.MAV_PROTOCOL_CAPABILITY_MISSION_INT;
        sendMessage(msg);
    }

    public void send_heartbeat() {
        msg_heartbeat msg = new msg_heartbeat();
        msg.type = MAV_TYPE.MAV_TYPE_QUADROTOR;
        msg.autopilot = MAV_AUTOPILOT.MAV_AUTOPILOT_ARDUPILOTMEGA;

        // For base mode logic, see Copter::sendHeartBeat() in ArduCopter/GCS_Mavlink.cpp
        msg.base_mode = MAV_MODE_FLAG.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED;
        msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_MANUAL_INPUT_ENABLED;

        switch (djiAircraft.getFlightController().getState().getFlightMode()) {
            case MANUAL:
                msg.custom_mode = ArduCopterFlightModes.STABILIZE;
                break;
            case ATTI:
                msg.custom_mode = ArduCopterFlightModes.LOITER;
                break;
            case ATTI_COURSE_LOCK:
                break;
            case GPS_ATTI:
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
        if (mGCSCommandedMode == ArduCopterFlightModes.GUIDED)
            msg.custom_mode = ArduCopterFlightModes.GUIDED;
        if (mGCSCommandedMode == ArduCopterFlightModes.BRAKE)
            msg.custom_mode = ArduCopterFlightModes.BRAKE;

        if (mMotorsArmed)
            msg.base_mode |= MAV_MODE_FLAG.MAV_MODE_FLAG_SAFETY_ARMED;

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

    public void send_attitude() {
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

    public void send_altitude() {
        msg_altitude msg = new msg_altitude();
        LocationCoordinate3D coord = djiAircraft.getFlightController().getState().getAircraftLocation();
        msg.altitude_relative = (int) (coord.getAltitude() * 1000);
        sendMessage(msg);
    }

    public void send_command_ack(int message_id, int result) {
        msg_command_ack msg = new msg_command_ack();
        msg.command = message_id;
        msg.result = (short) result;
        sendMessage(msg);
    }

    public void send_global_position_int() {
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

    public void send_global_position_int_cov() {
        // not implemented
        return;
    }

    public void send_gps_raw_int() {
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

    public void send_sys_status() {
        msg_sys_status msg = new msg_sys_status();

        Log.d(TAG, "Full charge capacity: " + String.valueOf(mFullChargeCapacity_mAh));
        Log.d(TAG, "Charge remaining: " + String.valueOf(mChargeRemaining_mAh));
        Log.d(TAG, "Full charge capacity: " + String.valueOf(mFullChargeCapacity_mAh));

        if (mFullChargeCapacity_mAh > 0) {
            msg.battery_remaining = (byte) ((float) mChargeRemaining_mAh / (float) mFullChargeCapacity_mAh * 100.0);
            Log.d(TAG, "calc'ed bat remain: " + String.valueOf(msg.battery_remaining));
        } else {
            Log.d(TAG, "divide by zero");
            msg.battery_remaining = 100; // Prevent divide by zero
        }
        msg.voltage_battery = mVoltage_mV;
        msg.current_battery = (short) mCurrent_mA;
        sendMessage(msg);
    }

    public void send_power_status() {
        msg_power_status msg = new msg_power_status();
        sendMessage(msg);
    }

    public void send_radio_status() {
        msg_radio_status msg = new msg_radio_status();
        msg.rssi = 0; // TODO: work out units conversion (see issue #1)
        msg.remrssi = 0; // TODO: work out units conversion (see issue #1)
        sendMessage(msg);
    }

    public void send_rc_channels() {
        msg_rc_channels msg = new msg_rc_channels();
        msg.rssi = (short) mUplinkQuality;
        sendMessage(msg);
    }

    public void send_vibration() {
        msg_vibration msg = new msg_vibration();
        sendMessage(msg);
    }

    public void send_battery_status() {
        msg_battery_status msg = new msg_battery_status();
        msg.current_consumed = mFullChargeCapacity_mAh - mChargeRemaining_mAh;
        msg.voltages = mCellVoltages;
        msg.temperature = (short) (mBatteryTemp_C * 100);
        msg.current_battery = (short) (mCurrent_mA * 10);
        Log.d(TAG, "temp: " + String.valueOf(mBatteryTemp_C));
        Log.d(TAG, "send_battery_status() complete");
        // TODO cell voltages
        sendMessage(msg);
    }

    public void send_vfr_hud() {
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

    public void send_home_position() {
        msg_home_position msg = new msg_home_position();

        msg.latitude = (int) (djiAircraft.getFlightController().getState().getHomeLocation().getLatitude() * Math.pow(10, 7));
        msg.longitude = (int) (djiAircraft.getFlightController().getState().getHomeLocation().getLongitude() * Math.pow(10, 7));
        msg.altitude = (int) (djiAircraft.getFlightController().getState().getHomePointAltitude());

        // msg.x = 0;
        // msg.y = 0;
        // msg.z = 0;
        // msg.approach_x = 0;
        // msg.approach_y = 0;
        // msg.approach_z = 0;
        sendMessage(msg);
    }

    public void send_statustext(String text, int severity) {
        msg_statustext msg = new msg_statustext();
        msg.text = text.getBytes();
        msg.severity = (short) severity;
        sendMessage(msg);
    }

    public void send_param(int index) {
        MAVParam param = params.get(index);
        send_param(param.getParamName(),
                param.getParamValue(),
                param.getParamType(),
                params.size(),
                index);
    }

    public void send_param(String key, float value, short type, int count, int index) {

        msg_param_value msg = new msg_param_value();
        msg.setParam_Id(key);
        msg.param_value = value;
        msg.param_type = type;
        msg.param_count = count;
        msg.param_index = index;

        Log.d("Rosetta", "Sending param: " + msg.toString());

        sendMessage(msg);
    }

    public void send_all_params() {
        for (int i = 0; i < params.size(); i++)
            send_param(i);
    }

    public boolean loadParamsFromDJI() {
        if (getDjiAircraft() == null)
            return false;
        for (int i = 0; i < getParams().size(); i++) {
            switch (getParams().get(i).getParamName()) {
                case "DJI_CTRL_MODE":
                    getDjiAircraft().getFlightController().getControlMode(new ParamControlModeCallback(i));
                    break;
                case "DJI_ENBL_LEDS":
                    getDjiAircraft().getFlightController().getLEDsEnabled(new ParamBooleanCallback(i));
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

    public void changeParam(MAVParam param) {
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
                        getDjiAircraft().getFlightController().setLEDsEnabled(param.getParamValue() > 0, new ParamWriteCompletionCallback(i));
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
                        else if (param.getParamValue() == 0)
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


    public void send_mission_count() {
        msg_mission_count msg = new msg_mission_count();
        msg.mission_type = MAV_MISSION_TYPE.MAV_MISSION_TYPE_MISSION;
        sendMessage(msg);
        return;
    }

    public void send_mission_item(int i) {
        msg_mission_item msg = new msg_mission_item();

        if (i == 0) {
            msg.x = (float) (djiAircraft.getFlightController().getState().getHomeLocation().getLatitude());
            msg.y = (float) (djiAircraft.getFlightController().getState().getHomeLocation().getLongitude());
            msg.z = 0;
        } else {
            Waypoint wp = getWaypointMissionOperator().getLoadedMission().getWaypointList().get(i - 1);
            msg.x = (float) (wp.coordinate.getLatitude());
            msg.y = (float) (wp.coordinate.getLongitude());
            msg.z = wp.altitude;
        }

        msg.seq = i;
        msg.frame = MAV_FRAME.MAV_FRAME_GLOBAL_RELATIVE_ALT;
        sendMessage(msg);
    }

    public void send_mission_item_reached(int seq) {
        msg_mission_item_reached msg = new msg_mission_item_reached();
        msg.seq = seq;
        sendMessage(msg);
    }

    public void send_mission_ack() {
        msg_mission_ack msg = new msg_mission_ack();
        msg.type = MAV_MISSION_RESULT.MAV_MISSION_ACCEPTED;
        msg.mission_type = MAV_MISSION_TYPE.MAV_MISSION_TYPE_MISSION;
        sendMessage(msg);
    }

    public void fetch_gcs_mission() {
        request_mission_list();
    }

    public void request_mission_list() {
        msg_mission_request_list msg = new msg_mission_request_list();
        sendMessage(msg);
    }

    public void request_mission_item(int seq) {
        msg_mission_request msg = new msg_mission_request();
        msg.seq = seq;
        msg.mission_type = MAV_MISSION_TYPE.MAV_MISSION_TYPE_MISSION;
        sendMessage(msg);
    }

    public void startWaypointMission() {
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
            parent.logMessageDJI("You must turn off safety_layout to start mission");
            return;
        }

        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError != null)
                    parent.logMessageDJI("Error: " + djiError.toString());
                else
                    parent.logMessageDJI("Mission started!");
            }
        });
    }


    public void stopWaypointMission() {
        if (getWaypointMissionOperator() == null) {
            parent.logMessageDJI("stopWaypointMission() - mWaypointMissionOperator null");
            return;
        }

        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTING) {
            parent.logMessageDJI("Stopping mission...\n");
            getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null)
                        parent.logMessageDJI("Error: " + djiError.toString());
                    else
                        parent.logMessageDJI("Mission stopped!\n");
                }
            });
        }
    }

    public void pauseWaypointMission() {
        if (getWaypointMissionOperator() == null) {
            parent.logMessageDJI("pauseWaypointMission() - mWaypointMissionOperator null");
            return;
        }

        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTING) {
            parent.logMessageDJI("Pausing mission...\n");
            getWaypointMissionOperator().pauseMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null)
                        parent.logMessageDJI("Error: " + djiError.toString());
                    else
                        parent.logMessageDJI("Mission paused!\n");
                }
            });
        }
    }

    public void resumeWaypointMission() {
        if (getWaypointMissionOperator() == null) {
            parent.logMessageDJI("resumeWaypointMission() - mWaypointMissionOperator null");
            return;
        }

        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTION_PAUSED) {
            parent.logMessageDJI("Resuming mission...\n");
            getWaypointMissionOperator().resumeMission(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null)
                        parent.logMessageDJI("Error: " + djiError.toString());
                    else {
                        parent.logMessageDJI("Mission resumed!\n");
                        mGCSCommandedMode = NOT_USING_GCS_COMMANDED_MODE;
                    }
                }
            });
        }
    }

    public void do_takeoff() {
        if (mSafetyEnabled) {
            parent.logMessageDJI("You must turn off safety_layout to takeoff");
            send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_DENIED);
            return;
        }

        if (getWaypointMissionOperator().getCurrentState() == WaypointMissionState.READY_TO_EXECUTE) {
            startWaypointMission();
            send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_ACCEPTED);
        } else {
            parent.logMessageDJI("Initiating takeoff");
            djiAircraft.getFlightController().startTakeoff(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        parent.logMessageDJI("Error: " + djiError.toString());
                        send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_FAILED);
                    } else {
                        parent.logMessageDJI("Takeoff successful!\n");
                        send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_ACCEPTED);
                    }
                    mGCSCommandedMode = NOT_USING_GCS_COMMANDED_MODE;
                }
            });
        }
    }

    public void do_land() {
        parent.logMessageDJI("Initiating landing");
        djiAircraft.getFlightController().startLanding(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError != null)
                    parent.logMessageDJI("Error: " + djiError.toString());
                else {
                    parent.logMessageDJI("Landing successful!\n");
                    mMotorsArmed = false;
                }
            }
        });
    }

    public void do_go_home() {
        parent.logMessageDJI("Initiating Go Home");
        djiAircraft.getFlightController().startGoHome(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError != null)
                    parent.logMessageDJI("Error: " + djiError.toString());
                else
                    parent.logMessageDJI("Go home successful!\n");
            }
        });
    }

    public void set_flight_mode(FlightControlState djiMode) {
        // TODO
        return;
    }

    public void takePhoto() {
        SettingsDefinitions.ShootPhotoMode photoMode = SettingsDefinitions.ShootPhotoMode.SINGLE;
        djiAircraft.getCamera().startShootPhoto(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    parent.logMessageDJI("Took photo");
                    send_command_ack(MAV_CMD_DO_DIGICAM_CONTROL, MAV_RESULT.MAV_RESULT_ACCEPTED);
                } else {
                    parent.logMessageDJI("Error taking photo: " + djiError.toString());
                    send_command_ack(MAV_CMD_DO_DIGICAM_CONTROL, MAV_RESULT.MAV_RESULT_FAILED);
                }
            }
        });
    }


    /********************************************
     * CompletionCallback implementation        *
     ********************************************/

    @Override
    public void onResult(DJIError djiError) {

    }

    public void echoLoadedMission() {
        getWaypointMissionOperator().downloadMission(
                new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError == null) {
                            parent.logMessageDJI("Waypoint mission successfully downloaded");
                        } else {
                            parent.logMessageDJI("Error downloading: " + djiError.getDescription());
                        }
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
    }

    /********************************************
     * Parameter callbacks                      *
     ********************************************/

    public class ParamIntegerCallback implements CommonCallbacks.CompletionCallbackWith<Integer> {
        private int paramIndex;

        public ParamIntegerCallback(int paramIndex) {
            this.paramIndex = paramIndex;
        }

        @Override
        public void onSuccess(Integer integer) {
            getParams().get(paramIndex).setParamValue((float) integer);
            parent.logMessageDJI("Fetched param from DJI: " + getParams().get(paramIndex).getParamName() + "=" + String.valueOf(integer));
        }

        @Override
        public void onFailure(DJIError djiError) {
            getParams().get(paramIndex).setParamValue(-99.0f);
            parent.logMessageDJI("Param fetch fail: " + getParams().get(paramIndex).getParamName());
        }
    }

    public class ParamBooleanCallback implements CommonCallbacks.CompletionCallbackWith<Boolean> {
        private int paramIndex;

        public ParamBooleanCallback(int paramIndex) {
            this.paramIndex = paramIndex;
        }

        @Override
        public void onSuccess(Boolean aBoolean) {
            getParams().get(paramIndex).setParamValue(aBoolean ? 1.0f : 0.0f);
            parent.logMessageDJI("Fetched param from DJI: " + getParams().get(paramIndex).getParamName() + "=" + String.valueOf(aBoolean));
        }

        @Override
        public void onFailure(DJIError djiError) {
            getParams().get(paramIndex).setParamValue(-99.0f);
            parent.logMessageDJI("Param fetch fail: " + getParams().get(paramIndex).getParamName());
        }
    }

    public class ParamControlModeCallback implements CommonCallbacks.CompletionCallbackWith<ControlMode> {
        private int paramIndex;

        public ParamControlModeCallback(int paramIndex) {
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

            parent.logMessageDJI("Fetched param from DJI: " + getParams().get(paramIndex).getParamName() + "=" + String.valueOf(cMode));
        }

        @Override
        public void onFailure(DJIError djiError) {
            getParams().get(paramIndex).setParamValue(-99.0f);
            parent.logMessageDJI("Param fetch fail: " + getParams().get(paramIndex).getParamName());
        }
    }

    public class ParamConnectionFailSafeBehaviorCallback implements CommonCallbacks.CompletionCallbackWith<ConnectionFailSafeBehavior> {
        private int paramIndex;

        public ParamConnectionFailSafeBehaviorCallback(int paramIndex) {
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

            parent.logMessageDJI("Fetched param from DJI: " + getParams().get(paramIndex).getParamName() + "=" + String.valueOf(behavior));
        }

        @Override
        public void onFailure(DJIError djiError) {
            getParams().get(paramIndex).setParamValue(-99.0f);
            parent.logMessageDJI("Param fetch fail: " + getParams().get(paramIndex).getParamName());
        }
    }

    public class ParamWriteCompletionCallback implements CommonCallbacks.CompletionCallback {

        private int paramIndex;

        public ParamWriteCompletionCallback(int paramIndex) {
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
        public void onSuccess(Integer integer[]) {
            for (int i = 0; i < integer.length; i++)
                mCellVoltages[i] = integer[i];
            Log.d(TAG, "got cell voltages, v[0] =" + String.valueOf(mCellVoltages[0]));
        }

        @Override
        public void onFailure(DJIError djiError) {

        }

    }
}
