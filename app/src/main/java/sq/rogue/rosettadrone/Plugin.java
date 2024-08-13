package sq.rogue.rosettadrone;

public class Plugin {
    protected PluginManager pluginManager;

    protected void init(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    protected void start() {
    }

    /**
     * Video mode, resolution or codec changed.
     */
    protected void onVideoChange() {
    }

    protected boolean isEnabled() {
        return true;
    }

    public boolean onMenuItemClick(int itemId) {
        return false;
    }

    public void stop() {
    }

    public void settingsChanged() {
    }

    public String getPrefString(String pref, String defPref) {
        return pluginManager.mainActivity.sharedPreferences.getString(pref, defPref);
    }

    public Boolean getPrefBoolean(String pref, Boolean defPref) {
        return pluginManager.mainActivity.sharedPreferences.getBoolean(pref, defPref);
    }

}
