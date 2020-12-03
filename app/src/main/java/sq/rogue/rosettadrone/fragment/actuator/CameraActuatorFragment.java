package sq.rogue.rosettadrone.fragment.actuator;

import android.graphics.PointF;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import dji.common.mission.waypointv2.Action.ActionTypes;
import dji.common.mission.waypointv2.Action.WaypointActuator;
import dji.common.mission.waypointv2.Action.WaypointCameraActuatorParam;
import dji.common.mission.waypointv2.Action.WaypointCameraFocusParam;
import dji.common.mission.waypointv2.Action.WaypointCameraZoomParam;
import sq.rogue.rosettadrone.R;
import sq.rogue.rosettadrone.settings.Tools;

;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CameraActuatorFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CameraActuatorFragment extends Fragment implements IActuatorCallback {

    @BindView(R.id.rb_shoot_single_photo)
    RadioButton rbShootSinglePhoto;
    @BindView(R.id.rb_start_record_video)
    RadioButton rbStartRecordVideo;
    @BindView(R.id.rb_stop_record_video)
    RadioButton rbStopRecordVideo;
    @BindView(R.id.rb_focus)
    RadioButton rbFocus;
    @BindView(R.id.rb_zoom)
    RadioButton rbZoom;
    @BindView(R.id.radio_camera_type)
    RadioGroup radioCameraType;
    @BindView(R.id.et_zoom)
    EditText etZoom;

    @BindView(R.id.et_focus_target_x)
    EditText etFocusTargetX;
    @BindView(R.id.et_focus_target_y)
    EditText etFocusTargetY;

    Unbinder unbinder;

    public static CameraActuatorFragment newInstance() {
        CameraActuatorFragment fragment = new CameraActuatorFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_camera_actuator, container, false);
        unbinder = ButterKnife.bind(this, root);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        radioCameraType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.rb_shoot_single_photo:
                        hide(R.id.et_focus_target_x, R.id.et_focus_target_y, R.id.et_zoom);
                        break;
                    case R.id.rb_start_record_video:
                        hide(R.id.et_focus_target_x, R.id.et_focus_target_y, R.id.et_zoom);
                        break;
                    case R.id.rb_stop_record_video:
                        hide(R.id.et_focus_target_x, R.id.et_focus_target_y, R.id.et_zoom);
                        break;
                    case R.id.rb_focus:
                        hide(R.id.et_zoom);
                        show(R.id.et_focus_target_x, R.id.et_focus_target_y);
                        break;
                    case R.id.rb_zoom:
                        show(R.id.et_zoom);
                        hide(R.id.et_focus_target_x, R.id.et_focus_target_y);
                        break;
                }
            }
        });
    }

    private void hide(int... ids) {
        for (int id : ids) {
            getView().findViewById(id).setVisibility(View.GONE);
        }
    }

    private void show(int... ids) {
        for (int id : ids) {
            getView().findViewById(id).setVisibility(View.VISIBLE);
        }
    }

    @Override
    public WaypointActuator getActuator() {
        int focalLength = Tools.getInt(etZoom.getText().toString(), 10);
        ActionTypes.CameraOperationType type = getType();
        WaypointCameraFocusParam focusParam = new WaypointCameraFocusParam.Builder()
                .focusTarget(new PointF(Tools.getFloat(etFocusTargetX.getText().toString(), 0.5f), Tools.getFloat(etFocusTargetY.getText().toString(), 0.5f)))
                .build();
        WaypointCameraZoomParam zoomParam = new WaypointCameraZoomParam.Builder()
                .setFocalLength(focalLength)
                .build();
        WaypointCameraActuatorParam actuatorParam = new WaypointCameraActuatorParam.Builder()
                .setCameraOperationType(type)
                .setFocusParam(focusParam)
                .setZoomParam(zoomParam)
                .build();
        return new WaypointActuator.Builder()
                .setActuatorType(ActionTypes.ActionActuatorType.CAMERA)
                .setCameraActuatorParam(actuatorParam)
                .build();
    }

    public ActionTypes.CameraOperationType getType() {
        switch (radioCameraType.getCheckedRadioButtonId()) {
            case R.id.rb_shoot_single_photo:
                return ActionTypes.CameraOperationType.SHOOT_SINGLE_PHOTO;
            case R.id.rb_start_record_video:
                return ActionTypes.CameraOperationType.START_RECORD_VIDEO;
            case R.id.rb_stop_record_video:
                return ActionTypes.CameraOperationType.STOP_RECORD_VIDEO;
            case R.id.rb_focus:
                return ActionTypes.CameraOperationType.FOCUS;
            case R.id.rb_zoom:
                return ActionTypes.CameraOperationType.ZOOM;
        }
        return ActionTypes.CameraOperationType.SHOOT_SINGLE_PHOTO;
    }
}
