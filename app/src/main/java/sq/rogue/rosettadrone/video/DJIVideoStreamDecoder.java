package sq.rogue.rosettadrone.video;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import dji.common.product.Model;
import dji.log.DJILog;
import dji.midware.data.model.P3.DataCameraGetPushStateInfo;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import dji.sdk.base.BaseProduct;
import sq.rogue.rosettadrone.R;

/**
 * This class is a helper class for hardware decoding. Please follow the following steps to use it:
 *
 * 1. Initialize and set the instance as a listener of NativeDataListener to receive the frame data.
 *
 * 2. Send the raw data from camera to ffmpeg for frame parsing.
 *
 * 3. Get the parsed frame data from ffmpeg parsing frame callback and cache the parsed framed data into the frameQueue.
 *
 * 4. Initialize the MediaCodec as a decoder and then check whether there is any i-frame in the MediaCodec. If not, get
 * the default i-frame from sdk resource and insert it at the head of frameQueue. Then dequeue the framed data from the
 * frameQueue and feed it(which is Byte buffer) into the MediaCodec.
 *
 * 5. Get the output byte buffer from MediaCodec, if a surface(Video Previewing View) is configured in the MediaCodec,
 * the output byte buffer is only need to be released. If not, the output yuv data should invoke the callback and pass
 * it out to external listener, it should also be released too.
 *
 * 6. Release the ffmpeg and the MediaCodec, stop the decoding thread.
 */
public class DJIVideoStreamDecoder implements NativeHelper.NativeDataListener {
    private static final String TAG = DJIVideoStreamDecoder.class.getSimpleName();
    private static final int BUF_QUEUE_SIZE = 60;
    private static final int MSG_INIT_CODEC = 0;
    private static final int MSG_FRAME_QUEUE_IN = 1;
    private static final int MSG_DECODE_FRAME = 2;
    private static final int MSG_YUV_DATA = 3;
    public static final String VIDEO_ENCODING_FORMAT = "video/avc";
    private HandlerThread  handlerThreadNew;
    private Handler handlerNew;
    private final boolean DEBUG = false;
    private static DJIVideoStreamDecoder instance;
    private Queue<DJIFrame> frameQueue;
    private HandlerThread dataHandlerThread;
    private Handler dataHandler;
    private Context context;
    private MediaCodec codec;
    private Surface surface;

    public int frameIndex = -1;
    private long currentTime;
    public int width;
    public int height;
    private boolean hasIFrameInQueue = false;
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    LinkedList<Long> bufferChangedQueue=new LinkedList<Long>();

    private long createTime;

    /**
     * Set the yuv frame data receiving callback. The callback method will be invoked when the decoder
     * output yuv frame data. What should be noted here is that the hardware decoder would not output
     * any yuv data if a surface is configured into, which mean that if you want the yuv frames, you
     * should set "null" surface when calling the "configure" method of MediaCodec.
     * @param yuvDataListener
     */
    public void setYuvDataListener(DJICodecManager.YuvDataCallback yuvDataListener) {
        this.yuvDataListener = yuvDataListener;
    }

    private DJICodecManager.YuvDataCallback yuvDataListener;

    /**
     * A data structure for containing the frames.
     */
    private static class DJIFrame {
        public byte[] videoBuffer;
        public int size;
        public long pts;
        public long incomingTimeMs;
        public long fedIntoCodecTime;
        public long codecOutputTime;
        public boolean isKeyFrame;
        public int frameNum;
        public long frameIndex;
        public int width;
        public int height;

        public DJIFrame(byte[] videoBuffer, int size, long pts, long incomingTimeUs, boolean isKeyFrame,
                        int frameNum, long frameIndex, int width, int height){
            this.videoBuffer=videoBuffer;
            this.size=size;
            this.pts =pts;
            this.incomingTimeMs=incomingTimeUs;
            this.isKeyFrame=isKeyFrame;
            this.frameNum=frameNum;
            this.frameIndex=frameIndex;
            this.width=width;
            this.height=height;
        }

        public long getQueueDelay()
        {
            return fedIntoCodecTime-incomingTimeMs;
        }

        public long getDecodingDelay()
        {
            return codecOutputTime-fedIntoCodecTime;
        }

        public long getTotalDelay()
        {
            return codecOutputTime-fedIntoCodecTime;
        }
    }

    private void logd(String tag, String log) {
        if (!DEBUG) {
            return;
        }
        Log.d(tag, log);
    }
    private void loge(String tag, String log) {
        if (!DEBUG) {
            return;
        }
        Log.e(tag, log);
    }

    private void logd(String log) {
        logd(TAG, log);
    }
    private void loge(String log) {
        loge(TAG, log);
    }

    private DJIVideoStreamDecoder() {
        createTime = System.currentTimeMillis();
        frameQueue = new ArrayBlockingQueue<DJIFrame>(BUF_QUEUE_SIZE);
        startDataHandler();
        handlerThreadNew = new HandlerThread("native parser thread");
        handlerThreadNew.start();
        handlerNew = new Handler(handlerThreadNew.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                byte[] buf = (byte[])msg.obj;
                NativeHelper.getInstance().parse(buf, msg.arg1, 0);
                return false;
            }
        });
    }


    public synchronized static DJIVideoStreamDecoder getInstance() {
        if (instance == null) {
            instance = new DJIVideoStreamDecoder();
        }
        return instance;
    }

    /**
     * Initialize the decoder
     * @param context The application context
     * @param surface The displaying surface for the video stream. What should be noted here is that the hardware decoder would not output
     * any yuv data if a surface is configured into, which mean that if you want the yuv frames, you
     * should set "null" surface when calling the "configure" method of MediaCodec.
     */
    public void init(Context context, Surface surface) {
        this.context = context;
        this.surface = surface;
        NativeHelper.getInstance().setDataListener(this);
        if (dataHandler != null && !dataHandler.hasMessages(MSG_INIT_CODEC)) {
            dataHandler.sendEmptyMessage(MSG_INIT_CODEC);
        }
    }

    /**
     * Framing the raw data from the camera.
     * @param buf Raw data from camera.
     * @param size Data length
     */
    public void parse(byte[] buf, int size) {
        Message message =handlerNew.obtainMessage();
        message.obj = buf;
        message.arg1 = size;
        handlerNew.sendMessage(message);
    }

    /**
     * Get the resource ID of the IDR frame.
     * @param pModel Product model of connecting DJI product.
     * @param width Width of current video stream.
     * @return Resource ID of the IDR frame
     */
    public int getIframeRawId(Model pModel, int width) {
        return R.raw.iframe_1280x720_wm220;
    }

    /** Get default black IDR frame.
     * @param width Width of current video stream.
     * @return IDR frame data
     * @throws IOException
     */
    private byte[] getDefaultKeyFrame(int width) throws IOException {
        BaseProduct product = DJISDKManager.getInstance().getProduct();
        if (product == null || product.getModel() == null) {
            return null;
        }
        int iframeId=getIframeRawId(product.getModel(), width);
        if (iframeId > 0){

            InputStream inputStream = context.getResources().openRawResource(iframeId);
            int length = inputStream.available();
            logd("iframeId length=" + length);
            byte[] buffer = new byte[length];
            inputStream.read(buffer);
            inputStream.close();

            return buffer;
        }
        return null;
    }


    /**
     * Initialize the hardware decoder.
     */
    private void initCodec() {
        if (width == 0 || height == 0) {
            return;
        }
        if (codec != null) {
            releaseCodec();
        }
        loge("initVideoDecoder----------------------------------------------------------");
        loge("initVideoDecoder video width = " + width + "  height = " + height);
        // create the media format
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_ENCODING_FORMAT, width, height);
        if (surface == null) {
            logd("initVideoDecoder: yuv output");
            // The surface is null, which means that the yuv data is needed, so the color format should
            // be set to YUV420.
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
        } else {
            logd("initVideoDecoder: display");
            // The surface is set, so the color format should be set to format surface.
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        }

        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        //format.setInteger(MediaFormat.KEY_MAX_WIDTH, 0);
        //format.setInteger(MediaFormat.KEY_MAX_HEIGHT, 0);

        try {
            // Create the codec instance.
            codec = MediaCodec.createDecoderByType(VIDEO_ENCODING_FORMAT);
            logd( "initVideoDecoder create: " + (codec == null));
            // Configure the codec. What should be noted here is that the hardware decoder would not output
            // any yuv data if a surface is configured into, which mean that if you want the yuv frames, you
            // should set "null" surface when calling the "configure" method of MediaCodec.
            codec.configure(format, surface, null, 0);
            logd( "initVideoDecoder configure");
            //            codec.configure(format, null, null, 0);
            if (codec == null) {
                loge("Can't find video info!");
                return;
            }
            // Start the codec
            codec.start();
        } catch (Exception e) {
            loge("init codec failed, do it again: " + e);
            e.printStackTrace();
        }
    }

    private void startDataHandler() {
        if (dataHandlerThread != null && dataHandlerThread.isAlive()) {
            return;
        }
        dataHandlerThread = new HandlerThread("frame data handler thread");
        dataHandlerThread.start();
        dataHandler = new Handler(dataHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_INIT_CODEC:
                        try {
                            initCodec();
                        } catch (Exception e) {
                            loge("init codec error: " + e.getMessage());
                            e.printStackTrace();
                        }

                        removeCallbacksAndMessages(null);
                        sendEmptyMessageDelayed(MSG_DECODE_FRAME, 1);
                        break;
                    case MSG_FRAME_QUEUE_IN:
                        try {
                            onFrameQueueIn(msg);
                        } catch (Exception e) {
                            loge("queue in frame error: " + e);
                            e.printStackTrace();
                        }

                        if (!hasMessages(MSG_DECODE_FRAME)) {
                            sendEmptyMessage(MSG_DECODE_FRAME);
                        }
                        break;
                    case MSG_DECODE_FRAME:
                        try {
                            decodeFrame();
                        } catch (Exception e) {
                            loge("handle frame error: " + e);
                            if (e instanceof MediaCodec.CodecException) {
                            }
                            e.printStackTrace();
                            initCodec();
                        }finally {
                            if (frameQueue.size() > 0) {
                                sendEmptyMessage(MSG_DECODE_FRAME);
                            }
                        }
                        break;
                    case MSG_YUV_DATA:

                        break;
                    default:
                        break;
                }
            }
        };
    }

    /**
     * Stop the data processing thread
     */
    private void stopDataHandler() {
        if (dataHandlerThread == null || !dataHandlerThread.isAlive()) {
            return;
        }
        if (dataHandler != null) {
            dataHandler.removeCallbacksAndMessages(null);
        }
        if (Build.VERSION.SDK_INT >= 18) {
            dataHandlerThread.quitSafely();
        } else {
            dataHandlerThread.quit();
        }

        try {
            dataHandlerThread.join(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        releaseCodec();
        dataHandler = null;
    }

    /**
     * Change the displaying surface of the decoder. What should be noted here is that the hardware decoder would not output
     * any yuv data if a surface is configured into, which mean that if you want the yuv frames, you
     * should set "null" surface when calling the "configure" method of MediaCodec.
     * @param surface
     */
    public void changeSurface(Surface surface) {
        if (this.surface != surface) {
            this.surface = surface;
            if (dataHandler != null && !dataHandler.hasMessages(MSG_INIT_CODEC)) {
                dataHandler.sendEmptyMessage(MSG_INIT_CODEC);
            }
        }
    }

    /**
     * Release and close the codec.
     */
    private void releaseCodec() {
        if (frameQueue!=null){
            frameQueue.clear();
            hasIFrameInQueue = false;
        }
        if (codec != null) {
            try {
                codec.flush();
            } catch (Exception e) {
                loge("flush codec error: " + e.getMessage());
            }

            try {
                codec.stop();
                codec.release();
            } catch (Exception e) {
                loge("close codec error: " + e.getMessage());
            } finally {
                codec = null;
            }
        }
    }

    /**
     * Queue in the frame.
     * @param msg
     */
    private void onFrameQueueIn(Message msg) {
        DJIFrame inputFrame = (DJIFrame)msg.obj;
        if (inputFrame == null) {
            return;
        }
        if (!hasIFrameInQueue) { // check the I frame flag
            if (inputFrame.frameNum != 0 && !inputFrame.isKeyFrame) {
                loge("the timing for setting iframe has not yet come frameNum: " + inputFrame.frameNum );
                return;
            }
            byte[] defaultKeyFrame = null;
            try {
                defaultKeyFrame = getDefaultKeyFrame(inputFrame.width); // Get I frame data
            } catch (IOException e) {
                loge("get default key frame error: " + e.getMessage());
            }
             if (inputFrame.isKeyFrame) {
                hasIFrameInQueue = true;
            } else {
                loge("input key frame failed");
            }
        }
        if (inputFrame.width!=0 && inputFrame.height != 0 &&
            (inputFrame.width != this.width ||
                inputFrame.height != this.height)) {
            this.width = inputFrame.width;
            this.height = inputFrame.height;
    	   /*
    	    * On some devices, the codec supports changing of resolution during the fly
    	    * However, on some devices, that is not the case.
    	    * So, reset the codec in order to fix this issue.
    	    */
            loge("init decoder for the 1st time or when resolution changes");
            if (dataHandler != null && !dataHandler.hasMessages(MSG_INIT_CODEC)) {
                dataHandler.sendEmptyMessage(MSG_INIT_CODEC);
            }
        }
        // Queue in the input frame.
        if (this.frameQueue.offer(inputFrame)){
            logd("put a frame into the Extended-Queue with index=" + inputFrame.frameIndex);
        } else {
            // If the queue is full, drop a frame.
            DJIFrame dropFrame = frameQueue.poll();
            this.frameQueue.offer(inputFrame);
            loge("Drop a frame with index=" + dropFrame.frameIndex+" and append a frame with index=" + inputFrame.frameIndex);
        }
    }

    /**
     * Dequeue the frames from the queue and decode them using the hardware decoder.
     * @throws Exception
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void decodeFrame() throws Exception {
        DJIFrame inputFrame = frameQueue.poll();
        if (inputFrame == null) {
            return;
        }
        if (codec == null) {
            if (dataHandler != null && !dataHandler.hasMessages(MSG_INIT_CODEC)) {
                dataHandler.sendEmptyMessage(MSG_INIT_CODEC);
            }
            return;
        }

        if(DEBUG)
            logd("Decoding Frame " + inputFrame.frameIndex);

        int inIndex = codec.dequeueInputBuffer(0);

        // Decode the frame using MediaCodec
        if (inIndex >= 0) {
            Log.d(TAG, "decodeFrame: index=" + inIndex);
            ByteBuffer buffer = codec.getInputBuffer(inIndex);
            buffer.clear();
            buffer.limit(buffer.position() + inputFrame.size);
            buffer.put(inputFrame.videoBuffer);
            inputFrame.fedIntoCodecTime = System.currentTimeMillis();
            long queueingDelay = inputFrame.getQueueDelay();
            // Feed the frame data to the decoder.
            codec.queueInputBuffer(inIndex, 0, inputFrame.size, inputFrame.pts, 0);

            // Get the output data from the decoder.
            int outIndex = codec.dequeueOutputBuffer(bufferInfo, 0);

            if(DEBUG)
                Log.d(TAG, "decodeFrame: outIndex: " + outIndex);

            if (outIndex >= 0) {
                if (surface == null && yuvDataListener != null) {
                    // If the surface is null, the yuv data should be get from the buffer and invoke the callback.
                    logd("decodeFrame: need callback");
                    ByteBuffer yuvDataBuf = codec.getOutputBuffer(outIndex);
                    yuvDataBuf.position(bufferInfo.offset);
                    yuvDataBuf.limit(bufferInfo.size - bufferInfo.offset);
                    if (yuvDataListener != null) {
                        yuvDataListener.onYuvDataReceived(codec.getOutputFormat(), yuvDataBuf, bufferInfo.size - bufferInfo.offset,  width, height);
                    }
                }
                // All the output buffer must be release no matter whether the yuv data is output or
                // not, so that the codec can reuse the buffer.
                codec.releaseOutputBuffer(outIndex, true);
            } else if (outIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // The output buffer set is changed. So the decoder should be reinitialized and the
                // output buffers should be retrieved.
                long curTime = System.currentTimeMillis();
                bufferChangedQueue.addLast(curTime);
                if (bufferChangedQueue.size() >= 10) {
                    long headTime = bufferChangedQueue.pollFirst();
                    if (curTime - headTime < 1000) {
                        // reset decoder
                        loge("Reset decoder. Get INFO_OUTPUT_BUFFERS_CHANGED more than 10 times within a second.");
                        bufferChangedQueue.clear();
                        dataHandler.removeCallbacksAndMessages(null);
                        dataHandler.sendEmptyMessage(MSG_INIT_CODEC);
                        return;
                    }
                }
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                loge("format changed, color: " + codec.getOutputFormat().getInteger(MediaFormat.KEY_COLOR_FORMAT));
            }
        }else {
            codec.flush();
        }
    }

    /**
     * Stop the decoding process.
     */
    public void stop() {
        if (dataHandler != null) {
            dataHandler.removeCallbacksAndMessages(null);
        }

        if (frameQueue!=null){
            frameQueue.clear();
            hasIFrameInQueue = false;
        }
        if (codec != null) {
            try {
                codec.flush();
            } catch (IllegalStateException e) {
            }
        }
        stopDataHandler();
    }

    public void resume() {
        startDataHandler();
    }

    public void destroy() {
    }

    @Override
    public void onDataRecv(byte[] data, int size, int frameNum, boolean isKeyFrame, int width, int height) {
        if (dataHandler == null || dataHandlerThread == null || !dataHandlerThread.isAlive()) {
            return;
        }
        
        if (data.length != size) {
            loge( "recv data size: " + size + ", data lenght: " + data.length);
        } else {
            logd( "recv data size: " + size + ", frameNum: "+frameNum+", isKeyframe: "+isKeyFrame+"," +
                    " width: "+width+", height: " + height);
            currentTime = System.currentTimeMillis();
            frameIndex ++;
            DJIFrame newFrame = new DJIFrame(data, size, currentTime, currentTime, isKeyFrame, frameNum, frameIndex, width, height);
            dataHandler.obtainMessage(MSG_FRAME_QUEUE_IN, newFrame).sendToTarget();

        }
    }
}