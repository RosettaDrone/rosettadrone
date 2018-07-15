package sq.rogue.rosettadrone.settings.drone;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;

import sq.rogue.rosettadrone.R;
import sq.rogue.rosettadrone.settings.SettingsFragment;

public class DroneSettingsActivity extends AppCompatActivity {

    /**
     * @param savedInstanceState Any saved state we are carrying over into the new activity instance
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (savedInstanceState == null) {
            Fragment preferenceFragment = new SettingsFragment();
            FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.add(R.id.pref_container, preferenceFragment);
            fragmentTransaction.commit();
        }


//        // Display the fragment as the main content.
//        getFragmentManager().beginTransaction()
//                .replace(android.R.id.content, new SettingsFragment())
//                .commit();
    }

}