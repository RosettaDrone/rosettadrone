package sq.rogue.rosettadrone;

import android.app.Application;
import android.content.Context;

import com.secneo.sdk.Helper;

import dji.sdk.base.BaseProduct;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.camera.Camera;
import dji.sdk.products.Aircraft;
import dji.sdk.products.HandHeld;

public class RDApplication extends Application {

    public static boolean useMavLink2 = true; // MAVSDK only speaks MAVLink 2
    private static DJISimulatorApplication simulatorApplication;
    private static boolean isSimulator = false;
    public static boolean isTestMode = false;

    public static boolean getSim() {
        return isSimulator;
    }

    public static void setSim(boolean sim) {
        isSimulator = sim;
    }

    public static synchronized BaseProduct getProductOrDummy() {
        if(isTestMode) {
            // IMPORTANT: DummyProduct can not be instantiated nor referenced here (in RDApplication.java),
            // because the SDK license protection system will crash the app.
            // Instead, we need to use an auxiliar method defined in MainActivity
            // BUG: return DummyProduct.getProductInstance();
            return MainActivity.createDummyProduct();
            }else {
            return DJISDKManager.getInstance().getProduct();
        }
    }

    public static void startLoginApplication() {
        simulatorApplication.onCreate();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isSimulator = false;
    }

    @Override
    protected void attachBaseContext(Context paramContext) {
        super.attachBaseContext(paramContext);
        Helper.install(RDApplication.this);

        if (simulatorApplication == null) {
            simulatorApplication = new DJISimulatorApplication();
            simulatorApplication.setContext(this);
        }
    }

    public static synchronized Camera getCameraInstance() {
        if(isTestMode) return null;
        BaseProduct product = getProductOrDummy();
        if (product == null) return null;
        Camera camera = null;
        if (product instanceof Aircraft) {
            camera = ((Aircraft) product).getCamera();
        } else if (product instanceof HandHeld) {
            camera = ((HandHeld) product).getCamera();
        }
        return camera;
    }
}


