package sq.rogue.rosettadrone;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;


public class GUIActivity extends AppCompatActivity {

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
     //   setTitle(R.string.settings);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.gui_main);
    }
}
