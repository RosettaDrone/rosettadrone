package sq.rogue.rosettadrone;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;
import android.widget.TextView;

public class LogFragment extends Fragment {

    private final int DEFAULT_MAX_CHARACTERS = 200000;

    private TextView mTextViewTraffic;
    private ScrollView mScrollView;
    private boolean mViewAtBottom = true;

    private int mMaxCharacters = DEFAULT_MAX_CHARACTERS;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log, container, false);
        mTextViewTraffic = (TextView) view.findViewById(R.id.textView_traffic);
        mScrollView = (ScrollView) view.findViewById(R.id.textAreaScrollerTraffic);

        mTextViewTraffic.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable arg0) {
                if (mViewAtBottom)
                    mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
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
                    if (mScrollView.getChildAt(0).getBottom() <= (mScrollView.getHeight() + mScrollView.getScrollY()) + 200) {
                        mViewAtBottom = true;
                    } else {
                        mViewAtBottom = false;
                    }
                }
            }
        });
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

    }

    public void appendLogText(String text) {
        String newText = mTextViewTraffic.getText().toString() + text;
        int overflow = newText.length() - mMaxCharacters;
        if (overflow > 0) {
            newText = newText.substring(overflow); // trim off oldest characters
            newText = newText.split("\n", 2)[1]; // ensure the remainder starts on a new message
        }
        mTextViewTraffic.setText(newText);
    }

    public void clearLogText(String text) {
        mTextViewTraffic.setText("");
    }

    public void setLogText(String text) {
        mTextViewTraffic.setText(text);
    }

    public String getLogText() {
        if (mTextViewTraffic != null)
            return mTextViewTraffic.getText().toString();
        return "";
    }

    public int getMaxCharacters() {
        return mMaxCharacters;
    }

    public void setMaxCharacters(int maxCharacters) {
        mMaxCharacters = maxCharacters;
    }
}
