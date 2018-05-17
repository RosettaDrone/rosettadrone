package sq.rogue.rosettadrone;

import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
import android.view.View;

class NotificationHandler {
    public static void notifySnackbar(View view, int resID, int duration) {
        Snackbar snackbar = Snackbar.make(view, resID, duration);
        snackbar.show();
    }
}
