package com.trendq;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SnapshotFragment extends Fragment implements View.OnClickListener{

    public static final int MEDIA_TYPE_IMAGE = 1;

    protected static final String IMAGE_NAME = "trendq.image_preview";

    private static final String TAG = "TrendQ.SF";

    Intent imagePreviewIntent;

    private AutoFitTextureView mTextureView;

    private File mFile;

    private CameraService cameraService;

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            Log.d(TAG,"onSurfaceTextureAvailable: "+width+":"+height);
            openCamera(width, height);
            cameraService.createCameraPreviewSession(mTextureView);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            Log.d(TAG,"onSurfaceTextureSizeChanged");
            //configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            Log.d(TAG,"onSurfaceTextureDestroyed");
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

    public static SnapshotFragment newInstance() {
        SnapshotFragment fragment = new SnapshotFragment();
        fragment.setRetainInstance(true);
        return fragment;
    }

    private void showToast(String text) {
        Message message = Message.obtain();
        message.obj = text;
        mMessageHandler.sendMessage(message);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        return inflater.inflate(R.layout.activity_snapshot_fragment, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated");
        cameraService  = new CameraService();
        view.findViewById(R.id.picture).setOnClickListener(this);
        view.findViewById(R.id.info).setOnClickListener(this);
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        cameraService.setmTextureView(mTextureView);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.d(TAG, "onActivityCreated");
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        mTextureView = cameraService.getmTextureView();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
            cameraService.createCameraPreviewSession(mTextureView);
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG,"onPause");
        closeCamera();
        super.onPause();
    }

    /**
     * Opens the camera and sets Preview size
     */
    private void openCamera(int width, int height) {
        //TODO opens only back facing camera, need to improvise for front support
        cameraService.initCamera(0);
        cameraService.setUpCameraParameters(width, height, getActivity());
        mTextureView.setAspectRatio(1, 1);
        cameraService.configureTransform(mTextureView.getWidth(), mTextureView.getHeight(), getActivity());
    }

    /**
     * Closes the current {@link SnapshotFragment}
     */
    private void closeCamera() {
        cameraService.releaseCamera();
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

            closeCamera();
            if(imagePreviewIntent!=null){
                imagePreviewIntent.putExtra(IMAGE_NAME,
                        Uri.fromFile(mFile).toString());
                Log.d(TAG, "Launching image preview activity");
                startActivity(imagePreviewIntent);
            }else{

            }
        }
    };

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

    private void takePicture() {
        final Activity activity = getActivity();
        if (null == activity) {
            return;
        }
        cameraService.getmCamera().takePicture(null, null, mPictureCallback);
        //initialize image preview intent
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

}
