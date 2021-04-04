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

    private static BaseProduct mProduct;
    private static DJISimulatorApplication simulatorApplication;
    private static boolean m_sim = false;

    // True if simulate...
    public static boolean getSim() {
        return m_sim;
    }
    public static void setSim(boolean sim) {
        m_sim = sim;
    }

    public static synchronized BaseProduct getProductInstance() {
        if (null == mProduct) {
            if( getSim() == false) {
                mProduct = DJISDKManager.getInstance().getProduct();
            }else {
                mProduct = DJISimulatorApplication.getProductInstance();
            }
        }
        return mProduct;
    }

    public static void startLoginApplication()
    {
        simulatorApplication.onCreate();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        m_sim = false;
  //      simulatorApplication.onCreate();
    }

    public static synchronized void updateProduct(BaseProduct product) {
        mProduct = product;
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
        if (getProductInstance() == null) return null;
        Camera camera = null;
        if (getProductInstance() instanceof Aircraft){
            camera = ((Aircraft) getProductInstance()).getCamera();
        } else if (getProductInstance() instanceof HandHeld) {
            camera = ((HandHeld) getProductInstance()).getCamera();
        }
        return camera;
    }
}


