package sq.rogue.rosettadrone;

public class util {

    public static final int TYPE_GCS_IP = 0x00;
    public static final int TYPE_GCS_PORT = 0x01;
    public static final int TYPE_VIDEO_IP = 0x02;
    public static final int TYPE_VIDEO_PORT = 0x03;
    public static final int TYPE_DRONE_ID = 0x04;
    public static final int TYPE_DRONE_RTL_ALTITUDE = 0x05;

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
