package sq.rogue.rosettadrone.settings;

import android.app.Activity;
import android.widget.Toast;

public class Tools {
    public static int getInt(String str, int defaultValue) {
        try {
            return Integer.valueOf(str);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static float getFloat(String str, float defaultValue) {
        try {
            return Float.valueOf(str);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static void showToast(final Activity activity, final String msg) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
