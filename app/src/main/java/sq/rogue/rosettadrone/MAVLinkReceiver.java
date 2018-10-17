package sq.rogue.rosettadrone;

import android.util.Log;

import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.common.msg_command_long;
import com.MAVLink.common.msg_mission_ack;
import com.MAVLink.common.msg_mission_count;
import com.MAVLink.common.msg_mission_item;
import com.MAVLink.common.msg_mission_request;
import com.MAVLink.common.msg_param_request_read;
import com.MAVLink.common.msg_param_set;
import com.MAVLink.common.msg_set_mode;
import com.MAVLink.enums.MAV_CMD;
import com.MAVLink.enums.MAV_RESULT;

import java.util.ArrayList;

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
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_COMPONENT_ARM_DISARM;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_DIGICAM_CONTROL;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_SET_CAM_TRIGG_DIST;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_SET_HOME;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_SET_MODE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_GET_HOME_POSITION;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_MISSION_START;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_LAND;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_LOITER_UNLIM;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_RETURN_TO_LAUNCH;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_TAKEOFF;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_REQUEST_AUTOPILOT_CAPABILITIES;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_START_CAPTURE;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_VIDEO_STOP_CAPTURE;
import static com.MAVLink.enums.MAV_MISSION_TYPE.MAV_MISSION_TYPE_MISSION;
import static sq.rogue.rosettadrone.util.TYPE_WAYPOINT_DISTANCE;
import static sq.rogue.rosettadrone.util.TYPE_WAYPOINT_MAX_ALTITUDE;
import static sq.rogue.rosettadrone.util.TYPE_WAYPOINT_MAX_SPEED;
import static sq.rogue.rosettadrone.util.TYPE_WAYPOINT_MIN_ALTITUDE;
import static sq.rogue.rosettadrone.util.TYPE_WAYPOINT_MIN_SPEED;

public class MAVLinkReceiver {
    private final String TAG = this.getClass().getSimpleName();
    private final int WP_STATE_INACTIVE = 0;
    private final int WP_STATE_REQ_COUNT = 1;
    private final int WP_STATE_REQ_WP = 2;
    private final int MAX_WAYPOINT_DISTANCE = 475;
    public boolean curvedFlightPath = true;
    public float flightPathRadius = .2f;
    DroneModel mModel;
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

    }

    public void process(MAVLinkMessage msg) {
//        parent.logMessageDJI(String.valueOf(msg.msgid));
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

                switch (msg_cmd.command) {
                    case MAV_CMD_COMPONENT_ARM_DISARM:
                        if (msg_cmd.param1 == 1)
                            mModel.armMotors();
                        else
                            mModel.disarmMotors();

                        break;
                    case MAV_CMD_DO_SET_MODE:
                        changeFlightMode((int) msg_cmd.param1);
                        break;
                    case MAV_CMD_NAV_LOITER_UNLIM:
//                        mModel.set_flight_mode(ATTI);
                        break;
                    case MAV_CMD_NAV_TAKEOFF:
//                        parent.logMessageDJI("TAKEOFF TARGET = " + msg_cmd.target_system);
//                        parent.logMessageDJI("TAKEOFF SYSID = " + msg.sysid);
                        mModel.do_takeoff();
                        break;
                    case MAV_CMD_NAV_LAND:
                        mModel.do_land();
                        break;
                    case MAV_CMD_DO_SET_HOME:
                        parent.logMessageDJI("LAT = " + msg_cmd.param5);
                        parent.logMessageDJI("LONG = " + msg_cmd.param6);
                        parent.logMessageDJI("ALT = " + msg_cmd.param7);

                        // TODO;
                        break;
                    case MAV_CMD_NAV_RETURN_TO_LAUNCH:
                        mModel.do_go_home();
                        mModel.send_command_ack(MAV_CMD_NAV_RETURN_TO_LAUNCH, MAV_RESULT.MAV_RESULT_ACCEPTED);
                        break;
                    case MAV_CMD_GET_HOME_POSITION:
                        mModel.send_home_position();
                        break;
                    case MAV_CMD_REQUEST_AUTOPILOT_CAPABILITIES:
                        mModel.send_autopilot_version();
                        break;
                    case MAV_CMD_VIDEO_START_CAPTURE:
                        break;
                    case MAV_CMD_VIDEO_STOP_CAPTURE:
                        break;
                    case MAV_CMD_DO_DIGICAM_CONTROL:
                        // DEPRECATED but still used by QGC
                        mModel.takePhoto();
                        break;
                    case MAV_CMD_MISSION_START:
                        mModel.startWaypointMission();
                        break;
                }
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
             * These messages are used when GCS requests params from MAV  *
             **************************************************************/

            case MAVLINK_MSG_ID_PARAM_REQUEST_LIST:
                mModel.send_all_params();
                break;

            case MAVLINK_MSG_ID_PARAM_REQUEST_READ:
                msg_param_request_read msg_param = (msg_param_request_read) msg;
                //String paramStr = msg_param.getParam_Id();
                //parent.logMessageFromGCS("***" + paramStr);
                mModel.send_param(msg_param.param_index);
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
                generateNewMission();
                //mModel.getMissionControl().getWaypointMissionOperator().getLoadedMission().getWaypointList().clear();
                msg_mission_count msg_count = (msg_mission_count) msg;

                if (mModel.getSystemId() != msg_count.target_system) {
                    return;
                }

                mNumGCSWaypoints = msg_count.count;
                wpState = WP_STATE_REQ_WP;
                mMissionItemList = new ArrayList<msg_mission_item>();
                mModel.request_mission_item(0);
                break;

            case MAVLINK_MSG_ID_MISSION_ITEM:
                msg_mission_item msg_item = (msg_mission_item) msg;

                if (mModel.getSystemId() != msg_item.target_system) {
                    return;
                }
                mMissionItemList.add(msg_item);

                // We are done fetching a complete mission from the GCS...
                if (msg_item.seq == mNumGCSWaypoints - 1) {
                    wpState = WP_STATE_INACTIVE;
                    finalizeNewMission();
                    mModel.send_mission_ack();
                } else {
                    mModel.request_mission_item((msg_item.seq + 1));
                }
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
                mModel.getWaypointMissionOperator().getLoadedMission().getWaypointList().clear();
                break;
        }

    }

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
                autoFlightSpeed(5.0f).
                maxFlightSpeed(15.0f).
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
                case MAV_CMD_NAV_TAKEOFF:
//                    parent.logMessageDJI("P1 = " + m.param1);
//                    parent.logMessageDJI("P2 = " + m.param2);
//                    parent.logMessageDJI("P3 = " + m.param3);
//                    parent.logMessageDJI("P4 = " + m.param4);
//                    parent.logMessageDJI("x = " + m.x);
//                    parent.logMessageDJI("y = " + m.y);
//                    parent.logMessageDJI("z = " + m.z);
                    break;
                case MAV_CMD.MAV_CMD_NAV_WAYPOINT:
                    if (isHome) {
//                        homeValue = m.z;
                        isHome = false;
                    } else {
                        if ((m.z) > 500) {
//                            m.z = 500;
                            parent.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    NotificationHandler.notifyAlert(parent, TYPE_WAYPOINT_MAX_ALTITUDE,
                                            null, null);
                                }
                            });
                            stopUpload = true;
                            break waypoint_loop;

                        } else if ((m.z) < -200) {
//                            m.z = -200;
                            parent.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    NotificationHandler.notifyAlert(parent, TYPE_WAYPOINT_MIN_ALTITUDE,
                                            null, null);
                                }
                            });
                            stopUpload = true;
                            break waypoint_loop;
                        }
                        currentWP = new Waypoint(m.x, m.y, m.z); // TODO check altitude conversion

                        if (m.command == MAV_CMD.MAV_CMD_NAV_WAYPOINT) {
                            if (m.param1 > 0)
                                currentWP.addAction(new WaypointAction(WaypointActionType.STAY, (int) m.param1 * 1000));
                        }

                        if (curvedFlightPath) {
                            currentWP.cornerRadiusInMeters = flightPathRadius;
                        }

                        dji_wps.add(currentWP);
//                        parent.logMessageDJI("Waypoint: " + m.x + ", " + m.y + " at " + m.z + "m");
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
                case MAV_CMD_DO_SET_CAM_TRIGG_DIST:

                    if (!triggerDistanceEnabled) {
                        if (m.param1 != 0) {
                            triggerDistanceEnabled = true;
                            triggerDistance = m.param1;
                        } else {
                            triggerDistance = 0;
                        }
                    }
                    break;
                case MAV_CMD_NAV_RETURN_TO_LAUNCH:
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

            mBuilder.waypointList(correctedWps).waypointCount(correctedWps.size());
            WaypointMission builtMission = mBuilder.build();
            mModel.setWaypointMission(builtMission);
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

        // No need for intermediate waypoints if only 0 or 1 waypoints
        if (wpIn.size() < 2)
            return wpIn;

        ArrayList<Waypoint> wpOut = new ArrayList<>();
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
