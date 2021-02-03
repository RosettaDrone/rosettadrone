package sq.rogue.rosettadrone.fragment.actuator;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.fragment.app.Fragment;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.mission.waypointv2.Action.ActionTypes;
import dji.common.mission.waypointv2.Action.WaypointActuator;
import dji.common.mission.waypointv2.Action.WaypointGimbalActuatorParam;
import sq.rogue.rosettadrone.R;
import sq.rogue.rosettadrone.fragment.trigger.ITriggerCallback;
import sq.rogue.rosettadrone.settings.Tools;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link GimbalActuatorFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class GimbalActuatorFragment extends Fragment implements IActuatorCallback {
    private ITriggerCallback callback;

    Unbinder unbinder;
    @BindView(R.id.tv_title)
    TextView tvTitle;
    @BindView(R.id.rb_rotate_gimbal)
    RadioButton rbRotateGimbal;
    @BindView(R.id.rb_aircraft_control_gimbal)
    RadioButton rbAircraftControlGimbal;
    @BindView(R.id.radio_gimbal_type)
    RadioGroup radioGimbalType;
    @BindView(R.id.et_gimbal_roll)
    EditText etGimbalRoll;
    @BindView(R.id.et_gimbal_pitch)
    EditText etGimbalPitch;
    @BindView(R.id.et_gimbal_yaw)
    EditText etGimbalYaw;
    @BindView(R.id.et_duration_time)
    EditText etDurationTime;
    @BindView(R.id.box_absulote)
    AppCompatCheckBox boxAbsulote;
    @BindView(R.id.box_rollIgnore)
    AppCompatCheckBox boxRollIgnore;
    @BindView(R.id.box_pitch_ignore)
    AppCompatCheckBox boxPitchIgnore;
    @BindView(R.id.box_yaw_igore)
    AppCompatCheckBox boxYawIgore;
    @BindView(R.id.box_abs_yaw_ref)
    AppCompatCheckBox boxAbsYawRef;

    public static GimbalActuatorFragment newInstance(ITriggerCallback callback) {
        GimbalActuatorFragment fragment = new GimbalActuatorFragment(callback);
        return fragment;
    }

    private GimbalActuatorFragment(ITriggerCallback callback) {
        this.callback = callback;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_gimbal_actuator, container, false);
        unbinder = ButterKnife.bind(this, root);
        flush();
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    public void flush() {
        if (getContext() == null) {
            return;
        }
        if (callback.getTrigger() == null) {
            Toast.makeText(getContext(), "please select trigger.", Toast.LENGTH_SHORT).show();
            rbAircraftControlGimbal.setVisibility(View.GONE);
            rbRotateGimbal.setVisibility(View.VISIBLE);
            return;
        }
        if (callback.getTrigger().getTriggerType() == ActionTypes.ActionTriggerType.TRAJECTORY) {
            rbAircraftControlGimbal.setVisibility(View.VISIBLE);
            rbRotateGimbal.setVisibility(View.GONE);
        } else {
            rbAircraftControlGimbal.setVisibility(View.GONE);
            rbRotateGimbal.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public WaypointActuator getActuator() {
        float roll = Tools.getFloat(etGimbalRoll.getText().toString(), 0.1f);
        float pitch = Tools.getFloat(etGimbalPitch.getText().toString(), 0.2f);
        float yaw = Tools.getFloat(etGimbalYaw.getText().toString(), 0.3f);
        int duration = Tools.getInt(etDurationTime.getText().toString(), 10);
        ActionTypes.GimbalOperationType type = getType();

        Rotation.Builder rotationBuilder = new Rotation.Builder();
        if (!boxRollIgnore.isChecked()) {
            rotationBuilder.roll(roll);
        }
        if (!boxPitchIgnore.isChecked()) {
            rotationBuilder.pitch(pitch);
        }
        if (!boxYawIgore.isChecked()) {
            rotationBuilder.yaw(yaw);
        }
        if (boxAbsulote.isChecked()) {
            rotationBuilder.mode(RotationMode.ABSOLUTE_ANGLE);
        } else {
            rotationBuilder.mode(RotationMode.RELATIVE_ANGLE);
        }
        rotationBuilder.time(duration);
        WaypointGimbalActuatorParam actuatorParam = new WaypointGimbalActuatorParam.Builder()
                .operationType(type)
                .rotation(rotationBuilder.build())
                .build();
        return new WaypointActuator.Builder()
                .setActuatorType(ActionTypes.ActionActuatorType.GIMBAL)
                .setGimbalActuatorParam(actuatorParam)
                .build();
    }

    public ActionTypes.GimbalOperationType getType() {
        switch (radioGimbalType.getCheckedRadioButtonId()) {
            case R.id.rb_rotate_gimbal:
                return ActionTypes.GimbalOperationType.ROTATE_GIMBAL;
            case R.id.rb_aircraft_control_gimbal:
                // Rotates the gimbal. Only valid when the trigger type is `TRAJECTORY`.
                return ActionTypes.GimbalOperationType.AIRCRAFT_CONTROL_GIMBAL;
        }
        return ActionTypes.GimbalOperationType.UNKNOWN;
    }
}
