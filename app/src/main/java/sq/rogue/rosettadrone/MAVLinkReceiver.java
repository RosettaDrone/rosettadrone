package sq.rogue.rosettadrone;

import android.util.Log;

import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.common.msg_altitude;
import com.MAVLink.common.msg_command_long;
import com.MAVLink.common.msg_global_position_int;
import com.MAVLink.common.msg_manual_control;
import com.MAVLink.common.msg_mission_ack;
import com.MAVLink.common.msg_mission_count;
import com.MAVLink.common.msg_mission_item;
import com.MAVLink.common.msg_mission_request;
import com.MAVLink.common.msg_param_request_read;
import com.MAVLink.common.msg_param_set;
import com.MAVLink.common.msg_set_mode;
import com.MAVLink.common.msg_set_position_target_global_int;
import com.MAVLink.enums.MAV_CMD;
import com.MAVLink.enums.MAV_MISSION_RESULT;
import com.MAVLink.enums.MAV_PROTOCOL_CAPABILITY;
import com.MAVLink.enums.MAV_RESULT;
import com.MAVLink.common.msg_set_position_target_local_ned;

import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

import dji.common.error.DJIError;
import dji.common.flightcontroller.LocationCoordinate3D;
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
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_DIGICAM_CONTROL;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_SET_CAM_TRIGG_DIST;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_SET_HOME;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_SET_MODE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_SET_SERVO;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_GET_HOME_POSITION;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_MISSION_START;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_CONDITION_YAW;
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

import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.util.CommonCallbacks;
import dji.sdk.mission.waypoint.WaypointMissionOperator;

public class MAVLinkReceiver {
    private final String TAG = this.getClass().getSimpleName();
    private final int WP_STATE_INACTIVE = 0;
    private final int WP_STATE_REQ_COUNT = 1;
    private final int WP_STATE_REQ_WP = 2;
    private final int MAX_WAYPOINT_DISTANCE = 475;
    public boolean curvedFlightPath = true;
    public float flightPathRadius = .2f;
    DroneModel mModel;
    CommandsManager commandsManager;
    MissionsManager missionsManager;
    private long mTimeStampLastGCSHeartbeat = 0;
    private int mNumGCSWaypoints = 0;
    private int wpState = 0;
    private MainActivity parent;
    private WaypointMission.Builder mBuilder;
    private ArrayList<msg_mission_item> mMissionItemList;
    private boolean isHome = true;
    private float homeValue = 0;

    public MAVLinkReceiver(MainActivity parent, DroneModel model) {

        this.parent = parent;
        this.mModel = model;
        this.commandsManager = new CommandsManager(parent, mModel);
        this.missionsManager = new MissionsManager(parent,
                mModel,
                mNumGCSWaypoints,
                wpState,
                mMissionItemList,
                mBuilder,
                WP_STATE_INACTIVE,
                WP_STATE_REQ_COUNT,
                WP_STATE_REQ_WP,
                isHome,
                flightPathRadius,
                curvedFlightPath,
                MAX_WAYPOINT_DISTANCE);
    }

    public void process(MAVLinkMessage msg) {

        if(msg.msgid != 0) {
            Log.d(TAG, msg.toString());
  //          parent.logMessageDJI(String.valueOf(msg));
     //       parent.logMessageDJI(String.valueOf(msg.msgid));
        }

       // parent.logMessageDJI("Message: " + msg);

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
//                    parent.logMessageDJI(parent.getResources().getString(R.string.safety_launch));
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
                commandsManager.changeFlightMode((int) msg_set_mode.custom_mode);
                break;

            /**************************************************************
             * These messages are used when GCS sends Velocity commands   *
             **************************************************************/
            case MAVLINK_MSG_ID_SET_POSITION_TARGET_LOCAL_NED:
                msg_set_position_target_local_ned msg_param = (msg_set_position_target_local_ned) msg;
                if ( ((msg_param.type_mask & 0b0000100000000111) == 0x007) ){  // If no move and we use yaw rate...
                    mModel.mAutonomy = false; // Velocity must halt autonomy (as Autonomy tries to reach a point, whule veliciity tries to set a speed)
                    mModel.do_set_motion_velocity(msg_param.vx, msg_param.vy, msg_param.vz, (float)Math.toDegrees(msg_param.yaw_rate), msg_param.type_mask);
                } else {
                    mModel.do_set_motion_relative(
                            MAVLINK_MSG_ID_SET_POSITION_TARGET_LOCAL_NED,
                            (double)msg_param.x,
                            (double)msg_param.y,
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
                if ( ((msg_param_4.type_mask & 0b0000100000000111) == 0x007) ){  // If no move and we use yaw rate...
                    mModel.mAutonomy = false;
                    mModel.do_set_motion_velocity_NED(msg_param_4.vx, msg_param_4.vy, msg_param_4.vz, (float)Math.toDegrees(msg_param_4.yaw_rate),msg_param_4.type_mask);
                } else{
                    mModel.do_set_motion_absolute(
                            (double)msg_param_4.lat_int/10000000,
                            (double)msg_param_4.lon_int/10000000,
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
                mModel.do_set_motion_velocity(msg_param_5.x / (float) 100.0, msg_param_5.y / (float) 100.0,  msg_param_5.z / (float) 260.0, msg_param_5.r / (float) 50.0, 0b0000100000111000);
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
                msg_mission_request msg_request = new msg_mission_request();
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

            case MAVLINK_MSG_ID_MISSION_COUNT:
                missionsManager.generateNewMission();
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
            case MAV_GOTO_HOLD_AT_SPECIFIED_POSITION:
                msg_mission_item goto_item = (msg_mission_item) msg;

                if (mModel.getSystemId() != goto_item.target_system) {
                    break;
                }
                Log.d(TAG, "Lat = " + goto_item.x);
                Log.d(TAG, "Lon = " + goto_item.y);
                Log.d(TAG, "ALT = " + goto_item.z);
                mModel.do_set_motion_absolute(
                        (double) goto_item.x,
                        (double) goto_item.y,
                        goto_item.z,
                        goto_item.param4,
                        0,
                        0,
                        0,
                        0,
                        0b0000111111111000);
                break;
            case MAVLINK_MSG_ID_MISSION_ITEM:
                missionsManager.manage_mission(((msg_mission_item) msg));
                break;

            /**************************************************************
             * These messages from GCS direct a mission-related action    *
             **************************************************************/

            case MAVLINK_MSG_ID_MISSION_SET_CURRENT:
                parent.logMessageDJI("MSN: received set_current from GCS");
                // TODO
                break;

            case MAVLINK_MSG_ID_MISSION_CLEAR_ALL:
                parent.logMessageDJI("MSN: received clear_all from GCS");
                if(mModel.mission_started){
                    mModel.stopWaypointMission();
                }
                WaypointMission y = mModel.getWaypointMissionOperator().getLoadedMission();
                if( y != null){
                    try {
                        parent.logMessageDJI(y.toString());
                        for (int i = 0; i < y.getWaypointCount(); i++) {
                            parent.logMessageDJI("M:" + y.getWaypointList().get(i).toString());
                        }
                    } catch (Exception e){
                        parent.logMessageDJI("Error: " + e);
                    }
                    y.getWaypointList().clear();
                }
                break;
        }

    }

    public long getTimestampLastGCSHeartbeat() {
        return mTimeStampLastGCSHeartbeat;
    }
}
