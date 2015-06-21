package com.trendq;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class SnapshotFragment extends Fragment implements View.OnClickListener{

    public static final int MEDIA_TYPE_IMAGE = 1;
    protected static final String IMAGE_NAME = "trendq.image_preview";
    Intent imagePreviewIntent;
    private static final String TAG = "TrendQ.SF";

    private Camera mCamera = null;

    private Camera.Size mPreviewSize;

    private int mCameraId =0;

    private AutoFitTextureView mTextureView;

    private File mFile;

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            Log.d(TAG,"Surface available: "+width+":"+height);
            openCamera(width, height);
            if(mCamera!=null)createCameraPreviewSession();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            //Log.d(TAG,"Surface size changed");
            //configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            Log.d(TAG,"Surface destroyed");
            closeCamera();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    private Handler mMessageHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Activity activity = getActivity();
            if (activity != null) {
                Log.d(TAG,"making toast");
                Toast.makeText(activity, (String) msg.obj, Toast.LENGTH_SHORT).show();
            }
        }
    };

    /**
     * Given  choices of mPreviewSizes supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal mPreviewSize, or an arbitrary one if none were big enough
     */
    private static Camera.Size chooseOptimalSize(List<Camera.Size> choices, int width, int height, Camera.Size aspectRatio) {
        Log.d(TAG,"choosing optimal size from "+choices.size()+" choices");
        List<Camera.Size> bigEnough = new ArrayList<Camera.Size>();
        int w = aspectRatio.width;
        int h = aspectRatio.height;
        for (Camera.Size option : choices) {
            if (option.height == option.width * h / w &&
                    option.width >= width && option.height >= height) {
                bigEnough.add(option);
            }
        }
        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices.get(0);
        }
    }

    public static SnapshotFragment newInstance() {
        SnapshotFragment fragment = new SnapshotFragment();
        fragment.setRetainInstance(true);
        return fragment;
    }

    public static Camera getCameraInstance(int mCameraId) {
        Camera c = null;
        try {
            c = Camera.open(mCameraId);
            Log.d(TAG, "Camera opened");
        } catch (Exception e) {
            Log.e(TAG, "Could not obtain Camera." + e.getMessage());
        }
        return c;
    }

    private void showToast(String text) {
        Message message = Message.obtain();
        message.obj = text;
        mMessageHandler.sendMessage(message);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_snapshot_fragment, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        view.findViewById(R.id.picture).setOnClickListener(this);
        view.findViewById(R.id.info).setOnClickListener(this);
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume invoked");
        super.onResume();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
            if(mCamera!=null)createCameraPreviewSession();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG,"onPause invoked");
        closeCamera();
        super.onPause();
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        Log.d(TAG, "setting camera outputs");
        Camera.Parameters params = mCamera.getParameters();

        params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);

        if(params.isAutoExposureLockSupported()){
            configureExposure(params);
            mCamera.setParameters(params);
        }

        if(params.isAutoWhiteBalanceLockSupported()){
            configureWhiteBalance(params);
            mCamera.setParameters(params);
        }
        setCameraDisplayOrientation(mCameraId, mCamera);

        List<Camera.Size> list = params.getSupportedPictureSizes();

//        Log.d(TAG, "supported pictures are:");
//        printSizeList(params.getSupportedPictureSizes());

        Camera.Size largest = Collections.max(
                list, new CompareSizesByHeight());
        Log.d(TAG, "largest supported picture size: " + largest.width + ":" + largest.height);

        params.setPictureSize(largest.width, largest.height);
        mCamera.setParameters(params);
//        Log.d(TAG, "Supported preview sizes are: ");
//        printSizeList(params.getSupportedPreviewSizes());

        mPreviewSize = Collections.max(params.getSupportedPreviewSizes(), new CompareSizesByHeight());
        Log.d(TAG, "Size w:h is " + mPreviewSize.width + ":" + mPreviewSize.height);

        params.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        mCamera.setParameters(params);
        int orientation = getResources().getConfiguration().orientation;
        Log.d(TAG, "Orientation is " + orientation);

        configureSquareAspectRatio();
        mCamera.setParameters(params);

    }

    private void configureWhiteBalance(Camera.Parameters params) {
        String whiteBalance = params.getWhiteBalance();
        if(whiteBalance==null || Camera.Parameters.WHITE_BALANCE_AUTO.equalsIgnoreCase(whiteBalance)){
            params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        }
    }

    private void configureExposure(Camera.Parameters params) {
        boolean isLocked = params.getAutoExposureLock();
        if(isLocked){
            return;
        }
        params.setAutoWhiteBalanceLock(true);

    }

    private void configureSquareAspectRatio() {
        mTextureView.setAspectRatio(
                1,1);
    }

    private void printSizeList(List<Camera.Size> list) {
        int i = 1;
        for(Camera.Size size : list ) {
            Log.d(TAG, "Preview option " + i + " " + size.width + ":" + size.height);
        }
    }

    /**
     * Opens the camera specified by {@link SnapshotFragment#mCameraId}.
     */
    private void openCamera(int width, int height) {

        mCamera = getCameraInstance(mCameraId);
        if(mCamera!=null){
            setUpCameraOutputs(width, height);
            configureTransform(width, height);
        }
    }

    /**
     * Closes the current {@link SnapshotFragment#mCamera}
     */
    private void closeCamera() {
        if (null != mCamera) {
            releaseCamera();
        }
    }

    /**
     * Starts camera preview.
     */
    private void createCameraPreviewSession() {
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        assert texture != null;
        //FIXME setting square aspect ratio
        int less = mPreviewSize.width<mPreviewSize.height?mPreviewSize.width:mPreviewSize.height;
        //texture.setDefaultBufferSize(less,less);
        texture.setDefaultBufferSize(mPreviewSize.width,mPreviewSize.height);
        try{
            mCamera.setPreviewTexture(texture);
            mCamera.startPreview();
        }catch (IOException e){
            Log.e(TAG, e.getMessage());
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Log.d(TAG,"inside configureTransform");
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Log.d(TAG,"activity rotation is "+rotation);
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        Log.d(TAG,"Screen view x:y are "+viewWidth+":"+viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.width, mPreviewSize.height);
        Log.d(TAG,"Preview x:y are "+mPreviewSize.width+":"+mPreviewSize.height);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.CENTER);
            //FIXME: Don't just scale, Scale and center the larger on to smaller.
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.height,
                    (float) viewWidth / mPreviewSize.width);
            //matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
        Log.d(TAG, "leaving configureTransform");
    }

    private void takePicture() {
        captureStillPicture();
    }

    private Camera.PictureCallback mPictureCallback = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            mFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
            Thread imageSaverThread = new Thread(new ImageSaver(data, mFile));
            if (mFile == null) {
                Log.d(TAG, "Error creating media file, check storage permissions: ");
                return;
            } else {
                imageSaverThread.start();
            }
            try{
                imageSaverThread.join();
            }catch (Exception e){
                Log.e(TAG,e.getMessage());
            }
            showToast("Saved: " + mFile.getName());
            imagePreviewIntent.putExtra(IMAGE_NAME,
                    Uri.fromFile(mFile).toString());
            Log.d(TAG, "Launching image preview activity");
            closeCamera();
            startActivity(imagePreviewIntent);
        }
    };

    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final byte[] mData;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        public ImageSaver(byte[] data, File file) {
            mData = data;
            mFile = file;
        }

        @Override
        public void run() {
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(mData);
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            } finally {
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }



    }
    private static File getOutputMediaFile(int type) {
        String storageState = Environment.getExternalStorageState();
        Log.d(TAG, "Storage State: " + storageState);
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "TrendQ");
        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "failed to create directory");
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
            Log.d(TAG, "Media File Path is: " + mediaFile.getAbsolutePath());
            return mediaFile;
        } else {
            return null;
        }
    }

    private void captureStillPicture() {
        final Activity activity = getActivity();
        if (null == activity || null == mCamera) {
            return;
        }
        mCamera.takePicture(null, null, mPictureCallback);
        imagePreviewIntent = new Intent(getActivity(), ImagePreview.class);

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.picture: {
                Log.d(TAG,"Click tracked, taking picture");
                takePicture();
                break;
            }
            case R.id.info: {
                Activity activity = getActivity();
                if (null != activity) {
                    new AlertDialog.Builder(activity)
                            .setMessage(R.string.intro_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
                break;
            }
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            Log.d(TAG,"camera released");
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    public void setCameraDisplayOrientation(int cameraId, Camera camera) {
        Camera.CameraInfo info =
                new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        Activity activity = getActivity();
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
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
}
