/**
 * TODO: Move here all code not directly related with Rosetta Drone.
 * TODO: Code should be moved to different plugins.
 */
package sq.rogue.rosettadrone;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PluginManager {
    private final String TAG = DroneModel.class.getSimpleName();

    public MainActivity mainActivity;

    List<Plugin> plugins = new ArrayList<Plugin>();

    // TODO: Obtain plugins list automatically

    //List<String> classNames = Arrays.asList("RawVideoStreamer"); // Plugin disabled, because it disables the video preview in Rosetta
    //List<String> classNames = Arrays.asList("RawVideoStreamer", "AI9Tek");
    //List<String> classNames = Arrays.asList("AI9Tek");

    List<String> classNames = Arrays.asList("WebRTCStreaming");
    //List<String> classNames = Arrays.asList();

    PluginManager(MainActivity mainActivity) {
        this.mainActivity = mainActivity;

        for (String className : classNames) {
            try {
                Class<?> myClass = Class.forName("sq.rogue.rosettadrone.plugins." + className);
                Plugin plugin = (Plugin) myClass.newInstance();
                if(plugin.isEnabled()) {
                    plugin.init(this);
                    plugins.add(plugin);
                }

            } catch (Exception e) {
//                e.printStackTrace();
                Log.e(TAG, e.getClass().getName() + " occurred, stack trace: " + e.getMessage() + "\n" + e.getCause());
            }
        }
    }

    public void start() {
        for (Plugin plugin : plugins) {
            plugin.start();
        }
    }

    public void stop() {
        for (Plugin plugin : plugins) {
            plugin.stop();
        }
    }

    public void onVideoChange() {
        for (Plugin plugin : plugins) {
            plugin.onVideoChange();
        }
    }

    public boolean onMenuItemClick(int itemId) {
        for (Plugin plugin : plugins) {
            if(plugin.onMenuItemClick(itemId)) return true;
        }
        return false;
    }

    public void settingsChanged() {
        for (Plugin plugin : plugins) {
            plugin.settingsChanged();
        }
    }
}
