package sq.rogue.rosettadrone;

public class util {

    public static final int TYPE_GCS_IP = 0x00;
    public static final int TYPE_GCS_PORT = 0x01;
    public static final int TYPE_VIDEO_IP = 0x02;
    public static final int TYPE_VIDEO_PORT = 0x03;
    public static final int TYPE_DRONE_ID = 0x04;
    public static final int TYPE_DRONE_RTL_ALTITUDE = 0x05;
    public static final int TYPE_VIDEO_BITRATE = 0x06;
    public static final int TYPE_FLIGHT_PATH_RADIUS = 0x07;

    //DJI Minimum altitude relative to home
    public static final int TYPE_WAYPOINT_MIN_ALTITUDE = 0x20;

    //DJI Maximum altitude relative to home
    public static final int TYPE_WAYPOINT_MAX_ALTITUDE = 0x21;

    //DJI Maximum distance between two points
    public static final int TYPE_WAYPOINT_DISTANCE = 0x22;

    //DJI Maximum total path length
    public static final int TYPE_WAYPOINT_TOTAL_DISTANCE = 0x23;

    //DJI Minimum Speed
    public static final int TYPE_WAYPOINT_MIN_SPEED = 0x24;

    //DJI Maximum speed
    public static final int TYPE_WAYPOINT_MAX_SPEED = 0x25;

    //Default waypoint error type
    public static final int TYPE_WAYPOINT_DEFAULT = 0x30;

    public static String getErrorString(int errorCode) {
        switch (errorCode) {
            case TYPE_GCS_IP:
                break;
            case TYPE_GCS_PORT:
                break;
            case TYPE_VIDEO_IP:
                break;
            case TYPE_VIDEO_PORT:
                break;
            case TYPE_DRONE_ID:
                break;
            case TYPE_DRONE_RTL_ALTITUDE:
                break;
            case TYPE_VIDEO_BITRATE:
                break;
            case TYPE_FLIGHT_PATH_RADIUS:
                break;
            case TYPE_WAYPOINT_MIN_ALTITUDE:
                break;
            case TYPE_WAYPOINT_MAX_ALTITUDE:
                break;
            case TYPE_WAYPOINT_MIN_SPEED:
                break;
            case TYPE_WAYPOINT_MAX_SPEED:
                break;
            case TYPE_WAYPOINT_DISTANCE:
                break;
            case TYPE_WAYPOINT_TOTAL_DISTANCE:
                break;
            default:
                break;
        }
        return null;
    }


    public static long getTimestampMicroseconds() {
        return System.currentTimeMillis() / 10;
    }

    public static long getTimestampMilliseconds() {
        return System.currentTimeMillis();
    }

    public static long getTimestampSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    public static void safeSleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
        }
    }

}
