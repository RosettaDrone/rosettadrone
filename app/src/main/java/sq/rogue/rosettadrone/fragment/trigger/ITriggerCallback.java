package sq.rogue.rosettadrone.fragment.trigger;

import dji.common.mission.waypointv2.Action.WaypointTrigger;

public interface ITriggerCallback {
    WaypointTrigger getTrigger();
}
