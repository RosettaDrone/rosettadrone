package sq.rogue.rosettadrone;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

// Display value of preference in summary field

import java.util.Arrays;

public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    SharedPreferences sharedPreferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public void onResume() {
        super.onResume();
        sharedPreferences = getPreferenceManager().getSharedPreferences();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        PreferenceScreen preferenceScreen = getPreferenceScreen();
        for (int i = 0; i < preferenceScreen.getPreferenceCount(); i++) {
            setSummary(getPreferenceScreen().getPreference(i));
        }
    }

    @Override
    public void onPause() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = getPreferenceScreen().findPreference(key);
        setSummary(pref);
    }

    private void setSummary(Preference pref) {
        if (pref instanceof EditTextPreference) {
            updateSummary((EditTextPreference) pref);
        } else if (pref instanceof ListPreference) {
            updateSummary((ListPreference) pref);
        } else if (pref instanceof MultiSelectListPreference) {
            updateSummary((MultiSelectListPreference) pref);
        }
    }

    private void updateSummary(MultiSelectListPreference pref) {
        pref.setSummary(Arrays.toString(pref.getValues().toArray()));
    }

    private void updateSummary(ListPreference pref) {
        pref.setSummary(pref.getValue());
    }

    private void updateSummary(EditTextPreference preference) {
        preference.setSummary(preference.getText());
    }
}