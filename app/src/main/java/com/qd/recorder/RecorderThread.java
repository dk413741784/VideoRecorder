package com.qd.recorder;

import android.os.AsyncTask;
import android.util.Log;

import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacpp.opencv_core;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecorderThread extends Thread{

    private opencv_core.IplImage mYuvIplImage;
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


    public RecorderThread(opencv_core.IplImage yuvIplImage,FFmpegFrameRecorder videoRecorder,int size,int frame){
        this.mYuvIplImage = yuvIplImage;
        this.mVideoRecorder = videoRecorder;
        this.mSize = size;
        mTotalFrame = frame;
        mTime = new long[mTotalFrame];
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
                    mYuvIplImage.getByteBuffer().put(mBytes);

                    try {
                        int width = mYuvIplImage.width();
                        int height = mYuvIplImage.height();
                        int depth = mYuvIplImage.depth();
                        int channels = mYuvIplImage.nChannels();
                        int stride = mYuvIplImage.widthStep() * 8 / Math.abs(depth);
                        Buffer[] image = new Buffer[]{};
                        if(mYuvIplImage.imageData()!=null) {
                            image = new Buffer[]{mYuvIplImage.imageData().asBuffer()};
                        }
                        mVideoRecorder.recordImage(width, height, depth, channels, stride, avutil.AV_PIX_FMT_NONE, image);
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
        mYuvIplImage = null;
        mVideoRecorder = null;
        if(mByteBuffer != null){
            mByteBuffer.clear();
        }
        mByteBuffer = null;
        mIndex = 0;
    }
}
