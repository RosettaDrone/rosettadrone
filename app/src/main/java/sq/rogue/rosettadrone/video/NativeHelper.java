package sq.rogue.rosettadrone.video;

/**
 * A helper class to invoke native methods
 */
public class NativeHelper {

    public static final String TAG = NativeHelper.class.getSimpleName();

    public interface NativeDataListener {
        /**
         * Callback method for receiving the frame data from NativeHelper.
         * Note that this method will be invoke in framing thread, which means time consuming
         * processing should not in this thread, or the framing process will be blocked.
         *
         * @param data
         * @param size
         * @param frameNum
         * @param isKeyFrame
         * @param width
         * @param height
         */
        void onDataRecv(byte[] data, int size, int frameNum, boolean isKeyFrame, int width, int height);
    }

    private NativeDataListener dataListener;

    public void setDataListener(NativeDataListener dataListener) {
        this.dataListener = dataListener;
    }
    //JNI

    /**
     * Test the ffmpeg.
     *
     * @return
     */
    public native String codecinfotest();

    /**
     * Initialize the ffmpeg.
     *
     * @return
     */
    public native boolean init();


    /**
     * Framing the raw data from camera
     *
     * @param buf
     * @param size
     * @return
     */
    public native boolean parse(byte[] buf, int size);


    /**
     * Release the ffmpeg
     *
     * @return
     */
    public native boolean release();

    static {
        System.loadLibrary("ffmpeg");
        System.loadLibrary("djivideojni");
    }

    private static NativeHelper instance;

    public static NativeHelper getInstance() {
        if (instance == null) {
            instance = new NativeHelper();
        }
        return instance;
    }

    private NativeHelper() {
    }

    /**
     * Invoke by JNI
     * Callback the frame data.
     *
     * @param buf
     * @param size
     * @param frameNum
     * @param isKeyFrame
     * @param width
     * @param height
     */
    public void onFrameDataRecv(byte[] buf, int size, int frameNum, boolean isKeyFrame, int width, int height) {
        if (dataListener != null) {
            dataListener.onDataRecv(buf, size, frameNum, isKeyFrame, width, height);
        }
    }
}
