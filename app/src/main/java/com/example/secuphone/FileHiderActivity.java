package com.example.secuphone;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity for handling hidden files in the app's private storage
 * Compatible with all Android versions, including Android 11+ Scoped Storage
 */
public class FileHiderActivity extends AppCompatActivity {
    private static final String TAG = "FileHiderActivity";
    private static final String HIDDEN_FILES_DIR = ".hidden";
    private static final String HIDDEN_FILES_METADATA = "hidden_files_metadata";

    private Button selectFileButton;
    private TextView emptyStateText;
    private RecyclerView hiddenFilesRecyclerView;
    private FloatingActionButton addFileFab;

    private List<File> hiddenFiles = new ArrayList<>();
    private SharedPreferences prefs;

    // Activity Result Launcher for file picking
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_hider);

        // Initialize UI elements
        selectFileButton = findViewById(R.id.select_file_button);
        emptyStateText = findViewById(R.id.empty_state_text);
        hiddenFilesRecyclerView = findViewById(R.id.hidden_files_recycler_view);
        addFileFab = findViewById(R.id.add_file_fab);

        // Get shared preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Setup RecyclerView
        hiddenFilesRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize file picker launcher
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

        // Set click listeners
        selectFileButton.setOnClickListener(v -> openFilePicker());
        addFileFab.setOnClickListener(v -> openFilePicker());
        
        // Set up back button
        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> onBackPressed());

        // Create hidden directory if it doesn't exist
        createHiddenDirectory();

        // Load hidden files
        loadHiddenFiles();
    }

    /**
     * Create the hidden directory in app's private files directory
     */
    private void createHiddenDirectory() {
        File hiddenDir = new File(getFilesDir(), HIDDEN_FILES_DIR);
        if (!hiddenDir.exists()) {
            boolean created = hiddenDir.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create hidden directory");
            }
            
            // Create a .nomedia file to hide media from gallery apps
            try {
                File nomedia = new File(hiddenDir, ".nomedia");
                if (!nomedia.exists()) {
                    boolean success = nomedia.createNewFile();
                    if (!success) {
                        Log.e(TAG, "Failed to create .nomedia file");
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error creating .nomedia file", e);
            }
        }
    }

    /**
     * Launch the file picker to select a file to hide
     */
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }

    /**
     * Hide a file by copying it to the app's private storage
     */
    private void hideFile(Uri sourceUri) {
        try {
            // Get file info
            String fileName = getFileNameFromUri(sourceUri);
            if (fileName == null || fileName.isEmpty()) {
                fileName = "hidden_file_" + System.currentTimeMillis();
            }

            // Create destination file in hidden directory
            File hiddenDir = new File(getFilesDir(), HIDDEN_FILES_DIR);
            File destinationFile = new File(hiddenDir, fileName);

            // Handle file name collisions
            if (destinationFile.exists()) {
                String baseName = fileName;
                String extension = "";
                int lastDot = fileName.lastIndexOf('.');
                if (lastDot > 0) {
                    extension = fileName.substring(lastDot);
                    baseName = fileName.substring(0, lastDot);
                }

                int counter = 1;
                while (destinationFile.exists()) {
                    destinationFile = new File(hiddenDir, baseName + "_" + counter + extension);
                    counter++;
                }
            }

            // Copy the file to app private storage
            boolean success = copyFile(sourceUri, destinationFile);
            
            if (success) {
                // Save original URI metadata for potential deletion
                saveOriginalUriMetadata(destinationFile.getName(), sourceUri.toString());
                
                // Show dialog asking if user wants to delete original
                promptForOriginalFileDeletion(sourceUri);
                
                // Refresh the file list
                loadHiddenFiles();
                
                // Show success message with Snackbar
                Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.file_hidden_success,
                    Snackbar.LENGTH_SHORT
                ).show();
            } else {
                // Show error message with Snackbar
                Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.file_delete_failed,
                    Snackbar.LENGTH_SHORT
                ).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error hiding file", e);
            // Show error message with Snackbar
            Snackbar.make(
                findViewById(android.R.id.content),
                getString(R.string.file_delete_failed) + ": " + e.getMessage(),
                Snackbar.LENGTH_LONG
            ).show();
        }
    }

    /**
     * Prompt user if they want to delete the original file
     */
    private void promptForOriginalFileDeletion(Uri sourceUri) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_original)
                .setMessage(R.string.delete_original_question)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    deleteOriginalFile(sourceUri);
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    /**
     * Attempt to delete the original file
     * This may not work on all Android versions due to permission restrictions
     */
    private void deleteOriginalFile(Uri sourceUri) {
        try {
            // For Android 10+ (Q/API 29+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Try to delete with DocumentsContract
                int rowsDeleted = 0;
                
                // First try DocumentsContract, which works for most document URIs
                try {
                    boolean deleted = DocumentsContract.deleteDocument(getContentResolver(), sourceUri);
                    rowsDeleted = deleted ? 1 : 0;
                } catch (Exception e) {
                    Log.e(TAG, "Error using DocumentsContract to delete file", e);
                }
                
                // If that failed, try MediaStore for media files
                if (rowsDeleted == 0) {
                    rowsDeleted = getContentResolver().delete(sourceUri, null, null);
                }
                
                if (rowsDeleted > 0) {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.original_deleted,
                        Snackbar.LENGTH_SHORT
                    ).show();
                } else {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.original_not_deleted,
                        Snackbar.LENGTH_SHORT
                    ).show();
                }
            } else {
                // For older Android versions (pre-Q/API 29)
                // Here we need to try to resolve the URI to a file path
                String filePath = getPathFromUri(sourceUri);
                if (filePath != null) {
                    File file = new File(filePath);
                    if (file.exists() && file.delete()) {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.original_deleted,
                            Snackbar.LENGTH_SHORT
                        ).show();
                    } else {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.original_not_deleted,
                            Snackbar.LENGTH_SHORT
                        ).show();
                    }
                } else {
                    // Direct delete using ContentResolver
                    int rowsDeleted = getContentResolver().delete(sourceUri, null, null);
                    if (rowsDeleted > 0) {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.original_deleted,
                            Snackbar.LENGTH_SHORT
                        ).show();
                    } else {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.original_not_deleted,
                            Snackbar.LENGTH_SHORT
                        ).show();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting original file", e);
            Snackbar.make(
                findViewById(android.R.id.content),
                getString(R.string.original_not_deleted) + ": " + e.getMessage(),
                Snackbar.LENGTH_SHORT
            ).show();
        }
    }

    /**
     * Save metadata about the original URI for potential deletion or restoration
     */
    private void saveOriginalUriMetadata(String fileName, String uriString) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(HIDDEN_FILES_METADATA + "_" + fileName, uriString);
        editor.apply();
    }

    /**
     * Get the original URI string for a hidden file
     */
    private String getOriginalUriString(String fileName) {
        return prefs.getString(HIDDEN_FILES_METADATA + "_" + fileName, null);
    }

    /**
     * Remove metadata for a hidden file when unhiding or deleting
     */
    private void removeMetadata(String fileName) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(HIDDEN_FILES_METADATA + "_" + fileName);
        editor.apply();
    }

    /**
     * Copy a file from the given URI to a destination File
     */
    private boolean copyFile(Uri sourceUri, File destFile) {
        try (InputStream inputStream = getContentResolver().openInputStream(sourceUri);
             FileOutputStream outputStream = new FileOutputStream(destFile)) {
            
            if (inputStream == null) {
                return false;
            }
            
            // Buffer for file copying
            byte[] buffer = new byte[4096];
            int read;
            
            // Copy the file
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error copying file", e);
            return false;
        }
    }

    /**
     * Get the file name from a URI
     */
    private String getFileNameFromUri(Uri uri) {
        String result = null;
        
        // Try to get the display name from the content provider
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    result = cursor.getString(nameIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting filename from URI", e);
        }
        
        // If display name is not available, try to extract from path
        if (result == null) {
            result = uri.getLastPathSegment();
            // If still null, use timestamp as filename
            if (result == null) {
                result = "file_" + System.currentTimeMillis();
            }
        }
        
        return result;
    }

    /**
     * Try to resolve a content URI to an actual file path
     * Note: This will not work for all URIs, especially on newer Android versions
     */
    private String getPathFromUri(Uri uri) {
        try {
            String[] projection = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
            if (cursor == null) {
                return null;
            }
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(column_index);
            cursor.close();
            return path;
        } catch (Exception e) {
            Log.e(TAG, "Error getting real path from URI", e);
            return null;
        }
    }

    /**
     * Load the list of hidden files from the app's private storage
     */
    private void loadHiddenFiles() {
        // Clear the current list
        hiddenFiles.clear();
        
        // Get the hidden directory
        File hiddenDir = new File(getFilesDir(), HIDDEN_FILES_DIR);
        
        // If the directory exists, list all files except .nomedia
        if (hiddenDir.exists() && hiddenDir.isDirectory()) {
            File[] files = hiddenDir.listFiles(file -> 
                    file.isFile() && !file.getName().equals(".nomedia"));
            
            if (files != null && files.length > 0) {
                for (File file : files) {
                    hiddenFiles.add(file);
                }
            }
        }
        
        // Update UI based on whether we have files
        if (hiddenFiles.isEmpty()) {
            hiddenFilesRecyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
            addFileFab.setVisibility(View.GONE);
            
            // Make sure select file button is visible
            View headerSection = findViewById(R.id.header_section);
            if (headerSection != null) {
                headerSection.setVisibility(View.VISIBLE);
            }
        } else {
            hiddenFilesRecyclerView.setVisibility(View.VISIBLE);
            emptyStateText.setVisibility(View.GONE);
            addFileFab.setVisibility(View.VISIBLE);
            
            // Set up adapter
            HiddenFileAdapter adapter = new HiddenFileAdapter(hiddenFiles, this::onFileClick);
            hiddenFilesRecyclerView.setAdapter(adapter);
        }
    }

    /**
     * Handle clicks on hidden files
     */
    private void onFileClick(File file) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.file_options)
                .setItems(new String[]{
                        getString(R.string.open_file), 
                        getString(R.string.unhide_file), 
                        getString(R.string.delete_file)
                }, (dialog, which) -> {
                    switch (which) {
                        case 0: // Open File
                            openHiddenFile(file);
                            break;
                        case 1: // Unhide File
                            unhideFile(file);
                            break;
                        case 2: // Delete File
                            confirmDeleteFile(file);
                            break;
                    }
                })
                .show();
    }

    /**
     * Open a hidden file within the app
     */
    private void openHiddenFile(File file) {
        try {
            // Create a content URI using FileProvider
            Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file);
            
            // Determine MIME type
            String mimeType = getMimeTypeFromExtension(getFileExtension(file.getName()));
            
            // Create and start the intent
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // Check if there's an app to handle this type
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.no_app_for_file,
                    Snackbar.LENGTH_SHORT
                ).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening file", e);
            Snackbar.make(
                findViewById(android.R.id.content),
                getString(R.string.error_opening_file) + ": " + e.getMessage(),
                Snackbar.LENGTH_LONG
            ).show();
        }
    }

    /**
     * Unhide a file by allowing the user to select a destination
     */
    private void unhideFile(File file) {
        // Create intent to create a new document
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, file.getName());
        
        // Launch the document creation intent
        startActivityForResult(intent, 123); // Using old startActivityForResult for simplicity
        
        // Store the file path for use in onActivityResult
        getPreferences(MODE_PRIVATE).edit()
                .putString("pendingUnhideFile", file.getAbsolutePath())
                .apply();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 123 && resultCode == RESULT_OK && data != null) {
            // Get the destination URI selected by the user
            Uri destinationUri = data.getData();
            
            if (destinationUri != null) {
                // Get the source file path from preferences
                String sourcePath = getPreferences(MODE_PRIVATE).getString("pendingUnhideFile", null);
                
                if (sourcePath != null) {
                    File sourceFile = new File(sourcePath);
                    if (sourceFile.exists()) {
                        try {
                            // Copy the file to the user-selected destination
                            try (InputStream inputStream = new FileInputStream(sourceFile);
                                 FileOutputStream outputStream = (FileOutputStream) getContentResolver().openOutputStream(destinationUri)) {
                                
                                if (outputStream != null) {
                                    byte[] buffer = new byte[4096];
                                    int read;
                                    while ((read = inputStream.read(buffer)) != -1) {
                                        outputStream.write(buffer, 0, read);
                                    }
                                    
                                    // Remove metadata
                                    removeMetadata(sourceFile.getName());
                                    
                                    // Delete the hidden file
                                    if (sourceFile.delete()) {
                                        Snackbar.make(
                                            findViewById(android.R.id.content),
                                            R.string.file_unhidden_success,
                                            Snackbar.LENGTH_SHORT
                                        ).show();
                                        // Refresh the list
                                        loadHiddenFiles();
                                    } else {
                                        Snackbar.make(
                                            findViewById(android.R.id.content),
                                            R.string.file_delete_failed,
                                            Snackbar.LENGTH_SHORT
                                        ).show();
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error unhiding file", e);
                            Snackbar.make(
                                findViewById(android.R.id.content),
                                getString(R.string.error_opening_file) + ": " + e.getMessage(),
                                Snackbar.LENGTH_SHORT
                            ).show();
                        }
                    }
                }
            }
        }
    }

    /**
     * Confirm before deleting a hidden file
     */
    private void confirmDeleteFile(File file) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_file)
                .setMessage(R.string.confirm_delete)
                .setPositiveButton(R.string.delete_file, (dialog, which) -> {
                    if (file.delete()) {
                        // Remove metadata
                        removeMetadata(file.getName());
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.file_deleted,
                            Snackbar.LENGTH_SHORT
                        ).show();
                        // Refresh the list
                        loadHiddenFiles();
                    } else {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.file_delete_failed,
                            Snackbar.LENGTH_SHORT
                        ).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Get MIME type from file extension
     */
    private String getMimeTypeFromExtension(String extension) {
        switch (extension) {
            case "pdf":
                return "application/pdf";
            case "doc":
            case "docx":
                return "application/msword";
            case "xls":
            case "xlsx":
                return "application/vnd.ms-excel";
            case "ppt":
            case "pptx":
                return "application/vnd.ms-powerpoint";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "txt":
                return "text/plain";
            case "mp4":
                return "video/mp4";
            case "mp3":
                return "audio/mp3";
            case "wav":
                return "audio/wav";
            default:
                return "*/*";
        }
    }

    /**
     * Adapter interface for file click handling
     */
    public interface OnFileClickListener {
        void onFileClick(File file);
    }

    /**
     * Adapter for displaying hidden files in RecyclerView
     */
    private static class HiddenFileAdapter extends RecyclerView.Adapter<HiddenFileAdapter.FileViewHolder> {
        private final List<File> files;
        private final OnFileClickListener listener;

        HiddenFileAdapter(List<File> files, OnFileClickListener listener) {
            this.files = files;
            this.listener = listener;
        }

        @Override
        public FileViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_hidden_file, parent, false);
            return new FileViewHolder(view);
        }

        @Override
        public void onBindViewHolder(FileViewHolder holder, int position) {
            File file = files.get(position);
            holder.fileName.setText(file.getName());
            holder.fileSize.setText(formatFileSize(file.length()));
            
            // Set click listener
            holder.itemView.setOnClickListener(v -> listener.onFileClick(file));
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        /**
         * Format file size to readable format
         */
        private String formatFileSize(long size) {
            if (size <= 0) return "0 B";
            final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
            int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
            return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
        }

        static class FileViewHolder extends RecyclerView.ViewHolder {
            TextView fileName;
            TextView fileSize;

            FileViewHolder(View itemView) {
                super(itemView);
                fileName = itemView.findViewById(R.id.file_name);
                fileSize = itemView.findViewById(R.id.file_size);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 