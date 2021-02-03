package sq.rogue.rosettadrone;

import android.util.Log;

import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.common.msg_command_long;
import com.MAVLink.common.msg_manual_control;
import com.MAVLink.common.msg_mission_ack;
import com.MAVLink.common.msg_mission_count;
import com.MAVLink.common.msg_mission_item;
import com.MAVLink.common.msg_mission_item_int;
import com.MAVLink.common.msg_mission_request;
import com.MAVLink.common.msg_mission_request_int;
import com.MAVLink.common.msg_param_request_read;
import com.MAVLink.common.msg_param_set;
import com.MAVLink.common.msg_set_mode;
import com.MAVLink.common.msg_set_position_target_global_int;
import com.MAVLink.common.msg_set_position_target_local_ned;
import com.MAVLink.enums.MAV_CMD;
import com.MAVLink.enums.MAV_MISSION_TYPE;
import com.MAVLink.enums.MAV_RESULT;

import java.util.ArrayList;

import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionGotoWaypointMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionState;

import static com.MAVLink.common.msg_command_int.MAVLINK_MSG_ID_COMMAND_INT;
import static com.MAVLink.common.msg_command_long.MAVLINK_MSG_ID_COMMAND_LONG;
import static com.MAVLink.common.msg_heartbeat.MAVLINK_MSG_ID_HEARTBEAT;
import static com.MAVLink.common.msg_manual_control.MAVLINK_MSG_ID_MANUAL_CONTROL;
import static com.MAVLink.common.msg_mission_ack.MAVLINK_MSG_ID_MISSION_ACK;
import static com.MAVLink.common.msg_mission_clear_all.MAVLINK_MSG_ID_MISSION_CLEAR_ALL;
import static com.MAVLink.common.msg_mission_count.MAVLINK_MSG_ID_MISSION_COUNT;
import static com.MAVLink.common.msg_mission_item.MAVLINK_MSG_ID_MISSION_ITEM;
import static com.MAVLink.common.msg_mission_item_int.MAVLINK_MSG_ID_MISSION_ITEM_INT;
import static com.MAVLink.common.msg_mission_request.MAVLINK_MSG_ID_MISSION_REQUEST;
import static com.MAVLink.common.msg_mission_request_list.MAVLINK_MSG_ID_MISSION_REQUEST_LIST;
import static com.MAVLink.common.msg_mission_request_partial_list.MAVLINK_MSG_ID_MISSION_REQUEST_PARTIAL_LIST;
import static com.MAVLink.common.msg_mission_set_current.MAVLINK_MSG_ID_MISSION_SET_CURRENT;
import static com.MAVLink.common.msg_param_request_list.MAVLINK_MSG_ID_PARAM_REQUEST_LIST;
import static com.MAVLink.common.msg_param_request_read.MAVLINK_MSG_ID_PARAM_REQUEST_READ;
import static com.MAVLink.common.msg_param_set.MAVLINK_MSG_ID_PARAM_SET;
import static com.MAVLink.common.msg_set_mode.MAVLINK_MSG_ID_SET_MODE;
import static com.MAVLink.common.msg_set_position_target_global_int.MAVLINK_MSG_ID_SET_POSITION_TARGET_GLOBAL_INT;
import static com.MAVLink.common.msg_set_position_target_local_ned.MAVLINK_MSG_ID_SET_POSITION_TARGET_LOCAL_NED;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_COMPONENT_ARM_DISARM;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_CONDITION_YAW;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_DIGICAM_CONTROL;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_JUMP;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_SET_HOME;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_SET_MODE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_SET_SERVO;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_GET_HOME_POSITION;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_MISSION_START;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_LAND;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_LOITER_UNLIM;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_RETURN_TO_LAUNCH;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_TAKEOFF;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_WAYPOINT;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_REQUEST_AUTOPILOT_CAPABILITIES;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_START_CAPTURE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_STOP_CAPTURE;
import static com.MAVLink.enums.MAV_MISSION_TYPE.MAV_MISSION_TYPE_MISSION;
import static com.MAVLink.enums.MAV_GOTO.MAV_GOTO_HOLD_AT_SPECIFIED_POSITION;
import static sq.rogue.rosettadrone.util.TYPE_WAYPOINT_DISTANCE;
import static sq.rogue.rosettadrone.util.TYPE_WAYPOINT_MAX_ALTITUDE;
import static sq.rogue.rosettadrone.util.TYPE_WAYPOINT_MAX_SPEED;
import static sq.rogue.rosettadrone.util.TYPE_WAYPOINT_MIN_ALTITUDE;
import static sq.rogue.rosettadrone.util.TYPE_WAYPOINT_MIN_SPEED;
import static sq.rogue.rosettadrone.util.safeSleep;

public class MAVLinkReceiver {
    private static final int MAV_MISSION_ACCEPTED = 0;
    private final String TAG = this.getClass().getSimpleName();

    private float m_autoFlightSpeed = 2.0f;
    private float m_maxFlightSpeed = 5.0f;


    private final int WP_STATE_INACTIVE = 0;
    private final int WP_STATE_REQ_COUNT = 1;
    private final int WP_STATE_REQ_WP = 2;
    private final int MAX_WAYPOINT_DISTANCE = 475;
    public boolean curvedFlightPath = true;
    public float flightPathRadius = .2f;
    DroneModel mModel;
    CommandsManager commandsManager;
    private long mTimeStampLastGCSHeartbeat = 0;
    private int mNumGCSWaypoints = 0;
    private int wpState = 0;
    private MainActivity parent;
    private WaypointMission.Builder mBuilder;
    private ArrayList<msg_mission_item_int> mMissionItemList;
    private boolean isHome = true;

    public MAVLinkReceiver(MainActivity parent, DroneModel model) {

        this.parent = parent;
        this.mModel = model;
        this.commandsManager = new CommandsManager(parent, mModel);
    }

    public void process(MAVLinkMessage msg) {

        // IS 0 is hart beat...
        if (msg.msgid != 0) {
            Log.d(TAG, msg.toString());
            Log.d(TAG, "Message: " + msg);
            //          Log.d(TAG, String.valueOf(msg));
            //       Log.d(TAG, String.valueOf(msg.msgid));
        }

        switch (msg.msgid) {
            case MAVLINK_MSG_ID_HEARTBEAT:
                this.mTimeStampLastGCSHeartbeat = System.currentTimeMillis();
                break;

            case MAVLINK_MSG_ID_COMMAND_LONG:
                msg_command_long msg_cmd = (msg_command_long) msg;

                if (mModel.getSystemId() != msg_cmd.target_system) {
                    return;
                }

//                if (mModel.isSafetyEnabled()) {
//                    Log.d(TAG, parent.getResources().getString(R.string.safety_launch));
//                    return;
//                }

                commandsManager.manage_cmds(msg_cmd);
                break;

            case MAVLINK_MSG_ID_COMMAND_INT:
                // TODO I don't understand what this message is, but ArduCopter handles it.
                // See ArduCopter/GCS_Mavlink.cpp
                break;

            case MAVLINK_MSG_ID_SET_MODE:
                msg_set_mode msg_set_mode = (msg_set_mode) msg;
                parent.logMessageDJI("MAVLINK_MSG_ID_SET_MODE: " + msg_set_mode.custom_mode);
                changeFlightMode((int) msg_set_mode.custom_mode);
                break;

            /**************************************************************
             * These messages are used when GCS sends Velocity commands   *
             **************************************************************/
            case MAVLINK_MSG_ID_SET_POSITION_TARGET_LOCAL_NED:
                msg_set_position_target_local_ned msg_param = (msg_set_position_target_local_ned) msg;
                if (((msg_param.type_mask & 0b0000100000000111) == 0x007)) {  // If no move and we use yaw rate...
                    mModel.mAutonomy = false; // Velocity must halt autonomy (as Autonomy tries to reach a point, whule veliciity tries to set a speed)
                    mModel.do_set_motion_velocity(msg_param.vx, msg_param.vy, msg_param.vz, (float) Math.toDegrees(msg_param.yaw_rate), msg_param.type_mask);
                    mModel.send_command_ack(MAVLINK_MSG_ID_SET_POSITION_TARGET_LOCAL_NED, MAV_RESULT.MAV_RESULT_ACCEPTED);
                } else {
                    mModel.send_command_ack(MAVLINK_MSG_ID_SET_POSITION_TARGET_LOCAL_NED, MAV_RESULT.MAV_RESULT_IN_PROGRESS);
                    mModel.do_set_motion_relative(
                            MAVLINK_MSG_ID_SET_POSITION_TARGET_LOCAL_NED,
                            (double) msg_param.x,
                            (double) msg_param.y,
                            msg_param.z,
                            msg_param.yaw,
                            msg_param.vx,
                            msg_param.vy,
                            msg_param.vz,
                            msg_param.yaw_rate,
                            msg_param.type_mask);
                }
                break;

            case MAVLINK_MSG_ID_SET_POSITION_TARGET_GLOBAL_INT:
                // This command must be sent every second...
                msg_set_position_target_global_int msg_param_4 = (msg_set_position_target_global_int) msg;

                // If position is set to zero then it must be a velocity command... We should use rather the mask ...
                if (((msg_param_4.type_mask & 0b0000100000000111) == 0x007)) {  // If no move and we use yaw rate...
                    mModel.mAutonomy = false;
                    mModel.do_set_motion_velocity_NED(msg_param_4.vx, msg_param_4.vy, msg_param_4.vz, (float) Math.toDegrees(msg_param_4.yaw_rate), msg_param_4.type_mask);
                    mModel.send_command_ack(MAVLINK_MSG_ID_SET_POSITION_TARGET_GLOBAL_INT, MAV_RESULT.MAV_RESULT_ACCEPTED);
                } else {
                    mModel.send_command_ack(MAVLINK_MSG_ID_SET_POSITION_TARGET_GLOBAL_INT, MAV_RESULT.MAV_RESULT_IN_PROGRESS);
                    mModel.do_set_motion_absolute(
                            (double) msg_param_4.lat_int / 10000000,
                            (double) msg_param_4.lon_int / 10000000,
                            msg_param_4.alt,
                            msg_param_4.yaw,
                            msg_param_4.vx,
                            msg_param_4.vy,
                            msg_param_4.vz,
                            msg_param_4.yaw_rate,
                            msg_param_4.type_mask);
                }
                break;

            // This command must be sent at 1Hz minimum...
            case MAVLINK_MSG_ID_MANUAL_CONTROL:
                msg_manual_control msg_param_5 = (msg_manual_control) msg;

                mModel.do_set_motion_velocity(
                        msg_param_5.x / (float) 100.0,
                        msg_param_5.y / (float) 100.0,
                        msg_param_5.z / (float) 260.0,
                        msg_param_5.r / (float) 50.0,
                        0b0000011111000111);

                mModel.send_command_ack(MAVLINK_MSG_ID_MANUAL_CONTROL, MAV_RESULT.MAV_RESULT_ACCEPTED);
                break;


            /**************************************************************
             * These messages are used when GCS requests params from MAV  *
             **************************************************************/

            case MAVLINK_MSG_ID_PARAM_REQUEST_LIST:
                mModel.send_all_params();
                break;

            case MAVLINK_MSG_ID_PARAM_REQUEST_READ:
                msg_param_request_read msg_param_3 = (msg_param_request_read) msg;
                //String paramStr = msg_param.getParam_Id();
                //parent.logMessageFromGCS("***" + paramStr);
                mModel.send_param(msg_param_3.param_index);
                // TODO I am not able to convert the param_id bytearray into String
//                for(int i = 0; i < mModel.getParams().size(); i++)
//                    if(mModel.getParams().get(i).getParamName().equals(msg_param.getParam_Id())) {
//                        mModel.send_param(i);
//                        break;
//                    }
                Log.d(TAG, "Request to read param that doesn't exist");
                break;

            case MAVLINK_MSG_ID_PARAM_SET:
                msg_param_set msg_param2 = (msg_param_set) msg;
                MAVParam param = new MAVParam(msg_param2.getParam_Id(),
                        msg_param2.param_value,
                        msg_param2.param_type);
                mModel.changeParam(param);
                break;

            /**************************************************************
             * These messages are used when GCS downloads mission from MAV *
             **************************************************************/

            case MAVLINK_MSG_ID_MISSION_REQUEST_LIST:
                mModel.send_mission_count();

                break;

            case MAVLINK_MSG_ID_MISSION_REQUEST_PARTIAL_LIST:
                // TODO
                break;

            case MAVLINK_MSG_ID_MISSION_REQUEST:
                msg_mission_request msg_request = (msg_mission_request) msg;;
                Log.d(TAG, "Request: "+String.valueOf(msg_request));
                mModel.send_mission_item(msg_request.seq);
                break;

            case MAVLINK_MSG_ID_MISSION_ACK:
                msg_mission_ack msg_ack = new msg_mission_ack();
                if (msg_ack.type == MAV_MISSION_TYPE_MISSION) {
                    // TODO success
                } else {
                    // TODO fail
                }
                break;

            /**************************************************************
             * These messages are used when GCS uploads a mission to MAV  *
             **************************************************************/

            // Start load new mission...
            case MAVLINK_MSG_ID_MISSION_COUNT:
                generateNewMission();
                //mModel.getMissionControl().getWaypointMissionOperator().getLoadedMission().getWaypointList().clear();
                msg_mission_count msg_count = (msg_mission_count) msg;

                if (mModel.getSystemId() != msg_count.target_system) {
                    return;
                }
                parent.logMessageDJI("Mission Counter: " + msg_count.count);
                mNumGCSWaypoints = msg_count.count;
                wpState = WP_STATE_REQ_WP;
                mMissionItemList = new ArrayList<msg_mission_item>();
                mModel.request_mission_item(0);
                break;

            case MAVLINK_MSG_ID_MISSION_ITEM:
                msg_mission_item msg_item = (msg_mission_item) msg;

                if (mModel.getSystemId() != msg_item.target_system) {
                    break;
                }
                parent.logMessageDJI("Add mission: " + msg_item.seq );

                if(((msg_mission_item) msg).command == MAV_CMD_NAV_WAYPOINT) {
                    Log.d(TAG, "Lat = " + msg_item.x);
                    Log.d(TAG, "Lon = " + msg_item.y);
                    Log.d(TAG, "ALT = " + msg_item.z);
                    mModel.do_set_motion_absolute(
                            (double) msg_item.x / 10000000,
                            (double) msg_item.y / 10000000,
                            msg_item.z,
                            msg_item.param4,
                            0,
                            0,
                            0,
                            0,
                            0b0000111111111000);
                }
                else {

                    // Somehow the GOTO from QGroundControl does not issue a mission count...
                    if (mMissionItemList == null) {
                        parent.logMessageDJI("Special single point mission!");
                        generateNewMission();
                        mMissionItemList = new ArrayList<msg_mission_item>();
                        mNumGCSWaypoints = 1;
                        wpState = WP_STATE_REQ_WP;
                        mModel.request_mission_item(0);
                    }

                    mMissionItemList.add(msg_item);

                    // We are done fetching a complete mission from the GCS...
                    if (msg_item.seq == mNumGCSWaypoints - 1) {
                        wpState = WP_STATE_INACTIVE;
                        finalizeNewMission();
                        //      mModel.send_mission_ack();
                    } else {
                        mModel.request_mission_item((msg_item.seq + 1));
                    }
                }
                break;
//            case MAVLINK_MSG_ID_MISSION_ITEM_INT:  // 0x73
//            {
                // Is this message to this system...
//                msg_mission_item_int msg_item = (msg_mission_item_int) msg;
//                Log.d(TAG, "Add mission: " + msg_item.seq);

//                if (mModel.getSystemId() != msg_item.target_system) {
//                    break;
//                }
//                Log.d(TAG, "To this system... ");

                // Somehow the GOTO from QGroundControl does not issue a mission count...
//                if (mMissionItemList == null && msg_item.command == MAV_CMD_NAV_WAYPOINT) {
//                    Log.d(TAG, "Goto... ");
//                    mModel.goto_position(msg_item.x/10000000.0, msg_item.y/10000000.0, msg_item.z, 0);
//                    mModel.send_command_ack(MAVLINK_MSG_ID_MISSION_ACK, MAV_RESULT.MAV_RESULT_ACCEPTED);
//                }
//                else {
//                    if (mMissionItemList == null) {
//                        Log.d(TAG, "Error Sequence error!");
//                        mModel.send_command_ack(MAVLINK_MSG_ID_MISSION_ACK, MAV_RESULT.MAV_RESULT_DENIED);
//                    }
//                    else {
//                        mMissionItemList.add(msg_item);

                        // We are done fetching a complete mission from the GCS...
//                        if (msg_item.seq == mNumGCSWaypoints - 1) {
//                            wpState = WP_STATE_INACTIVE;
//                            finalizeNewMission();
//                        } else {
//                            Log.d(TAG, "Mission REQ: " +msg_item.seq + 1);
//                            mModel.request_mission_item((msg_item.seq + 1));
//                        }
//                    }
//                }
//            }
//            break;

//            case MAVLINK_MSG_ID_MISSION_ITEM:  // 39
//            {
//                // Is this message to this system...
//                msg_mission_item msg_item = (msg_mission_item) msg;
//                if (mModel.getSystemId() != msg_item.target_system) {
//                    break;
//                }
//                Log.d(TAG, "Add mission: " + msg_item.seq);

                // Somehow the GOTO from QGroundControl does not issue a mission count...
//                if (mMissionItemList == null && msg_item.command == MAV_CMD_NAV_WAYPOINT) {
//                    Log.d(TAG, "Goto old... ");
//                    mModel.goto_position(msg_item.x, msg_item.y, msg_item.z, 0);
//                    mModel.send_command_ack(MAVLINK_MSG_ID_MISSION_ACK, MAV_RESULT.MAV_RESULT_ACCEPTED);

//                } else {
//                    if (mMissionItemList == null) {
//                        Log.d(TAG, "Error Sequence error!");
//                        mModel.send_command_ack(MAVLINK_MSG_ID_MISSION_ACK, MAV_RESULT.MAV_RESULT_DENIED);
//                    }
//                    else {
//                        msg_mission_item_int msg_item_f = new msg_mission_item_int();

//                        msg_item_f.param1=msg_item.param1;
//                        msg_item_f.param2=msg_item.param2;
//                        msg_item_f.param3=msg_item.param3;
//                        msg_item_f.param4=msg_item.param4;
//                        msg_item_f.x=(int)(msg_item.x*10000000.0);
//                        msg_item_f.y=(int)(msg_item.y*10000000.0);
//                        msg_item_f.z=msg_item.z;
//                        msg_item_f.seq=msg_item.seq;
//                        msg_item_f.command=msg_item.command;
//                        msg_item_f.target_system=msg_item.target_system;
//                        msg_item_f.target_component=msg_item.target_component;
//                        msg_item_f.frame=msg_item.frame;
//                        msg_item_f.current=msg_item.current;
//                        msg_item_f.autocontinue=msg_item.autocontinue;
//                        msg_item_f.mission_type=msg_item.mission_type;

//                        mMissionItemList.add(msg_item_f);
//                        // We are done fetching a complete mission from the GCS...
//                        if (msg_item.seq == mNumGCSWaypoints - 1) {
//                            wpState = WP_STATE_INACTIVE;
//                            finalizeNewMission();
//                        } else {
//                            Log.d(TAG, "Mission REQ: " +msg_item.seq + 1);
//                            mModel.request_mission_item((msg_item.seq + 1));
//                        }
//                    }
//                }
//            }
//            break;

            /**************************************************************
             * These messages from GCS direct a mission-related action    *
             **************************************************************/

            case MAVLINK_MSG_ID_MISSION_SET_CURRENT:
                Log.d(TAG, "MSN: received set_current from GCS");
                // TODO::::::
                break;

            // Clear all mission states...
            case MAVLINK_MSG_ID_MISSION_CLEAR_ALL:
                parent.logMessageDJI("MSN: received clear_all from GCS");
                WaypointMission y = mModel.getWaypointMissionOperator().getLoadedMission();
                if( y != null){
                    y.getWaypointList().clear();
                }
                break;
        }
    }

    // Support function...
    public long getTimestampLastGCSHeartbeat() {
        return mTimeStampLastGCSHeartbeat;
    }

    private void changeFlightMode(int flightMode) {
        mModel.setGCSCommandedMode(flightMode);

        if (flightMode == ArduCopterFlightModes.AUTO) {
            if (mModel.getWaypointMissionOperator().getCurrentState() == WaypointMissionState.EXECUTION_PAUSED) {
                parent.logMessageDJI("Resuming mission");
                mModel.resumeWaypointMission();
            } else if (mModel.getWaypointMissionOperator().getCurrentState() == WaypointMissionState.READY_TO_EXECUTE)
                mModel.startWaypointMission();
        } else if (flightMode == ArduCopterFlightModes.BRAKE) {
            mModel.pauseWaypointMission();
            mModel.setGCSCommandedMode(flightMode);
        } else if (flightMode == ArduCopterFlightModes.RTL)
            mModel.do_go_home();
        else if (flightMode == ArduCopterFlightModes.LAND)
            mModel.do_land();

        mModel.send_command_ack(MAV_CMD_DO_SET_MODE, MAV_RESULT.MAV_RESULT_ACCEPTED);

    }

    protected void generateNewMission() {
        mBuilder = new WaypointMission.Builder().
                autoFlightSpeed(2.0f).
                maxFlightSpeed(5.0f).
                setExitMissionOnRCSignalLostEnabled(false).
                finishedAction(WaypointMissionFinishedAction.NO_ACTION).
                gotoFirstWaypointMode(WaypointMissionGotoWaypointMode.SAFELY).
                headingMode(WaypointMissionHeadingMode.AUTO).
                repeatTimes(1);

        if (curvedFlightPath) {
            mBuilder.flightPathMode(WaypointMissionFlightPathMode.CURVED);
        } else {
            mBuilder.flightPathMode(WaypointMissionFlightPathMode.NORMAL);
        }
        mMissionItemList = new ArrayList<>();
    }

    protected void finalizeNewMission() {
        ArrayList<Waypoint> dji_wps = new ArrayList<Waypoint>();
        Waypoint currentWP = null;

        parent.logMessageDJI("==============================");
        parent.logMessageDJI("Waypoint Mission Uploading");
        parent.logMessageDJI("==============================");

        boolean stopUpload = false;
        int errorCode = 0;

        boolean triggerDistanceEnabled = false;
        float triggerDistance = 0;

        waypoint_loop:
        for (msg_mission_item m : this.mMissionItemList) {
            parent.logMessageDJI(String.valueOf(m.command));

            switch (m.command) {

                case MAV_CMD.MAV_CMD_NAV_TAKEOFF:
                    parent.logMessageDJI("Takeoff...");
//                    parent.logMessageDJI("P1 = " + m.param1);
//                    parent.logMessageDJI("P2 = " + m.param2);
//                    parent.logMessageDJI("P3 = " + m.param3);
//                    parent.logMessageDJI("P4 = " + m.param4);
//                    parent.logMessageDJI("x = " + m.x);
//                    parent.logMessageDJI("y = " + m.y);
//                    parent.logMessageDJI("z = " + m.z);
                    break;

                case MAV_CMD.MAV_CMD_NAV_WAYPOINT:
                    // TODO:   Is this to handle the first way point being the current location ????
                    if (isHome) {
                        parent.logMessageDJI("Is Home...");
//                        homeValue = m.z;
                        isHome = false;
                    } //else

                    {
                        if ((m.z) > 500) {  // TODO:  Shuld reqest max altitude not assume 500...
//                            m.z = 500;
                            parent.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    NotificationHandler.notifyAlert(parent, TYPE_WAYPOINT_MAX_ALTITUDE, null, null);
                                }
                            });
                            stopUpload = true;
                            break waypoint_loop;

                        } else if ((m.z) < -200) {  // TODO:  hmm so we can not take off from a mountain and fly down?? ...
//                            m.z = -200;
                            parent.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    NotificationHandler.notifyAlert(parent, TYPE_WAYPOINT_MIN_ALTITUDE, null, null);
                                }
                            });
                            stopUpload = true;
                            break waypoint_loop;
                        }

                        currentWP = new Waypoint(m.x, m.y, m.z); // TODO check altitude conversion

                        if (m.param1 > 0)
                            currentWP.addAction(new WaypointAction(WaypointActionType.STAY, (int) m.param1 * 1000)); // milliseconds...

                        if (m.param2 > 0)
                            currentWP.addAction(new WaypointAction(WaypointActionType.ROTATE_AIRCRAFT, (int)(m.param2 * 180.0/3.141592))); // +-180 deg...

                        if (curvedFlightPath) {
                            currentWP.cornerRadiusInMeters = flightPathRadius;
                        }

                        dji_wps.add(currentWP);

                        parent.logMessageDJI("Waypoint: " + m.x + ", " + m.y + " at " + m.z + " m " + m.param2 + " Yaw " + m.param1 + " Delay ");
//                        parent.logMessageDJI("P1 = " + m.param1);
//                        parent.logMessageDJI("P2 = " + m.param2);
//                        parent.logMessageDJI("P3 = " + m.param3);
//                        parent.logMessageDJI("P4 = " + m.param4);
//                        parent.logMessageDJI("x = " + m.x);
//                        parent.logMessageDJI("y = " + m.y);
//                        parent.logMessageDJI("z = " + m.z);
                    }
                    break;
                case MAV_CMD.MAV_CMD_DO_CHANGE_SPEED:
//                    final DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
//                        @Override
//                        public void onClick(DialogInterface dialog, int which) {
//                        }
//                    };
//
//                    final DialogInterface.OnCancelListener onCancelListener = new DialogInterface.OnCancelListener() {
//                        @Override
//                        public void onCancel(DialogInterface dialog) {
//                        }
//                    };

                    if (m.param2 < -15) {
                        parent.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                NotificationHandler.notifyAlert(parent, TYPE_WAYPOINT_MIN_SPEED,
                                        null, null);
                            }
                        });
                        stopUpload = true;
                        break waypoint_loop;
                    } else if (m.param2 > 15) {
                        parent.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                NotificationHandler.notifyAlert(parent, TYPE_WAYPOINT_MAX_SPEED,
                                        null, null);
                            }
                        });
                        stopUpload = true;
                        break waypoint_loop;
                    } else {
                        mBuilder.autoFlightSpeed(m.param2);
                    }
                    break;

                case MAV_CMD.MAV_CMD_DO_MOUNT_CONTROL:
                    currentWP.addAction(new WaypointAction(WaypointActionType.GIMBAL_PITCH, (int) m.param1));
                    parent.logMessageDJI("Set gimbal pitch: " + m.param1);
                    break;

                case MAV_CMD.MAV_CMD_IMAGE_START_CAPTURE:
                    if (currentWP != null)
                        currentWP.addAction(new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 0));
                    parent.logMessageDJI("Take photo");
                    break;

                case MAV_CMD.MAV_CMD_DO_SET_CAM_TRIGG_DIST:

                    if (!triggerDistanceEnabled) {
                        if (m.param1 != 0) {
                            triggerDistanceEnabled = true;
                            triggerDistance = m.param1;
                        } else {
                            triggerDistance = 0;
                        }
                    }
                    break;

                case MAV_CMD.MAV_CMD_NAV_RETURN_TO_LAUNCH:
                    mBuilder.finishedAction(WaypointMissionFinishedAction.GO_HOME);
                    parent.logMessageDJI("Waypoint RTL");
                    break;
            }
        }

        if (stopUpload) {
            parent.logMessageDJI("Waypoint upload aborted due to invalid parameters");
        } else {
            parent.logMessageDJI("Speed for mission will be " + mBuilder.getAutoFlightSpeed() + " m/s");
            parent.logMessageDJI("==============================");

            ArrayList<Waypoint> correctedWps;

            if (triggerDistanceEnabled) {
                parent.logMessageDJI("ADDING SURVEY WAYPOINTS");
                correctedWps = addSurveyWaypoints(dji_wps, triggerDistance);
                mBuilder.flightPathMode(WaypointMissionFlightPathMode.NORMAL);
            } else {
                correctedWps = addIntermediateWaypoints(dji_wps);
            }
            logWaypointstoRD(correctedWps);
            parent.logMessageDJI("WP size " + correctedWps.size());
            safeSleep(200);
            mBuilder.waypointList(correctedWps).waypointCount(correctedWps.size());
            safeSleep(200);
            WaypointMission builtMission = mBuilder.build();
            safeSleep(200);
            mModel.setWaypointMission(builtMission);
            safeSleep(200);
        }
        isHome = true;
    }

    private void logWaypointstoRD(ArrayList<Waypoint> wps) {
        parent.logMessageDJI("==============================");
        parent.logMessageDJI("Waypoints with intermediate wps");
        parent.logMessageDJI("==============================");
        for (Waypoint wp : wps)
            parent.logMessageDJI(wp.coordinate.getLatitude() + ", " + wp.coordinate.getLongitude() + ", " + wp.altitude);
    }

    private ArrayList<Waypoint> addSurveyWaypoints(ArrayList<Waypoint> wpIn, float triggerDistance) {
        ArrayList<Waypoint> wpOut = new ArrayList<>();
        float distanceRemainder = 0;

        Waypoint previousWaypoint = wpIn.get(0);

        for (Waypoint currentWaypoint : wpIn) {
            if (currentWaypoint == wpIn.get(0)) {
                currentWaypoint.addAction(new WaypointAction(WaypointActionType.STAY, 1));
                currentWaypoint.addAction(new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 0));
                wpOut.add(currentWaypoint);
                continue;
            }
            float distanceBetweenPoints = (float) getRangeBetweenWaypoints_m(currentWaypoint, previousWaypoint);
            float currentDistance;

            if (distanceRemainder != 0) {
                currentDistance = distanceRemainder;
            } else {
                currentDistance = triggerDistance;
            }

            float prevDistance = currentDistance;
            int numSurveyWaypoints = 0;


            while (prevDistance < distanceBetweenPoints) {
                prevDistance += triggerDistance;
                numSurveyWaypoints++;
            }


            for (int i = 1; currentDistance < distanceBetweenPoints; i++) {
                parent.logMessageDJI(String.valueOf("WAYPOINT ADDED AT " + currentDistance));

                Waypoint surveyWaypoint = createIntermediateWaypoint(previousWaypoint, currentWaypoint, i, numSurveyWaypoints, currentWaypoint.altitude, 0);
                surveyWaypoint.addAction(new WaypointAction(WaypointActionType.STAY, 1));
                surveyWaypoint.addAction(new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 0));
                wpOut.add(surveyWaypoint);

                currentDistance += triggerDistance;
            }
            distanceRemainder = (currentDistance - distanceBetweenPoints);
            currentWaypoint.addAction(new WaypointAction(WaypointActionType.STAY, 1));
            currentWaypoint.addAction(new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 0));
            wpOut.add(currentWaypoint);
            previousWaypoint = currentWaypoint;
        }
        return wpOut;
    }

    private ArrayList<Waypoint> addIntermediateWaypoints(ArrayList<Waypoint> wpIn) {

        // No need for intermediate waypoints if only 0, if 1 then we MUST as a waypoint (min 2 waypoints in DJI)
        if (wpIn.size() < 1)
            return wpIn;

        ArrayList<Waypoint> wpOut = new ArrayList<>();

        // If this is a goto position ... we must add at least one more waypoint (minimum 2 on DJI)
        if ( wpIn.size() == 1){
            // Get current position...
            double lat = mModel.get_current_lat();
            double lon = mModel.get_current_lon();
            float  alt = mModel.get_current_alt();
            Waypoint wpPrevious = new Waypoint(lat,lon,alt);

            Waypoint wpCurrent = wpIn.get(0);
            parent.logMessageDJI("Single point WP distance: " + String.valueOf(getRangeBetweenWaypoints_m(wpPrevious,  wpCurrent)));

            float waypointIncrement = (wpCurrent.altitude - wpPrevious.altitude) / 2;
            Waypoint intermediateWaypoint = createIntermediateWaypoint(wpPrevious, wpCurrent, 1, 1, 0, waypointIncrement);
            // Insert the intermediate wp at the beginning of the list...
            wpIn.add(0,intermediateWaypoint);
        }

        Waypoint wpPrevious = wpIn.get(0);
        boolean shouldNotify = false;

        for (Waypoint wpCurrent : wpIn) {

            if (wpCurrent == wpIn.get(0)) {
                wpOut.add(wpCurrent);
                continue;
            }
            parent.logMessageDJI("WP dist: " + String.valueOf(getRangeBetweenWaypoints_m(wpCurrent, wpPrevious)));
            if (getRangeBetweenWaypoints_m(wpCurrent, wpPrevious) > MAX_WAYPOINT_DISTANCE) {
                int numIntermediateWps = (int) getRangeBetweenWaypoints_m(wpCurrent, wpPrevious) / MAX_WAYPOINT_DISTANCE;
                shouldNotify = true;
                parent.logMessageDJI("creating " + numIntermediateWps + " intermediate wps");
                float waypointIncrement = (wpCurrent.altitude - wpPrevious.altitude) / (numIntermediateWps + 1);
                parent.logMessageDJI("WAYPOINT INCREMENT + " + waypointIncrement);
                float prevIntermediaryAltitude = 0;
                for (int i = 0; i < numIntermediateWps; i++) {
                    Waypoint intermediateWaypoint = createIntermediateWaypoint(wpPrevious, wpCurrent, i + 1, numIntermediateWps, prevIntermediaryAltitude, waypointIncrement);
                    prevIntermediaryAltitude = intermediateWaypoint.altitude;
                    wpOut.add(intermediateWaypoint);
                }
            }
            wpOut.add(wpCurrent);
            wpPrevious = wpCurrent;
        }

        if (shouldNotify) {
            parent.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    NotificationHandler.notifyAlert(parent, TYPE_WAYPOINT_DISTANCE,
                            null, null);
                }
            });
        }

        return wpOut;
    }

    private Waypoint createIntermediateWaypoint(Waypoint wp1, Waypoint wp2, int intermediateWpNum, int numIntermediateWaypoints, float prevIntermediaryAltitude, float waypointIncrement) {
        // If we need to add one intermediate waypoint, it is (1/2) between wp1 and wp2
        // If we need to add two intermediate waypoints, they are (1/3) and (2/3) between wp1 and wp2
        // etc.
        double new_lat = wp1.coordinate.getLatitude() + (wp2.coordinate.getLatitude() - wp1.coordinate.getLatitude()) * intermediateWpNum / (numIntermediateWaypoints + 1);
        double new_lon = wp1.coordinate.getLongitude() + (wp2.coordinate.getLongitude() - wp1.coordinate.getLongitude()) * intermediateWpNum / (numIntermediateWaypoints + 1);
        float new_alt;
        if (prevIntermediaryAltitude == 0) {
            new_alt = wp1.altitude + waypointIncrement;
        } else {
            new_alt = prevIntermediaryAltitude + waypointIncrement;
        }
//        float new_alt = Math.max(wp1.altitude, wp2.altitude);

        return new Waypoint(new_lat, new_lon, new_alt);
    }

    // Based on: https://stackoverflow.com/questions/3694380/calculating-distance-between-two-points-using-latitude-longitude-what-am-i-doi
    private double getRangeBetweenWaypoints_m(Waypoint wp1, Waypoint wp2) {

        double lat1 = wp1.coordinate.getLatitude();
        double lon1 = wp1.coordinate.getLongitude();
        double el1 = wp1.altitude;
        double lon2 = wp2.coordinate.getLongitude();
        double lat2 = wp2.coordinate.getLatitude();
        double el2 = wp2.altitude;

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
}
