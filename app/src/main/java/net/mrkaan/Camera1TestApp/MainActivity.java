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
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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
    static final String TAG = "tag";
    CameraPreview mPreview;
    Thread cameraWorker;
    byte[] byteImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        iv = findViewById(R.id.imageView);

        startCamera();
        setView();
        //takePictureIntent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCamera();
    }

    @SuppressLint({"WrongConstant", "SetTextI18n"})
    private void setView() {
        findViewById(R.id.awb_lock).setOnClickListener(view -> {
            mPreview.awb = !mPreview.awb;
            ((Button) view).setText("awb: " + (mPreview.awb ? "1" : "0"));
            mPreview.surfaceChanged();
        });

        findViewById(R.id.take_photo).setOnClickListener(view -> mCamera.takePicture(null, null, mPicture));

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
        cameraWorker = new Thread(() -> {
            /* first decode */
            Bitmap b = BitmapFactory.decodeByteArray(byteImage, 0, byteImage.length);
            /* changing rotation */
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            Bitmap lastBm = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);

            runOnUiThread(() -> iv.setImageBitmap(Bitmap.createScaledBitmap(lastBm, iv.getWidth(), iv.getHeight(), false)));

            File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
            if (pictureFile == null) {
                Log.d(TAG, "Error creating media file, check storage permissions");
                return;
            }
            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(byteImage);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }
        });
        if (checkCameraHardware(this)) {
            mCamera = getCameraInstance(this);
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
            cameraWorker.start();
        }
    };

    private File getOutputMediaFile(int mediaTypeImage) {
        return null;
    }

    /**
     * A safe way to get an instance of the Camera object.
     */
    public static Camera getCameraInstance(Context context) {
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
            Toast.makeText(context, "Cant start camera", Toast.LENGTH_SHORT).show();
        }
        return c; // returns null if camera is unavailable
    }
}