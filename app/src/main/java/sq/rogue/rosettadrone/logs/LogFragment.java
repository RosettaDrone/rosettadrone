package sq.rogue.rosettadrone.logs;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;
import android.widget.TextView;

import sq.rogue.rosettadrone.R;

public class LogFragment extends Fragment {

//    final Handler handler = new Handler();
    private final String TAG = getClass().getSimpleName();
    //    private final String INSTANCE_STATE_KEY = "saved_state";
    private final int DEFAULT_MAX_CHARACTERS = 200000;
//    GestureDetector gestureDetector;
    private TextView mTextViewTraffic;
    private ScrollView mScrollView;
    //    Runnable mLongPressed = new Runnable() {
//        public void run() {
//            clearLogText();
//        }
//    };
    private boolean mViewAtBottom = true;
    private int mMaxCharacters = DEFAULT_MAX_CHARACTERS;
    private int LONG_PRESS_TIMEOUT = 3000;

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

        mScrollView = (ScrollView) view.findViewById(R.id.textAreaScrollerTraffic);

        mTextViewTraffic.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable arg0) {
                if (mViewAtBottom) {
                    mScrollView.post(new Runnable() {
                        @Override
                        public void run() {
                            mScrollView.fullScroll(View.FOCUS_DOWN);
                        }
                    });
                }
            }

            @Override
            public void beforeTextChanged(CharSequence arg0, int arg1,
                                          int arg2, int arg3) {
                //override stub
            }

            @Override
            public void onTextChanged(CharSequence arg0, int arg1, int arg2,
                                      int arg3) {
                //override stub
            }
        });
        mScrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                if (mScrollView != null) {
                    if (mScrollView.getChildAt(0).getBottom() <= (mScrollView.getHeight() + mScrollView.getScrollY()) + 500) {
                        mViewAtBottom = true;

                    } else {
                        mViewAtBottom = false;
                    }
                }
            }
        });

//        mTextViewTraffic.setHorizontallyScrolling(true);

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