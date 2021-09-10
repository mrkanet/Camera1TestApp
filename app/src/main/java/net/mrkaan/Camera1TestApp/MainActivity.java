package net.mrkaan.Camera1TestApp;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    ImageView iv;
    Camera mCamera;
    Camera.Parameters mCameraParameters;
    Camera.CameraInfo info = new Camera.CameraInfo();
    static final String TAG = "tag";
    CameraPreview mPreview;
    byte[] byteImage;
    int devicePosition = 0;
    static boolean isCropping = false;
    int quality = 85;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        iv = findViewById(R.id.imageView);

        startCamera();
        setView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCamera();
    }

    @SuppressLint({"WrongConstant", "SetTextI18n"})
    private void setView() {
        findViewById(R.id.awb_lock).setOnClickListener(view -> {
            mPreview.awbLock = !mPreview.awbLock;
            ((Button) view).setText("awb: " + (mPreview.awbLock ? "1" : "0"));
            mPreview.surfaceChanged();
        });

        findViewById(R.id.take_photo).setOnClickListener(view -> mCamera.takePicture(null, null, mPicture));

        findViewById(R.id.device_position).setOnClickListener(view -> {
            Camera.getCameraInfo(devicePosition, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                devicePosition = 0;
            } else {
                devicePosition = 1;
            }
            mPreview.awbLock = false;
            mPreview.aeLock = false;
            mPreview.exposureCompensation = 0;
            mPreview.setExposureCompensation = false;
            startCamera();
        });

        findViewById(R.id.set_exposure).setOnClickListener(view -> {
            try {
                int expComp = Integer.parseInt(((EditText) findViewById(R.id.exp_comp)).getText().toString());
                int minExp = mCameraParameters.getMaxExposureCompensation();
                int maxExp = mCameraParameters.getMinExposureCompensation();
                if (expComp >= minExp && expComp <= maxExp) {
                    mPreview.exposureCompensation = expComp;
                    mPreview.aeLock = false;
                    mPreview.setExposureCompensation = true;
                } else {
                    Toast.makeText(view.getContext(), "Enter between: " + minExp + ", " + maxExp, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(view.getContext(), "Enter an integer", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.set_fps).setOnClickListener(view -> {
            try {
                int maxFps = Integer.parseInt(((EditText) findViewById(R.id.max_fps)).getText().toString());
                int minFps = Integer.parseInt(((EditText) findViewById(R.id.min_fps)).getText().toString());
                List<int[]> supportedFpsRanges = mCameraParameters.getSupportedPreviewFpsRange();
                boolean passed = false;
                for (int i = 0; i < supportedFpsRanges.size(); i++) {
                    int[] range = supportedFpsRanges.get(i);
                    if (range[0] <= minFps && range[1] >= maxFps) {
                        passed = true;
                        break;
                    }
                }
                if (passed) {
                    mPreview.fpsRange = new int[]{minFps, maxFps};
                    mPreview.surfaceChanged();
                } else {
                    String ranges = "";
                    for (int[] i : supportedFpsRanges) {
                        ranges += "[" + i[0] + ", " + i[1] + "]  ";
                    }
                    Toast.makeText(view.getContext(), "Supported fps ranges are: " + ranges, Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Toast.makeText(view.getContext(), "Enter integer", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.set_is_cropping).setOnClickListener(view -> {
            isCropping = !isCropping;
            ((Button) view).setText(isCropping ? "Crop" : "Don't Crop");
        });

        findViewById(R.id.set_quality).setOnClickListener(view -> {
            switch (quality) {
                case 70:
                    quality = 100;
                    ((Button) view).setText("Q: High");
                    break;
                case 100:
                    quality = 85;
                    ((Button) view).setText("Q: Normal");
                    break;
                default:
                    quality = 70;
                    ((Button) view).setText("Q: Low");
            }
        });

        Spinner frameSizeSpinner = findViewById(R.id.frame_size_spinner);
        Spinner sceneModeSpinner = findViewById(R.id.scene_mode_spinner);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> frameSizeAdapter = ArrayAdapter.createFromResource(this,
                R.array.framesizes, android.R.layout.simple_spinner_item);
        List<String> sceneModes = mCameraParameters.getSupportedSceneModes();
        ArrayAdapter<String> sceneModeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sceneModes);
        // Specify the layout to use when the list of choices appears
        frameSizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sceneModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        frameSizeSpinner.setAdapter(frameSizeAdapter);
        sceneModeSpinner.setAdapter(sceneModeAdapter);

        frameSizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                switch (i) {
                    case 0:
                        mPreview.frameSize = "low";
                        break;
                    case 2:
                        mPreview.frameSize = "high";
                        break;
                    default:
                        mPreview.frameSize = "normal";
                }
                startCamera();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                mPreview.frameSize = "normal";
                startCamera();
            }
        });

        sceneModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                mPreview.sceneMode = sceneModes.get(i);
                mPreview.surfaceChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                mPreview.sceneMode = "auto";
                mPreview.surfaceChanged();
            }
        });
    }

    private void startCamera() {
        if (checkCameraHardware(this)) {
            mCamera = getCameraInstance(this, devicePosition);
            if (mCamera != null) {
                mCamera.setDisplayOrientation(90);
                mPreview = new CameraPreview(this, mCamera);
                ((FrameLayout) findViewById(R.id.frameLayout)).addView(mPreview);
                mCameraParameters = mCamera.getParameters();
            }
        }
    }

    private boolean checkCameraHardware(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    private final Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            startCamera();
            byteImage = data;
            new Thread(() -> cameraWorker()).start();
        }
    };

    private File getOutputMediaFile(int mediaTypeImage) {
        return null;
    }

    /**
     * A safe way to get an instance of the Camera object.
     */
    public static Camera getCameraInstance(Context context, int devicePosition) {
        Camera c = null;
        try {
            c = Camera.open(devicePosition); // attempt to get a Camera instance
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
            Toast.makeText(context, "Cant start camera", Toast.LENGTH_SHORT).show();
        }
        return c; // returns null if camera is unavailable
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private void cameraWorker() {
        if (mCamera != null) {
            /* first decode */
            Bitmap b = BitmapFactory.decodeByteArray(byteImage, 0, byteImage.length);
            /* changing rotation */
            int degree = 90;
            if (devicePosition == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                degree = 270;
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(degree);
            Bitmap croppedBitmap;
            if (isCropping) {
                DisplayMetrics displayMetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                TypedValue tv = new TypedValue();
                int actionBarHeight = 0;
                if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                    actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
                }
                int height = (int) (b.getHeight() * getMorePercent(mPreview.getWidth(), displayMetrics.widthPixels));
                int width = (int) (b.getWidth() * getMorePercent(mPreview.getHeight(), displayMetrics.heightPixels));
                croppedBitmap = Bitmap.createBitmap(b, actionBarHeight, b.getHeight() - width, height, width, matrix, true);
            } else {
                croppedBitmap = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
            Bitmap finalBitmap = BitmapFactory.decodeStream(new ByteArrayInputStream(out.toByteArray()));
            runOnUiThread(() -> iv.setImageBitmap(Bitmap.createScaledBitmap(finalBitmap, iv.getWidth(), iv.getHeight(), false)));

            File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
            if (pictureFile != null) {
                try {
                    FileOutputStream fos = new FileOutputStream(pictureFile);
                    fos.write(byteImage);
                    fos.close();
                } catch (FileNotFoundException e) {
                    Log.d(TAG, "File not found: " + e.getMessage());
                } catch (IOException e) {
                    Log.d(TAG, "Error accessing file: " + e.getMessage());
                }
            } else {
                Log.d(TAG, "Error creating media file, check storage permissions");
            }
        }
    }

    private float getMorePercent(float prev, float disp) {
        return ((prev / disp) * 100 + 1) / 100;
    }
}