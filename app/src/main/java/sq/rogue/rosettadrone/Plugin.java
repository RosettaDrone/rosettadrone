package sq.rogue.rosettadrone;

public abstract class Plugin {
    protected abstract void init(PluginManager pluginManager);

    /**
     * Video mode, resolution or codec changed.
     */
    protected abstract void onVideoChange();

    protected boolean isEnabled() {
        return true;
    }
}
