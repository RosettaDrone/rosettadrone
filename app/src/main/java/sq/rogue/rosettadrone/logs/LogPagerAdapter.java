package sq.rogue.rosettadrone.logs;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

public class LogPagerAdapter extends FragmentPagerAdapter {

    private LogFragment[] fragments = new LogFragment[3];


    public LogPagerAdapter(LogFragment[] fragments, FragmentManager fm) {
        super(fm);
        this.fragments = fragments;
    }

    @Override
    public Fragment getItem(int position) {
        return fragments[position];
    }

    @Override
    public int getCount() {
        return 3;
    }
}