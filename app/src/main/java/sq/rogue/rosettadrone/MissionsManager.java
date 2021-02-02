package sq.rogue.rosettadrone;

import com.MAVLink.common.msg_mission_item;
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

import static com.MAVLink.enums.MAV_CMD.MAV_CMD_DO_SET_CAM_TRIGG_DIST;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_MISSION_START;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_LAND;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_RETURN_TO_LAUNCH;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_TAKEOFF;
import static com.MAVLink.enums.MAV_CMD.MAV_CMD_NAV_WAYPOINT;
import static sq.rogue.rosettadrone.util.TYPE_WAYPOINT_DISTANCE;
import static sq.rogue.rosettadrone.util.TYPE_WAYPOINT_MAX_ALTITUDE;
import static sq.rogue.rosettadrone.util.TYPE_WAYPOINT_MAX_SPEED;
import static sq.rogue.rosettadrone.util.TYPE_WAYPOINT_MIN_ALTITUDE;
import static sq.rogue.rosettadrone.util.TYPE_WAYPOINT_MIN_SPEED;
import static sq.rogue.rosettadrone.util.safeSleep;

public class MissionsManager {
    private MainActivity parent;
    private DroneModel mModel;
    private int wpState = 0;
    private int mNumGCSWaypoints;
    private ArrayList<msg_mission_item> mMissionItemList;
    private WaypointMission.Builder mBuilder;
    private int WP_STATE_INACTIVE;
    private int WP_STATE_REQ_COUNT;
    private int WP_STATE_REQ_WP;
    private int MAX_WAYPOINT_DISTANCE;
    private boolean isHome;
    private float flightPathRadius;
    public boolean curvedFlightPath = true;

    public MissionsManager(MainActivity parent,
                           DroneModel mModel,
                           int mNumGCSWaypoints,
                           int wpState,
                           ArrayList<msg_mission_item> mMissionItemList,
                           WaypointMission.Builder mBuilder,
                           int WP_STATE_INACTIVE,
                           int WP_STATE_REQ_COUNT,
                           int WP_STATE_REQ_WP,
                           boolean isHome,
                           float flightPathRadius,
                           boolean curvedFlightPath,
                           int MAX_WAYPOINT_DISTANCE) {
        this.parent = parent;
        this.mModel = mModel;
        this.mNumGCSWaypoints = mNumGCSWaypoints;
        this.wpState = wpState;
        this.mMissionItemList = mMissionItemList;
        this.mBuilder = mBuilder;
        this.WP_STATE_INACTIVE = WP_STATE_INACTIVE;
        this.WP_STATE_REQ_COUNT = WP_STATE_REQ_COUNT;
        this.WP_STATE_REQ_WP = WP_STATE_REQ_WP;
        this.isHome = isHome;
        this.flightPathRadius = flightPathRadius;
        this.curvedFlightPath = curvedFlightPath;
        this.MAX_WAYPOINT_DISTANCE = MAX_WAYPOINT_DISTANCE;

    }

    public void manage_mission(msg_mission_item msg){
        switch (((msg_mission_item) msg).command) {
            case MAV_CMD_NAV_WAYPOINT:
                parent.logMessageDJI("Received MAV: MAV_GOTO_HOLD_AT_SPECIFIED_POSITION");

                msg_mission_item msg_item = (msg_mission_item) msg;
                parent.logMessageDJI("Seq: " + msg_item.seq);
                parent.logMessageDJI("mNumGCSWaypoints - 1: " + (mNumGCSWaypoints - 1));


                if (mModel.getSystemId() != msg_item.target_system) {
                    break;
                }

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
                mModel.echoLoadedMission();
                break;
            case MAV_CMD_MISSION_START:
                parent.logMessageDJI("Received MAV: MAV_CMD_MISSION_START (breaks)");
            case MAV_CMD_NAV_LAND:
                parent.logMessageDJI("Received MAV: MAV_CMD_NAV_LAND (breaks)");
            case MAV_CMD_NAV_TAKEOFF:
                parent.logMessageDJI("Received MAV: MAV_CMD_NAV_TAKEOFF (breaks)");
            case MAV_CMD_NAV_RETURN_TO_LAUNCH:
                parent.logMessageDJI("Received MAV: MAV_CMD_NAV_RETURN_TO_LAUNCH (breaks)");
                break;
            default:
                parent.logMessageDJI("MAVLINK_MSG_ID_MISSION_ITEM unkown mission id: " + ((msg_mission_item) msg).command);
                parent.logMessageDJI("" + ((msg_mission_item) msg).toString());
        }
    }

    public void generateNewMission() {
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

    public void finalizeNewMission() {
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
                    parent.logMessageDJI("Received MAV: MAV_CMD_NAV_TAKEOFF (breaks)");
                    break;

                case MAV_CMD.MAV_CMD_NAV_WAYPOINT:
                    parent.logMessageDJI("Received MAV: MAV_CMD_NAV_WAYPOINT");
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
                }
                break;
                case MAV_CMD.MAV_CMD_DO_CHANGE_SPEED:
                    parent.logMessageDJI("Received MAV: MAV_CMD_DO_CHANGE_SPEED");
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
                    parent.logMessageDJI("Received MAV: MAV_CMD_DO_MOUNT_CONTROL");
                    currentWP.addAction(new WaypointAction(WaypointActionType.GIMBAL_PITCH, (int) m.param1));
                    parent.logMessageDJI("Set gimbal pitch: " + m.param1);
                    break;

                case MAV_CMD.MAV_CMD_IMAGE_START_CAPTURE:
                    parent.logMessageDJI("Received MAV: MAV_CMD_IMAGE_START_CAPTURE");
                    if (currentWP != null) {
                        currentWP.addAction(new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 0));
                        parent.logMessageDJI("Take photo");

                        // --

                    } else {
                        parent.logMessageDJI("currentWP == null");
                    }
                    break;

                case MAV_CMD.MAV_CMD_DO_SET_CAM_TRIGG_DIST:
                    parent.logMessageDJI("Received MAV: MAV_CMD_DO_SET_CAM_TRIGG_DIST");

                    if (!triggerDistanceEnabled) {
                        if (m.param1 != 0) {
                            triggerDistanceEnabled = true;
                            triggerDistance = m.param1;
                        } else {
                            triggerDistance = 0;
                        }
                    }
                    mModel.send_command_ack(MAV_CMD_DO_SET_CAM_TRIGG_DIST, MAV_RESULT.MAV_RESULT_FAILED);
                    break;

                case MAV_CMD.MAV_CMD_NAV_RETURN_TO_LAUNCH:
                    parent.logMessageDJI("Received MAV: MAV_CMD_NAV_RETURN_TO_LAUNCH");
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
