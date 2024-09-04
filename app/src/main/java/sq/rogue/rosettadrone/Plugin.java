package sq.rogue.rosettadrone;

public class Plugin {
    protected PluginManager pluginManager;

    public void init(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    protected void start() {
    }

    protected void pause() {}
    protected void resume() {}

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

    public boolean getPrefBoolean(String pref, boolean defPref) {
        return pluginManager.mainActivity.sharedPreferences.getBoolean(pref, defPref);
    }

}
