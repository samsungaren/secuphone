package com.example.secuphone.utils;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Manages encryption and decryption of files using AES-256 encryption.
 * Uses PBKDF2WithHmacSHA256 for key derivation from user password.
 */
public class FileEncryptionManager {
    private static final String TAG = "FileEncryptionManager";
    
    // Constants for encryption
    private static final String ENCRYPTED_FILES_DIR = "encrypted_files";
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 16;
    
    // File name suffix for metadata files
    private static final String METADATA_SUFFIX = ".meta";
    
    private final Context context;
    
    public FileEncryptionManager(Context context) {
        this.context = context;
        
        // Create the encrypted files directory if it doesn't exist
        File encryptedDir = getEncryptedFilesDir();
        if (!encryptedDir.exists()) {
            if (!encryptedDir.mkdirs()) {
                Log.e(TAG, "Failed to create encrypted files directory");
            } else {
                // Create a .nomedia file to hide contents from media scanners
                try {
                    File nomediaFile = new File(encryptedDir, ".nomedia");
                    if (!nomediaFile.exists()) {
                        nomediaFile.createNewFile();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to create .nomedia file", e);
                }
            }
        }
    }
    
    /**
     * Encrypts a file from a Uri and saves it to the app's private storage
     * @param sourceUri Uri of the file to encrypt
     * @param password Password to derive the encryption key
     * @return The encrypted file or null if encryption failed
     */
    public File encryptFile(Uri sourceUri, String password) throws IOException, GeneralSecurityException {
        // Get the file name from the Uri
        String fileName = getFileNameFromUri(sourceUri);
        if (fileName == null || fileName.isEmpty()) {
            fileName = "encrypted_file_" + System.currentTimeMillis();
        }
        
        // Create a temporary file
        File tempFile = new File(context.getCacheDir(), "temp_" + System.currentTimeMillis());
        
        try {
            // Get original file path if possible (for deletion)
            String originalFilePath = getRealPathFromUri(sourceUri);
            File originalFile = null;
            if (originalFilePath != null) {
                originalFile = new File(originalFilePath);
            }
            
            // Copy the input file to a temporary location
            copyFile(sourceUri, tempFile);
            
            // Create the destination file in our private directory
            File encryptedFile = new File(getEncryptedFilesDir(), fileName + ".enc");
            
            // Generate a random salt
            byte[] salt = new byte[SALT_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(salt);
            
            // Generate a random IV
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            
            // Derive key from password
            SecretKey key = deriveKeyFromPassword(password, salt);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            
            try (FileInputStream fis = new FileInputStream(tempFile);
                 FileOutputStream fos = new FileOutputStream(encryptedFile)) {
                
                // Write salt and IV to the beginning of the file
                fos.write(salt);
                fos.write(iv);
                
                // Read the file and encrypt in chunks
                byte[] buffer = new byte[8192];
                int bytesRead;
                byte[] output;
                
                while ((bytesRead = fis.read(buffer)) != -1) {
                    output = cipher.update(buffer, 0, bytesRead);
                    if (output != null) {
                        fos.write(output);
                    }
                }
                
                // Write the final block
                output = cipher.doFinal();
                if (output != null) {
                    fos.write(output);
                }
                
                // Save metadata about the original file
                saveFileMetadata(encryptedFile, fileName);
                
                // Enhanced file deletion logic
                boolean deleted = false;
                
                // Try all available deletion methods
                
                // 1. Try direct deletion if we have the file path
                if (originalFile != null && originalFile.exists()) {
                    deleted = originalFile.delete();
                    if (deleted) {
                        Log.d(TAG, "Original file deleted successfully: " + originalFilePath);
                    } else {
                        Log.w(TAG, "Failed to delete original file directly: " + originalFilePath);
                    }
                }
                
                // 2. If direct deletion failed, try via ContentResolver
                if (!deleted && sourceUri != null) {
                    try {
                        int rowsDeleted = context.getContentResolver().delete(sourceUri, null, null);
                        if (rowsDeleted > 0) {
                            deleted = true;
                            Log.d(TAG, "Original file deleted via ContentResolver");
                        } else {
                            Log.w(TAG, "ContentResolver returned 0 rows deleted");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error deleting via ContentResolver", e);
                    }
                }
                
                // 3. If media URI, try media-specific deletion
                if (!deleted && ContentUriHelper.isMediaUri(sourceUri)) {
                    deleted = deleteViaContentResolver(sourceUri);
                    if (deleted) {
                        Log.d(TAG, "Original file deleted via media-specific ContentResolver");
                    } else {
                        Log.w(TAG, "Failed to delete via media-specific ContentResolver");
                    }
                }
                
                // Force media scanner to refresh regardless of deletion success
                // This will remove the file from media databases even if physical deletion failed
                if (originalFilePath != null) {
                    notifyMediaScanner(originalFilePath);
                    
                    // Additional scan of parent directory
                    File parent = new File(originalFilePath).getParentFile();
                    if (parent != null && parent.exists()) {
                        notifyMediaScanner(parent.getAbsolutePath());
                    }
                }
                
                // Log final status
                if (!deleted) {
                    Log.w(TAG, "Could not delete original file through any method. File URI: " + sourceUri);
                }
                
                Log.d(TAG, "File encrypted successfully: " + encryptedFile.getAbsolutePath());
                return encryptedFile;
            }
        } finally {
            // Always delete the temporary file
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    
    /**
     * Decrypts a file for viewing
     * @param encryptedFile The encrypted file
     * @param password Password to derive the decryption key
     * @return A temporary decrypted file or null if decryption failed
     */
    public File decryptFile(File encryptedFile, String password) throws IOException, GeneralSecurityException {
        if (encryptedFile == null || !encryptedFile.exists()) {
            Log.e(TAG, "Encrypted file is null or does not exist");
            throw new IOException("Encrypted file not found");
        }
        
        Log.d(TAG, "Starting decryption of file: " + encryptedFile.getName());
        
        // Create a temporary file for the decrypted content
        String fileName = getOriginalFileName(encryptedFile);
        Log.d(TAG, "Original filename determined as: " + fileName);
        
        // Create a temp directory if it doesn't exist
        File tempDir = new File(context.getCacheDir(), "temp_decrypted");
        if (!tempDir.exists()) {
            boolean created = tempDir.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create temp directory");
                throw new IOException("Could not create temp directory");
            }
        }
        
        // Create the temp file with the original extension if available
        File decryptedFile = new File(tempDir, fileName);
        
        // Delete if already exists
        if (decryptedFile.exists()) {
            boolean deleted = decryptedFile.delete();
            if (!deleted) {
                Log.w(TAG, "Could not delete existing temp file: " + decryptedFile.getAbsolutePath());
            }
        }
        
        Log.d(TAG, "Decrypting to temp file: " + decryptedFile.getAbsolutePath());
        
        FileInputStream fis = null;
        FileOutputStream fos = null;
        
        try {
            fis = new FileInputStream(encryptedFile);
            fos = new FileOutputStream(decryptedFile);
             
            // Read the salt and IV from the beginning of the file
            byte[] salt = new byte[SALT_LENGTH];
            byte[] iv = new byte[IV_LENGTH];
            
            int saltRead = fis.read(salt);
            int ivRead = fis.read(iv);
            
            if (saltRead != SALT_LENGTH || ivRead != IV_LENGTH) {
                Log.e(TAG, "Invalid encrypted file format. Salt read: " + saltRead + ", IV read: " + ivRead);
                throw new IOException("Invalid encrypted file format - incorrect header size");
            }
            
            Log.d(TAG, "Successfully read salt and IV");
            
            try {
                // Derive key from password
                SecretKey key = deriveKeyFromPassword(password, salt);
                
                // Initialize cipher
                Cipher cipher = Cipher.getInstance(ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
                
                Log.d(TAG, "Cipher initialized for decryption");
                
                // Read and decrypt the file in chunks
                byte[] buffer = new byte[8192];
                int bytesRead;
                byte[] output;
                
                while ((bytesRead = fis.read(buffer)) != -1) {
                    try {
                        output = cipher.update(buffer, 0, bytesRead);
                        if (output != null) {
                            fos.write(output);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error during cipher update", e);
                        throw new GeneralSecurityException("Error decrypting data: " + e.getMessage(), e);
                    }
                }
                
                try {
                    // Write the final block
                    output = cipher.doFinal();
                    if (output != null) {
                        fos.write(output);
                    }
                    Log.d(TAG, "Successfully finalized decryption");
                } catch (Exception e) {
                    Log.e(TAG, "Error during cipher doFinal - likely incorrect password", e);
                    throw new GeneralSecurityException("Incorrect password or corrupted file", e);
                }
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                Log.e(TAG, "Encryption algorithm error", e);
                throw new GeneralSecurityException("Encryption algorithm error: " + e.getMessage(), e);
            }
            
            Log.d(TAG, "File decrypted successfully: " + decryptedFile.getAbsolutePath());
            
            // Ensure the file exists and has content
            if (!decryptedFile.exists() || decryptedFile.length() == 0) {
                Log.e(TAG, "Decrypted file doesn't exist or is empty: " + decryptedFile.getAbsolutePath());
                throw new IOException("Decryption produced empty or missing file");
            }
            
            return decryptedFile;
        } catch (Exception e) {
            // If decryption fails, delete the incomplete decrypted file
            if (decryptedFile.exists()) {
                decryptedFile.delete();
            }
            
            Log.e(TAG, "Decryption failed: " + e.getMessage(), e);
            throw e;
        } finally {
            // Close streams
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing input stream", e);
                }
            }
            
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing output stream", e);
                }
            }
        }
    }
    
    /**
     * Gets a list of all encrypted files in the app's private storage
     * @return Array of encrypted files
     */
    public File[] getEncryptedFiles() {
        File encryptedDir = getEncryptedFilesDir();
        return encryptedDir.listFiles(file -> 
            file.isFile() && !file.getName().equals(".nomedia") && !file.getName().endsWith(METADATA_SUFFIX));
    }
    
    /**
     * Deletes an encrypted file and its metadata
     * @param encryptedFile The encrypted file to delete
     * @return true if deletion was successful
     */
    public boolean deleteEncryptedFile(File encryptedFile) {
        boolean result = encryptedFile.delete();
        
        // Also delete metadata file if it exists
        File metadataFile = new File(encryptedFile.getAbsolutePath() + METADATA_SUFFIX);
        if (metadataFile.exists()) {
            metadataFile.delete();
        }
        
        return result;
    }
    
    /**
     * Derives an encryption key from a password using PBKDF2
     * @param password The password
     * @param salt The salt
     * @return A SecretKey derived from the password
     */
    private SecretKey deriveKeyFromPassword(String password, byte[] salt) 
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }
    
    /**
     * Gets the directory where encrypted files are stored
     * @return The encrypted files directory
     */
    private File getEncryptedFilesDir() {
        return new File(context.getFilesDir(), ENCRYPTED_FILES_DIR);
    }
    
    /**
     * Copies a file from a Uri to a destination File
     * @param sourceUri Source Uri
     * @param destFile Destination File
     * @throws IOException If the copy fails
     */
    private void copyFile(Uri sourceUri, File destFile) throws IOException {
        try (FileInputStream inStream = (FileInputStream) context.getContentResolver().openInputStream(sourceUri);
             FileOutputStream outStream = new FileOutputStream(destFile)) {
             
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inStream.read(buffer)) > 0) {
                outStream.write(buffer, 0, length);
            }
        }
    }
    
    /**
     * Deletes a file from a Uri
     * @param fileUri Uri of the file to delete
     * @return true if deletion was successful
     */
    private boolean deleteFile(Uri fileUri) {
        try {
            String path = fileUri.getPath();
            if (path != null) {
                File file = new File(path);
                return file.exists() && file.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete file: " + fileUri, e);
        }
        return false;
    }
    
    /**
     * Gets a file name from a Uri
     * @param uri The Uri
     * @return The file name or null if it couldn't be determined
     */
    private String getFileNameFromUri(Uri uri) {
        if (uri == null) {
            Log.e(TAG, "Null URI provided");
            return "unknown_file";
        }
        
        Log.d(TAG, "Getting filename from URI: " + uri);
        
        // Try to get the display name from the ContentResolver
        try {
            String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst() && cursor.getColumnCount() > 0) {
                    int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        String displayName = cursor.getString(nameIndex);
                        if (displayName != null && !displayName.isEmpty()) {
                            Log.d(TAG, "Found display name from cursor: " + displayName);
                            return displayName;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting display name", e);
        }
        
        // Try DocumentFile for content URIs (works better on newer Android versions)
        try {
            androidx.documentfile.provider.DocumentFile documentFile = 
                androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri);
            if (documentFile != null && documentFile.getName() != null) {
                Log.d(TAG, "Found name from DocumentFile: " + documentFile.getName());
                return documentFile.getName();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error using DocumentFile", e);
        }
        
        // Try to parse from the URI path
        if (uri.getPath() != null) {
            try {
                String path = uri.getPath();
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    String filename = path.substring(cut + 1);
                    if (!filename.isEmpty()) {
                        Log.d(TAG, "Extracted filename from path: " + filename);
                        return filename;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting filename from path", e);
            }
        }
        
        // If all else fails, generate a unique name
        String fallbackName = "file_" + System.currentTimeMillis();
        Log.w(TAG, "Could not determine filename, using fallback: " + fallbackName);
        return fallbackName;
    }
    
    /**
     * Saves metadata about the original file
     * @param encryptedFile The encrypted file
     * @param originalFilename The original file name
     * @throws IOException If saving metadata fails
     */
    private void saveFileMetadata(File encryptedFile, String originalFilename) throws IOException {
        // Ensure we have a filename with extension
        if (originalFilename == null || originalFilename.isEmpty()) {
            Log.w(TAG, "Empty original filename provided for metadata");
            originalFilename = "unknown_file";
        }
        
        Log.d(TAG, "Saving metadata for file: " + originalFilename);
        
        // Create metadata file
        File metadataFile = new File(encryptedFile.getAbsolutePath() + METADATA_SUFFIX);
        
        try (FileOutputStream fos = new FileOutputStream(metadataFile)) {
            fos.write(originalFilename.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "Error saving metadata file", e);
            throw e;
        }
        
        // Verify the metadata was written successfully
        if (!metadataFile.exists() || metadataFile.length() == 0) {
            Log.e(TAG, "Failed to create metadata file");
            throw new IOException("Failed to create metadata file");
        }
        
        Log.d(TAG, "Metadata saved successfully: " + metadataFile.getAbsolutePath());
    }
    
    /**
     * Gets the original file name from an encrypted file's metadata
     * @param encryptedFile The encrypted file
     * @return The original file name or the encrypted file name if metadata doesn't exist
     */
    private String getOriginalFileName(File encryptedFile) {
        File metadataFile = new File(encryptedFile.getAbsolutePath() + METADATA_SUFFIX);
        
        // Default name (with cleaned extension)
        String defaultName = encryptedFile.getName();
        if (defaultName.endsWith(".enc")) {
            defaultName = defaultName.substring(0, defaultName.length() - 4);
        }
        
        if (!metadataFile.exists()) {
            Log.w(TAG, "Metadata file not found for: " + encryptedFile.getName());
            return defaultName;
        }
        
        try (FileInputStream fis = new FileInputStream(metadataFile)) {
            byte[] data = new byte[(int) metadataFile.length()];
            int bytesRead = fis.read(data);
            
            if (bytesRead <= 0) {
                Log.e(TAG, "Empty metadata file");
                return defaultName;
            }
            
            String originalName = new String(data, 0, bytesRead, StandardCharsets.UTF_8);
            if (originalName.isEmpty()) {
                Log.e(TAG, "Empty original filename in metadata");
                return defaultName;
            }
            
            Log.d(TAG, "Restored original filename from metadata: " + originalName);
            return originalName;
        } catch (IOException e) {
            Log.e(TAG, "Error reading metadata file", e);
            return defaultName;
        }
    }
    
    /**
     * Deletes a file through the ContentResolver
     */
    private boolean deleteViaContentResolver(Uri uri) {
        try {
            if (ContentUriHelper.isMediaUri(uri)) {
                int rowsDeleted = context.getContentResolver().delete(uri, null, null);
                return rowsDeleted > 0;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting via ContentResolver", e);
            return false;
        }
    }
    
    /**
     * Get the actual file path from a Uri
     */
    private String getRealPathFromUri(Uri uri) {
        String result = null;
        
        try {
            if (ContentUriHelper.isMediaUri(uri)) {
                String[] projection = {MediaStore.Images.Media.DATA};
                try (android.database.Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                        result = cursor.getString(columnIndex);
                    }
                }
            } else if ("file".equals(uri.getScheme())) {
                result = uri.getPath();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting real path from uri", e);
        }
        
        return result;
    }
    
    /**
     * Notify media scanner about file changes to update galleries
     */
    private void notifyMediaScanner(String filePath) {
        try {
            if (filePath != null) {
                MediaScannerConnection.scanFile(
                    context,
                    new String[]{filePath},
                    null,
                    (path, uri) -> {
                        Log.d(TAG, "Media scan completed for: " + path);
                        
                        // Additional attempt to remove from gallery if this is a scan after deletion
                        if (uri != null && new File(path).exists() == false) {
                            try {
                                context.getContentResolver().delete(uri, null, null);
                                Log.d(TAG, "Deleted media URI after scan: " + uri);
                            } catch (Exception e) {
                                Log.e(TAG, "Error deleting URI after scan", e);
                            }
                        }
                    }
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Error notifying media scanner", e);
        }
    }

    /**
     * Helper class for Content Uri operations
     */
    private static class ContentUriHelper {
        /**
         * Check if the uri is a media uri
         */
        public static boolean isMediaUri(Uri uri) {
            if (uri == null) return false;
            
            String uriString = uri.toString().toLowerCase();
            return uriString.contains("content://media") || 
                   uriString.contains("content://com.android.providers.media");
        }
    }
} 