package sq.rogue.rosettadrone;

public abstract class Plugin {
    protected PluginManager pluginManager;

    protected void init(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
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
}
