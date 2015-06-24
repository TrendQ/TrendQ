package com.trendq;

import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImageSaver implements Runnable {

    /**
     * The JPEG image
     */
    private final byte[] mData;
    /**
     * The file we save the image into.
     */
    private final File mFile;

    private static final String TAG = "TrendQ.IS";

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