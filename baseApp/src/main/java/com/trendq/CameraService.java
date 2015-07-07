package com.trendq;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by smundra on 6/23/15.
 */
public class CameraService {

    private Camera mCamera;

    private static int mCameraId;

    private Camera.Size mPreviewSize;

    private AutoFitTextureView mTextureView;

    private static final String TAG = "TrendQ.CS";

    public Camera initCamera(int cameraId) {
        Camera c = null;
        try {
            mCameraId = cameraId;
            c = Camera.open(cameraId);
            setmCamera(c);
            Log.d(TAG, "Camera opened");
        } catch (Exception e) {
            Log.e(TAG, "Could not obtain Camera." + e.getMessage());
        }
        return c;
    }

    protected void setUpCameraParameters(int width, int height, Activity activity) {
        Log.d(TAG, "setting camera parameters");

        configureFlashMode();
        configureFocusMode();
        //configureExposure();
        //configureWhiteBalance();

        setCameraDisplayOrientation(mCameraId, mCamera, activity);

        Camera.Parameters params = mCamera.getParameters();
/*
        List<Camera.Size> list = params.getSupportedPictureSizes();
        Camera.Size largest = Collections.max(
                list, new CompareSizesByHeight());
        Log.d(TAG, "largest supported picture size: " + largest.width + ":" + largest.height);
        params.setPictureSize(largest.width, largest.height);
*/
        mPreviewSize = Collections.max(params.getSupportedPreviewSizes(), new CompareSizesByHeight());
        Log.d(TAG, "Size w:h is " + mPreviewSize.width + ":" + mPreviewSize.height);

        params.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        mCamera.setParameters(params);

    }

    private void configureFocusMode() {
        Log.d(TAG,"Setting auto-focus");
        mCamera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean b, Camera camera) {
                //Do nothing
            }
        });
        Camera.Parameters params = mCamera.getParameters();
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        mCamera.setParameters(params);
    }

    private void configureFlashMode() {
        Camera.Parameters params = mCamera.getParameters();
        List modeList = params.getSupportedFlashModes();
        //if(modeList!=null){
            params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
            mCamera.setParameters(params);
        //}
    }

    protected void configureTransform(int viewWidth, int viewHeight, Activity activity) {
        Log.d(TAG, "inside configureTransform");
        Log.d(TAG, "initial view x:y are " + viewWidth + ":" + viewHeight);
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        //Camera.CameraInfo info = new Camera.CameraInfo();
        //Camera.getCameraInfo(mCameraId,info);

        //mTextureView.setRotation((info.orientation - rotation + 360) % 360);

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.height, mPreviewSize.width);

        Log.d(TAG, "TextureView x:y are " + viewWidth + ":" + viewHeight);
        Log.d(TAG, "Preview x:y are " + mPreviewSize.height + ":" + mPreviewSize.width);

        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();


        Log.d(TAG, "centerX:centerY " + centerX + ":" + centerY);
        Log.d(TAG, "Buffer centerX:centerY " + bufferRect.centerX() + ":" + bufferRect.centerY());
        float scale = Math.max(
                (float) viewHeight / mPreviewSize.height,
                (float) viewWidth / mPreviewSize.width);

        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.CENTER);
        matrix.postScale(scale, scale);
//        RectF intersectF = new RectF();
//        boolean intersection = intersectF.setIntersect(viewRect, bufferRect);

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.CENTER);
            //FIXME: Don't just scale, Scale and center the larger on to smaller.
            scale = Math.max(
                    (float) viewHeight / mPreviewSize.height,
                    (float) viewWidth / mPreviewSize.width);
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
        Log.d(TAG, "leaving configureTransform");
    }

    private void configureExposure() {
        Camera.Parameters params = mCamera.getParameters();
        if(params.isAutoExposureLockSupported()){
            boolean isLocked = params.getAutoExposureLock();
            if(isLocked){
                return;
            }
            params.setAutoExposureLock(true);
        }
        mCamera.setParameters(params);
    }

    private void configureWhiteBalance() {
        Camera.Parameters params = mCamera.getParameters();
        if(params.isAutoWhiteBalanceLockSupported()){
            String whiteBalance = params.getWhiteBalance();
            if(whiteBalance==null || !Camera.Parameters.WHITE_BALANCE_AUTO.equalsIgnoreCase(whiteBalance)){
                params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            }
        }
        mCamera.setParameters(params);
    }

    public void setCameraDisplayOrientation(int cameraId, Camera camera, Activity activity) {
        Camera.CameraInfo info =
                new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        Log.d(TAG,"setCameraDisplayOrientation");
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            Log.d(TAG, "camera orientation is "+info.orientation);
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    /**
     * Starts camera preview.
     */
    protected void createCameraPreviewSession(TextureView mTextureView) {
        if(mCamera==null){
            return;
        }
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        assert texture != null;

        texture.setDefaultBufferSize(mTextureView.getWidth(), mTextureView.getHeight());
        try{
            mCamera.setPreviewTexture(texture);
            mCamera.startPreview();
            Camera.Parameters params = mCamera.getParameters();
            List<Camera.Size> list = params.getSupportedPictureSizes();
            Camera.Size largest = Collections.max(
                    list, new CompareSizesByHeight());
            Log.d(TAG, "largest supported picture size: " + largest.width + ":" + largest.height);
            params.setPictureSize(largest.width, largest.height);
        }catch (IOException e){
            Log.e(TAG, e.getMessage());
        }
    }

    private void printSizeList(List<Camera.Size> list) {
        int i = 1;
        for(Camera.Size size : list ) {
            Log.d(TAG, "Preview option " + i + " " + size.width + ":" + size.height);
        }
    }

    public void setmTextureView(AutoFitTextureView mTextureView) {
        this.mTextureView = mTextureView;
    }

    public void setmCamera(Camera mCamera) {
        this.mCamera = mCamera;
    }

    static class CompareSizesByArea implements Comparator<Camera.Size> {

        @Override
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            return Long.signum((long) lhs.width * lhs.height -
                    (long) rhs.width * rhs.height);
        }
    }

    static class CompareSizesByWidth implements Comparator<Camera.Size> {

        @Override
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            return lhs.width-rhs.width;
        }
    }

    static class CompareSizesByHeight implements Comparator<Camera.Size> {

        @Override
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            return lhs.height-rhs.height;
        }
    }

    protected void releaseCamera() {
        if (mCamera != null) {
            Log.d(TAG,"camera released");
            mCamera.stopPreview();
            mCamera.release();
            setmCamera(null);
        }
    }

    public AutoFitTextureView getmTextureView(){
        return mTextureView;
    }

    public Camera getmCamera(){ return mCamera;}

}
