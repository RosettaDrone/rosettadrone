package sq.rogue.rosettadrone.settings;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import androidx.recyclerview.widget.RecyclerView;
import dji.common.mission.waypointv2.Action.WaypointV2Action;
import sq.rogue.rosettadrone.R;

public class WaypointActionAdapter extends RecyclerView.Adapter<WaypointActionAdapter.ViewHolder> {
    private List<WaypointV2Action> data;
    private LayoutInflater mInflater;

    public WaypointActionAdapter(Context context, List<WaypointV2Action> data) {
        this.mInflater = LayoutInflater.from(context);
        this.data = data;
    }

    public void addItem(WaypointV2Action action) {
        if (action == null || data == null) {
            return;
        }
        data.add(action);
        notifyItemInserted(getItemCount() - 1);
    }

    public List<WaypointV2Action> getData() {
        return data;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(mInflater.inflate(R.layout.item_waypoint_action, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.setData(data.get(position));
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private TextView triggerTv;
        private TextView actuatorTv;
        private TextView actionIdTv;

        public ViewHolder(View itemView) {
            super(itemView);
            triggerTv = itemView.findViewById(R.id.tv_trigger);
            actuatorTv = itemView.findViewById(R.id.tv_actuator);
            actionIdTv = itemView.findViewById(R.id.tv_action_id);
        }

        public void setData(WaypointV2Action data) {
            if (data.getTrigger() != null) {
                triggerTv.setText("Trigger: " + data.getTrigger().getTriggerType().name());
            }
            if (data.getActuator() != null) {
                actuatorTv.setText("Actuator: " + data.getActuator().getActuatorType().name());
            }
            actionIdTv.setText("ActionId:" + data.getActionID());
        }
    }
}
