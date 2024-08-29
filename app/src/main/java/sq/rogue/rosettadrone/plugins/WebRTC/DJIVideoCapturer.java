/* Originally sourced from
* https://chromium.googlesource.com/external/webrtc/+/b6760f9e4442410f2bcb6090b3b89bf709e2fce2/webrtc/api/android/java/src/org/webrtc/CameraVideoCapturer.java
* and rewritten to work for DJI drones.
*  */
package sq.rogue.rosettadrone.plugins.WebRTC;

import org.webrtc.CapturerObserver;
import org.webrtc.JavaI420Buffer;
import org.webrtc.NV12Buffer;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import dji.common.product.Model;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;

public class DJIVideoCapturer implements VideoCapturer {
    private final static String TAG = DJIVideoCapturer.class.getSimpleName();

    private static DJICodecManager codecManager;
    private static final ArrayList<CapturerObserver> observers = new ArrayList<CapturerObserver>();

    private final Model aircraftModel;
    private Context context;
    private CapturerObserver capturerObserver;

    public DJIVideoCapturer(Model aircraftModel){
        this.aircraftModel = aircraftModel;
    }

    private void setupVideoListener(){
        if(codecManager != null) {
            Log.d(TAG, "codecManager not null");
            return;
        }

        // Pass SurfaceTexture as null to force the Yuv callback - width and height for the surface texture does not matter
        codecManager = new DJICodecManager(context, (SurfaceTexture)null, 0, 0);
        codecManager.enabledYuvData(true);
        codecManager.setYuvDataCallback(new DJICodecManager.YuvDataCallback() {
            @Override
            public void onYuvDataReceived(MediaFormat mediaFormat, ByteBuffer videoBuffer, int dataSize, int width, int height) {
                if (videoBuffer != null){
                    try{
                        long timestampNS = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
                        VideoFrame videoFrame;
                        // Check the color format. Could create more cases if needed
                        int colorFormat = mediaFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                        switch (colorFormat) {
                            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
                                // NV12 Buffer
                                NV12Buffer nv12Buffer = new NV12Buffer(width, height,
                                        mediaFormat.getInteger(MediaFormat.KEY_STRIDE),
                                        mediaFormat.getInteger(MediaFormat.KEY_SLICE_HEIGHT),
                                        videoBuffer, null);
                                videoFrame = new VideoFrame(nv12Buffer, 0, timestampNS);
                                break;
                            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
                                // I420 Buffer
                                // Calculate the offsets and lengths for the Y, U, and V planes
                                int ySize = width * height;
                                int uvSize = ySize / 4;

                                // Create YUV Buffers from the YUV data
                                ByteBuffer yPlane = ByteBuffer.allocateDirect(ySize);
                                ByteBuffer uPlane = ByteBuffer.allocateDirect(uvSize);
                                ByteBuffer vPlane = ByteBuffer.allocateDirect(uvSize);

                                // Copy data from videoBuffer to the respective planes
                                videoBuffer.position(0);
                                yPlane.put((ByteBuffer) videoBuffer.slice().limit(ySize));
                                videoBuffer.position(ySize);
                                uPlane.put((ByteBuffer) videoBuffer.slice().limit(uvSize));
                                videoBuffer.position(ySize + uvSize);
                                vPlane.put((ByteBuffer) videoBuffer.slice().limit(uvSize));

                                yPlane.rewind(); uPlane.rewind(); vPlane.rewind();

                                // Create JavaI420Buffer
                                JavaI420Buffer i420Buffer = JavaI420Buffer.wrap(width, height,
                                        yPlane, width,
                                        uPlane, width / 2,
                                        vPlane, width / 2, null);

                                videoFrame = new VideoFrame(i420Buffer, 0, timestampNS);
                                break;
                            default:
                                Log.e(TAG, "Color format: " + colorFormat + " is not implemented!!");
                                return;
                        }
                        // Feed the video frame to everyone
                        for (CapturerObserver obs : observers) {
                            obs.onFrameCaptured(videoFrame);
                        }
                        videoFrame.release();
                    } catch (Exception e){
                        Log.e(TAG, e.getClass().getName() + " occurred, stack trace: " + e.getMessage() + "\n" + e.getCause());
                    }
                }
            }
        });

        // Could create more cases if other drones from DJI require a different approach
        if (aircraftModel.getDisplayName().equals("DJI Air 2S")){
            // The Air 2S relies on the VideoDataListener to obtain the video feed
            // The onReceive callback provides us the raw H264 (at least according to official documentation). To decode it we send it to our DJICodecManager
            // H264 or H265 encoding is done to compress and save bandwidth. (4K video might force a switch to H265 on DJI drones)
            VideoFeeder.VideoDataListener videoDataListener = new VideoFeeder.VideoDataListener() {
                @Override
                public void onReceive(byte[] bytes, int dataSize) {
                    // Pass the encoded data along to obtain the YUV-color data
                    codecManager.sendDataToDecoder(bytes, dataSize);
                }
            };
            VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(videoDataListener);
        }
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext,
                           CapturerObserver capturerObserver) {
        this.context = applicationContext;
        this.capturerObserver = capturerObserver;

        observers.add(capturerObserver);
    }

    @Override
    public void startCapture(int width, int height, int framerate) {
        // Hook onto the DJI onYuvDataReceived event
        setupVideoListener();
    }

    @Override
    public void stopCapture() throws InterruptedException {
        codecManager.enabledYuvData(false);
        codecManager.setYuvDataCallback(null);
        codecManager.destroyCodec();
        codecManager = null;
    }

    @Override
    public void changeCaptureFormat(int width, int height, int framerate) {
        // Empty on purpose
    }

    @Override
    public void dispose() {
        // Stop receiving frames on the callback from the decoder
        if (observers.contains(capturerObserver))
            observers.remove(capturerObserver);
    }

    @Override
    public boolean isScreencast() {
        return false;
    }
}