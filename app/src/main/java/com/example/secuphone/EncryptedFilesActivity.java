package com.example.secuphone;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.secuphone.adapters.EncryptedFilesAdapter;
import com.example.secuphone.utils.FileEncryptionManager;
import com.example.secuphone.utils.PermissionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Activity for managing encrypted files.
 * Allows users to add, view, and delete encrypted files.
 */
public class EncryptedFilesActivity extends AppCompatActivity implements EncryptedFilesAdapter.EncryptedFileListener {

    private static final String TAG = "EncryptedFilesActivity";

    private FileEncryptionManager encryptionManager;
    private PermissionManager permissionManager;
    
    private RecyclerView fileRecyclerView;
    private TextView emptyStateText;
    private FloatingActionButton addFileFab;
    
    private EncryptedFilesAdapter filesAdapter;
    private List<File> encryptedFiles = new ArrayList<>();
    
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_encrypted_files);

        // Initialize toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.encrypted_files_title);

        // Initialize views
        fileRecyclerView = findViewById(R.id.encrypted_files_recycler_view);
        emptyStateText = findViewById(R.id.empty_state_text);
        addFileFab = findViewById(R.id.add_file_fab);

        // Initialize managers
        encryptionManager = new FileEncryptionManager(this);
        permissionManager = new PermissionManager(this);

        // Set up RecyclerView
        fileRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        filesAdapter = new EncryptedFilesAdapter(this, encryptedFiles, this);
        fileRecyclerView.setAdapter(filesAdapter);

        // Register file picker result launcher
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedFileUri = result.getData().getData();
                        if (selectedFileUri != null) {
                            promptForEncryptionPassword(selectedFileUri);
                        }
                    }
                });

        // Set click listeners
        addFileFab.setOnClickListener(v -> {
            if (permissionManager.hasStoragePermission()) {
                openFilePicker();
            } else {
                permissionManager.requestStoragePermission();
            }
        });

        // Load encrypted files
        loadEncryptedFiles();
    }

    /**
     * Load all encrypted files
     */
    private void loadEncryptedFiles() {
        // Clear existing list
        encryptedFiles.clear();
        
        // Get all encrypted files
        File[] files = encryptionManager.getEncryptedFiles();
        
        if (files != null && files.length > 0) {
            encryptedFiles.addAll(Arrays.asList(files));
            fileRecyclerView.setVisibility(View.VISIBLE);
            emptyStateText.setVisibility(View.GONE);
        } else {
            fileRecyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
        }
        
        // Notify adapter
        filesAdapter.notifyDataSetChanged();
    }

    /**
     * Open file picker to select a file to encrypt
     */
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }

    /**
     * Prompt for password to encrypt a file
     */
    private void promptForEncryptionPassword(Uri fileUri) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_enter_password, null);
        TextInputEditText passwordInput = dialogView.findViewById(R.id.password_input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.enter_encryption_password)
                .setMessage(R.string.enter_password_to_encrypt)
                .setView(dialogView)
                .setPositiveButton(R.string.encrypt, null)
                .setNegativeButton(R.string.cancel, null)
                .show()
                .getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String password = passwordInput.getText().toString().trim();
                    
                    if (password.isEmpty()) {
                        passwordInput.setError(getString(R.string.password_required));
                        return;
                    }
                    
                    // Dismiss dialog
                    if (v.getParent().getParent() instanceof AlertDialog) {
                        ((AlertDialog) v.getParent().getParent()).dismiss();
                    }
                    
                    // Show progress dialog
                    AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.encrypting_file)
                            .setMessage(R.string.please_wait)
                            .setCancelable(false)
                            .show();
                    
                    // Perform encryption in background thread
                    new Thread(() -> {
                        try {
                            encryptionManager.encryptFile(fileUri, password);
                            
                            // Update UI on main thread
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(this, R.string.file_encrypted_successfully, Toast.LENGTH_SHORT).show();
                                loadEncryptedFiles();
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Error encrypting file", e);
                            
                            // Update UI on main thread
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(this, getString(R.string.encryption_error, e.getMessage()), 
                                        Toast.LENGTH_LONG).show();
                            });
                        }
                    }).start();
                });
    }

    /**
     * Prompt for password to decrypt and view a file
     */
    private void promptForDecryptionPassword(File encryptedFile) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_enter_password, null);
        TextInputEditText passwordInput = dialogView.findViewById(R.id.password_input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.enter_decryption_password)
                .setMessage(R.string.enter_password_to_view)
                .setView(dialogView)
                .setPositiveButton(R.string.view, null)
                .setNegativeButton(R.string.cancel, null)
                .show()
                .getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String password = passwordInput.getText().toString().trim();
                    
                    if (password.isEmpty()) {
                        passwordInput.setError(getString(R.string.password_required));
                        return;
                    }
                    
                    // Dismiss dialog
                    if (v.getParent().getParent() instanceof AlertDialog) {
                        ((AlertDialog) v.getParent().getParent()).dismiss();
                    }
                    
                    // Show progress dialog
                    AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.decrypting_file)
                            .setMessage(R.string.please_wait)
                            .setCancelable(false)
                            .show();
                    
                    // Perform decryption in background thread
                    new Thread(() -> {
                        try {
                            File decryptedFile = encryptionManager.decryptFile(encryptedFile, password);
                            
                            // Update UI on main thread
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                openDecryptedFile(decryptedFile);
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Error decrypting file", e);
                            
                            // Update UI on main thread
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(this, getString(R.string.decryption_error, e.getMessage()), 
                                        Toast.LENGTH_LONG).show();
                            });
                        }
                    }).start();
                });
    }

    /**
     * Open a decrypted file using appropriate handler
     */
    private void openDecryptedFile(File file) {
        if (file == null || !file.exists() || file.length() == 0) {
            Log.e(TAG, "Invalid decrypted file: " + (file == null ? "null" : file.getAbsolutePath()));
            Toast.makeText(this, R.string.error_opening_file, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Ensure file has read permission
        boolean readPermissionGranted = file.setReadable(true, false);
        if (!readPermissionGranted) {
            Log.w(TAG, "Could not set file as readable: " + file.getAbsolutePath());
        }
        
        Log.d(TAG, "Opening decrypted file: " + file.getAbsolutePath() + 
              ", size: " + file.length() + " bytes, readable: " + file.canRead());

        try {
            String fileName = file.getName().toLowerCase();
            String mimeType = getMimeType(fileName);
            Log.d(TAG, "File mime type determined as: " + mimeType);
            
            // For images, show in a dialog
            if (isImageFile(fileName)) {
                Log.d(TAG, "Opening as image in internal viewer");
                showImageInDialog(file);
                return;
            }
            
            // For other files, try multiple approaches
            // First, try using FileProvider
            try {
                Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        file);
                
                Log.d(TAG, "Created content URI via FileProvider: " + fileUri);
                
                // Try to open with explicit MIME type first
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri, mimeType);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                try {
                    Log.d(TAG, "Attempting to open with specific MIME type: " + mimeType);
                    startActivity(intent);
                    return;
                } catch (android.content.ActivityNotFoundException e) {
                    Log.w(TAG, "No specific handler for mime type: " + mimeType, e);
                    // Continue to try generic viewer
                }
                
                // If no specific handler, try a generic file viewer
                Intent genericIntent = new Intent(Intent.ACTION_VIEW);
                genericIntent.setDataAndType(fileUri, "*/*");
                genericIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                genericIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                try {
                    Log.d(TAG, "Attempting to open with generic mime type */*");
                    startActivity(genericIntent);
                    return;
                } catch (android.content.ActivityNotFoundException e) {
                    Log.e(TAG, "No generic handler available", e);
                    Toast.makeText(this, R.string.no_app_to_open_file, Toast.LENGTH_SHORT).show();
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error creating FileProvider URI", e);
            }
            
            // If FileProvider approach failed, try creating a temporary copy in external storage
            // which might be more accessible to other apps
            try {
                File externalDir = new File(getExternalCacheDir(), "shared_files");
                if (!externalDir.exists()) {
                    externalDir.mkdirs();
                }
                
                File externalCopy = new File(externalDir, file.getName());
                copyFile(file, externalCopy);
                
                Uri externalUri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        externalCopy);
                
                Intent externalIntent = new Intent(Intent.ACTION_VIEW);
                externalIntent.setDataAndType(externalUri, mimeType);
                externalIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                externalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                try {
                    Log.d(TAG, "Attempting to open with external copy");
                    startActivity(externalIntent);
                    return;
                } catch (android.content.ActivityNotFoundException e) {
                    Log.e(TAG, "Failed to open with external copy", e);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating external copy", e);
            }
            
            // If all else fails
            Toast.makeText(this, R.string.no_app_to_open_file, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error opening decrypted file", e);
            Toast.makeText(this, getString(R.string.error_opening_file) + ": " + e.getMessage(), 
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Copy file from source to destination
     */
    private void copyFile(File src, File dst) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(src);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
            
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            
            // Ensure the new file is readable
            dst.setReadable(true, false);
        }
    }

    /**
     * Show image in a fullscreen dialog
     */
    private void showImageInDialog(File imageFile) {
        try {
            Log.d(TAG, "Showing image in dialog: " + imageFile.getAbsolutePath());
            
            Dialog imageDialog = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
            imageDialog.setContentView(R.layout.dialog_image_viewer);
            
            ImageView imageView = imageDialog.findViewById(R.id.image_view);
            ImageButton closeButton = imageDialog.findViewById(R.id.close_button);
            
            // Load image using safer method
            try {
                android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
                
                // Calculate optimal sample size to avoid OOM
                int maxDimension = Math.max(options.outWidth, options.outHeight);
                int sampleSize = 1;
                while (maxDimension / sampleSize > 2048) {
                    sampleSize *= 2;
                }
                
                options.inJustDecodeBounds = false;
                options.inSampleSize = sampleSize;
                
                Log.d(TAG, "Loading image with sample size: " + sampleSize);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
                
                if (bitmap != null) {
                    Log.d(TAG, "Image loaded successfully, dimensions: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                    imageView.setImageBitmap(bitmap);
                } else {
                    Log.e(TAG, "Failed to decode bitmap");
                    Toast.makeText(this, R.string.error_displaying_image, Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "Out of memory error loading image", e);
                Toast.makeText(this, R.string.error_image_too_large, Toast.LENGTH_SHORT).show();
                return;
            }
            
            closeButton.setOnClickListener(v -> imageDialog.dismiss());
            imageDialog.show();
            Log.d(TAG, "Image dialog shown");
        } catch (Exception e) {
            Log.e(TAG, "Error showing image", e);
            Toast.makeText(this, R.string.error_displaying_image, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Confirm file deletion
     */
    private void confirmDeleteFile(File file) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_file)
                .setMessage(R.string.delete_file_confirmation)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (encryptionManager.deleteEncryptedFile(file)) {
                        Toast.makeText(this, R.string.file_deleted, Toast.LENGTH_SHORT).show();
                        loadEncryptedFiles();
                    } else {
                        Toast.makeText(this, R.string.error_deleting_file, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onFileSelected(File file) {
        promptForDecryptionPassword(file);
    }

    @Override
    public void onFileDeleteRequested(File file) {
        confirmDeleteFile(file);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Check if file is an image based on extension
     */
    private boolean isImageFile(String fileName) {
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                fileName.endsWith(".png") || fileName.endsWith(".gif") ||
                fileName.endsWith(".bmp") || fileName.endsWith(".webp");
    }

    /**
     * Get MIME type from file extension
     */
    private String getMimeType(String fileName) {
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".gif")) return "image/gif";
        if (fileName.endsWith(".pdf")) return "application/pdf";
        if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) return "application/msword";
        if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) return "application/vnd.ms-excel";
        if (fileName.endsWith(".ppt") || fileName.endsWith(".pptx")) return "application/vnd.ms-powerpoint";
        if (fileName.endsWith(".txt")) return "text/plain";
        if (fileName.endsWith(".mp3")) return "audio/mpeg";
        if (fileName.endsWith(".mp4")) return "video/mp4";
        return "*/*";
    }
} 