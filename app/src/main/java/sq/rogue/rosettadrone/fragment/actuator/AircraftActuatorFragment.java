package sq.rogue.rosettadrone.fragment.actuator;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import dji.common.mission.waypointv2.Action.ActionTypes;
import dji.common.mission.waypointv2.Action.WaypointActuator;
import dji.common.mission.waypointv2.Action.WaypointAircraftControlParam;
import dji.common.mission.waypointv2.Action.WaypointAircraftControlRotateYawParam;
import dji.common.mission.waypointv2.Action.WaypointAircraftControlStartStopFlyParam;
import dji.common.mission.waypointv2.WaypointV2MissionTypes;
import sq.rogue.rosettadrone.R;
import sq.rogue.rosettadrone.settings.Tools;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link AircraftActuatorFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class AircraftActuatorFragment extends Fragment implements IActuatorCallback {


    Unbinder unbinder;
    @BindView(R.id.rb_rotate_yaw)
    RadioButton rbRotateYaw;
    @BindView(R.id.rb_start_stop_fly)
    RadioButton rbStartStopFly;
    @BindView(R.id.radio_type)
    RadioGroup radioType;
    @BindView(R.id.tv_tip_yaw)
    TextView tvTipYaw;
    @BindView(R.id.box_yaw_relative)
    AppCompatCheckBox boxYawRelative;
    @BindView(R.id.box_yaw_clockwise)
    AppCompatCheckBox boxYawClockwise;
    @BindView(R.id.cl_yaw)
    ConstraintLayout clYaw;
    @BindView(R.id.box_start_stop_fly)
    AppCompatCheckBox boxStartStopFly;
    @BindView(R.id.cl_start_stop)
    ConstraintLayout clStartStop;
    @BindView(R.id.et_yaw_angle)
    TextView yawAngle;

    private ActionTypes.AircraftControlType type = ActionTypes.AircraftControlType.UNKNOWN;

    public static AircraftActuatorFragment newInstance() {
        AircraftActuatorFragment fragment = new AircraftActuatorFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_aircraft_actuator, container, false);
        unbinder = ButterKnife.bind(this, root);
        radioType.setOnCheckedChangeListener((group, checkedId) -> {
            switch (checkedId) {
                case R.id.rb_rotate_yaw:
                    clYaw.setVisibility(View.VISIBLE);
                    clStartStop.setVisibility(View.GONE);
                    type = ActionTypes.AircraftControlType.ROTATE_YAW;
                    break;
                case R.id.rb_start_stop_fly:
                    clStartStop.setVisibility(View.VISIBLE);
                    clYaw.setVisibility(View.GONE);
                    type = ActionTypes.AircraftControlType.START_STOP_FLY;
                    break;
            }
        });
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override
    public WaypointActuator getActuator() {
        float yaw = Tools.getFloat(yawAngle.getText().toString(), 0);
        WaypointAircraftControlRotateYawParam yawParam = new WaypointAircraftControlRotateYawParam.Builder()
                .setDirection(boxYawClockwise.isChecked() ? WaypointV2MissionTypes.WaypointV2TurnMode.CLOCKWISE : WaypointV2MissionTypes.WaypointV2TurnMode.COUNTER_CLOCKWISE)
                .setRelative(boxYawRelative.isChecked())
                .setYawAngle(yaw)
                .build();
        WaypointAircraftControlStartStopFlyParam startParam = new WaypointAircraftControlStartStopFlyParam.Builder()
                .setStartFly(boxStartStopFly.isChecked())
                .build();
        WaypointAircraftControlParam controlParam = new WaypointAircraftControlParam.Builder()
                .setAircraftControlType(type)
                .setFlyControlParam(startParam)
                .setRotateYawParam(yawParam)
                .build();

        return new WaypointActuator.Builder()
                .setActuatorType(ActionTypes.ActionActuatorType.AIRCRAFT_CONTROL)
                .setAircraftControlActuatorParam(controlParam)
                .build();
    }
}
