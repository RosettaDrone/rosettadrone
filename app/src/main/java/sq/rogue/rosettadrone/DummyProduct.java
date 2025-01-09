/**
 * This class implements a dummy DJI Product to run Rosetta without actually connecting to an aircraft.
 * This facilitates debugging the MAVLink communication, UI, etc.
 * To use, just start Rosetta in TestMode, ie. click 5 times on the logo on the connection screen.
 * This class can be extended to simulate the aircraft, battery, flightcontroller, etc.
 *
 * Author: Christopher Pereira (rosetta@imatronix.com)
 */

package sq.rogue.rosettadrone;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import dji.common.Stick;
import dji.common.battery.BatteryState;
import dji.common.battery.LowVoltageBehavior;
import dji.common.battery.PairingState;
import dji.common.battery.WarningRecord;
import dji.common.error.DJIError;
import dji.common.flightcontroller.Attitude;
import dji.common.flightcontroller.ConnectionFailSafeBehavior;
import dji.common.flightcontroller.ControlGimbalBehavior;
import dji.common.flightcontroller.ControlMode;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.FlightOrientationMode;
import dji.common.flightcontroller.GPSSignalLevel;
import dji.common.flightcontroller.IOStateOnBoard;
import dji.common.flightcontroller.LEDsSettings;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.flightcontroller.NavigationSatelliteSystem;
import dji.common.flightcontroller.NavigationSystemError;
import dji.common.flightcontroller.OSDKEnabledState;
import dji.common.flightcontroller.RemoteControllerFlightMode;
import dji.common.flightcontroller.imu.IMUState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.model.LocationCoordinate2D;
import dji.common.product.Model;
import dji.common.remotecontroller.AircraftMapping;
import dji.common.remotecontroller.AircraftMappingStyle;
import dji.common.remotecontroller.AuthorizationInfo;
import dji.common.remotecontroller.ChargeMobileMode;
import dji.common.remotecontroller.ConnectToMasterResult;
import dji.common.remotecontroller.Credentials;
import dji.common.remotecontroller.CustomButtonTags;
import dji.common.remotecontroller.GimbalAxis;
import dji.common.remotecontroller.GimbalControlSpeedCoefficient;
import dji.common.remotecontroller.GimbalMapping;
import dji.common.remotecontroller.GimbalMappingStyle;
import dji.common.remotecontroller.HardwareState;
import dji.common.remotecontroller.Information;
import dji.common.remotecontroller.PairingDevice;
import dji.common.remotecontroller.RCMode;
import dji.common.remotecontroller.RequestGimbalControlResult;
import dji.common.remotecontroller.ResponseForGimbalControl;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.battery.Battery;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.remotecontroller.RemoteController;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

public class DummyProduct extends Aircraft {
    private double homeLat = -32.8540743;
    private double homeLng = -71.2153629;

    private double lat = homeLat;
    private double lng = homeLng;

    //private float alt = 30;
    //private boolean isFlying = true;
    private float alt = 0;
    private boolean isFlying = false;

    private double yaw = 0; // Deg
    private long lastTime = 0;

    private static final String TAG = DummyProduct.class.getSimpleName();
    private static DummyProduct instance = null;

    public static BaseProduct getProductInstance() {
        if(instance == null) {
            instance = new DummyProduct(new DJISDKManager.SDKManagerCallback() {

            @Override
            public void onRegister(DJIError djiError) {

            }

            @Override
            public void onProductDisconnect() {

            }

            @Override
            public void onProductConnect(BaseProduct baseProduct) {

            }

            @Override
            public void onProductChanged(BaseProduct baseProduct) {

            }

            @Override
            public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent baseComponent, BaseComponent baseComponent1) {

            }

            @Override
            public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {

            }

            @Override
            public void onDatabaseDownloadProgress(long l, long l1) {

            }
        });
        }
        return (BaseProduct)instance;
    }

    private class DummyBattery extends Battery {

        @Override
        public void getWarningRecords(@NonNull CommonCallbacks.CompletionCallbackWith<WarningRecord[]> completionCallbackWith) {

        }

        @Override
        public void getLatestWarningRecord(@NonNull CommonCallbacks.CompletionCallbackWith<WarningRecord> completionCallbackWith) {

        }

        @Override
        public void getCellVoltages(@NonNull CommonCallbacks.CompletionCallbackWith<Integer[]> completionCallbackWith) {

        }

        @Override
        public void setSelfDischargeInDays(int i, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getSelfDischargeInDays(@NonNull CommonCallbacks.CompletionCallbackWith<Integer> completionCallbackWith) {

        }

        @Override
        public void setNumberOfCells(int i, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void setLevel1CellVoltageThreshold(int i, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getLevel1CellVoltageThreshold(@NonNull CommonCallbacks.CompletionCallbackWith<Integer> completionCallbackWith) {

        }

        @Override
        public void setLevel2CellVoltageThreshold(int i, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getLEDsEnabled(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void setLEDsEnabled(boolean b, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getLevel2CellVoltageThreshold(@NonNull CommonCallbacks.CompletionCallbackWith<Integer> completionCallbackWith) {

        }

        @Override
        public void setLevel1CellVoltageBehavior(@NonNull LowVoltageBehavior lowVoltageBehavior, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getLevel1CellVoltageBehavior(@NonNull CommonCallbacks.CompletionCallbackWith<LowVoltageBehavior> completionCallbackWith) {

        }

        @Override
        public void setLevel2CellVoltageBehavior(@NonNull LowVoltageBehavior lowVoltageBehavior, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getLevel2CellVoltageBehavior(@NonNull CommonCallbacks.CompletionCallbackWith<LowVoltageBehavior> completionCallbackWith) {

        }

        @Override
        public void getPairingState(@NonNull CommonCallbacks.CompletionCallbackWith<PairingState> completionCallbackWith) {

        }

        @Override
        public void pairBatteries(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void setStateCallback(@Nullable BatteryState.Callback callback) {
            BatteryState.Builder builder = new BatteryState.Builder();
            builder.chargeRemainingInPercent(93);
            builder.chargeRemaining(100);
            builder.current(500);
            builder.fullChargeCapacity(1000);
            builder.temperature(30);
            builder.voltage(12);
            builder.designCapacity(1200);
            callback.onUpdate(builder.build());
        }
    }

    private class DummyRemoteController extends RemoteController {

        @Override
        public void setHardwareStateCallback(@Nullable HardwareState.HardwareStateCallback hardwareStateCallback) {
            super.setHardwareStateCallback(hardwareStateCallback);

            Stick leftStick = new Stick(0, 0);
            Stick rightStick = new Stick(0, 0);

            HardwareState.Builder builder = new HardwareState.Builder();
            builder.leftStick(leftStick);
            builder.rightStick(rightStick);
            builder.flightModeSwitch(HardwareState.FlightModeSwitch.POSITION_TWO);

            hardwareStateCallback.onUpdate(builder.build());
        }

        @Override
        public boolean isMasterSlaveModeSupported() {
            return false;
        }

        @Override
        public boolean isFocusControllerSupported() {
            return false;
        }

        @Override
        public boolean isCustomizableButtonSupported() {
            return false;
        }

        @Override
        public void setName(@NonNull String s, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getName(@NonNull CommonCallbacks.CompletionCallbackWith<String> completionCallbackWith) {

        }

        @Override
        public void setPassword(@NonNull String s, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getPassword(@NonNull CommonCallbacks.CompletionCallbackWith<String> completionCallbackWith) {

        }

        @Override
        public void setAircraftMappingStyle(@NonNull AircraftMappingStyle aircraftMappingStyle, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getAircraftMappingStyle(@NonNull CommonCallbacks.CompletionCallbackWith<AircraftMappingStyle> completionCallbackWith) {

        }

        @Override
        public void setCustomAircraftMapping(@NonNull AircraftMapping aircraftMapping, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getCustomAircraftMapping(@NonNull CommonCallbacks.CompletionCallbackWith<AircraftMapping> completionCallbackWith) {

        }

        @Override
        public void startPairing(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public boolean isMultiDevicePairingSupported() {
            return false;
        }

        @Override
        public void startMultiDevicePairing(PairingDevice pairingDevice, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void stopMultiDevicePairing(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void stopPairing(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getPairingState(@NonNull CommonCallbacks.CompletionCallbackWith<dji.common.remotecontroller.PairingState> completionCallbackWith) {

        }

        @Override
        public void setLeftDialGimbalControlAxis(@NonNull GimbalAxis gimbalAxis, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getLeftDialGimbalControlAxis(@NonNull CommonCallbacks.CompletionCallbackWith<GimbalAxis> completionCallbackWith) {

        }

        @Override
        public void setCustomButtonTags(@NonNull CustomButtonTags customButtonTags, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getCustomButtonTags(@NonNull CommonCallbacks.CompletionCallbackWith<CustomButtonTags> completionCallbackWith) {

        }

        @Override
        public void setMode(@NonNull RCMode rcMode, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getMode(@NonNull CommonCallbacks.CompletionCallbackWith<RCMode> completionCallbackWith) {

        }

        @Override
        public void connectToMaster(@NonNull Credentials credentials, @Nullable CommonCallbacks.CompletionCallbackWith<ConnectToMasterResult> completionCallbackWith) {

        }

        @Override
        public void getConnectedMasterCredentials(@NonNull CommonCallbacks.CompletionCallbackWith<Credentials> completionCallbackWith) {

        }

        @Override
        public void getAvailableMasters(@NonNull CommonCallbacks.CompletionCallbackWith<Information[]> completionCallbackWith) {

        }

        @Override
        public void startMasterSearching(@NonNull MasterSearchingCallback masterSearchingCallback) {

        }

        @Override
        public void stopMasterSearching(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getMasterSearchingState(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void getSlaveList(@NonNull CommonCallbacks.CompletionCallbackWith<Information[]> completionCallbackWith) {

        }

        @Override
        public void setGimbalMappingStyle(@NonNull GimbalMappingStyle gimbalMappingStyle, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getGimbalMappingStyle(@NonNull CommonCallbacks.CompletionCallbackWith<GimbalMappingStyle> completionCallbackWith) {

        }

        @Override
        public void setCustomGimbalMapping(@NonNull GimbalMapping gimbalMapping, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getCustomGimbalMapping(@NonNull CommonCallbacks.CompletionCallbackWith<GimbalMapping> completionCallbackWith) {

        }

        @Override
        public void setGimbalControlSpeedCoefficient(@NonNull GimbalControlSpeedCoefficient gimbalControlSpeedCoefficient, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getGimbalControlSpeedCoefficient(@NonNull CommonCallbacks.CompletionCallbackWith<GimbalControlSpeedCoefficient> completionCallbackWith) {

        }

        @Override
        public void setLeftDialGimbalControlSpeedCoefficient(int i, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getLeftDialGimbalControlSpeedCoefficient(@NonNull CommonCallbacks.CompletionCallbackWith<Integer> completionCallbackWith) {

        }

        @Override
        public void requestLegacyGimbalControl(@NonNull CommonCallbacks.CompletionCallbackWith<RequestGimbalControlResult> completionCallbackWith) {

        }

        @Override
        public void requestGimbalControl(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void respondToRequestForGimbalControl(ResponseForGimbalControl responseForGimbalControl) {

        }

        @Override
        public void getChargeMobileMode(@NonNull CommonCallbacks.CompletionCallbackWith<ChargeMobileMode> completionCallbackWith) {

        }

        @Override
        public void setChargeMobileMode(@NonNull ChargeMobileMode chargeMobileMode, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void setMasterAuthorizationCode(@NonNull String s, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void connectToMaster(@NonNull AuthorizationInfo authorizationInfo, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getMasters(@NonNull CommonCallbacks.CompletionCallbackWith<String[]> completionCallbackWith) {

        }

        @Override
        public void releaseGimbalControl(CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public boolean hasGimbalControl() {
            return false;
        }

        @Override
        public boolean isSecondaryVideoOutputSupported() {
            return false;
        }
    }

    private class DummyFlightController extends FlightController {

        @NonNull
        @Override
        public FlightControllerState getState() {
            FlightControllerState state = new FlightControllerState();
            state.setSatelliteCount(10);
            state.setAircraftHeadDirection(0);
            state.setGPSSignalLevel(GPSSignalLevel.LEVEL_5);

            synchronized (this) { // Consistency
                Attitude attitude = new Attitude(0, 0, yaw);
                state.setAttitude(attitude);

                state.setAircraftLocation(new LocationCoordinate3D(lat, lng, alt));
                state.setHomeLocation(new LocationCoordinate2D(homeLat, homeLng));

                state.setVelocityX(0.001f);
                state.setVelocityY(0.002f);
                state.setVelocityZ(0.003f);
            }

            state.setFlying(isFlying);

            return state;
        }

        public void setPropellerCoverLimitEnabled(boolean b, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        public void getPropellerCoverLimitEnabled(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public boolean isFlightAssistantSupported() {
            return false;
        }

        @Override
        public boolean isVirtualFenceSupported() {
            return false;
        }

        @Override
        public boolean isAccessLockerSupported() {
            return false;
        }

        @Override
        public boolean isLandingGearMovable() {
            return false;
        }

        @Override
        public int getCompassCount() {
            return 0;
        }

        @Override
        public boolean isRTKSupported() {
            return false;
        }

        @Override
        public void setConnectionFailSafeBehavior(@NonNull ConnectionFailSafeBehavior connectionFailSafeBehavior, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getConnectionFailSafeBehavior(@NonNull CommonCallbacks.CompletionCallbackWith<ConnectionFailSafeBehavior> completionCallbackWith) {

        }

        @Override
        public void setNoviceModeEnabled(boolean b, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getNoviceModeEnabled(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void startTakeoff(@Nullable CommonCallbacks.CompletionCallback completionCallback) {
            isFlying = true;
            completionCallback.onResult(null);
        }

        @Override
        public void startPrecisionTakeoff(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void cancelTakeoff(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void startLanding(@Nullable CommonCallbacks.CompletionCallback completionCallback) {
            // HACK: Teletransporting to ground
            alt = 0;
            isFlying = false;
            completionCallback.onResult(null);
        }

        @Override
        public void cancelLanding(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void turnOnMotors(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void turnOffMotors(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void startGoHome(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void cancelGoHome(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void setHomeLocation(@NonNull LocationCoordinate2D locationCoordinate2D, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getHomeLocation(@NonNull CommonCallbacks.CompletionCallbackWith<LocationCoordinate2D> completionCallbackWith) {

        }

        @Override
        public void setGoHomeHeightInMeters(int i, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getGoHomeHeightInMeters(@NonNull CommonCallbacks.CompletionCallbackWith<Integer> completionCallbackWith) {

        }

        @Override
        public boolean isOnboardSDKDeviceAvailable() {
            return false;
        }

        @Override
        public void sendDataToOnboardSDKDevice(byte[] bytes, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void setLEDsEnabledSettings(LEDsSettings leDsSettings, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getLEDsEnabledSettings(@NonNull CommonCallbacks.CompletionCallbackWith<LEDsSettings> completionCallbackWith) {

        }

        @Override
        public void setFlightOrientationMode(@NonNull FlightOrientationMode flightOrientationMode, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void lockCourseUsingCurrentHeading(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public boolean isVirtualStickControlModeAvailable() {
            return false;
        }

        @Override
        public void sendVirtualStickFlightControlData(@NonNull FlightControlData flightControlData, @Nullable CommonCallbacks.CompletionCallback completionCallback) {
            long time = System.currentTimeMillis();
            double dt = (time - lastTime) / 1000.0;
            lastTime = time;
            if(dt > 0.1) { // TODO: Check DJI doc
                // Timed out => ignore
                return;
            }

            double fwdVel = flightControlData.getRoll();
            double rightVel = flightControlData.getPitch();
            double yawVel = flightControlData.getYaw();
            double throttleVel = flightControlData.getVerticalThrottle();

            double rad = Math.toRadians(yaw);
            double[] dLatLng = Functions.getLatLngDiff(lat, rad, fwdVel, rightVel);

            // Virtual stick movements are not in m/s
            // TODO: Adjust to match real drone motion
            double f1 = 0.5;
            double f2 = 0.5;
            double f3 = 0.5;

            synchronized (this) { // Consistency
                lat += dLatLng[0] * dt * f1;
                lng += dLatLng[1] * dt * f1;
                alt += throttleVel * dt * f2;
                yaw += yawVel * dt * f3;

                if (yaw > 180) {
                    yaw -= 360;
                } else if (yaw < -180) {
                    yaw += 360;
                }
            }
        }

        @Override
        public void setVirtualStickModeEnabled(boolean b, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getVirtualStickModeEnabled(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void setLowBatteryWarningThreshold(int i, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getLowBatteryWarningThreshold(@NonNull CommonCallbacks.CompletionCallbackWith<Integer> completionCallbackWith) {

        }

        @Override
        public void setSeriousLowBatteryWarningThreshold(int i, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getSeriousLowBatteryWarningThreshold(@NonNull CommonCallbacks.CompletionCallbackWith<Integer> completionCallbackWith) {

        }

        @Override
        public void setHomeLocationUsingAircraftCurrentLocation(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void setMultipleFlightModeEnabled(boolean b, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getMultipleFlightModeEnabled(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void setIMUStateCallback(@Nullable IMUState.Callback callback) {

        }

        @Override
        public void startIMUCalibration(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void startIMUCalibration(int i, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public int getIMUCount() {
            return 0;
        }

        @Override
        public void setControlMode(@NonNull ControlMode controlMode, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getControlMode(@NonNull CommonCallbacks.CompletionCallbackWith<ControlMode> completionCallbackWith) {

        }

        @Override
        public void setTripodModeEnabled(boolean b, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getTripodModeEnabled(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void setCinematicBrakeSensitivity(int i, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getCinematicBrakeSensitivity(@NonNull CommonCallbacks.CompletionCallbackWith<Integer> completionCallbackWith) {

        }

        @Override
        public void setCinematicYawSpeed(float v, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getCinematicYawSpeed(@NonNull CommonCallbacks.CompletionCallbackWith<Float> completionCallbackWith) {

        }

        @Override
        public void setAircraftHeadingTurningSpeed(@NonNull HardwareState.FlightModeSwitch flightModeSwitch, int i, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getAircraftHeadingTurningSpeed(@NonNull HardwareState.FlightModeSwitch flightModeSwitch, @NonNull CommonCallbacks.CompletionCallbackWith<Integer> completionCallbackWith) {

        }

        @Override
        public void setAircraftHeadingTurningSmoothness(@NonNull HardwareState.FlightModeSwitch flightModeSwitch, int i, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getAircraftHeadingTurningSmoothness(@NonNull HardwareState.FlightModeSwitch flightModeSwitch, @NonNull CommonCallbacks.CompletionCallbackWith<Integer> completionCallbackWith) {

        }

        @Override
        public void setCinematicModeEnabled(boolean b, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getCinematicModeEnabled(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void setAutoQuickSpinEnabled(boolean b, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getQuickSpinEnabled(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void setTerrainFollowModeEnabled(Boolean aBoolean, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getTerrainFollowModeEnabled(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void confirmLanding(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getRCSwitchFlightModeMapping(@NonNull CommonCallbacks.CompletionCallbackWith<RemoteControllerFlightMode[]> completionCallbackWith) {

        }

        @Override
        public void setSmartReturnToHomeEnabled(boolean b, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getSmartReturnToHomeEnabled(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void confirmSmartReturnToHomeRequest(boolean b, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void setESCBeepEnabled(boolean b, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getESCBeepEnabled(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void startGravityCenterCalibration(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void stopGravityCenterCalibration(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void setUrgentStopModeEnabled(boolean b, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getUrgentStopModeEnabled(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void reboot(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public boolean isPropellerCalibrationSupported() {
            return false;
        }

        @Override
        public void startPropellerCalibration(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void stopPropellerCalibration(@Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void addNavigationSystemErrorCallback(NavigationSystemError.Callback callback) {

        }

        @Override
        public void removeNavigationSystemErrorCallback(NavigationSystemError.Callback callback) {

        }

        @Override
        public boolean isOnboardFChannelAvailable() {
            return false;
        }

        @Override
        public void setPowerSupplyPortEnabled(boolean b, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getPowerSupplyPortEnabled(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void setOsdkEnabledState(OSDKEnabledState osdkEnabledState, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getOsdkEnabledState(@NonNull CommonCallbacks.CompletionCallbackWith<OSDKEnabledState> completionCallbackWith) {

        }

        @Override
        public void setPPSEnabledState(OSDKEnabledState osdkEnabledState, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getPPSEnabledState(@NonNull CommonCallbacks.CompletionCallbackWith<OSDKEnabledState> completionCallbackWith) {

        }

        @Override
        public void setVisionEnabledState(OSDKEnabledState osdkEnabledState, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getVisionEnabledState(@NonNull CommonCallbacks.CompletionCallbackWith<OSDKEnabledState> completionCallbackWith) {

        }

        @Override
        public void initOnBoardIO(@NonNull Integer integer, @NonNull IOStateOnBoard ioStateOnBoard, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void setOnBoardIO(@NonNull Integer integer, @NonNull IOStateOnBoard ioStateOnBoard, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getOnBoardIO(@NonNull Integer integer, @NonNull CommonCallbacks.CompletionCallbackWith<IOStateOnBoard> completionCallbackWith) {

        }

        @Override
        public void setControlGimbalBehavior(@NonNull ControlGimbalBehavior controlGimbalBehavior, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getControlGimbalBehavior(@NonNull CommonCallbacks.CompletionCallbackWith<ControlGimbalBehavior> completionCallbackWith) {

        }

        @Override
        public void setPropellerCageProtectionEnabled(boolean b, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getPropellerCageProtectionEnabled(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void toggleAttitudeFlightMode(boolean b, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

        }

        @Override
        public void getIsAttitudeFlightModeOpen(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void lockTakeoffWithoutGPS(boolean b, @Nullable CommonCallbacks.CompletionCallback<DJIError> completionCallback) {

        }

        @Override
        public void isLockTakeoffWithoutGPS(@NonNull CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void setCoordinatedTurnEnabled(@NonNull boolean b, @Nullable CommonCallbacks.CompletionCallback<DJIError> completionCallback) {

        }

        @Override
        public void getCoordinatedTurnEnabled(@Nullable CommonCallbacks.CompletionCallbackWith<Boolean> completionCallbackWith) {

        }

        @Override
        public void setNavigationSatelliteSystem(@NonNull NavigationSatelliteSystem navigationSatelliteSystem, @Nullable CommonCallbacks.CompletionCallback<DJIError> completionCallback) {

        }

        @Override
        public void getNavigationSatelliteSystem(@Nullable CommonCallbacks.CompletionCallbackWith<NavigationSatelliteSystem> completionCallbackWith) {

        }
    }

    private Battery battery = new DummyBattery();
    private RemoteController remoteController = new DummyRemoteController();
    private FlightController flightController = new DummyFlightController();

    public DummyProduct(DJISDKManager.SDKManagerCallback sdkManagerCallback) {
        super(sdkManagerCallback);
    }

    @Override
    public Battery getBattery() {
        return battery;
    }

    @Override
    public RemoteController getRemoteController() {
        return remoteController;
    }

    @Override
    public FlightController getFlightController() {
        return flightController;
    }

    @Override
    public void setName(@NonNull String s, @Nullable CommonCallbacks.CompletionCallback completionCallback) {

    }

    @Override
    public void getName(CommonCallbacks.CompletionCallbackWith<String> completionCallbackWith) {
    }

    @Override
    public Model getModel() {
        return Model.MAVIC_MINI;
    }

    @Override
    public boolean isConnected() {
        return true;
    }
}
