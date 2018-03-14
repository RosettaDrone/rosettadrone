package sq.rogue.rosettadrone;

import android.app.Application;
import android.content.Context;

import com.secneo.sdk.Helper;

public class RDApplication extends Application {
    @Override
    protected void attachBaseContext(Context paramContext) {
        super.attachBaseContext(paramContext);
        Helper.install(RDApplication.this);
    }
}
