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
import android.view.SurfaceHolder;
import android.view.SurfaceView;
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

@SuppressWarnings("ALL")
public class MainActivity extends AppCompatActivity {

    ImageView iv;
    Camera mCamera;
    Camera.Parameters mCameraParameters;
    static final String TAG = "tag";
    CameraPreview mPreview;
    boolean awb = false;
    Thread cameraWorker;
    byte[] byteImage;
    String framesize = "normal";
    String sceneMode = "auto";

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

    @SuppressLint("WrongConstant")
    private void setView() {
        findViewById(R.id.awb_lock).setOnClickListener(view -> {
            awb = !awb;
            ((Button) view).setText("awb: " + (awb ? "1" : "0"));
            /* these datas are not using when surface changes */
            mPreview.surfaceChanged();
            /*Camera.Parameters parameters = mCamera.getParameters();
            parameters.setAutoWhiteBalanceLock(awb);
            parameters.setAutoExposureLock(awb);
            mCamera.setParameters(parameters);
            startCamera();*/
        });

        findViewById(R.id.take_photo).setOnClickListener(view -> {

            mCamera.takePicture(null, null, mPicture);
        });

        Spinner framesizeSpinner = (Spinner) findViewById(R.id.framesize_spinner);
        Spinner sceneModeSpinner = (Spinner) findViewById(R.id.scene_mode_spinner);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> framesizeAdapter = ArrayAdapter.createFromResource(this,
                R.array.framesizes, android.R.layout.simple_spinner_item);
        List<String> sceneModes = mCameraParameters.getSupportedSceneModes();
        ArrayAdapter<String> sceneModeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sceneModes);
        // Specify the layout to use when the list of choices appears
        framesizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sceneModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        framesizeSpinner.setAdapter(framesizeAdapter);
        sceneModeSpinner.setAdapter(sceneModeAdapter);

        framesizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                switch (i) {
                    case 0:
                        framesize = "low";
                        break;
                    case 1:
                        framesize = "normal";
                        break;
                    case 2:
                        framesize = "high";
                        break;
                    default:
                        framesize = "normal";
                }
                startCamera();
//                mPreview.surfaceChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                framesize = "normal";
                mPreview.surfaceChanged();
            }
        });

        sceneModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                sceneMode = sceneModes.get(i);
                mPreview.surfaceChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                sceneMode = "auto";
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

            runOnUiThread(() -> {
                iv.setImageBitmap(Bitmap.createScaledBitmap(lastBm, iv.getWidth(), iv.getHeight(), false));
            });

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

    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

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

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) h / w;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    /**
     * A basic Camera preview class
     */
    public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        private SurfaceHolder mHolder;
        private Camera mCamera;
        private List<Camera.Size> mSupportedPreviewSizes;
        private Camera.Size mPreviewSize;

        public CameraPreview(Context context, Camera camera) {
            super(context);
            mCamera = camera;
            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
            // deprecated setting, but required on Android versions prior to 3.0
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        public void surfaceCreated(SurfaceHolder holder) {
            // The Surface has been created, now tell the camera where to draw the preview.
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            } catch (IOException e) {
                Log.d(TAG, "Error setting camera preview: " + e.getMessage());
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // empty. Take care of releasing the Camera preview in your activity.
        }

        public void surfaceChanged() {
            this.surfaceChanged(null, 0, 0, 0);
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            // If your preview can change or rotate, take care of those events here.
            // Make sure to stop the preview before resizing or reformatting it.

            if (mHolder.getSurface() == null) {
                // preview surface does not exist
                return;
            }

            // stop preview before making changes
            try {
                mCamera.stopPreview();
            } catch (Exception e) {
                // ignore: tried to stop a non-existent preview
            }

            // set preview size and make any resize, rotate or
            // reformatting changes here

            // start preview with new settings
            try {
                Camera.Parameters parameters = mCamera.getParameters();
                parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
                parameters.setAutoWhiteBalanceLock(awb);
                parameters.setSceneMode(sceneMode);
                mCamera.setParameters(parameters);
                mCamera.setPreviewDisplay(mHolder);
                mCamera.startPreview();

            } catch (Exception e) {
                Log.d(TAG, "Error starting camera preview: " + e.getMessage());
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
            int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
            setMeasuredDimension(width, height);
            switch (framesize) {
                case "low":
                    width = width / 2;
                    height = height / 2;
                    break;
                case "normal":
                    width = width * 2 / 3;
                    height = height * 2 / 3;
                    break;
            }
            if (mSupportedPreviewSizes != null) {
                mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
            }
        }
    }
}