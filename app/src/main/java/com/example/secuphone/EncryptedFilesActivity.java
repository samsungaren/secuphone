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
        try {
            String fileName = file.getName().toLowerCase();
            String mimeType = getMimeType(fileName);
            
            // For images, show in a dialog
            if (isImageFile(fileName)) {
                showImageInDialog(file);
                return;
            }
            
            // For other files, use system handler
            Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file);
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, mimeType);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.no_app_to_open_file, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening decrypted file", e);
            Toast.makeText(this, R.string.error_opening_file, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show image in a fullscreen dialog
     */
    private void showImageInDialog(File imageFile) {
        try {
            Dialog imageDialog = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
            imageDialog.setContentView(R.layout.dialog_image_viewer);
            
            ImageView imageView = imageDialog.findViewById(R.id.image_view);
            ImageButton closeButton = imageDialog.findViewById(R.id.close_button);
            
            // Load image
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            imageView.setImageBitmap(bitmap);
            
            closeButton.setOnClickListener(v -> imageDialog.dismiss());
            imageDialog.show();
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