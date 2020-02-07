package sq.rogue.rosettadrone.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import android.util.Patterns;

import sq.rogue.rosettadrone.MainActivity;
import sq.rogue.rosettadrone.NotificationHandler;
import sq.rogue.rosettadrone.R;

import static sq.rogue.rosettadrone.util.TYPE_GCS_IP;
import static sq.rogue.rosettadrone.util.TYPE_GCS_PORT;
import static sq.rogue.rosettadrone.util.TYPE_VIDEO_BITRATE;
import static sq.rogue.rosettadrone.util.TYPE_VIDEO_IP;
import static sq.rogue.rosettadrone.util.TYPE_VIDEO_PORT;

// Display value of preference in summary field

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    protected static final String PAGE_ID = "settings";
    private static final String TAG = SettingsFragment.class.getSimpleName();
    SharedPreferences sharedPreferences;


    public static SettingsFragment newInstance(String pageId) {
        SettingsFragment settingsFragment = new SettingsFragment();
        Bundle args = new Bundle();
        args.putString(PAGE_ID, pageId);
        settingsFragment.setArguments(args);
        return (settingsFragment);
    }

    /**
     * @param savedInstanceState Any saved state we are bringing into the new fragment instance
     **/
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getPreferenceManager().getSharedPreferences();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * @param savedInstanceState
     * @param rootKey
     */
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        setListeners();
    }

    public void setListeners() {
        findPreference("pref_external_gcs").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                MainActivity.FLAG_PREFS_CHANGED = true;
                MainActivity.FLAG_TELEMETRY_ADDRESS_CHANGED = true;
                MainActivity.FLAG_VIDEO_ADDRESS_CHANGED = true;

                return true;
            }
        });

        findPreference("pref_separate_gcs").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                MainActivity.FLAG_PREFS_CHANGED = true;
                MainActivity.FLAG_VIDEO_ADDRESS_CHANGED = true;
                return true;
            }
        });

        findPreference("pref_gcs_ip").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                if (Patterns.IP_ADDRESS.matcher((String) newValue).matches()) {
                    MainActivity.FLAG_PREFS_CHANGED = true;
                    MainActivity.FLAG_TELEMETRY_ADDRESS_CHANGED = true;
                    return true;
                } else {
                    NotificationHandler.notifyAlert(SettingsFragment.this.getActivity(), TYPE_GCS_IP,
                            null, null);
                    return false;
                }
            }
        });

        findPreference("pref_telem_port").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                try {
                    if (Integer.parseInt((String) newValue) >= 1 && Integer.parseInt((String) newValue) <= 65535) {
                        MainActivity.FLAG_PREFS_CHANGED = true;
                        MainActivity.FLAG_TELEMETRY_ADDRESS_CHANGED = true;
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
                NotificationHandler.notifyAlert(SettingsFragment.this.getActivity(), TYPE_GCS_PORT,
                        null, null);
                return false;
            }
        });

        findPreference("pref_secondary_telemetry_ip").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                if (Patterns.IP_ADDRESS.matcher((String) newValue).matches()) {
                    MainActivity.FLAG_PREFS_CHANGED = true;
                    MainActivity.FLAG_TELEMETRY_ADDRESS_CHANGED = true;
                    return true;
                } else {
                    NotificationHandler.notifyAlert(SettingsFragment.this.getActivity(), TYPE_GCS_IP,
                            null, null);
                    return false;
                }
            }
        });

        findPreference("pref_secondary_telemetry_port").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                try {
                    if (Integer.parseInt((String) newValue) >= 1 && Integer.parseInt((String) newValue) <= 65535) {
                        MainActivity.FLAG_PREFS_CHANGED = true;
                        MainActivity.FLAG_TELEMETRY_ADDRESS_CHANGED = true;
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
                NotificationHandler.notifyAlert(SettingsFragment.this.getActivity(), TYPE_GCS_PORT,
                        null, null);
                return false;
            }
        });

        findPreference("pref_video_ip").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (Patterns.IP_ADDRESS.matcher((String) newValue).matches()) {
                    MainActivity.FLAG_PREFS_CHANGED = true;
                    MainActivity.FLAG_VIDEO_ADDRESS_CHANGED = true;
                    return true;
                } else {
                    NotificationHandler.notifyAlert(SettingsFragment.this.getActivity(), TYPE_VIDEO_IP,
                            null, null);
                    return false;
                }
            }
        });

        findPreference("pref_enable_video").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                MainActivity.FLAG_PREFS_CHANGED = true;
                MainActivity.FLAG_VIDEO_ADDRESS_CHANGED = true;
                return true;
            }
        });

        findPreference("pref_video_port").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                try {
                    if (Integer.parseInt((String) newValue) >= 1 && Integer.parseInt((String) newValue) <= 65535) {
                        MainActivity.FLAG_PREFS_CHANGED = true;
                        MainActivity.FLAG_VIDEO_ADDRESS_CHANGED = true;
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
                NotificationHandler.notifyAlert(SettingsFragment.this.getActivity(), TYPE_VIDEO_PORT,
                        null, null);
                return false;
            }
        });

        findPreference("pref_video_bitrate").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                try {
                    if (Integer.parseInt((String) newValue) >= 1 && Integer.parseInt((String) newValue) <= 65535) {
                        MainActivity.FLAG_PREFS_CHANGED = true;
                        MainActivity.FLAG_VIDEO_ADDRESS_CHANGED = true;
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
                NotificationHandler.notifyAlert(SettingsFragment.this.getActivity(), TYPE_VIDEO_BITRATE,
                        null, null);
                return false;
            }
        });

        findPreference("pref_encode_speed").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                MainActivity.FLAG_PREFS_CHANGED = true;
                MainActivity.FLAG_VIDEO_ADDRESS_CHANGED = true;
                return true;
            }
        });
    }


    /**
     *
     */
    @Override
    public void onResume() {
        super.onResume();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); ++i) {
            Preference preference = getPreferenceScreen().getPreference(i);
            if (preference instanceof PreferenceGroup) {
                PreferenceGroup preferenceGroup = (PreferenceGroup) preference;
                for (int j = 0; j < preferenceGroup.getPreferenceCount(); ++j) {
                    Preference singlePref = preferenceGroup.getPreference(j);
                    updatePreference(singlePref);
                }
            } else {
                updatePreference(preference);
            }
        }

    }

    /**
     *
     */
    @Override
    public void onPause() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    /**
     *
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /**
     * @param sharedPreferences
     * @param key
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        updatePreference(findPreference(key));
    }

    /**
     * @param preference
     */
    private void updatePreference(Preference preference) {
        if (preference == null) return;
        if (preference instanceof EditTextPreference) {
            EditTextPreference editTextPref = (EditTextPreference) preference;
            preference.setSummary(editTextPref.getText());
        } else if (preference instanceof ListPreference) {
            ListPreference listPreference = (ListPreference) preference;
            listPreference.setSummary(listPreference.getEntry());
            return;
        } else {
            return;
        }
        SharedPreferences sharedPrefs = getPreferenceManager().getSharedPreferences();
        preference.setSummary(sharedPrefs.getString(preference.getKey(), "Default"));
    }


}