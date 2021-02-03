package sq.rogue.rosettadrone;

import android.util.Log;

import com.MAVLink.common.msg_mission_item;
import com.MAVLink.enums.MAV_RESULT;
import com.MAVLink.common.msg_command_long;

import dji.common.mission.waypoint.WaypointMissionState;

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
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_OVERRIDE_GOTO;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_REQUEST_AUTOPILOT_CAPABILITIES;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_START_CAPTURE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_STOP_CAPTURE;
import static com.MAVLink.enums.MAV_GOTO.MAV_GOTO_DO_CONTINUE;
import static com.MAVLink.enums.MAV_GOTO.MAV_GOTO_DO_HOLD;
import static com.MAVLink.enums.MAV_GOTO.MAV_GOTO_HOLD_AT_CURRENT_POSITION;
import static com.MAVLink.enums.MAV_GOTO.MAV_GOTO_HOLD_AT_SPECIFIED_POSITION;
import static com.MAVLink.enums.MAV_PROTOCOL_CAPABILITY.MAV_PROTOCOL_CAPABILITY_FTP;
import static sq.rogue.rosettadrone.util.safeSleep;

public class CommandsManager {
    private final String TAG = this.getClass().getSimpleName();
    private MainActivity parent;
    private DroneModel mModel;

    public CommandsManager(MainActivity parent, DroneModel mModel) {
        this.parent = parent;
        this.mModel = mModel;
    }

    public void manage_cmds(msg_command_long msg_cmd){


        switch (msg_cmd.command) {
            case MAV_CMD_COMPONENT_ARM_DISARM:
                parent.logMessageDJI("Received MAV: MAV_CMD_COMPONENT_ARM_DISARM");
                if (msg_cmd.param1 == 1)
                    mModel.armMotors();
                else
                    mModel.disarmMotors();

                break;
            case MAV_CMD_DO_SET_MODE:
                parent.logMessageDJI("Received MAV: MAV_CMD_DO_SET_MODE");
                changeFlightMode((int) msg_cmd.param1);
                break;
            case MAV_CMD_NAV_LOITER_UNLIM:
                parent.logMessageDJI("Received MAV: MAV_CMD_NAV_LOITER_UNLIM");
                // mModel.set_flight_mode(ATTI);
                break;
            case MAV_CMD_NAV_TAKEOFF:
                parent.logMessageDJI("Received MAV: MAV_CMD_NAV_TAKEOFF");
                Log.d(TAG,"ALT = " + msg_cmd.param7);
                mModel.mAirBorn = 0;
                mModel.do_takeoff(msg_cmd.param7 * (float) 1000.0);
                mModel.send_command_ack(MAV_CMD_NAV_TAKEOFF, MAV_RESULT.MAV_RESULT_IN_PROGRESS);
                break;
            case MAV_CMD_NAV_LAND:
                parent.logMessageDJI("Received MAV: MAV_CMD_NAV_LAND");
                mModel.do_land();
                break;
            case MAV_CMD_DO_SET_HOME:
                parent.logMessageDJI("Received MAV: MAV_CMD_DO_SET_HOME");
                parent.logMessageDJI("LAT = " + msg_cmd.param5);
                parent.logMessageDJI("LONG = " + msg_cmd.param6);
                parent.logMessageDJI("ALT = " + msg_cmd.param7);
                // TODO;
                break;
            case MAV_CMD_NAV_RETURN_TO_LAUNCH:
                parent.logMessageDJI("Received MAV: MAV_CMD_NAV_RETURN_TO_LAUNCH");
                mModel.do_go_home();
                mModel.send_command_ack(MAV_CMD_NAV_RETURN_TO_LAUNCH, MAV_RESULT.MAV_RESULT_ACCEPTED);
                break;
            case MAV_CMD_GET_HOME_POSITION:
                parent.logMessageDJI("Received MAV: MAV_CMD_GET_HOME_POSITION");
                mModel.send_home_position();
                break;
            case MAV_CMD_REQUEST_AUTOPILOT_CAPABILITIES:
                parent.logMessageDJI("Received MAV: MAV_CMD_REQUEST_AUTOPILOT_CAPABILITIES");
                mModel.send_autopilot_version();
                break;
            case MAV_CMD_VIDEO_START_CAPTURE:
                parent.logMessageDJI("Received MAV: MAV_CMD_VIDEO_START_CAPTURE");
                mModel.startRecordingVideo();
                break;
            case MAV_CMD_VIDEO_STOP_CAPTURE:
                parent.logMessageDJI("Received MAV: MAV_CMD_VIDEO_STOP_CAPTURE");
                mModel.stopRecordingVideo();
                break;
            case MAV_CMD_DO_DIGICAM_CONTROL:
                // DEPRECATED but still used by QGC
                parent.logMessageDJI("Received MAV: MAV_CMD_DO_DIGICAM_CONTROL");
                mModel.takePhoto();

                break;
            case MAV_CMD_MISSION_START:
                parent.logMessageDJI("Received MAV: MAV_CMD_MISSION_START");
                if(mModel.mission_started){
                    mModel.resumeWaypointMission();
                } else {
                    mModel.startWaypointMission();
                }
                break;
            case MAV_CMD_OVERRIDE_GOTO:
                parent.logMessageDJI("Received MAV: MAV_CMD_OVERRIDE_GOTO");
                if (mModel.getSystemId() != msg_cmd.target_system) {
                    break;
                }
                mModel.pauseWaypointMission();
                if (msg_cmd.param2 == MAV_GOTO_HOLD_AT_CURRENT_POSITION) {
                    int x = (int) mModel.get_current_lat();
                    int y = (int) mModel.get_current_lon();
                    int z = (int) mModel.get_current_alt();

                    Log.d(TAG, "Lat = " + x);
                    Log.d(TAG, "Lon = " + y);
                    Log.d(TAG, "ALT = " + z);
                    mModel.do_set_motion_absolute(
                            (double) x,
                            (double) y,
                            z,
                            msg_cmd.param4,
                            0,
                            0,
                            0,
                            0,
                            0b0000111111111000);
                } else if (msg_cmd.param2 == MAV_GOTO_HOLD_AT_SPECIFIED_POSITION) {

                    Log.d(TAG, "Lat = " + msg_cmd.param5);
                    Log.d(TAG, "Lon = " + msg_cmd.param6);
                    Log.d(TAG, "ALT = " + msg_cmd.param7);
                    mModel.do_set_motion_absolute(
                            (double) msg_cmd.param5,
                            (double) msg_cmd.param6,
                            msg_cmd.param7,
                            msg_cmd.param4,
                            0,
                            0,
                            0,
                            0,
                            0b0000111111111000);
                }
                if(msg_cmd.param1 == MAV_GOTO_DO_CONTINUE) {
                    mModel.resumeWaypointMission();
                }
                mModel.send_command_ack(MAV_CMD_OVERRIDE_GOTO, MAV_RESULT.MAV_RESULT_ACCEPTED);
                break;
            case MAV_CMD_CONDITION_YAW:
                parent.logMessageDJI("Yaw = " + msg_cmd.param1);
                // If absolut yaw...
                if( msg_cmd.param4 == 0) {
                    mModel.do_set_motion_absolute(
                            0,
                            0,
                            0,
                            msg_cmd.param1*(float)(Math.PI/180.0),
                            0,
                            0,
                            0,
                            10,
                            0b1111001111111111);
                }
                break;
            case MAV_CMD_DO_SET_SERVO:
                parent.logMessageDJI("Received MAV: MAV_CMD_DO_SET_SERVO");
                mModel.do_set_Gimbal(msg_cmd.param1, msg_cmd.param2);
                mModel.send_command_ack(MAV_CMD_DO_SET_SERVO, MAV_RESULT.MAV_RESULT_ACCEPTED);
                break;
//                        CUSTOM
            case MAV_PROTOCOL_CAPABILITY_FTP:
                parent.logMessageDJI("Received MAV: MAV_PROTOCOL_CAPABILITY_FTP");
                mModel.send_command_ack(MAV_PROTOCOL_CAPABILITY_FTP, MAV_RESULT.MAV_RESULT_ACCEPTED);
                break;
            // JUMP is just a test function to enter the Timeline...
            case MAV_CMD_DO_JUMP:
                Log.d(TAG, "Start Timeline...");
                //    mModel.echoLoadedMission();
                break;
            default:
                parent.logMessageDJI("Received unknown command id: " + msg_cmd.command);
                parent.logMessageDJI("R:" + msg_cmd.toString());
                break;
        }
    }

    public void changeFlightMode(int flightMode) {
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
}
