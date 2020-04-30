package com.irshadillias.tunegenerator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.baoyz.actionsheet.ActionSheet;
import com.irshadillias.tunegenerator.cloudtextrecognition.CloudTextRecognitionProcessor;
import com.irshadillias.tunegenerator.common.VisionImageProcessor;
import com.irshadillias.tunegenerator.databinding.ActivityMainBinding;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ActionSheet.ActionSheetListener {
    ImageButton btnSelect;
    TextView txtContent;
    ActionSheet.Builder builder;
    private static final String TAG = "ChooserActivity";
    private static final int PERMISSION_REQUESTS = 1;
    private ActivityMainBinding mBinding;
    private static final int REQUEST_IMAGE_CAPTURE = 1001;
    private static final int REQUEST_CHOOSE_IMAGE = 1002;
    private Uri imageUri;
    private static final String SIZE_PREVIEW = "w:max"; // Available on-screen width.
    private static final String SIZE_1024_768 = "w:1024"; // ~1024*768 in a normal ratio
    private static final String SIZE_640_480 = "w:640";
    private String selectedSize = SIZE_PREVIEW;
    private VisionImageProcessor imageProcessor;
    private Integer imageMaxWidth;
    // Max height (portrait mode)
    private Integer imageMaxHeight;
    boolean isLandScape;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        builder = ActionSheet.createBuilder(this, getSupportFragmentManager())
                .setCancelButtonTitle("Cancel")
                .setOtherButtonTitles("Camera", "Gallery")
                .setCancelableOnTouchOutside(true)
                .setListener(this);

        mBinding.select.setOnClickListener(v-> builder.show());
        mBinding.txtgenerated.setMovementMethod(new ScrollingMovementMethod());
        mBinding.txtgenerated.setText("Please select your scene");

        if (!allPermissionsGranted()) {
            getRuntimePermissions();
        }
        createImageProcessor();
        isLandScape =
                (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void onDismiss(ActionSheet actionSheet, boolean isCancel) {

    }

    @Override
    public void onOtherButtonClick(ActionSheet actionSheet, int index) {
        if(index == 0){
            startCameraIntentForResult();
        }else if(index == 1){
            startChooseImageIntentForResult();
        }
    }

    private String[] getRequiredPermissions() {
        try {
            PackageInfo info =
                    this.getPackageManager()
                            .getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                return false;
            }
        }
        return true;
    }

    private void getRuntimePermissions() {
        List<String> allNeededPermissions = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                allNeededPermissions.add(permission);
            }
        }

        if (!allNeededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, allNeededPermissions.toArray(new String[0]), PERMISSION_REQUESTS);
        }
    }

    private static boolean isPermissionGranted(Context context, String permission) {
        if (ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission granted: " + permission);
            return true;
        }
        Log.i(TAG, "Permission NOT granted: " + permission);
        return false;
    }


    private void startChooseImageIntentForResult() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_CHOOSE_IMAGE);
    }

    private void startCameraIntentForResult() {
        // Clean up last time's image
        imageUri = null;
        mBinding.albumArt.setImageBitmap(null);

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, "New Picture");
            values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera");
            imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            tryReloadAndDetectInImage();
        } else if (requestCode == REQUEST_CHOOSE_IMAGE && resultCode == RESULT_OK) {
            // In this case, imageUri is returned by the chooser, save it.
            imageUri = data.getData();
            tryReloadAndDetectInImage();
        }
    }

    private void tryReloadAndDetectInImage() {
        try {
            if (imageUri == null) {
                return;
            }

            // Clear the overlay first
            mBinding.txtgenerated.setText("");

            Bitmap imageBitmap;
            if (Build.VERSION.SDK_INT < 29) {
                imageBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            } else {
                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), imageUri);
                imageBitmap = ImageDecoder.decodeBitmap(source);
            }

            // Get the dimensions of the View
            Pair<Integer, Integer> targetedSize = getTargetedWidthHeight();

            int targetWidth = targetedSize.first;
            int maxHeight = targetedSize.second;

            // Determine how much to scale down the image
            float scaleFactor =
                    Math.max(
                            (float) imageBitmap.getWidth() / (float) targetWidth,
                            (float) imageBitmap.getHeight() / (float) maxHeight);

            Bitmap resizedBitmap =
                    Bitmap.createScaledBitmap(
                            imageBitmap,
                            (int) (imageBitmap.getWidth() / scaleFactor),
                            (int) (imageBitmap.getHeight() / scaleFactor),
                            true);

            mBinding.albumArt.setImageBitmap(resizedBitmap);

            imageProcessor.process(resizedBitmap, mBinding.txtgenerated);
        } catch (IOException e) {
            Log.e(TAG, "Error retrieving saved image");
        }

    }

    private void createImageProcessor() {
        imageProcessor = new CloudTextRecognitionProcessor();
    }

    // Gets the targeted width / height.
    private Pair<Integer, Integer> getTargetedWidthHeight() {
        int targetWidth;
        int targetHeight;

        switch (selectedSize) {
            case SIZE_PREVIEW:
                int maxWidthForPortraitMode = getImageMaxWidth();
                int maxHeightForPortraitMode = getImageMaxHeight();
                targetWidth = isLandScape ? maxHeightForPortraitMode : maxWidthForPortraitMode;
                targetHeight = isLandScape ? maxWidthForPortraitMode : maxHeightForPortraitMode;
                break;
            case SIZE_640_480:
                targetWidth = isLandScape ? 640 : 480;
                targetHeight = isLandScape ? 480 : 640;
                break;
            case SIZE_1024_768:
                targetWidth = isLandScape ? 1024 : 768;
                targetHeight = isLandScape ? 768 : 1024;
                break;
            default:
                throw new IllegalStateException("Unknown size");
        }

        return new Pair<>(targetWidth, targetHeight);
    }

    private Integer getImageMaxWidth() {
        if (imageMaxWidth == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to wait for
            // a UI layout pass to get the right values. So delay it to first time image rendering time.
            if (isLandScape) {
                imageMaxWidth =
                        ((View) mBinding.albumArt.getParent()).getHeight() ;
            } else {
                imageMaxWidth = ((View) mBinding.albumArt.getParent()).getWidth();
            }
        }

        return imageMaxWidth;
    }

    // Returns max image height, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    private Integer getImageMaxHeight() {
        if (imageMaxHeight == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to wait for
            // a UI layout pass to get the right values. So delay it to first time image rendering time.
            if (isLandScape) {
                imageMaxHeight = ((View) mBinding.albumArt.getParent()).getWidth();
            } else {
                imageMaxHeight =
                        ((View) mBinding.albumArt.getParent()).getHeight();
            }
        }

        return imageMaxHeight;
    }
}
