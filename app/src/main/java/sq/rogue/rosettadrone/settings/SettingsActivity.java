package sq.rogue.rosettadrone.settings;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import android.util.Log;

import sq.rogue.rosettadrone.R;
import sq.rogue.rosettadrone.settings.drone.DroneSettingsFragment;

public class SettingsActivity extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {
    private static final String TAG = SettingsActivity.class.getSimpleName();

    /**
     * @param savedInstanceState Any saved state we are carrying over into the new activity instance
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);


        Fragment fragment = getSupportFragmentManager().findFragmentByTag(SettingsFragment.PAGE_ID);
        if (fragment == null) {
            fragment = new SettingsFragment();
        }

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.pref_container, fragment, SettingsFragment.PAGE_ID);
        ft.commit();

//        // Display the fragment as the main content.
//        getFragmentManager().beginTransaction()
//                .replace(android.R.id.content, new SettingsFragment())
//                .commit();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setTitle(R.string.settings);
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen preferenceScreen) {
        Log.d(TAG, "onPreferenceStartScreen");
//        Log.d(TAG, "callback called to attach the preference sub screen");
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        DroneSettingsFragment fragment = new DroneSettingsFragment();
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, preferenceScreen.getKey());
        fragment.setArguments(args);
        ft.replace(R.id.pref_container, fragment, preferenceScreen.getKey());
        ft.addToBackStack(preferenceScreen.getKey());
        ft.commit();
        setTitle(preferenceScreen.getTitle());
        return true;
    }
}