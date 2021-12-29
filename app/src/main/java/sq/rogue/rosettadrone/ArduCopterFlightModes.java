package sq.rogue.rosettadrone;

public class ArduCopterFlightModes {
    final static int STABILIZE = 0;  // manual airframe angle with manual throttle
    final static int ACRO = 1;  // manual body-frame angular rate with manual throttle
    final static int ALT_HOLD = 2;  // manual airframe angle with automatic throttle
    final static int AUTO = 3;  // fully automatic waypoint control using mission commands
    final static int GUIDED = 4;  // fully automatic fly to coordinate or fly at velocity/direction using GCS immediate commands
    final static int LOITER = 5;  // automatic horizontal acceleration with automatic throttle
    final static int RTL = 6;  // automatic return to launching point
    final static int CIRCLE = 7;  // automatic circular flight with automatic throttle
    final static int LAND = 9;  // automatic landing with horizontal position control
    final static int DRIFT = 11;  // semi-automous position, yaw and throttle control
    final static int SPORT = 13;  // manual earth-frame angular rate control with manual throttle
    final static int FLIP = 14;  // automatically flip the vehicle on the roll axis
    final static int AUTOTUNE = 15;  // automatically tune the vehicle's roll and pitch gains
    final static int POSHOLD = 16;  // automatic position hold with manual override, with automatic throttle
    final static int BRAKE = 17;  // full-brake using inertial/GPS system, no pilot input
    final static int THROW = 18;  // throw to launch mode using inertial/GPS system, no pilot input
    final static int AVOID_ADSB = 19;  // automatic avoidance of obstacles in the macro scale - e.g. full-sized aircraft
    final static int GUIDED_NOGPS = 20;  // guided mode but only accepts attitude and altitude
    final static int SMART_RTL = 21;  // SMART_RTL returns to home by retracing its steps
};

