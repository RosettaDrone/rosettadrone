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
import dji.common.mission.waypointv2.Action.WaypointTrajectoryTriggerParam;
import dji.common.mission.waypointv2.Action.WaypointTrigger;
import sq.rogue.rosettadrone.R;
import sq.rogue.rosettadrone.settings.Tools;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link TrajectoryTriggerFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class TrajectoryTriggerFragment extends BaseTriggerFragment implements ITriggerCallback {

    @BindView(R.id.et_start_index)
    EditText etStartIndex;
    @BindView(R.id.et_end_index)
    EditText etEndIndex;
    Unbinder unbinder;

    public static TrajectoryTriggerFragment newInstance() {
        TrajectoryTriggerFragment fragment = new TrajectoryTriggerFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_trajectory_trigger, container, false);
        unbinder = ButterKnife.bind(this, root);
        return root;
    }

    @Override
    public WaypointTrigger getTrigger() {
        int start = Tools.getInt(etStartIndex.getText().toString(), 1);
        int end = Tools.getInt(etEndIndex.getText().toString(), 1);

        WaypointTrajectoryTriggerParam param = new WaypointTrajectoryTriggerParam.Builder()
                .setEndIndex(end)
                .setStartIndex(start)
                .build();
        return new WaypointTrigger.Builder()
                .setTriggerType(ActionTypes.ActionTriggerType.TRAJECTORY)
                .setTrajectoryParam(param)
                .build();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }
}
