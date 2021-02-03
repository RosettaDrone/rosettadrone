package sq.rogue.rosettadrone.settings;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import dji.common.error.DJIError;
import dji.common.error.DJIWaypointV2Error;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.RTKState;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypointv2.Action.WaypointV2Action;
import dji.common.mission.waypointv2.WaypointV2;
import dji.common.mission.waypointv2.WaypointV2Mission;
import dji.common.mission.waypointv2.WaypointV2MissionDownloadEvent;
import dji.common.mission.waypointv2.WaypointV2MissionExecutionEvent;
import dji.common.mission.waypointv2.WaypointV2MissionState;
import dji.common.mission.waypointv2.WaypointV2MissionTypes;
import dji.common.mission.waypointv2.WaypointV2MissionUploadEvent;
import dji.common.model.LocationCoordinate2D;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.flightcontroller.RTK;
import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointV2MissionOperator;
import dji.sdk.mission.waypoint.WaypointV2MissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;
import sq.rogue.rosettadrone.DJISimulatorApplication;
import sq.rogue.rosettadrone.R;
import sq.rogue.rosettadrone.RDApplication;

public class Waypoint2Activity extends FragmentActivity implements View.OnClickListener, GoogleMap.OnMapClickListener, OnMapReadyCallback {

    protected static final String TAG = "Waypoint2Activity";

    private GoogleMap gMap;

    private Button locate, add, clear;
    private Button config, upload, start, stop;

    private boolean isAdd = false;

    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;

    private float altitude = 100.0f;
    private float mSpeed = 10.0f;

    private List<WaypointV2> waypointList = new ArrayList<>();

    public static WaypointV2Mission.Builder waypointMissionBuilder;

    private FlightController mFlightController;
    private WaypointV2MissionOperator instance;
    private WaypointV2MissionTypes.MissionFinishedAction mFinishedAction = WaypointV2MissionTypes.MissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;
    private WaypointV2MissionTypes.MissionGotoWaypointMode firstMode = WaypointV2MissionTypes.MissionGotoWaypointMode.SAFELY;
    private WaypointV2ActionDialog mActionDialog;
    private List<WaypointV2Action> v2Actions;
    private boolean canUploadAction;
    private boolean canStartMission;
    private double mHomeLat = 181;
    private double mHomeLng = 181;
    private double mAircraftLat = 181;
    private double mAircraftLng = 181;
    private boolean useRTKLocation = false;
    private RTK mRtk;
    private float droneHeading;
    private float droneHeight;

    private TextView logTv;

    @Override
    protected void onResume() {
        super.onResume();
        initFlightController();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mReceiver);
        removeListener();
        super.onDestroy();
    }

    /**
     * @Description : RETURN Button RESPONSE FUNCTION
     */
    public void onReturn(View view) {
        Log.d(TAG, "onReturn");
        this.finish();
    }

    private void setResultToToast(final String string) {
        Waypoint2Activity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(Waypoint2Activity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initUI() {

        locate = findViewById(R.id.locate);
        add = findViewById(R.id.add);
        clear = findViewById(R.id.clear);
        config = findViewById(R.id.config);
        upload = findViewById(R.id.upload);
        start = findViewById(R.id.start);
        stop = findViewById(R.id.stop);
        logTv = findViewById(R.id.tv_log);

        locate.setOnClickListener(this);
        add.setOnClickListener(this);
        clear.setOnClickListener(this);
        config.setOnClickListener(this);
        upload.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);

        findViewById(R.id.btn_add_action).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isAdd) {
                    Toast.makeText(v.getContext(), "Adding waypoint!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (waypointMissionBuilder == null || waypointMissionBuilder.getWaypointCount() == 0) {
                    Toast.makeText(v.getContext(), "Please add waypoint first!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mActionDialog == null) {
                    mActionDialog = new WaypointV2ActionDialog();
                    mActionDialog.setActionCallback(actions -> {
                        v2Actions = actions;
                        Log.d("v2_action", "originSize=" + actions.size());
                    });
                }
                mActionDialog.setSize(waypointMissionBuilder.getWaypointCount());
                mActionDialog.show(getSupportFragmentManager(), "add_action");
            }
        });

        findViewById(R.id.btn_upload_action).setOnClickListener(this);
    }

    private void initMapView() {

        if (gMap != null) {
            gMap.setOnMapClickListener(this);// add the listener for click for amap object
        }

        gMap.moveCamera(CameraUpdateFactory.zoomTo(18));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_waypoint2);

        IntentFilter filter = new IntentFilter();
        filter.addAction(DJISimulatorApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        if (getWaypointMissionOperator() == null) {
            setResultToToast("Not support Waypoint2.0");
            return;
        }

        initUI();
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        addListener();
        onProductConnectionChange();
        setDroneLocationListener();

    }

    private void setDroneLocationListener() {
        if (mFlightController == null) {
            Tools.showToast(this, "FC is null, comeback later!");
            return;
        }
        if (useRTKLocation) {
            if ((mRtk = mFlightController.getRTK()) == null) {
                Tools.showToast(this, "Not support RTK, use Flyc GPS!");
                mFlightController.setStateCallback(new FlightControllerState.Callback() {
                    @Override
                    public void onUpdate(FlightControllerState state) {
                        mHomeLat = state.getHomeLocation().getLatitude();
                        mHomeLng = state.getHomeLocation().getLongitude();
                        mAircraftLat = state.getAircraftLocation().getLatitude();
                        mAircraftLng = state.getAircraftLocation().getLongitude();
                        droneHeading = state.getAircraftHeadDirection();
                        droneHeight = state.getAircraftLocation().getAltitude();
                    }
                });
            } else {
                mFlightController.setStateCallback((new FlightControllerState.Callback() {
                    @Override
                    public void onUpdate(FlightControllerState state) {
                        mHomeLat = state.getHomeLocation().getLatitude();
                        mHomeLng = state.getHomeLocation().getLongitude();
                    }
                }));
                mRtk.setStateCallback(new RTKState.Callback() {
                    @Override
                    public void onUpdate(@NonNull RTKState state) {
                        mAircraftLat = state.getFusionMobileStationLocation().getLatitude();
                        mAircraftLng = state.getFusionMobileStationLocation().getLongitude();
                        droneHeading = state.getFusionHeading();
                        droneHeight = state.getFusionMobileStationAltitude();
                        updateDroneLocation();
                    }
                });
            }
        } else {
            if ((mRtk = mFlightController.getRTK()) != null) {
                mFlightController.getRTK().setStateCallback(null);
            }
            mFlightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(FlightControllerState state) {
                    mHomeLat = state.getHomeLocation().getLatitude();
                    mHomeLng = state.getHomeLocation().getLongitude();
                    mAircraftLat = state.getAircraftLocation().getLatitude();
                    mAircraftLng = state.getAircraftLocation().getLongitude();
                    droneHeading = state.getAircraftHeadDirection();
                    droneHeight = state.getAircraftLocation().getAltitude();
                    updateDroneLocation();
                }
            });
        }
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };

    private void onProductConnectionChange() {
        initFlightController();
        loginAccount();
    }

    private void loginAccount() {

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e(TAG, "Login Success");
                    }

                    @Override
                    public void onFailure(DJIError error) {
                        setResultToToast("Login Error:"
                                + error.getDescription());
                    }
                });
    }

    private void initFlightController() {

        BaseProduct product = RDApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
            }
        }
    }

    //Add Listener for WaypointMissionOperator
    private void addListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().addWaypointEventListener(eventNotificationListener);
        }
    }

    private void removeListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().removeWaypointListener(eventNotificationListener);
        }
    }

    private WaypointV2MissionOperatorListener eventNotificationListener = new WaypointV2MissionOperatorListener() {

        @Override
        public void onDownloadUpdate(WaypointV2MissionDownloadEvent waypointV2MissionDownloadEvent) {

        }

        @Override
        public void onUploadUpdate(WaypointV2MissionUploadEvent uploadEvent) {
            if (uploadEvent.getCurrentState() == WaypointV2MissionState.UPLOADING
                    || (uploadEvent.getError() != null)) {
                // deal with the progress or the error info
            }

            if (uploadEvent.getCurrentState() == WaypointV2MissionState.READY_TO_EXECUTE) {
                // Can upload actions in it.
                // getWaypointMissionOperator().uploadWaypointActions();
                canUploadAction = true;
            }
            if (uploadEvent.getPreviousState() == WaypointV2MissionState.UPLOADING
                    && uploadEvent.getCurrentState() == WaypointV2MissionState.READY_TO_EXECUTE) {
                // upload complete, can start mission
                // getWaypointMissionOperator().startMission();
                canStartMission = true;
            }

            logTv.post(new Runnable() {
                @Override
                public void run() {
                    logTv.setText("cur_state:" + uploadEvent.getCurrentState().name());
                }
            });
        }

        @Override
        public void onExecutionUpdate(WaypointV2MissionExecutionEvent waypointV2MissionExecutionEvent) {

        }

        @Override
        public void onExecutionStart() {

        }

        @Override
        public void onExecutionFinish(DJIWaypointV2Error djiWaypointV2Error) {

        }

        @Override
        public void onExecutionStopped() {

        }
    };

    public WaypointV2MissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            MissionControl missionControl = DJISDKManager.getInstance().getMissionControl();
            if (missionControl != null) {
                instance = missionControl.getWaypointMissionV2Operator();
            }
        }
        return instance;
    }

    @Override
    public void onMapClick(LatLng point) {
        if (isAdd == true) {
            markWaypoint(point);
            WaypointV2 mWaypoint = new WaypointV2.Builder()
                    .setAltitude(altitude)
                    .setCoordinate(new LocationCoordinate2D(point.latitude, point.longitude))
                    .build();
            //Add Waypoints to Waypoint arraylist;
            if (waypointMissionBuilder != null) {
                waypointList.add(mWaypoint);
                waypointMissionBuilder.addWaypoint(mWaypoint);
            } else {
                waypointMissionBuilder = new WaypointV2Mission.Builder();
                waypointList.add(mWaypoint);
                waypointMissionBuilder.addWaypoint(mWaypoint);
            }
        } else {
            setResultToToast("Cannot Add Waypoint");
        }
    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    // Update the drone location based on states from MCU.
    private void updateDroneLocation() {

        LatLng pos = new LatLng(mAircraftLat, mAircraftLng);
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_drone));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordination(mAircraftLat, mAircraftLng)) {
                    droneMarker = gMap.addMarker(markerOptions);
                    droneMarker.setRotation(droneHeading * -1.0f);
                }
            }
        });
    }

//    private void updateDroneLocation() {
//        // Set the McuUpdateSateCallBack
//
//        LatLng pos = new LatLng(mAircraftLat, mAircraftLng);
//        final CameraUpdate cu = CameraUpdateFactory.changeLatLng(pos);
//        //Create MarkerOptions object
//        final MarkerOptions markerOptions = new MarkerOptions();
//        markerOptions.position(pos);
//        markerOptions.icon(droneBitmap);
//        markerOptions.anchor(0.5f, 0.5f);
//
//        LatLng posHome = new LatLng(mHomeLat, mHomeLng);
//        final MarkerOptions markerOptionsHomePoint = new MarkerOptions();
//        markerOptionsHomePoint.position(posHome);
//        markerOptionsHomePoint.icon(homePointBitmap);
//        markerOptionsHomePoint.anchor(0.5f, 0.5f);
//
//        final double distance = Helper.distance(mAircraftLat, mAircraftLng, mHomeLat, mHomeLng);
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                if (mAircraftMarker != null) {
//                    mAircraftMarker.remove();
//                }
//                if (Tools.checkGpsCoordination(mAircraftLat, mAircraftLng)) {
//                    mAircraftMarker = gMap.addMarker(markerOptions);
//                    mAircraftMarker.setRotateAngle(droneHeading * -1.0f);
//                    gMap.moveCamera(cu);
//                }
//
//                if (Tools.checkGpsCoordination(mHomeLat, mHomeLng)) {
//                    if (homePointMarker != null) {
//                        homePointMarker.remove();
//                    }
//                    homePointMarker = gMap.addMarker(markerOptionsHomePoint);
//                }
//                distanceView.setText(String.format("distanceToHome:%.2f", distance) + "\n"
//                        + String.format("Heading:%.2f",droneHeading) + "\n"
//                        + String.format("Height:%.2f",droneHeight));
//            }
//        });
//    }

    private void markWaypoint(LatLng point) {
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        Marker marker = gMap.addMarker(markerOptions);
        mMarkers.put(mMarkers.size(), marker);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.locate: {
                updateDroneLocation();
                cameraUpdate(); // Locate the drone's place
                break;
            }
            case R.id.add: {
                enableDisableAdd();
                break;
            }
            case R.id.clear: {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gMap.clear();
                        if (waypointList != null) {
                            waypointList.clear();
                        }
                        if (v2Actions != null) {
                            v2Actions.clear();
                        }
                    }

                });

                waypointMissionBuilder = null;
                updateDroneLocation();
                break;
            }
            case R.id.config: {
                showSettingDialog();
                break;
            }
            case R.id.upload: {
                uploadWayPointMission();
                break;
            }
            case R.id.start: {
                startWaypointMission();
                break;
            }
            case R.id.stop: {
                stopWaypointMission();
                break;
            }
            case R.id.btn_upload_action:
                if (!canUploadAction) {
                    setResultToToast("Can`t Upload action");
                    return;
                }
                if (v2Actions == null || v2Actions.isEmpty()) {
                    setResultToToast("Please Add Actions");
                    return;
                }
                getWaypointMissionOperator().uploadWaypointActions(v2Actions, new CommonCallbacks.CompletionCallback<DJIWaypointV2Error>() {
                    @Override
                    public void onResult(DJIWaypointV2Error djiWaypointV2Error) {
                        if (djiWaypointV2Error == null) {
                            setResultToToast("Upload action success");
                        } else {
                            setResultToToast("Upload action fail:" + djiWaypointV2Error.getDescription());
                        }
                    }
                });
                break;
            default:
                break;
        }
    }

    private void cameraUpdate() {
        LatLng pos = new LatLng(mAircraftLat, mAircraftLng);
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        gMap.moveCamera(cu);

    }

    private void enableDisableAdd() {
        if (isAdd == false) {
            isAdd = true;
            add.setText("Exit");
        } else {
            isAdd = false;
            add.setText("Add");
        }
    }

    private void showSettingDialog() {
        LinearLayout wayPointSettings = (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_waypoint2setting, null);

        final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinished_RG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);
        RadioGroup heading_RG = (RadioGroup) wayPointSettings.findViewById(R.id.heading);
        RadioGroup firstModeRg = wayPointSettings.findViewById(R.id.go_to_first_mode);

        firstModeRg.setOnCheckedChangeListener((group, checkedId) -> {
            switch (checkedId) {
                case R.id.rb_p2p:
                    firstMode = WaypointV2MissionTypes.MissionGotoWaypointMode.POINT_TO_POINT;
                    break;
                case R.id.rb_safely:
                    firstMode = WaypointV2MissionTypes.MissionGotoWaypointMode.SAFELY;
                    break;
            }
        });

        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.lowSpeed) {
                    mSpeed = 3.0f;
                } else if (checkedId == R.id.MidSpeed) {
                    mSpeed = 5.0f;
                } else if (checkedId == R.id.HighSpeed) {
                    mSpeed = 10.0f;
                }
            }

        });

        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select finish action");
                if (checkedId == R.id.finishNone) {
                    mFinishedAction = WaypointV2MissionTypes.MissionFinishedAction.NO_ACTION;
                } else if (checkedId == R.id.finishGoHome) {
                    mFinishedAction = WaypointV2MissionTypes.MissionFinishedAction.GO_HOME;
                } else if (checkedId == R.id.finishAutoLanding) {
                    mFinishedAction = WaypointV2MissionTypes.MissionFinishedAction.AUTO_LAND;
                } else if (checkedId == R.id.finishToFirst) {
                    mFinishedAction = WaypointV2MissionTypes.MissionFinishedAction.GO_FIRST_WAYPOINT;
                }
//                else if (checkedId == R.id.untilStop) {
//                    mFinishedAction = WaypointV2MissionTypes.MissionFinishedAction.CONTINUE_UNTIL_STOP;
//                }
            }
        });

        heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select heading");

                if (checkedId == R.id.headingNext) {
                    mHeadingMode = WaypointMissionHeadingMode.AUTO;
                } else if (checkedId == R.id.headingInitDirec) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_INITIAL_DIRECTION;
                } else if (checkedId == R.id.headingRC) {
                    mHeadingMode = WaypointMissionHeadingMode.CONTROL_BY_REMOTE_CONTROLLER;
                } else if (checkedId == R.id.headingWP) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;
                }
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Finish", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {

                        String altitudeString = wpAltitude_TV.getText().toString();
                        altitude = Integer.parseInt(nulltoIntegerDefalt(altitudeString));
                        Log.e(TAG, "altitude " + altitude);
                        Log.e(TAG, "speed " + mSpeed);
                        Log.e(TAG, "mFinishedAction " + mFinishedAction);
                        Log.e(TAG, "mHeadingMode " + mHeadingMode);
                        configWayPointMission();
                    }

                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }

                })
                .create()
                .show();
    }

    String nulltoIntegerDefalt(String value) {
        if (!isIntValue(value)) {
            value = "0";
        }
        return value;
    }

    boolean isIntValue(String val) {
        try {
            val = val.replace(" ", "");
            Integer.parseInt(val);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private void configWayPointMission() {

        if (waypointMissionBuilder == null) {
//            waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
//                    .headingMode(mHeadingMode)
//                    .autoFlightSpeed(mSpeed)
//                    .maxFlightSpeed(mSpeed)
//                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);
            waypointMissionBuilder = new WaypointV2Mission.Builder();

        }
        waypointMissionBuilder.setFinishedAction(mFinishedAction)
                .setMissionID(new Random().nextInt(65535))
                .setFinishedAction(mFinishedAction)
                .setGotoFirstWaypointMode(firstMode)
                .setMaxFlightSpeed(mSpeed)
                .setAutoFlightSpeed(mSpeed);

        getWaypointMissionOperator().loadMission(waypointMissionBuilder.build(), new CommonCallbacks.CompletionCallback<DJIWaypointV2Error>() {
            @Override
            public void onResult(DJIWaypointV2Error error) {
                if (error == null) {
                    setResultToToast("loadWaypoint succeeded");
                    canUpload = true;
                } else {
                    setResultToToast("loadWaypoint failed " + error.getDescription());
                }

            }
        });


    }

    boolean canUpload = false;

    private void uploadWayPointMission() {
        if (!canUpload) {
            Toast.makeText(this, "please click 'CONFIG' button first", Toast.LENGTH_SHORT).show();
            return;
        }
        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    setResultToToast("Mission upload successfully!");
                } else {
                    setResultToToast("Mission upload failed, error: " + error.getDescription());
                }
            }
        });

    }

    private void startWaypointMission() {

        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }

    private void stopWaypointMission() {

        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
            initMapView();
        }

        LatLng shenzhen = new LatLng(62.5362, 12.9454);
        gMap.addMarker(new MarkerOptions().position(shenzhen).title("Marker in Norway"));
        gMap.moveCamera(CameraUpdateFactory.newLatLng(shenzhen));

    }
}
