package sq.rogue.rosettadrone;

import android.util.Log;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.Nullable;
import dji.common.error.DJIError;
import dji.common.gimbal.Attitude;
import dji.common.gimbal.Rotation;
import dji.common.mission.hotpoint.HotpointHeading;
import dji.common.mission.hotpoint.HotpointMission;
import dji.common.mission.hotpoint.HotpointStartPoint;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionGotoWaypointMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.model.LocationCoordinate2D;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.Triggerable;
import dji.sdk.mission.timeline.TimelineElement;
import dji.sdk.mission.timeline.TimelineEvent;
import dji.sdk.mission.timeline.TimelineMission;
import dji.sdk.mission.timeline.actions.GimbalAttitudeAction;
import dji.sdk.mission.timeline.actions.GoHomeAction;
import dji.sdk.mission.timeline.actions.GoToAction;
import dji.sdk.mission.timeline.actions.HotpointAction;
import dji.sdk.mission.timeline.actions.TakeOffAction;
import dji.sdk.mission.timeline.triggers.AircraftLandedTrigger;
import dji.sdk.mission.timeline.triggers.BatteryPowerLevelTrigger;
import dji.sdk.mission.timeline.triggers.Trigger;
import dji.sdk.mission.timeline.triggers.TriggerEvent;
import dji.sdk.mission.timeline.triggers.WaypointReachedTrigger;
import sq.rogue.rosettadrone.settings.GeneralUtils;

/**
 * Class for Timeline MissionControl.
 */
public class TimeLineMissionControlView {

    private final String TAG = TimeLineMissionControlView.class.getSimpleName();

    private MissionControl missionControl;
    private FlightController flightController;
    private TimelineEvent preEvent;
    private TimelineElement preElement;
    private DJIError preError;

    protected TextView timelineInfoTV;
    protected TextView runningInfoTV;

    protected double homeLatitude = 181;
    protected double homeLongitude = 181;


    private void setRunningResultToText(final String s) {
        Log.d(TAG, s);
    }

    private void setTimelinePlanToText(final String s) {
        Log.d(TAG, s);
    }

    /**
     * Demo on BatteryPowerLevelTrigger.  Once the batter remaining power is equal or less than the value,
     * the trigger's action will be called.
     *
     * @param triggerTarget which can be any action object or timeline object.
     */
    private void addBatteryPowerLevelTrigger(Triggerable triggerTarget) {
        float value = 20f;
        BatteryPowerLevelTrigger trigger = new BatteryPowerLevelTrigger();
        trigger.setPowerPercentageTriggerValue(value);
        addTrigger(trigger, triggerTarget, " at level " + value);
    }

    /**
     * Demo on WaypointReachedTrigger.  Once the expected waypoint is reached in the waypoint mission execution process,
     * this trigger's action will be called. If user has some special things to do for this waypoint, the code can be put
     * in this trigger action method.
     *
     * @param triggerTarget
     */
    private void addWaypointReachedTrigger(Triggerable triggerTarget) {
        int value = 1;
        WaypointReachedTrigger trigger = new WaypointReachedTrigger();
        trigger.setWaypointIndex(value);
        addTrigger(trigger, triggerTarget, " at index " + value);
    }

    /**
     * Demo on AircraftLandedTrigger. Once the aircraft is landed, this trigger action will be called if the timeline is
     * not finished yet.
     *
     * @param triggerTarget
     */
    private void addAircraftLandedTrigger(Triggerable triggerTarget) {
        AircraftLandedTrigger trigger = new AircraftLandedTrigger();
        addTrigger(trigger, triggerTarget, "");
    }

    private Trigger.Listener triggerListener = new Trigger.Listener() {
        @Override
        public void onEvent(Trigger trigger, TriggerEvent event, @Nullable DJIError error) {
            setRunningResultToText("Trigger " + trigger.getClass().getSimpleName() + " event is " + event.name() + (error == null ? " " : error.getDescription()));
        }
    };

    private void initTrigger(final Trigger trigger) {
        trigger.addListener(triggerListener);
        trigger.setAction(new Trigger.Action() {
            @Override
            public void onCall() {
                setRunningResultToText("Trigger " + trigger.getClass().getSimpleName() + " Action method onCall() is invoked");
            }
        });
    }

    private void addTrigger(Trigger trigger, Triggerable triggerTarget, String additionalComment) {

        if (triggerTarget != null) {

            initTrigger(trigger);
            List<Trigger> triggers = triggerTarget.getTriggers();
            if (triggers == null) {
                triggers = new ArrayList<>();
            }

            triggers.add(trigger);
            triggerTarget.setTriggers(triggers);

            setTimelinePlanToText(triggerTarget.getClass().getSimpleName()
                    + " Trigger "
                    + triggerTarget.getTriggers().size()
                    + ") "
                    + trigger.getClass().getSimpleName()
                    + additionalComment);
        }
    }

    void initTimeline() {
        if (!GeneralUtils.checkGpsCoordinate(homeLatitude, homeLongitude)) {
            Log.d(TAG, "No home point!!!");
            return;
        }

        List<TimelineElement> elements = new ArrayList<>();

        missionControl = MissionControl.getInstance();
        final TimelineEvent preEvent = null;
        MissionControl.Listener listener = (element, event, error) -> updateTimelineStatus(element, event, error);

        //Step 1: takeoff from the ground
        setTimelinePlanToText("Step 1: takeoff from the ground");
        elements.add(new TakeOffAction());

        //Step 2: reset the gimbal to horizontal angle in 2 seconds.
        setTimelinePlanToText("Step 2: set the gimbal pitch -30 angle in 2 seconds");
        Attitude attitude = new Attitude(20, Rotation.NO_ROTATION, Rotation.NO_ROTATION);
        GimbalAttitudeAction gimbalAction = new GimbalAttitudeAction(attitude);
        gimbalAction.setCompletionTime(2);
        elements.add(gimbalAction);

        //Step 3: Go 10 meters from home point
        setTimelinePlanToText("Step 3: Vlimbe 10 meters from home point");
        elements.add(new GoToAction(new LocationCoordinate2D(homeLatitude, homeLongitude), 10));
/*
        //Step 4: shoot 3 photos with 2 seconds interval between each
        setTimelinePlanToText("Step 4: shoot 3 photos with 2 seconds interval between each");
        elements.add(ShootPhotoAction.newShootIntervalPhotoAction(3,2));

        //Step 5: shoot a single photo
        setTimelinePlanToText("Step 5: shoot a single photo");
        elements.add(ShootPhotoAction.newShootSinglePhotoAction());

        //Step 6: start recording video
        setTimelinePlanToText("Step 6: start recording video");
        elements.add(RecordVideoAction.newStartRecordVideoAction());
*/
        //Step 7: start a waypoint mission while the aircraft is still recording the video
        setTimelinePlanToText("Step 7: start a waypoint mission while the aircraft is still recording the video");
        TimelineElement waypointMission = TimelineMission.elementFromWaypointMission(initTestingWaypointMission());
        elements.add(waypointMission);
        addWaypointReachedTrigger(waypointMission);
/*
        //Step 8: stop the recording when the waypoint mission is finished
        setTimelinePlanToText("Step 8: stop the recording when the waypoint mission is finished");
        elements.add(RecordVideoAction.newStopRecordVideoAction());

        //Step 9: shoot a single photo
        setTimelinePlanToText("Step 9: shoot a single photo");
        elements.add(ShootPhotoAction.newShootSinglePhotoAction());
*/
        //Step 10: start a hotpoint mission
        setTimelinePlanToText("Step 10: start a hotpoint mission to surround 360 degree");
        HotpointMission hotpointMission = new HotpointMission();
        hotpointMission.setHotpoint(new LocationCoordinate2D(homeLatitude, homeLongitude));
        hotpointMission.setAltitude(10);
        hotpointMission.setRadius(10);
        hotpointMission.setAngularVelocity(10);
        HotpointStartPoint startPoint = HotpointStartPoint.NEAREST;
        hotpointMission.setStartPoint(startPoint);
        HotpointHeading heading = HotpointHeading.TOWARDS_HOT_POINT;
        hotpointMission.setHeading(heading);
        elements.add(new HotpointAction(hotpointMission, 360));

        //Step 11: go back home
        setTimelinePlanToText("Step 11: go back home");
        elements.add(new GoHomeAction());

        //Step 12: restore gimbal attitude
        //This last action will delay the timeline to finish after land on ground, which will
        //make sure the AircraftLandedTrigger will be triggered.
        setTimelinePlanToText("Step 2: set the gimbal pitch -30 angle in 2 seconds");
        attitude = new Attitude(Rotation.NO_ROTATION, Rotation.NO_ROTATION, 10);
        gimbalAction = new GimbalAttitudeAction(attitude);
        gimbalAction.setCompletionTime(2);
        elements.add(gimbalAction);

        addAircraftLandedTrigger(missionControl);
        addBatteryPowerLevelTrigger(missionControl);

        if (missionControl.scheduledCount() > 0) {
            missionControl.unscheduleEverything();
            missionControl.removeAllListeners();
        }

        missionControl.scheduleElements(elements);
        missionControl.addListener(listener);
    }

    // Takeoff and climbe to "alt" located at "lat,lon" rotating "yaw" degrees relative...
    void TimeLinetakeOff(double lat, double lon, float alt, float yaw) {
        List<TimelineElement> elements = new ArrayList<>();

        missionControl = MissionControl.getInstance();
        final TimelineEvent preEvent = null;
        MissionControl.Listener listener = (element, event, error) -> updateTimelineStatus(element, event, error);

        setTimelinePlanToText("Step 1: takeoff from the ground");
        elements.add(new TakeOffAction());

        setTimelinePlanToText("Step 2: climb x meters from home point");
        setTimelinePlanToText("Lat: " + lat + " Lon: " + lon + " Alt: " + alt);
        elements.add(new GoToAction(new LocationCoordinate2D(lat, lon), alt / (float) 1000.0));

        if (missionControl.scheduledCount() > 0) {
            missionControl.unscheduleEverything();
            missionControl.removeAllListeners();
        }

        missionControl.scheduleElements(elements);
        missionControl.addListener(listener);
    }

    // Takeoff and climbe to "alt" located at "lat,lon" rotating "yaw" degrees relative...
    void TimeLineGoTo(double lat, double lon, float alt, float speed, float yaw) {
        List<TimelineElement> elements = new ArrayList<>();

        missionControl = MissionControl.getInstance();
        final TimelineEvent preEvent = null;
        MissionControl.Listener listener = (element, event, error) -> updateTimelineStatus(element, event, error);

        setTimelinePlanToText("Step 1: takeoff from the ground");
        setTimelinePlanToText("Lat: " + lat + " Lon: " + lon + " Alt: " + alt);
        elements.add(new GoToAction(new LocationCoordinate2D(lat, lon), alt ));

        if (missionControl.scheduledCount() > 0) {
            missionControl.unscheduleEverything();
            missionControl.removeAllListeners();
        }

        missionControl.scheduleElements(elements);
        missionControl.addListener(listener);
    }

    private void updateTimelineStatus(@Nullable TimelineElement element, TimelineEvent event, DJIError error) {

        if (element == preElement && event == preEvent && error == preError) {
            return;
        }

        if (element != null) {
            if (element instanceof TimelineMission) {
                setRunningResultToText(((TimelineMission) element).getMissionObject().getClass().getSimpleName()
                        + " event is "
                        + event.toString()
                        + " "
                        + (error == null ? "" : error.getDescription()));
            } else {
                setRunningResultToText(element.getClass().getSimpleName()
                        + " event is "
                        + event.toString()
                        + " "
                        + (error == null ? "" : error.getDescription()));
            }
        } else {
            setRunningResultToText("Timeline Event is " + event.toString() + " " + (error == null
                    ? ""
                    : "Failed:"
                    + error.getDescription()));

            if (event.toString() == "FINISHED") {

            }
        }

        preEvent = event;
        preElement = element;
        preError = error;
    }

    private WaypointMission initTestingWaypointMission() {
        if (!GeneralUtils.checkGpsCoordinate(homeLatitude, homeLongitude)) {
            Log.d(TAG, "No home point!!!");
            return null;
        }

        WaypointMission.Builder waypointMissionBuilder = new WaypointMission.Builder()
                .autoFlightSpeed(2f)
                .maxFlightSpeed(5f)
                .setExitMissionOnRCSignalLostEnabled(false)
                .finishedAction(WaypointMissionFinishedAction.NO_ACTION)
                .flightPathMode(WaypointMissionFlightPathMode.NORMAL)
                .gotoFirstWaypointMode(WaypointMissionGotoWaypointMode.SAFELY)
                .headingMode(WaypointMissionHeadingMode.AUTO)
                .repeatTimes(1);
        ;
        List<Waypoint> waypoints = new LinkedList<>();

        Waypoint northPoint = new Waypoint(homeLatitude + 10 * GeneralUtils.ONE_METER_OFFSET, homeLongitude, 10f);
        Waypoint eastPoint = new Waypoint(homeLatitude, homeLongitude + 10 * GeneralUtils.calcLongitudeOffset(homeLatitude), 15f);
        Waypoint southPoint = new Waypoint(homeLatitude - 10 * GeneralUtils.ONE_METER_OFFSET, homeLongitude, 10f);
        Waypoint westPoint = new Waypoint(homeLatitude, homeLongitude - 10 * GeneralUtils.calcLongitudeOffset(homeLatitude), 15f);

//        northPoint.addAction(new WaypointAction(WaypointActionType.RESET_GIMBAL_YAW, 0));
        northPoint.addAction(new WaypointAction(WaypointActionType.GIMBAL_PITCH, -60));
        southPoint.addAction(new WaypointAction(WaypointActionType.ROTATE_AIRCRAFT, 60));

        waypoints.add(northPoint);
        waypoints.add(eastPoint);
        waypoints.add(southPoint);
        waypoints.add(westPoint);

        waypointMissionBuilder.waypointList(waypoints).waypointCount(waypoints.size());
        return waypointMissionBuilder.build();
    }

    void startTimeline() {
        if (MissionControl.getInstance().scheduledCount() > 0) {
            MissionControl.getInstance().startTimeline();
        } else {
            Log.d(TAG, "Wait for takeoff...");
        }
    }

    void stopTimeline() {
        MissionControl.getInstance().stopTimeline();
    }

    void pauseTimeline() {
        MissionControl.getInstance().pauseTimeline();
    }


    void resumeTimeline() {
        MissionControl.getInstance().resumeTimeline();
    }

    void cleanTimelineDataAndLog() {
        if (missionControl.scheduledCount() > 0) {
            missionControl.unscheduleEverything();
            missionControl.removeAllListeners();
        }
        runningInfoTV.setText("");
        timelineInfoTV.setText("");
    }


    void addHome(double lat, double lon) {
        homeLatitude = lat;
        homeLongitude = lon;

    }
}