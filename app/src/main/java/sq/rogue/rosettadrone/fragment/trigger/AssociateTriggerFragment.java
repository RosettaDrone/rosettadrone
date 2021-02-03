package sq.rogue.rosettadrone.fragment.trigger;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioGroup;

import androidx.appcompat.widget.AppCompatRadioButton;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import dji.common.mission.waypointv2.Action.ActionTypes;
import dji.common.mission.waypointv2.Action.WaypointTrigger;
import dji.common.mission.waypointv2.Action.WaypointV2AssociateTriggerParam;
import sq.rogue.rosettadrone.R;
import sq.rogue.rosettadrone.settings.Tools;

public class AssociateTriggerFragment extends BaseTriggerFragment implements ITriggerCallback {


    @BindView(R.id.et_wait_time)
    EditText etWaitTime;
    Unbinder unbinder;
    @BindView(R.id.radio_group_type)
    RadioGroup radioGroupType;
    @BindView(R.id.rb_sync)
    AppCompatRadioButton rbSync;
    @BindView(R.id.rb_after)
    AppCompatRadioButton rbAfter;
    @BindView(R.id.et_action_id)
    EditText etActionId;

    public AssociateTriggerFragment() {
    }

    public static AssociateTriggerFragment newInstance() {
        AssociateTriggerFragment fragment = new AssociateTriggerFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_associate_trigger, container, false);
        unbinder = ButterKnife.bind(this, root);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override
    public WaypointTrigger getTrigger() {
        float waitTime = Tools.getFloat(etWaitTime.getText().toString(), 1);
        ActionTypes.AssociatedTimingType type = rbSync.isChecked()
                ? ActionTypes.AssociatedTimingType.SIMULTANEOUSLY : ActionTypes.AssociatedTimingType.AFTER_FINISHED;
        int actionId = Tools.getInt(etActionId.getText().toString(), 1);

        if (actionId > size) {
            Tools.showToast(getActivity(), "actionId can`t bigger existed action size, size=" + size);
            return null;
        }

        WaypointV2AssociateTriggerParam param = new WaypointV2AssociateTriggerParam.Builder()
                .setAssociateType(type)
                .setWaitingTime(waitTime)
                .setAssociateActionID(actionId)
                .build();
        return new WaypointTrigger.Builder()
                .setTriggerType(ActionTypes.ActionTriggerType.ASSOCIATE)
                .setAssociateParam(param)
                .build();
    }
}
