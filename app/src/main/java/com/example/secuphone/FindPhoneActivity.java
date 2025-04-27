package com.example.secuphone;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import com.example.secuphone.R;


public class FindPhoneActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_STORAGE_PERMISSION = 201;
    private static final String PREF_NAME = "FindPhonePrefs";
    private static final String PASSWORD_KEY = "password";
    private static final int MAX_ATTEMPTS = 3;
    
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private EditText verifyPasswordInput;
    private Button savePasswordButton;
    private Button verifyButton;
    private TextView errorMessage;
    private TextView successMessage;
    private TextView attemptsText;
    private LinearLayout setupSection;
    private LinearLayout verifyPasswordSection;
    
    private String savedPassword;
    private int failedAttempts = 0;
    
    // Camera related variables
    private TextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_phone);
        
        // Setup back navigation
        ImageButton menuButton = findViewById(R.id.menu_button);
        menuButton.setOnClickListener(v -> finish());
        
        // Initialize views
        initializeViews();
        
        // Check for existing password
        checkExistingPassword();
        
        // Setup button listeners
        setupButtonListeners();
        
        // Initialize camera preview
        textureView = findViewById(R.id.texture_view);
        textureView.setSurfaceTextureListener(textureListener);
    }
    
    private void initializeViews() {
        passwordInput = findViewById(R.id.password_input);
        confirmPasswordInput = findViewById(R.id.confirm_password_input);
        verifyPasswordInput = findViewById(R.id.verify_password_input);
        savePasswordButton = findViewById(R.id.save_password_button);
        verifyButton = findViewById(R.id.verify_button);
        errorMessage = findViewById(R.id.error_message);
        successMessage = findViewById(R.id.success_message);
        attemptsText = findViewById(R.id.attempts_text);
        setupSection = findViewById(R.id.setup_section);
        verifyPasswordSection = findViewById(R.id.verify_password_section);
        
        // Set initial attempts text
        updateAttemptsText();
    }
    
    private void checkExistingPassword() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        savedPassword = prefs.getString(PASSWORD_KEY, null);
        
        if (savedPassword != null) {
            // Password exists, show verification UI
            setupSection.setVisibility(View.GONE);
            verifyPasswordSection.setVisibility(View.VISIBLE);
        } else {
            // No password, show setup UI
            setupSection.setVisibility(View.VISIBLE);
            verifyPasswordSection.setVisibility(View.GONE);
        }
    }
    
    private void setupButtonListeners() {
        savePasswordButton.setOnClickListener(v -> {
            String password = passwordInput.getText().toString();
            String confirmPassword = confirmPasswordInput.getText().toString();
            
            if (TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
                Toast.makeText(this, "Please enter and confirm your password", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Save password to shared preferences
            SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            prefs.edit().putString(PASSWORD_KEY, password).apply();
            
            // Update UI
            savedPassword = password;
            setupSection.setVisibility(View.GONE);
            verifyPasswordSection.setVisibility(View.VISIBLE);
            successMessage.setText(R.string.setup_complete);
            successMessage.setVisibility(View.VISIBLE);
            
            // Hide success message after 3 seconds
            new Handler().postDelayed(() -> successMessage.setVisibility(View.GONE), 3000);
        });
        
        verifyButton.setOnClickListener(v -> {
            String inputPassword = verifyPasswordInput.getText().toString();
            
            if (TextUtils.isEmpty(inputPassword)) {
                Toast.makeText(this, "Please enter your password", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (inputPassword.equals(savedPassword)) {
                // Correct password
                verifyPasswordInput.setText("");
                errorMessage.setVisibility(View.GONE);
                successMessage.setText(R.string.password_saved);
                successMessage.setVisibility(View.VISIBLE);
                
                // Reset failed attempts
                failedAttempts = 0;
                updateAttemptsText();
                
                // Hide success message after 3 seconds
                new Handler().postDelayed(() -> successMessage.setVisibility(View.GONE), 3000);
            } else {
                // Wrong password
                failedAttempts++;
                updateAttemptsText();
                errorMessage.setVisibility(View.VISIBLE);
                
                if (failedAttempts >= MAX_ATTEMPTS) {
                    // Take photo after 3 failed attempts
                    if (checkCameraPermissions()) {
                        takePicture();
                    }
                    // Reset failed attempts after taking photo
                    failedAttempts = 0;
                    updateAttemptsText();
                }
            }
        });
    }
    
    private void updateAttemptsText() {
        int remainingAttempts = MAX_ATTEMPTS - failedAttempts;
        attemptsText.setText(getString(R.string.attempts_remaining, remainingAttempts));
    }
    
    // Check required permissions
    private boolean checkCameraPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return false;
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            return false;
        }
        
        return true;
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, check for storage permission
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    takePicture();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
                }
            } else {
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takePicture();
            } else {
                Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    // Camera setup and capturing functions
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            // Not initializing camera here as we only want to use it when needed
        }
        
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }
        
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
    
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }
        
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }
        
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        }
    };
    
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // Get front camera ID
            String cameraId = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id;
                    break;
                }
            }
            
            if (cameraId == null) {
                // If no front camera, use the first available camera
                cameraId = manager.getCameraIdList()[0];
            }
            
            // Get camera characteristics and output sizes
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            
            // Check permissions again
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            
            // Setup image reader for capturing still images
            imageReader = ImageReader.newInstance(imageDimension.getWidth(), imageDimension.getHeight(), ImageFormat.JPEG, 1);
            imageReader.setOnImageAvailableListener(readerListener, backgroundHandler);
            
            // Open camera
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    
    private void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                return;
                            }
                            
                            cameraCaptureSession = session;
                            updatePreview();
                        }
                        
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(FindPhoneActivity.this, "Camera configuration failed", Toast.LENGTH_SHORT).show();
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    
    private void updatePreview() {
        if (cameraDevice == null) {
            return;
        }
        
        try {
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    
    private void takePicture() {
        if (cameraDevice == null) {
            startBackgroundThread();
            if (textureView.isAvailable()) {
                openCamera();
            } else {
                textureView.setSurfaceTextureListener(textureListener);
            }
        } else {
            captureStill();
        }
    }
    
    private void captureStill() {
        try {
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            
            // Orientation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 270); // Portrait orientation for front camera
            
            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    closeCamera();
                }
            };
            
            cameraCaptureSession.stopRepeating();
            cameraCaptureSession.abortCaptures();
            cameraCaptureSession.capture(captureBuilder.build(), captureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    
    private final ImageReader.OnImageAvailableListener readerListener = reader -> {
        try (Image image = reader.acquireLatestImage()) {
            if (image != null) {
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);
                saveImageToGallery(bytes);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    };
    
    private void saveImageToGallery(byte[] bytes) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "Intruder_" + timeStamp + ".jpg";
        
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SecuPhone");
        
        Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        
        try {
            if (imageUri != null) {
                getContentResolver().openOutputStream(imageUri).write(bytes);
                runOnUiThread(() -> Toast.makeText(FindPhoneActivity.this, R.string.photo_saved, Toast.LENGTH_LONG).show());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("Camera Background");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    
    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
    }
    
    @Override
    protected void onPause() {
        stopBackgroundThread();
        closeCamera();
        super.onPause();
    }
} 