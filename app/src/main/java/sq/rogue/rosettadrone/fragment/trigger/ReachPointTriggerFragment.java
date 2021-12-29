package sq.rogue.rosettadrone.fragment.trigger;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.fragment.app.Fragment;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import dji.common.mission.waypointv2.Action.ActionTypes;
import dji.common.mission.waypointv2.Action.WaypointReachPointTriggerParam;
import dji.common.mission.waypointv2.Action.WaypointTrigger;
import sq.rogue.rosettadrone.R;
import sq.rogue.rosettadrone.settings.Tools;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ReachPointTriggerFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ReachPointTriggerFragment extends BaseTriggerFragment implements ITriggerCallback {


    @BindView(R.id.et_start_index)
    EditText etStartIndex;
    @BindView(R.id.et_auto_terminate_count)
    EditText etAutoTerminateCount;
    Unbinder unbinder;

    public static ReachPointTriggerFragment newInstance() {
        ReachPointTriggerFragment fragment = new ReachPointTriggerFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_simple_reach_point_trigger, container, false);
        unbinder = ButterKnife.bind(this, root);
        return root;
    }

    @Override
    public WaypointTrigger getTrigger() {
        int start = Tools.getInt(etStartIndex.getText().toString(), 1);
        int count = Tools.getInt(etAutoTerminateCount.getText().toString(), 1);

        if (start > size) {
            Tools.showToast(getActivity(), "start can`t bigger waypoint mission size, size=" + size);
            return null;
        }

        WaypointReachPointTriggerParam param = new WaypointReachPointTriggerParam.Builder()
                .setAutoTerminateCount(count)
                .setStartIndex(start)
                .build();
        return new WaypointTrigger.Builder()
                .setTriggerType(ActionTypes.ActionTriggerType.REACH_POINT)
                .setReachPointParam(param)
                .build();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }
}
