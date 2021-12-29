package sq.rogue.rosettadrone.settings;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import dji.common.mission.waypointv2.Action.ActionTypes;
import dji.common.mission.waypointv2.Action.WaypointActuator;
import dji.common.mission.waypointv2.Action.WaypointTrigger;
import dji.common.mission.waypointv2.Action.WaypointV2Action;
import sq.rogue.rosettadrone.R;
import sq.rogue.rosettadrone.fragment.actuator.AircraftActuatorFragment;
import sq.rogue.rosettadrone.fragment.actuator.CameraActuatorFragment;
import sq.rogue.rosettadrone.fragment.actuator.GimbalActuatorFragment;
import sq.rogue.rosettadrone.fragment.actuator.IActuatorCallback;
import sq.rogue.rosettadrone.fragment.trigger.AssociateTriggerFragment;
import sq.rogue.rosettadrone.fragment.trigger.BaseTriggerFragment;
import sq.rogue.rosettadrone.fragment.trigger.ITriggerCallback;
import sq.rogue.rosettadrone.fragment.trigger.ReachPointTriggerFragment;
import sq.rogue.rosettadrone.fragment.trigger.SimpleIntervalTriggerFragment;
import sq.rogue.rosettadrone.fragment.trigger.TrajectoryTriggerFragment;

public class WaypointV2ActionDialog extends DialogFragment implements ITriggerCallback {
    @BindView(R.id.tv_title)
    TextView tvTitle;
    @BindView(R.id.view_division)
    View viewDivision;
    @BindView(R.id.rv_added_action)
    RecyclerView rvAddedAction;
    @BindView(R.id.nsv_action_detail)
    NestedScrollView nsvActionDetail;
    @BindView(R.id.tv_ok)
    TextView tvOk;
    Unbinder unbinder;
    @BindView(R.id.tv_trigger_title)
    TextView tvTriggerTitle;
    @BindView(R.id.cl_trigger)
    ConstraintLayout clTrigger;
    @BindView(R.id.spinner_trigger_type)
    AppCompatSpinner spinnerTriggerType;
    @BindView(R.id.fl_trigger_info)
    FrameLayout flTriggerInfo;
    @BindView(R.id.tv_actuator_title)
    TextView tvActuatorTitle;
    @BindView(R.id.spinner_actuator_type)
    AppCompatSpinner spinnerActuatorType;
    @BindView(R.id.fl_actuator_info)
    FrameLayout flActuatorInfo;

    private WaypointActionAdapter actionAdapter;

    List<String> triggerType;
    List<String> actuatorType;
    List<String> actuatorNames;

    private AssociateTriggerFragment associateTriggerFragment;
    private SimpleIntervalTriggerFragment simpleIntervalTriggerFragment;
    private ReachPointTriggerFragment reachPointTriggerFragment;
    private TrajectoryTriggerFragment trajectoryTriggerFragment;

    private AircraftActuatorFragment aircraftActuatorFragment;
    private CameraActuatorFragment cameraActuatorFragment;
    private GimbalActuatorFragment gimbalActuatorFragment;

    private Fragment currentTriggerFragment;
    private Fragment currentActuatorFragment;

    private IActionCallback actionCallback;
    ArrayAdapter<String> actuatorAdapter;

    private int position;
    private int size;


    public void setActionCallback(IActionCallback actionCallback) {
        this.actionCallback = actionCallback;
    }

    public void setSize(int size) {
        this.size = size;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.dialog_waypoint_v2, container, false);
        unbinder = ButterKnife.bind(this, root);
        initData();
        initView();
        return root;
    }

    private void initData() {
        triggerType = new ArrayList<>();
        actuatorType = new ArrayList<>();
        triggerType.add("Please select trigger type");
        for (ActionTypes.ActionTriggerType type : ActionTypes.ActionTriggerType.values()) {
            if (type == ActionTypes.ActionTriggerType.COMPLEX_REACH_POINTS) {
                // not support
                continue;
            }
            triggerType.add(type.name());
        }
        actuatorNames = new ArrayList();
        actuatorNames.add("Please select actuator type");
        actuatorNames.add(ActionTypes.ActionActuatorType.GIMBAL.name());
        actuatorNames.add(ActionTypes.ActionActuatorType.CAMERA.name());
        actuatorNames.add(ActionTypes.ActionActuatorType.AIRCRAFT_CONTROL.name());
        actuatorType.addAll(actuatorNames);
    }

    private void initView() {
        rvAddedAction.setLayoutManager(new LinearLayoutManager(getContext()));
        rvAddedAction.addItemDecoration(new DividerItemDecoration(getContext(), LinearLayout.HORIZONTAL));

        actionAdapter = new WaypointActionAdapter(getContext(), new ArrayList<>());
        rvAddedAction.setAdapter(actionAdapter);

        ArrayAdapter<String> triggerAdapter = new ArrayAdapter(getActivity(),
                android.R.layout.simple_spinner_dropdown_item, triggerType);
        spinnerTriggerType.setAdapter(triggerAdapter);
        spinnerTriggerType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    hideTriggerFragment();
                    return;
                }
                switch (ActionTypes.ActionTriggerType.valueOf(triggerType.get(position))) {
                    case ASSOCIATE:
                        if (associateTriggerFragment == null) {
                            associateTriggerFragment = AssociateTriggerFragment.newInstance();
                        }
                        associateTriggerFragment.setSize(actionAdapter.getData().size());
                        showFragment(associateTriggerFragment, R.id.fl_trigger_info);
                        currentTriggerFragment = associateTriggerFragment;
                        break;
                    case SIMPLE_INTERVAL:
                        if (simpleIntervalTriggerFragment == null) {
                            simpleIntervalTriggerFragment = SimpleIntervalTriggerFragment.newInstance();
                        }
                        simpleIntervalTriggerFragment.setSize(size);
                        showFragment(simpleIntervalTriggerFragment, R.id.fl_trigger_info);
                        currentTriggerFragment = simpleIntervalTriggerFragment;
                        break;
                    case REACH_POINT:
                        if (reachPointTriggerFragment == null) {
                            reachPointTriggerFragment = ReachPointTriggerFragment.newInstance();
                        }
                        reachPointTriggerFragment.setSize(size);
                        showFragment(reachPointTriggerFragment, R.id.fl_trigger_info);
                        currentTriggerFragment = reachPointTriggerFragment;
                        break;
                    case TRAJECTORY:
                        if (trajectoryTriggerFragment == null) {
                            trajectoryTriggerFragment = TrajectoryTriggerFragment.newInstance();
                        }
                        trajectoryTriggerFragment.setSize(size);
                        showFragment(trajectoryTriggerFragment, R.id.fl_trigger_info);
                        currentTriggerFragment = trajectoryTriggerFragment;
                        break;
                    case UNKNOWN:
                        hideTriggerFragment();
                        break;
                }
                hideActuatorFragment();
                changeActuatorAdapter(ActionTypes.ActionTriggerType.valueOf(triggerType.get(position)));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        actuatorAdapter = new ArrayAdapter(getActivity(),
                android.R.layout.simple_spinner_dropdown_item, actuatorType);
        spinnerActuatorType.setAdapter(actuatorAdapter);
        spinnerActuatorType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    hideActuatorFragment();
                    return;
                }
                switch (ActionTypes.ActionActuatorType.valueOf(actuatorType.get(position))) {
                    case SPRAY:
                        // 暂不支持
                        Tools.showToast(getActivity(), "Not Support");
                        hideActuatorFragment();
                        break;
                    case CAMERA:
                        if (cameraActuatorFragment == null) {
                            cameraActuatorFragment = CameraActuatorFragment.newInstance();
                        }
                        showFragment(cameraActuatorFragment, R.id.fl_actuator_info);
                        currentActuatorFragment = cameraActuatorFragment;
                        break;
                    case PLAYLOAD:
                        Tools.showToast(getActivity(), "Not Support");
                        hideActuatorFragment();
                        break;
                    case GIMBAL:
                        if (gimbalActuatorFragment == null) {
                            gimbalActuatorFragment = GimbalActuatorFragment.newInstance(WaypointV2ActionDialog.this);
                        }
                        showFragment(gimbalActuatorFragment, R.id.fl_actuator_info);
                        currentActuatorFragment = gimbalActuatorFragment;
                        gimbalActuatorFragment.flush();
                        break;
                    case AIRCRAFT_CONTROL:
                        if (aircraftActuatorFragment == null) {
                            aircraftActuatorFragment = AircraftActuatorFragment.newInstance();
                        }
                        showFragment(aircraftActuatorFragment, R.id.fl_actuator_info);
                        currentActuatorFragment = aircraftActuatorFragment;
                        break;
                    case NAVIGATION:
                        Tools.showToast(getActivity(), "Not Support");
                        hideActuatorFragment();
                        break;
                    case UNKNOWN:
                        hideActuatorFragment();
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void changeActuatorAdapter(ActionTypes.ActionTriggerType triggerType) {
        switch (triggerType) {
            case COMPLEX_REACH_POINTS:
                flushActuator();
                break;
            case ASSOCIATE:
                flushActuator();
                break;
            case SIMPLE_INTERVAL:
                flushActuator();
                break;
            case REACH_POINT:
                flushActuator();
                break;
            case TRAJECTORY:
                // this trigger is one by one with ActionTypes.GimbalOperationType#AIRCRAFT_CONTROL_GIMBAL.
                actuatorType.removeAll(actuatorNames);
                actuatorType.add("Please select actuator type");
                actuatorType.add(ActionTypes.ActionActuatorType.GIMBAL.name());
                break;
            default:
                break;
        }
    }

    private void flushActuator() {
        actuatorType.clear();
        actuatorType.addAll(actuatorNames);
        actionAdapter.notifyDataSetChanged();
    }

    private void hideActuatorFragment() {
        if (currentActuatorFragment == null) {
            return;
        }
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.hide(currentActuatorFragment);
        transaction.commit();
    }


    private void showFragment(Fragment fragment, @IdRes int id) {
        if (fragment == null || fragment.isVisible()) {
            return;
        }

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        if (fragment.isAdded()) {
            if (fragment instanceof BaseTriggerFragment && currentTriggerFragment != null) {
                transaction.hide(currentTriggerFragment);
            } else if (fragment instanceof IActuatorCallback && currentActuatorFragment != null) {
                transaction.hide(currentActuatorFragment);
            }
            transaction.show(fragment);
        } else {
            transaction.replace(id, fragment);
        }
        transaction.commit();
    }

    private void hideTriggerFragment() {
        if (currentTriggerFragment == null) {
            return;
        }
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.hide(currentTriggerFragment);
        transaction.commit();
    }

    @Override
    public void onStart() {
        super.onStart();
        DisplayMetrics dm = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
        getDialog().getWindow().setLayout((int) (dm.widthPixels * 0.9), (int) (dm.heightPixels * 0.9));

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    @OnClick({R.id.tv_ok, R.id.tv_add})
    public void onViewClick(View v) {
        switch (v.getId()) {
            case R.id.tv_ok:
                if (actionCallback != null) {
                    actionCallback.getActions(actionAdapter.getData());
                }
                dismiss();
                position = 0;
                break;
            case R.id.tv_add:
                WaypointTrigger trigger = getWaypointTrigger();
                WaypointActuator actuator = getWaypointActuator();
                boolean result = verifyAction(trigger, actuator);
                if (!result) {
                    break;
                }
                WaypointV2Action action = new WaypointV2Action.Builder()
                        .setTrigger(trigger)
                        .setActuator(actuator)
                        .setActionID(++position)
                        .build();
                actionAdapter.addItem(action);
                updateSize();
                break;
        }
    }

    private boolean verifyAction(WaypointTrigger trigger, WaypointActuator actuator) {
        if (trigger == null || actuator == null) {
            Tools.showToast(getActivity(), "add fail");
            return false;
        }
        if (actuator.getActuatorType() == ActionTypes.ActionActuatorType.GIMBAL
                && actuator.getGimbalActuatorParam().getOperationType() == ActionTypes.GimbalOperationType.AIRCRAFT_CONTROL_GIMBAL) {
            if (trigger.getTriggerType() != ActionTypes.ActionTriggerType.TRAJECTORY) {
                Tools.showToast(getActivity(), "this trigger `TRAJECTORY` is one by one with `ActionTypes.GimbalOperationType.AIRCRAFT_CONTROL_GIMBAL`");
                return false;
            }
        }
        if (trigger.getTriggerType() == ActionTypes.ActionTriggerType.TRAJECTORY) {
            if (actuator.getActuatorType() != ActionTypes.ActionActuatorType.GIMBAL
                    || actuator.getGimbalActuatorParam().getOperationType() != ActionTypes.GimbalOperationType.AIRCRAFT_CONTROL_GIMBAL) {
                Tools.showToast(getActivity(), "this trigger `TRAJECTORY` is one by one with `ActionTypes.GimbalOperationType.AIRCRAFT_CONTROL_GIMBAL`");
                return false;
            }
        }
        return true;
    }

    private void updateSize() {
        if (reachPointTriggerFragment != null) {
            reachPointTriggerFragment.setSize(actionAdapter.getItemCount());
        }
        if (associateTriggerFragment != null) {
            associateTriggerFragment.setSize(actionAdapter.getItemCount());
        }
        if (simpleIntervalTriggerFragment != null) {
            simpleIntervalTriggerFragment.setSize(actionAdapter.getItemCount());
        }
        if (trajectoryTriggerFragment != null) {
            trajectoryTriggerFragment.setSize(actionAdapter.getItemCount());
        }
    }

    private WaypointActuator getWaypointActuator() {
        if (currentActuatorFragment == null) {
            return null;
        }
        if (currentActuatorFragment instanceof IActuatorCallback) {
            return ((IActuatorCallback) currentActuatorFragment).getActuator();
        }
        return null;
    }

    private WaypointTrigger getWaypointTrigger() {
        if (currentTriggerFragment == null) {
            return null;
        }
        if (currentTriggerFragment instanceof ITriggerCallback) {
            return ((ITriggerCallback) currentTriggerFragment).getTrigger();
        }
        return null;
    }

    @Override
    public WaypointTrigger getTrigger() {
        return getWaypointTrigger();
    }

    public interface IActionCallback {
        void getActions(List<WaypointV2Action> actions);
    }
}
