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
                
                // Delete the original file properly
                boolean deleted = false;
                
                // Try direct delete if we have the file path
                if (originalFile != null && originalFile.exists()) {
                    deleted = originalFile.delete();
                    if (deleted) {
                        Log.d(TAG, "Original file deleted: " + originalFilePath);
                    }
                }
                
                // If direct deletion failed, try via ContentResolver
                if (!deleted && ContentUriHelper.isMediaUri(sourceUri)) {
                    deleted = deleteViaContentResolver(sourceUri);
                    if (deleted) {
                        Log.d(TAG, "Original file deleted via content resolver");
                    }
                }
                
                // Force media scanner to refresh
                notifyMediaScanner(originalFilePath);
                
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
        // Create a temporary file for the decrypted content
        String fileName = getOriginalFileName(encryptedFile);
        
        // Create a temp directory if it doesn't exist
        File tempDir = new File(context.getCacheDir(), "temp_decrypted");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        
        // Create the temp file with the original extension if available
        File decryptedFile = new File(tempDir, fileName);
        
        // Delete if already exists
        if (decryptedFile.exists()) {
            decryptedFile.delete();
        }
        
        try (FileInputStream fis = new FileInputStream(encryptedFile);
             FileOutputStream fos = new FileOutputStream(decryptedFile)) {
             
            // Read the salt and IV from the beginning of the file
            byte[] salt = new byte[SALT_LENGTH];
            byte[] iv = new byte[IV_LENGTH];
            
            if (fis.read(salt) != SALT_LENGTH || fis.read(iv) != IV_LENGTH) {
                throw new IOException("Invalid encrypted file format");
            }
            
            // Derive key from password
            SecretKey key = deriveKeyFromPassword(password, salt);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            
            // Read and decrypt the file in chunks
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
            
            Log.d(TAG, "File decrypted successfully: " + decryptedFile.getAbsolutePath());
            return decryptedFile;
        } catch (Exception e) {
            // If decryption fails, delete the incomplete decrypted file
            if (decryptedFile.exists()) {
                decryptedFile.delete();
            }
            throw e;
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
     * Gets the file name from a Uri
     * @param uri The Uri
     * @return The file name or null if it can't be determined
     */
    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting filename from Uri", e);
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
    
    /**
     * Saves metadata about the original file
     * @param encryptedFile The encrypted file
     * @param originalFilename The original file name
     * @throws IOException If saving metadata fails
     */
    private void saveFileMetadata(File encryptedFile, String originalFilename) throws IOException {
        File metadataFile = new File(encryptedFile.getAbsolutePath() + METADATA_SUFFIX);
        try (FileOutputStream fos = new FileOutputStream(metadataFile)) {
            fos.write(originalFilename.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    /**
     * Gets the original file name from an encrypted file's metadata
     * @param encryptedFile The encrypted file
     * @return The original file name or the encrypted file name if metadata doesn't exist
     */
    private String getOriginalFileName(File encryptedFile) {
        File metadataFile = new File(encryptedFile.getAbsolutePath() + METADATA_SUFFIX);
        if (!metadataFile.exists()) {
            // Remove the .enc extension if it exists
            String name = encryptedFile.getName();
            if (name.endsWith(".enc")) {
                return name.substring(0, name.length() - 4);
            }
            return name;
        }
        
        try (FileInputStream fis = new FileInputStream(metadataFile)) {
            byte[] data = new byte[(int) metadataFile.length()];
            fis.read(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.e(TAG, "Error reading metadata file", e);
            return encryptedFile.getName();
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
     * Notify the MediaScanner to remove the file from gallery
     */
    private void notifyMediaScanner(String filePath) {
        if (filePath != null) {
            try {
                MediaScannerConnection.scanFile(
                    context,
                    new String[]{filePath},
                    null,
                    (path, uri) -> Log.d(TAG, "Media scan completed for: " + path)
                );
            } catch (Exception e) {
                Log.e(TAG, "Error notifying media scanner", e);
            }
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