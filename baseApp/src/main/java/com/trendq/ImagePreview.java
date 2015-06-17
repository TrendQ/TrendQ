package com.trendq;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;


/**
 * Activity responsible for Image Preview
 */
public class ImagePreview extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_saved_image_preview);
        Intent intent = getIntent();
        intent.getStringExtra(SnapshotMain.IMAGENAME);
    }
}
