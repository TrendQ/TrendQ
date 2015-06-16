package com.trendq;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class SnapshotMain extends AppCompatActivity {
    private static final String TAG = "TrendQ.SnapshotMain";
    public static final int MEDIA_TYPE_IMAGE = 1;
    static final int REQUEST_TAKE_PHOTO = 1;
    protected static final String IMAGENAME = "trendq.imagepreview";
    private Camera mCamera = null;
    private CameraPreview mPreview = null;
    Intent imagePreview = new Intent(this, ImagePreview.class);
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Creating Activity");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_snapshot_main);
        if(!checkCameraHardware(this))
            return;
        mCamera = getCameraInstance();
        if(mCamera!=null){
            Camera.Parameters params = mCamera.getParameters();
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            List<Camera.Size> list = params.getSupportedPictureSizes();
            params.setPreviewSize(list.get(0).width,list.get(0).height);
            params.set("orientation", "portrait");
            mCamera.setDisplayOrientation(90);
            mCamera.setParameters(params);
            Log.d(TAG,"Focus mode is "+params.getFocusMode());
            mPreview = new CameraPreview(this, mCamera);
            Log.d(TAG,"camera preview created");
            FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
            preview.addView(mPreview);
            // Add a listener to the Capture button
            Button captureButton = (Button) findViewById(R.id.button_capture);
            captureButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mCamera.takePicture(null, null, mPicture);
                            Log.d(TAG, "camera took picture");
                            Intent intent= new Intent();
                        }
                    });
        }
    }

    /** Check if this device has a camera */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            Log.d(TAG, "Camera found");
            return true;
        } else {
            Log.e(TAG,"No camera found");
            return false;
        }
    }

    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open();
            Log.d(TAG,"Camera opened");
        }
        catch (Exception e){
            Log.e(TAG, "Could not obtain Camera."+e.getMessage());
        }
        return c;
    }

    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
            if (pictureFile == null){
                Log.d(TAG, "Error creating media file, check storage permissions: ");
                return;
            } else{
                try {
                    FileOutputStream fos = new FileOutputStream(pictureFile);
                    fos.write(data);
                    fos.close();
                } catch (FileNotFoundException e) {
                    Log.d(TAG, "File not found: " + e.getMessage());
                } catch (IOException e) {
                    Log.d(TAG, "Error accessing file: " + e.getMessage());
                }
            }
            // Continue only if the File was successfully created
            if (pictureFile != null) {
                imagePreview.putExtra(IMAGENAME,
                        Uri.fromFile(pictureFile));
                startActivity(imagePreview);
            }
        }
    };


    /** Create a File for saving an image or video */
    private static File getOutputMediaFile(int type){
        String storageState = Environment.getExternalStorageState();
        Log.d(TAG,"Storage State: "+storageState);
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "TrendQ");
        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d(TAG, "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
            Log.d(TAG,"Media File Path is: "+mediaFile.getAbsolutePath());
        } else {
            return null;
        }

        return mediaFile;
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();
            mCamera = null;
        }
    }

}
