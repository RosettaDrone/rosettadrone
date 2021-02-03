package sq.rogue.rosettadrone.fragment.trigger;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioGroup;

import androidx.appcompat.widget.AppCompatRadioButton;
import androidx.fragment.app.Fragment;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import dji.common.mission.waypointv2.Action.ActionTypes;
import dji.common.mission.waypointv2.Action.WaypointIntervalTriggerParam;
import dji.common.mission.waypointv2.Action.WaypointTrigger;
import sq.rogue.rosettadrone.R;
import sq.rogue.rosettadrone.settings.Tools;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SimpleIntervalTriggerFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SimpleIntervalTriggerFragment extends BaseTriggerFragment implements ITriggerCallback {


    @BindView(R.id.et_start_index)
    EditText etStartIndex;
    @BindView(R.id.radio_group_type)
    RadioGroup radioGroupType;
    @BindView(R.id.et_value)
    EditText etValue;
    @BindView(R.id.rb_distance)
    AppCompatRadioButton rbDistance;
    @BindView(R.id.rb_time)
    AppCompatRadioButton rbTime;

    Unbinder unbinder;

    public static SimpleIntervalTriggerFragment newInstance() {
        SimpleIntervalTriggerFragment fragment = new SimpleIntervalTriggerFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_simple_interval_trigger, container, false);
        unbinder = ButterKnife.bind(this, root);
        return root;
    }

    @Override
    public WaypointTrigger getTrigger() {
        float value = Tools.getFloat(etValue.getText().toString(), 1.1f);
        int start = Tools.getInt(etStartIndex.getText().toString(), 1);

        if (start > size) {
            Tools.showToast(getActivity(), "start can`t bigger waypoint mission size, size=" + size);
            return null;
        }

        ActionTypes.ActionIntervalType type = rbDistance.isChecked()
                ? ActionTypes.ActionIntervalType.DISTANCE : ActionTypes.ActionIntervalType.TIME;
        WaypointIntervalTriggerParam param = new WaypointIntervalTriggerParam.Builder()
                .setStartIndex(start)
                .setInterval(value)
                .setType(type)
                .build();
        return new WaypointTrigger.Builder()
                .setTriggerType(ActionTypes.ActionTriggerType.SIMPLE_INTERVAL)
                .setIntervalTriggerParam(param)
                .build();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }
}
