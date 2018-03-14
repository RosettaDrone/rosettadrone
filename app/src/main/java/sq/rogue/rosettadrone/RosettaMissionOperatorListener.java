package sq.rogue.rosettadrone;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import dji.common.error.DJIError;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;

class RosettaMissionOperatorListener implements WaypointMissionOperatorListener {
    private String TAG = "RosettaDrone";
    private int WAYPOINT_COUNT = 0;

    @Override
    public void onDownloadUpdate(@NonNull WaypointMissionDownloadEvent waypointMissionDownloadEvent) {
        // Example of Download Listener
        if (waypointMissionDownloadEvent.getProgress() != null
                && waypointMissionDownloadEvent.getProgress().isSummaryDownloaded
                && waypointMissionDownloadEvent.getProgress().downloadedWaypointIndex == (WAYPOINT_COUNT - 1)) {
            Log.d(TAG, "Download successful!");
        }
        updateWaypointMissionState();
    }

    @Override
    public void onUploadUpdate(@NonNull WaypointMissionUploadEvent waypointMissionUploadEvent) {
        // Example of Upload Listener
        if (waypointMissionUploadEvent.getProgress() != null
                && waypointMissionUploadEvent.getProgress().isSummaryUploaded
                && waypointMissionUploadEvent.getProgress().uploadedWaypointIndex == (WAYPOINT_COUNT - 1)) {
            Log.d(TAG, "Upload successful!");
        }
        updateWaypointMissionState();
    }

    @Override
    public void onExecutionUpdate(@NonNull WaypointMissionExecutionEvent waypointMissionExecutionEvent) {
        // Example of Execution Listener
        Log.d(TAG,
                (waypointMissionExecutionEvent.getPreviousState() == null
                        ? ""
                        : waypointMissionExecutionEvent.getPreviousState().getName())
                        + ", "
                        + waypointMissionExecutionEvent.getCurrentState().getName()
                        + (waypointMissionExecutionEvent.getProgress() == null
                        ? ""
                        : waypointMissionExecutionEvent.getProgress().targetWaypointIndex));
        updateWaypointMissionState();
    }

    @Override
    public void onExecutionStart() {
        Log.d(TAG, "Execution started!");
        updateWaypointMissionState();
    }

    @Override
    public void onExecutionFinish(@Nullable DJIError djiError) {
        Log.d(TAG, "Execution finished!");
        updateWaypointMissionState();
    }

    private void updateWaypointMissionState() {
    }
};