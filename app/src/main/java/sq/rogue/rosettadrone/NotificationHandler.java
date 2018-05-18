package sq.rogue.rosettadrone;

import android.content.Context;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.view.ContextThemeWrapper;
import android.view.View;

import static sq.rogue.rosettadrone.util.TYPE_GCS_IP;
import static sq.rogue.rosettadrone.util.TYPE_GCS_PORT;
import static sq.rogue.rosettadrone.util.TYPE_VIDEO_IP;
import static sq.rogue.rosettadrone.util.TYPE_VIDEO_PORT;

public class NotificationHandler {
    public static void notifySnackbar(View view, int resID, int duration) {
        Snackbar snackbar = Snackbar.make(view, resID, duration);
        snackbar.show();
    }

    public static void notifyAlert(Context context, int input) {
        AlertDialog.Builder builder;
        builder = new AlertDialog.Builder(new ContextThemeWrapper(context, R.style.AppDialog));
        builder.setPositiveButton(android.R.string.ok, null);
        switch (input) {
            case TYPE_GCS_IP:
                builder.setMessage("Invalid IP address entered for GCS IP.");
                break;
            case TYPE_GCS_PORT:
                builder.setMessage("Invalid port entered for GCS Port.");
                break;
            case TYPE_VIDEO_IP:
                builder.setMessage("Invalid IP address entered for Video IP.");
                break;
            case TYPE_VIDEO_PORT:
                builder.setMessage("Invalid port entered for Video Port.");
                break;
        }
        builder.show();
    }

}
