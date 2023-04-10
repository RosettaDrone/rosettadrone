/**
 * TODO: Move here all code not directly related with Rosetta Drone.
 * TODO: Code should be moved to different plugins.
 */
package sq.rogue.rosettadrone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PluginManager {
    private final String TAG = DroneModel.class.getSimpleName();

    public MainActivity mainActivity;

    List<Plugin> plugins = new ArrayList<Plugin>();

    // TODO: Obtain plugins list automatically

    // Plugin disabled, because it disables the video preview in Rosetta
    //List<String> classNames = Arrays.asList("RawVideoStreamer");
    List<String> classNames = Arrays.asList("AI9Tek");

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
            }
        }
    }

    public void init() {
        for (Plugin plugin : plugins) {
            plugin.init(this);
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
}
