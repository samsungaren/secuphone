package com.example.secuphone_bycoursor;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.secuphone_bycoursor.adapters.HiddenFilesAdapter;
import com.example.secuphone_bycoursor.utils.PermissionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class HiddenFilesActivity extends AppCompatActivity {

    private static final String TAG = "HiddenFilesActivity";
    private static final String HIDDEN_FILES_DIR = ".secuphone_hidden";
    private static final String PREF_MASTER_PASSWORD_HASH = "master_password_hash";
    private static final String PREF_MASTER_PASSWORD_SALT = "master_password_salt";
    private static final String ENCRYPTION_IV_SUFFIX = ".iv";
    private static final String ENCRYPTION_METADATA_SUFFIX = ".meta";

    private Button hideFileButton;
    private TextView noFilesText;
    private RecyclerView hiddenFilesList;
    private FloatingActionButton hideFileFab;

    private List<File> hiddenFiles = new ArrayList<>();

    private ActivityResultLauncher<Intent> filePickerLauncher;
    private PermissionManager permissionManager;
    private SharedPreferences prefs;
    private boolean isAuthenticated = false;

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

        // Initialize preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

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
                            if (isMasterPasswordSet()) {
                                promptForPassword(selectedFileUri, true);
                            } else {
                                setupMasterPassword(selectedFileUri);
                            }
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

        // Check if master password is set
        if (isMasterPasswordSet() && !isAuthenticated) {
            promptForAuthenticationPassword();
        } else {
            loadHiddenFilesWithPermissionCheck();
        }
    }

    /**
     * Check if master password has been set
     */
    private boolean isMasterPasswordSet() {
        return prefs.contains(PREF_MASTER_PASSWORD_HASH) && prefs.contains(PREF_MASTER_PASSWORD_SALT);
    }

    /**
     * Prompt user to set up a master password for encryption/decryption
     */
    private void setupMasterPassword(Uri fileUri) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_set_password, null);
        TextInputEditText passwordInput = dialogView.findViewById(R.id.password_input);
        TextInputEditText confirmPasswordInput = dialogView.findViewById(R.id.confirm_password_input);
        
        new MaterialAlertDialogBuilder(this)
            .setTitle("Set Encryption Password")
            .setMessage("Set a password to protect your hidden files. You'll need this password to access or unhide your files.")
            .setView(dialogView)
            .setPositiveButton("Set Password", null) // Set in the show() method to prevent auto-dismiss
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
            .getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String password = passwordInput.getText().toString();
                String confirmPassword = confirmPasswordInput.getText().toString();
                
                if (password.isEmpty()) {
                    passwordInput.setError("Password cannot be empty");
                    return;
                }
                
                if (!password.equals(confirmPassword)) {
                    confirmPasswordInput.setError("Passwords do not match");
                    return;
                }
                
                // Save the password hash & salt
                saveMasterPassword(password);
                isAuthenticated = true;
                
                // Continue with file hiding
                hideFile(fileUri, password);
                
                // Dismiss the dialog
                if (v.getParent().getParent() instanceof androidx.appcompat.app.AlertDialog) {
                    ((androidx.appcompat.app.AlertDialog) v.getParent().getParent()).dismiss();
                }
            });
    }
    
    /**
     * Hash and save the master password
     */
    private void saveMasterPassword(String password) {
        try {
            // Generate a random salt
            byte[] salt = new byte[16];
            SecureRandom random = new SecureRandom();
            random.nextBytes(salt);
            
            // Generate the hash
            byte[] hash = hashPassword(password, salt);
            
            // Save both to preferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(PREF_MASTER_PASSWORD_HASH, Base64.getEncoder().encodeToString(hash));
            editor.putString(PREF_MASTER_PASSWORD_SALT, Base64.getEncoder().encodeToString(salt));
            editor.apply();
            
            Toast.makeText(this, "Password set successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error setting password: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Prompt for password when accessing hidden files
     */
    private void promptForAuthenticationPassword() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_enter_password, null);
        TextInputEditText passwordInput = dialogView.findViewById(R.id.password_input);
        
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle("Enter Password")
            .setMessage("Enter your password to access hidden files")
            .setView(dialogView)
            .setPositiveButton("Unlock", null)
            .setCancelable(false)
            .show();
            
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = passwordInput.getText().toString();
            
            if (verifyPassword(password)) {
                isAuthenticated = true;
                dialog.dismiss(); // Properly dismiss the dialog
                
                // Load files
        loadHiddenFilesWithPermissionCheck();
            } else {
                passwordInput.setError("Incorrect password");
            }
        });
    }
    
    /**
     * Prompt for password when hiding or unhiding files
     */
    private void promptForPassword(Uri fileUri, boolean isHiding) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_enter_password, null);
        TextInputEditText passwordInput = dialogView.findViewById(R.id.password_input);
        
        String title = isHiding ? "Enter Password to Hide File" : "Enter Password to Unhide File";
        String actionButtonText = isHiding ? "Hide File" : "Unhide File";
        
        new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage("Enter your encryption password")
            .setView(dialogView)
            .setPositiveButton(actionButtonText, null)
            .setNegativeButton("Cancel", null)
            .show()
            .getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String password = passwordInput.getText().toString();
                
                if (verifyPassword(password)) {
                    // Dismiss the dialog
                    if (v.getParent().getParent() instanceof androidx.appcompat.app.AlertDialog) {
                        ((androidx.appcompat.app.AlertDialog) v.getParent().getParent()).dismiss();
                    }
                    
                    if (isHiding) {
                        hideFile(fileUri, password);
                    }
                } else {
                    passwordInput.setError("Incorrect password");
                }
            });
    }
    
    /**
     * Verify password against stored hash
     */
    private boolean verifyPassword(String password) {
        try {
            // Get stored hash and salt
            String storedHashBase64 = prefs.getString(PREF_MASTER_PASSWORD_HASH, "");
            String storedSaltBase64 = prefs.getString(PREF_MASTER_PASSWORD_SALT, "");
            
            if (storedHashBase64.isEmpty() || storedSaltBase64.isEmpty()) {
                return false;
            }
            
            byte[] storedHash = Base64.getDecoder().decode(storedHashBase64);
            byte[] storedSalt = Base64.getDecoder().decode(storedSaltBase64);
            
            // Hash the provided password with stored salt
            byte[] computedHash = hashPassword(password, storedSalt);
            
            // Compare the hashes
            return MessageDigest.isEqual(storedHash, computedHash);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Hash a password with given salt using PBKDF2
     */
    private byte[] hashPassword(String password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
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
        if (!isAuthenticated && isMasterPasswordSet()) {
            promptForAuthenticationPassword();
            return;
        }
        
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }

    private void hideFile(Uri sourceUri, String password) {
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
            
            // Create temporary file for encryption
            File tempFile = new File(getExternalCacheDir(), "temp_" + System.currentTimeMillis());
            
            // Copy the file to temporary location first
            copyFile(sourceUri, tempFile);
            
            // Encrypt the file
            encryptFile(tempFile, destinationFile, password);
            
            // Delete temporary file
            tempFile.delete();
            
            // Show success message
            Toast.makeText(this, R.string.file_hidden_success, Toast.LENGTH_SHORT).show();
            
            // Refresh the list
            loadHiddenFiles();
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error hiding file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Encrypt a file with the given password
     */
    private void encryptFile(File sourceFile, File destFile, String password) throws Exception {
        try {
            Log.d(TAG, "Starting encryption of file: " + sourceFile.getAbsolutePath());
            
            // Generate a fixed salt - important to use the same one for both encryption and decryption
            byte[] salt = "SecuPhoneFixedSalt12345".getBytes(StandardCharsets.UTF_8);
            
            // Get the key bytes directly without SecretKeySpec
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, 10000, 256);
            byte[] keyBytes = factory.generateSecret(keySpec).getEncoded();
            
            // Create a fixed SecretKeySpec with the key bytes
            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
            
            // Create a fixed IV for simplicity and to avoid IV handling issues
            byte[] iv = new byte[16];
            for (int i = 0; i < 16; i++) {
                iv[i] = (byte) i; // Simple deterministic IV
            }
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            
            // Read the file in a single operation to avoid streaming issues
            byte[] fileContent = new byte[(int) sourceFile.length()];
            try (FileInputStream fis = new FileInputStream(sourceFile)) {
                fis.read(fileContent);
            }
            
            // Encrypt all at once
            byte[] encryptedData = cipher.doFinal(fileContent);
            
            // Write the encrypted data
            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                fos.write(encryptedData);
            }
            
            // Save the original filename metadata
            saveFileMetadata(destFile, sourceFile.getName());
            
            Log.d(TAG, "Successfully encrypted file: " + destFile.getAbsolutePath() + 
                    ", size: " + destFile.length() + " bytes");
                
        } catch (Exception e) {
            Log.e(TAG, "Error during encryption", e);
            throw new Exception("Encryption failed: " + e.getMessage());
        }
    }
    
    /**
     * Save metadata about the original file
     */
    private void saveFileMetadata(File encryptedFile, String originalFilename) throws IOException {
        File metadataFile = new File(encryptedFile + ENCRYPTION_METADATA_SUFFIX);
        try (FileOutputStream fos = new FileOutputStream(metadataFile)) {
            fos.write(originalFilename.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    /**
     * Read stored metadata for a file
     */
    private String readFileMetadata(File encryptedFile) {
        File metadataFile = new File(encryptedFile + ENCRYPTION_METADATA_SUFFIX);
        if (!metadataFile.exists()) {
            return encryptedFile.getName(); // Fallback to encrypted filename if no metadata
        }
        
        try (FileInputStream fis = new FileInputStream(metadataFile)) {
            byte[] data = new byte[(int) metadataFile.length()];
            fis.read(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return encryptedFile.getName();
        }
    }
    
    /**
     * View a hidden file by decrypting to a temporary file and opening it
     */
    private void viewHiddenFile(File file) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_enter_password, null);
        TextInputEditText passwordInput = dialogView.findViewById(R.id.password_input);
        
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle("Enter Password to View File")
            .setMessage("Enter your encryption password to view this file")
            .setView(dialogView)
            .setPositiveButton("View", null)
            .setNegativeButton("Cancel", null)
            .show();
            
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = passwordInput.getText().toString();
            
            if (verifyPassword(password)) {
                dialog.dismiss(); // Properly dismiss the dialog
                
                // Show progress dialog
                androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                    .setTitle("Decrypting File")
                    .setMessage("Please wait...")
                    .setCancelable(false)
                    .show();
                
                // Run decryption in background thread
                new Thread(() -> {
                    try {
                        // Get original filename for MIME type detection
                        String originalFilename = readFileMetadata(file);
                        Log.d(TAG, "Original filename for viewing: " + originalFilename);
                        
                        // Create temp directory
                        File tempDir = new File(getExternalCacheDir(), "temp_view");
                        if (!tempDir.exists()) {
                            tempDir.mkdirs();
                        }
                        
                        // Create temp file with a simple name
                        String extension = getFileExtension(originalFilename);
                        if (extension.isEmpty()) extension = "tmp";
                        // Make sure extension is effectively final for lambda
                        final String finalExtension = extension;
                        File tempFile = new File(tempDir, "temp_file." + finalExtension);
                        
                        // Delete if exists
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                        
                        // Decrypt the file
                        decryptFile(file, tempFile, password);
                        
                        // Final file reference for UI thread
                        final File finalTempFile = tempFile;
                        
                        // Handle on UI thread
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            
                            if (finalTempFile.exists() && finalTempFile.length() > 0) {
                                // Check file type
                                try {
                                    if (isImageFile(finalExtension)) {
                                        // For images, display directly
                                        showImageInDialog(finalTempFile);
                                    } else {
                                        // For other files, try to open with system handler
                                        openWithSystemApp(finalTempFile, finalExtension);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error displaying file", e);
                                    Toast.makeText(HiddenFilesActivity.this, 
                                        "Error viewing file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(HiddenFilesActivity.this, 
                                    "Failed to decrypt file", Toast.LENGTH_SHORT).show();
                            }
                        });
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error preparing file for viewing", e);
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(HiddenFilesActivity.this, 
                                "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                }).start();
            } else {
                passwordInput.setError("Incorrect password");
            }
        });
    }
    
    /**
     * Show image in a fullscreen dialog
     */
    private void showImageInDialog(File imageFile) {
        try {
            // Create a dialog for viewing
            Dialog imageDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            imageDialog.setContentView(R.layout.dialog_image_viewer);
            
            // Set up image view
            ImageView imageView = imageDialog.findViewById(R.id.image_view);
            ImageButton closeButton = imageDialog.findViewById(R.id.close_button);
            
            // Load the image
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                closeButton.setOnClickListener(v -> imageDialog.dismiss());
                imageDialog.show();
            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing image", e);
            Toast.makeText(this, "Error displaying image", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Open file with system app
     */
    private void openWithSystemApp(File file, String extension) {
        try {
            // Create URI using FileProvider
            Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this, 
                    "com.example.secuphone_bycoursor.fileprovider", 
                    file);
            
            // Create intent
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, getMimeTypeFromExtension(extension));
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // Try to start an activity to handle the file
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "No app found to open this type of file", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening file with system app", e);
            Toast.makeText(this, "Error opening file", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Decrypt a file with the given password
     */
    private void decryptFile(File sourceFile, File destFile, String password) throws Exception {
        try {
            Log.d(TAG, "Starting decryption of file: " + sourceFile.getAbsolutePath());
            
            // Use the same fixed salt as in encryption
            byte[] salt = "SecuPhoneFixedSalt12345".getBytes(StandardCharsets.UTF_8);
            
            // Get the key bytes using the same derivation parameters
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, 10000, 256);
            byte[] keyBytes = factory.generateSecret(keySpec).getEncoded();
            
            // Create SecretKeySpec the same way as in encryption
            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
            
            // Use the same fixed IV as in encryption
            byte[] iv = new byte[16];
            for (int i = 0; i < 16; i++) {
                iv[i] = (byte) i; // Same deterministic IV as in encryption
            }
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            
            // Read the entire encrypted file
            byte[] encryptedData = new byte[(int) sourceFile.length()];
            try (FileInputStream fis = new FileInputStream(sourceFile)) {
                fis.read(encryptedData);
            }
            
            // Decrypt all at once
            byte[] decryptedData = cipher.doFinal(encryptedData);
            
            // Write the decrypted data
            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                fos.write(decryptedData);
            }
            
            Log.d(TAG, "Successfully decrypted file: " + destFile.getAbsolutePath() + 
                    ", size: " + destFile.length() + " bytes");
                
        } catch (Exception e) {
            Log.e(TAG, "Error during decryption", e);
            throw new Exception("Decryption failed: " + e.getMessage());
        }
    }
    
    /**
     * Generate encryption key from password
     */
    private SecretKey generateKey(String password) throws NoSuchAlgorithmException, InvalidKeySpecException {
        // Use fixed salt for consistent key generation
        String fixedSaltStr = "SecuPhoneFixedSalt";
        byte[] salt = fixedSaltStr.getBytes(StandardCharsets.UTF_8);
        
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
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
        // Don't load files if not authenticated
        if (isMasterPasswordSet() && !isAuthenticated) {
            promptForAuthenticationPassword();
            return;
        }
        
        // Clear previous list
        hiddenFiles.clear();
        
        // Get hidden files directory
        File hiddenDir = new File(getExternalFilesDir(null), HIDDEN_FILES_DIR);
        if (hiddenDir.exists() && hiddenDir.isDirectory()) {
            File[] files = hiddenDir.listFiles(file -> {
                String name = file.getName();
                return file.isFile() && 
                       !name.equals(".nomedia") && 
                       !name.endsWith(ENCRYPTION_IV_SUFFIX) && 
                       !name.endsWith(ENCRYPTION_METADATA_SUFFIX);
            });
            
            if (files != null && files.length > 0) {
                for (File file : files) {
                        hiddenFiles.add(file);
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
            HiddenFilesAdapter adapter = new HiddenFilesAdapter(hiddenFiles, HiddenFilesActivity.this::confirmUnhideFile);
            hiddenFilesList.setAdapter(adapter);
        }
    }
    
    /**
     * Show confirmation dialog before unhiding file
     */
    private void confirmUnhideFile(File file) {
        if (!isAuthenticated) {
            promptForAuthenticationPassword();
            return;
        }
        
        // Show options dialog: View or Unhide
        new MaterialAlertDialogBuilder(this)
            .setTitle("File Options")
            .setItems(new String[]{"View File", "Unhide File"}, (dialog, which) -> {
                if (which == 0) { // View File
                    viewHiddenFile(file);
                } else { // Unhide File
                    promptForUnhidePassword(file);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * Prompt for password when unhiding a file
     */
    private void promptForUnhidePassword(File file) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_enter_password, null);
        TextInputEditText passwordInput = dialogView.findViewById(R.id.password_input);
        
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle("Enter Password to Unhide File")
            .setMessage("Enter your encryption password to access this file")
            .setView(dialogView)
            .setPositiveButton("Unhide", null)
            .setNegativeButton("Cancel", null)
            .show();
            
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = passwordInput.getText().toString();
            
            if (verifyPassword(password)) {
                dialog.dismiss(); // Properly dismiss the dialog
                unhideFile(file, password);
            } else {
                passwordInput.setError("Incorrect password");
            }
        });
    }
    
    /**
     * Check if the file is an image based on its extension
     */
    private boolean isImageFile(String extension) {
        return extension.equals("jpg") || 
               extension.equals("jpeg") || 
               extension.equals("png") || 
               extension.equals("gif") || 
               extension.equals("bmp");
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
     * Get file extension from filename
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(lastDot + 1);
        }
        return "";
    }
    
    private void unhideFile(File file, String password) {
        try {
            // Show progress dialog
            androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Unhiding File")
                .setMessage("Please wait...")
                .setCancelable(false)
                .show();
            
            // Run in background thread
            new Thread(() -> {
                try {
                    // Get original filename
                    String originalFileName = readFileMetadata(file);
                    Log.d(TAG, "Original filename for unhiding: " + originalFileName);
                    
                    // Create destination path
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File destinationFile = new File(downloadsDir, originalFileName);
                    
                    // Handle name collision
            if (destinationFile.exists()) {
                        String baseName = originalFileName;
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
            
                    // Ensure parent directory exists
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs();
                    }
                    
                    // Decrypt the file
                    decryptFile(file, destinationFile, password);
                    
                    // Make sure the file actually got created
                    if (!destinationFile.exists() || destinationFile.length() == 0) {
                        throw new Exception("Failed to create unhidden file");
                    }
                    
                    // Delete the encrypted file and metadata
                    boolean mainDeleted = file.delete();
                    
                    // Delete the metadata file
                    File metadataFile = new File(file.getAbsolutePath() + ENCRYPTION_METADATA_SUFFIX);
                    boolean metaDeleted = true;
                    if (metadataFile.exists()) {
                        metaDeleted = metadataFile.delete();
                    }
                    
                    Log.d(TAG, "Deleted files: main=" + mainDeleted + ", metadata=" + metaDeleted);
                    
                    // Notify media scanner about the new file
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(Uri.fromFile(destinationFile));
                    sendBroadcast(mediaScanIntent);
                    
                    // Update UI
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(HiddenFilesActivity.this, 
                                "File unhidden to Downloads folder", Toast.LENGTH_LONG).show();
                        
                        // Reload hidden files list
                loadHiddenFiles();
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error unhiding file", e);
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(HiddenFilesActivity.this, 
                                "Error unhiding file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting unhide process", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Set hidden files feature as active in preferences if we have encryption set up
        if (isMasterPasswordSet()) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("hidden_files_active", true);
            editor.apply();
        }
        
        // Check permissions and load files
        if (permissionManager.hasStoragePermission() && isAuthenticated) {
            loadHiddenFiles();
        }
    }
} 