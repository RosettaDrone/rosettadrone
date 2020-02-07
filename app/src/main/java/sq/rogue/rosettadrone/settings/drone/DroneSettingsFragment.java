package sq.rogue.rosettadrone.settings.drone;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.Map;

import sq.rogue.rosettadrone.MainActivity;
import sq.rogue.rosettadrone.NotificationHandler;
import sq.rogue.rosettadrone.R;

import static sq.rogue.rosettadrone.util.TYPE_DRONE_ID;
import static sq.rogue.rosettadrone.util.TYPE_DRONE_RTL_ALTITUDE;
import static sq.rogue.rosettadrone.util.TYPE_FLIGHT_PATH_RADIUS;

public class DroneSettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = DroneSettingsFragment.class.getSimpleName();
    private static final String PAGE_ID = "drone_config";

    SharedPreferences sharedPreferences;

    public static DroneSettingsFragment newInstance(String pageID) {
        DroneSettingsFragment droneSettingsFragment = new DroneSettingsFragment();
        Bundle args = new Bundle();
        args.putString(PAGE_ID, pageID);
        droneSettingsFragment.setArguments(args);

        return droneSettingsFragment;
    }

    /**
     * @param savedInstanceState Any saved state we are bringing into the new fragment instance
     **/
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getPreferenceManager().getSharedPreferences();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        setSummaries();
    }

    /**
     *
     */
    @Override
    public void onPause() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }
    //    /**
//     *
//     */
//    @Override
//    public void onResume() {
//        super.onResume();
//        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
//
//
//        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); ++i) {
//            Preference preference = getPreferenceScreen().getPreference(i);
//            if (preference instanceof PreferenceGroup) {
//                PreferenceGroup preferenceGroup = (PreferenceGroup) preference;
//                for (int j = 0; j < preferenceGroup.getPreferenceCount(); ++j) {
//                    Preference singlePref = preferenceGroup.getPreference(j);
//                    updatePreference(singlePref);
//                }
//            } else {
//                updatePreference(preference);
//            }
//        }
//    }

    /**
     * @param savedInstanceState
     * @param rootKey
     */
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        setListeners();
    }

    private void setSummaries() {
        Map<String, ?> keys = sharedPreferences.getAll();

        for (Map.Entry<String, ?> entry : keys.entrySet()) {
            updatePreference(findPreference(entry.getKey()));
        }
    }

    private void setListeners() {
        findPreference("pref_drone_id").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                try {
                    if (Integer.parseInt((String) newValue) >= 1 && Integer.parseInt((String) newValue) <= 254) {
                        MainActivity.FLAG_PREFS_CHANGED = true;
                        MainActivity.FLAG_DRONE_ID_CHANGED = true;
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
                NotificationHandler.notifyAlert(DroneSettingsFragment.this.getActivity(), TYPE_DRONE_ID,
                        null, null);
                return false;
            }
        });
        findPreference("pref_drone_rtl_altitude").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                try {
                    if (Integer.parseInt((String) newValue) >= 20 && Integer.parseInt((String) newValue) <= 500) {
                        MainActivity.FLAG_PREFS_CHANGED = true;
                        MainActivity.FLAG_DRONE_RTL_ALTITUDE_CHANGED = true;
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
                NotificationHandler.notifyAlert(DroneSettingsFragment.this.getActivity(), TYPE_DRONE_RTL_ALTITUDE,
                        null, null);
                return false;
            }
        });
        findPreference("pref_drone_smart_rtl").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                MainActivity.FLAG_PREFS_CHANGED = true;
                MainActivity.FLAG_DRONE_SMART_RTL_CHANGED = true;
                return true;
            }
        });
        findPreference("pref_drone_max_height").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                try {
                    if (Integer.parseInt((String) newValue) >= 20 && Integer.parseInt((String) newValue) <= 500) {
                        MainActivity.FLAG_PREFS_CHANGED = true;
                        MainActivity.FLAG_DRONE_MAX_HEIGHT_CHANGED = true;
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
                return false;
            }
        });
        findPreference("pref_drone_multi_mode").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                MainActivity.FLAG_PREFS_CHANGED = true;
                MainActivity.FLAG_DRONE_MULTI_MODE_CHANGED = true;
                return true;
            }
        });
        findPreference("pref_drone_leds").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                MainActivity.FLAG_PREFS_CHANGED = true;
                MainActivity.FLAG_DRONE_LEDS_CHANGED = true;
                return true;
            }
        });

        findPreference("pref_drone_flight_path_mode").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                MainActivity.FLAG_PREFS_CHANGED = true;
                MainActivity.FLAG_DRONE_FLIGHT_PATH_MODE_CHANGED = true;
                return true;
            }
        });
        findPreference("pref_drone_flight_path_radius").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                try {
                    if (Float.parseFloat((String) newValue) >= .2f && Float.parseFloat((String) newValue) <= 1000) {
                        MainActivity.FLAG_PREFS_CHANGED = true;
                        MainActivity.FLAG_DRONE_FLIGHT_PATH_MODE_CHANGED = true;
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
                NotificationHandler.notifyAlert(DroneSettingsFragment.this.getActivity(), TYPE_FLIGHT_PATH_RADIUS,
                        null, null);
                return false;
            }
        });


        findPreference("pref_drone_collision_avoidance").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                MainActivity.FLAG_PREFS_CHANGED = true;
                MainActivity.FLAG_DRONE_COLLISION_AVOIDANCE_CHANGED = true;
                return true;
            }
        });
        findPreference("pref_drone_upward_avoidance").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                MainActivity.FLAG_PREFS_CHANGED = true;
                MainActivity.FLAG_DRONE_UPWARD_AVOIDANCE_CHANGED = true;
                return true;
            }
        });
        findPreference("pref_drone_landing_protection").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                MainActivity.FLAG_PREFS_CHANGED = true;
                MainActivity.FLAG_DRONE_LANDING_PROTECTION_CHANGED = true;
                return true;
            }
        });
    }

    /**
     * //     * @param sharedPreferences
     * //     * @param key
     * //
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
