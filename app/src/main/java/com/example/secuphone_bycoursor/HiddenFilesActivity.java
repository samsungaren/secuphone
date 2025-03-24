package com.example.secuphone_bycoursor;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.secuphone_bycoursor.adapters.HiddenFilesAdapter;
import com.example.secuphone_bycoursor.utils.PermissionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class HiddenFilesActivity extends AppCompatActivity {

    private static final String HIDDEN_FILES_DIR = ".secuphone_hidden";

    private Button hideFileButton;
    private TextView noFilesText;
    private RecyclerView hiddenFilesList;
    private FloatingActionButton hideFileFab;

    private List<File> hiddenFiles = new ArrayList<>();

    private ActivityResultLauncher<Intent> filePickerLauncher;
    private PermissionManager permissionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_hidden_files);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.hidden_files_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        hideFileButton = findViewById(R.id.hide_file_button);
        noFilesText = findViewById(R.id.no_files_text);
        hiddenFilesList = findViewById(R.id.hidden_files_list);
        hideFileFab = findViewById(R.id.hide_file_fab);

        ImageButton menuButton = findViewById(R.id.menu_button);
        menuButton.setOnClickListener(v -> finish());

        hiddenFilesList.setLayoutManager(new LinearLayoutManager(this));

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedFileUri = result.getData().getData();
                        if (selectedFileUri != null) {
                            hideFile(selectedFileUri);
                        }
                    }
                });

        permissionManager = new PermissionManager(this);

        hideFileButton.setOnClickListener(v -> {
            if (permissionManager.hasStoragePermission()) {
                openFilePicker();
            } else {
                permissionManager.requestDirectStoragePermission();
            }
        });
        
        // Set up floating action button
        if (hideFileFab != null) {
            hideFileFab.setOnClickListener(v -> {
                if (permissionManager.hasStoragePermission()) {
                    openFilePicker();
                } else {
                    permissionManager.requestDirectStoragePermission();
                }
            });
        }

        loadHiddenFilesWithPermissionCheck();
    }

    private void loadHiddenFilesWithPermissionCheck() {
        if (permissionManager.hasStoragePermission()) {
            loadHiddenFiles();
        } else {
            permissionManager.checkStoragePermission(new PermissionManager.OnPermissionResultListener() {
                @Override
                public void onPermissionGranted() {
                    loadHiddenFiles();
                }

                @Override
                public void onPermissionDenied() {
                    hiddenFilesList.setVisibility(View.GONE);
                    noFilesText.setVisibility(View.VISIBLE);
                    noFilesText.setText(R.string.storage_permission_required);
                    
                    // Show a toast with instructions
                    Toast.makeText(HiddenFilesActivity.this, 
                        "Use the 'Hide a New File' button to grant storage access when needed", 
                        Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }

    private void hideFile(Uri sourceUri) {
        try {
            // Create the hidden directory if it doesn't exist
            File hiddenDir = new File(getExternalFilesDir(null), HIDDEN_FILES_DIR);
            if (!hiddenDir.exists()) {
                hiddenDir.mkdirs();
                // Create a .nomedia file to hide the folder from media scanners
                File nomedia = new File(hiddenDir, ".nomedia");
                if (!nomedia.exists()) {
                    nomedia.createNewFile();
                }
            }
            
            // Create destination file
            String fileName = getFileNameFromUri(sourceUri);
            if (fileName == null || fileName.isEmpty()) {
                fileName = "hidden_file_" + System.currentTimeMillis();
            }
            File destinationFile = new File(hiddenDir, fileName);
            
            // Copy the file
            copyFile(sourceUri, destinationFile);
            
            // Show success message
            Toast.makeText(this, R.string.file_hidden_success, Toast.LENGTH_SHORT).show();
            
            // Refresh the list
            loadHiddenFiles();
            
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error hiding file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
    
    private void copyFile(Uri sourceUri, File destFile) throws IOException {
        try (FileInputStream inStream = (FileInputStream) getContentResolver().openInputStream(sourceUri);
             FileOutputStream outStream = new FileOutputStream(destFile)) {
            
            FileChannel inChannel = inStream.getChannel();
            FileChannel outChannel = outStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
    }
    
    private void loadHiddenFiles() {
        // Clear previous list
        hiddenFiles.clear();
        
        // Get hidden files directory
        File hiddenDir = new File(getExternalFilesDir(null), HIDDEN_FILES_DIR);
        if (hiddenDir.exists() && hiddenDir.isDirectory()) {
            File[] files = hiddenDir.listFiles(file -> !file.getName().equals(".nomedia"));
            if (files != null && files.length > 0) {
                for (File file : files) {
                    if (file.isFile()) {
                        hiddenFiles.add(file);
                    }
                }
            }
        }
        
        // Update UI based on results
        if (hiddenFiles.isEmpty()) {
            hiddenFilesList.setVisibility(View.GONE);
            noFilesText.setVisibility(View.VISIBLE);
            noFilesText.setText(R.string.no_hidden_files);
            
            // Hide FAB if we have a button
            if (hideFileFab != null) {
                hideFileFab.setVisibility(View.GONE);
            }
        } else {
            hiddenFilesList.setVisibility(View.VISIBLE);
            noFilesText.setVisibility(View.GONE);
            
            // Show FAB if we have files
            if (hideFileFab != null) {
                hideFileFab.setVisibility(View.VISIBLE);
            }
            
            // Create and set adapter
            HiddenFilesAdapter adapter = new HiddenFilesAdapter(hiddenFiles, this::confirmUnhideFile);
            hiddenFilesList.setAdapter(adapter);
        }
    }
    
    /**
     * Show confirmation dialog before unhiding file
     */
    private void confirmUnhideFile(File file) {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Unhide File")
            .setMessage("This file will be moved to your Downloads folder. Continue?")
            .setPositiveButton("Unhide", (dialog, which) -> unhideFile(file))
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void unhideFile(File file) {
        try {
            // Create destination in Downloads folder
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File destinationFile = new File(downloadsDir, file.getName());
            
            // If file with same name exists, rename
            if (destinationFile.exists()) {
                String baseName = file.getName();
                String extension = "";
                int dotIndex = baseName.lastIndexOf('.');
                if (dotIndex > 0) {
                    extension = baseName.substring(dotIndex);
                    baseName = baseName.substring(0, dotIndex);
                }
                
                int counter = 1;
                while (destinationFile.exists()) {
                    destinationFile = new File(downloadsDir, baseName + "_" + counter + extension);
                    counter++;
                }
            }
            
            // Copy file
            try (FileInputStream inStream = new FileInputStream(file);
                 FileOutputStream outStream = new FileOutputStream(destinationFile)) {
                
                FileChannel inChannel = inStream.getChannel();
                FileChannel outChannel = outStream.getChannel();
                inChannel.transferTo(0, inChannel.size(), outChannel);
            }
            
            // Delete original
            if (file.delete()) {
                Toast.makeText(this, R.string.file_unhidden_success, Toast.LENGTH_SHORT).show();
                
                // Refresh list
                loadHiddenFiles();
            }
            
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error unhiding file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Check permissions and load files
        if (permissionManager.hasStoragePermission()) {
            loadHiddenFiles();
        }
    }
} 