package sq.rogue.rosettadrone;

public class util {

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
