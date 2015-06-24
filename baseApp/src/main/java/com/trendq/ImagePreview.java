package com.trendq;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;


/**
 * Activity responsible for Image Preview
 */
public class ImagePreview extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_saved_image_preview);
        Intent intent = getIntent();
        Uri uri = Uri.parse(intent.getStringExtra(SnapshotFragment.IMAGE_NAME));
        ImageView imageView = (ImageView)findViewById(R.id.image_preview);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setImageURI(uri);
    }
}
