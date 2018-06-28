package sq.rogue.rosettadrone.logs;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TextView;

import sq.rogue.rosettadrone.R;

public class LogFragment extends Fragment {

    private final String TAG = getClass().getSimpleName();

    private final int DEFAULT_MAX_CHARACTERS = 200000;
//    private final String INSTANCE_STATE_KEY = "saved_state";

    private TextView mTextViewTraffic;
    private boolean mViewAtBottom = true;

    private int mMaxCharacters = DEFAULT_MAX_CHARACTERS;
    private int LONG_PRESS_TIMEOUT = 3000;

    GestureDetector gestureDetector;

    final Handler handler = new Handler();
    Runnable mLongPressed = new Runnable() {
        public void run() {
            clearLogText();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
//        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//        Log.d(TAG, "onCreateView");
        super.onCreateView(inflater, container, savedInstanceState);
        this.setRetainInstance(true);

        View view = inflater.inflate(R.layout.fragment_log, container, false);
        mTextViewTraffic = view.findViewById(R.id.log);

        mTextViewTraffic.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
//                Log.d(TAG, "onTouch");
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    Log.d(TAG, "ACTION_DOWN");
                    handler.postDelayed(mLongPressed, LONG_PRESS_TIMEOUT);
                }
                if((event.getAction() == MotionEvent.ACTION_UP)) {
                    if (event.getAction() == MotionEvent.ACTION_UP)
                        Log.d(TAG, "ACTION_UP");

                    handler.removeCallbacks(mLongPressed);
                }
                return false;
            }
        });

        mTextViewTraffic.setMovementMethod(new ScrollingMovementMethod());
        mTextViewTraffic.setHorizontallyScrolling(true);
//        mTextViewTraffic.setOnLongClickListener(new View.OnLongClickListener() {
//            @Override
//            public boolean onLongClick(View v) {
//                clearLogText();
//                return true;
//            }
//        });
        return view;
    }




    /**
     * Set the backing TextView
     *
     * @param textView The new backing TextView
     */
    public void setTextView(TextView textView) {
        mTextViewTraffic = textView;
    }

    /**
     * @param savedInstanceState Any saved state we are carrying over into the new activity instance
     */
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
//        Log.d(TAG, "onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        this.setRetainInstance(true);


    }

    /**
     * Checks the length of log and compares it against the maximum number of characters permitted.
     * If the log is longer than the maximum number of characters the log is cleared.
     *
     * @return True if the log is cleared. False if the log is not.
     */
    // TODO: Implement a better solution to overflow control
    public boolean checkOverflow() {
        /*
        Very naive solution. Writing out to a log is possible solution if log needs preserved,
        however parsing with substring will have a very severe impact on performance
         */
//        if (mTextViewTraffic.getText().length() > DEFAULT_MAX_CHARACTERS) {
//            clearLogText();
//            return true;
//        }
        return false;
    }

    /**
     * Verifies that the log can hold more text, then appends the text to the log and if enabled scrolls
     * to the bottom of the log.
     *
     * @param text The text to append to the log.
     */
    public void appendLogText(String text) {
        checkOverflow();

        mTextViewTraffic.append(text);

        scrollToBottom();
    }

    /**
     * Calculates the difference between the top of the TextView and the height of the TextView then
     * scrolls to the difference.
     */
    public void scrollToBottom() {
        if (mTextViewTraffic != null && mTextViewTraffic.getLayout() != null) {
            final int scrollAmt = mTextViewTraffic.getLayout().getLineTop(mTextViewTraffic.getLineCount())
                    - mTextViewTraffic.getHeight();
            if (scrollAmt > 0 && scrollAmt < 1200) {
                mTextViewTraffic.scrollTo(0, scrollAmt);
            }
        }

//        else {
//            mTextViewTraffic.scrollTo(0, 0);
//        }

//        Log.d("TEST", String.valueOf(scrollAmt));
    }

    /**
     * Clears all text out of the TextView.
     */
    public void clearLogText() {
        mTextViewTraffic.setText("");
    }

    /**
     * Retrieves the text from the underlying TextView.
     *
     * @return String representation of the log.
     */
    public String getLogText() {
        if (mTextViewTraffic != null)
            return mTextViewTraffic.getText().toString();
        return null;
    }

    /**
     * Helper method to set the underlying TextView text. Will overwrite all text currently in the log.
     *
     * @param text Text to set.
     */
    public void setLogText(String text) {
        mTextViewTraffic.setText(text);
    }

    /**
     * Gets the maximum number of characters the log can hold.
     *
     * @return The maximum number of characters the log can hold.
     */
    public int getMaxCharacters() {
        return mMaxCharacters;
    }

    /**
     * Sets the maximum number of characters the log can hold.
     *
     * @param maxCharacters The new maximum number of characters the log can hold.
     */
    public void setMaxCharacters(int maxCharacters) {
        mMaxCharacters = maxCharacters;
    }
}