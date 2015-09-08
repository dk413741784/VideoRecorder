package com.qd.recorder;

import android.os.AsyncTask;
import android.util.Log;

import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FrameRecorder;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecorderThread extends Thread{

    private VideoMeteInfo videoMeteInfo;
    private FFmpegFrameRecorder mVideoRecorder;
    private ByteBuffer mByteBuffer;
    private FFmpegRecorderActivity.AsyncStopRecording mAsyncTask;

    private AtomicBoolean mIsStop = new AtomicBoolean(false);
    private AtomicBoolean mIsFinish = new AtomicBoolean(false);

    private byte[] mBytes;
    private int mSize;
    private long[] mTime;
    private int mIndex;
    private int mTotalFrame = 180;


    public RecorderThread(VideoMeteInfo videoMeteInfo,FFmpegFrameRecorder videoRecorder,int size,int frame){
        this.videoMeteInfo = videoMeteInfo;
        this.mVideoRecorder = videoRecorder;
        this.mSize = size;
        this.mTotalFrame = frame;
        this.mTime = new long[mTotalFrame];
    }

    public void putByteData(SavedFrames lastSavedframe){
        if(mByteBuffer != null && mByteBuffer.hasRemaining()){
            mTime[mIndex++] = lastSavedframe.getTimeStamp();
            mByteBuffer.put(lastSavedframe.getFrameBytesData());
        }
    }

    @Override
    public void run() {
        try {
            if(mByteBuffer == null){
                mByteBuffer = ByteBuffer.allocateDirect(mSize * mTotalFrame);
                mBytes = new byte[mSize];
            }
            int timeIndex = 0;
            int pos = 0;
            int byteIndex = 0;
            while (!mIsFinish.get()) {
                if (mByteBuffer.position() > pos) {
                    for(byteIndex = 0;byteIndex < mSize;byteIndex++){
                        mBytes[byteIndex] = mByteBuffer.get(pos + byteIndex);
                    }

                    pos += mSize;
                    mVideoRecorder.setTimestamp(mTime[timeIndex++]);
                    try {
                        Buffer[] image = new Buffer[]{ ByteBuffer.wrap(mBytes)};
                        mVideoRecorder.recordImage(videoMeteInfo.width,
                                videoMeteInfo.height,
                                videoMeteInfo.depth,
                                videoMeteInfo.channels,
                                videoMeteInfo.stride,
                                avutil.AV_PIX_FMT_NONE, image);
                    } catch (FrameRecorder.Exception e) {
                        Log.i("recorder", "录制错误" + e.getMessage());
                        e.printStackTrace();
                    }

                    if(mAsyncTask != null){
                        int progress = (int)((pos*100l)/mByteBuffer.position());
                        mAsyncTask.publishProgressFromOther(progress);
                    }

                } else {
                    if(mIsStop.get()){
                        break;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }finally {
            release();
        }
    }

    public void stopRecord(AsyncTask asyncTask){
        mAsyncTask = (FFmpegRecorderActivity.AsyncStopRecording)asyncTask;
        mIsStop.set(true);
        try {
            this.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void finish(){
        mIsFinish.set(true);
        try {
            this.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void release(){
        mAsyncTask = null;
        mVideoRecorder = null;
        if(mByteBuffer != null){
            mByteBuffer.clear();
        }
        mByteBuffer = null;
        mIndex = 0;
    }

    public static class VideoMeteInfo{

        private int width;
        private int height;
        private int depth;
        private int channels;
        private int stride;

        public VideoMeteInfo(int width, int height) {
            this(width, height, 8/*opencv_core.IPL_DEPTH_8U*/, 2);
        }

        public VideoMeteInfo(int width, int height, int depth, int channels) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.channels = channels;
            this.stride =  width * 8 / Math.abs(depth);
        }

        public VideoMeteInfo(int width, int height, int depth, int channels, int stride) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.channels = channels;
            this.stride = stride;
        }
    }
}
