package com.example.secuphone.authentication;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Manages user session data and authentication state
 */
public class UserSessionManager {
    private static final String TAG = "UserSessionManager";
    private static final String PREF_NAME = "secuphone_user_session";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    
    private static UserSessionManager instance;
    private final SharedPreferences preferences;
    private final FirebaseAuth firebaseAuth;
    private FirebaseUser currentUser;
    
    private UserSessionManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        firebaseAuth = FirebaseAuth.getInstance();
        currentUser = firebaseAuth.getCurrentUser();
    }
    
    public static synchronized UserSessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserSessionManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Create user session after successful login or signup
     */
    public void createUserSession(FirebaseUser user) {
        if (user == null) {
            Log.e(TAG, "Cannot create session for null user");
            return;
        }
        
        currentUser = user;
        
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_USER_ID, user.getUid());
        editor.putString(KEY_USER_EMAIL, user.getEmail());
        editor.putString(KEY_USER_NAME, user.getDisplayName());
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.apply();
        
        Log.d(TAG, "User session created for: " + user.getEmail());
    }
    
    /**
     * Check if user is logged in
     */
    public boolean isLoggedIn() {
        currentUser = firebaseAuth.getCurrentUser();
        boolean isSessionActive = preferences.getBoolean(KEY_IS_LOGGED_IN, false);
        
        // Verify both Firebase authentication and local session exist
        if (currentUser == null && isSessionActive) {
            // Local session exists but Firebase user doesn't - clear session
            logoutUser();
            return false;
        }
        
        // Check if user's email is verified
        if (currentUser != null && !currentUser.isEmailVerified()) {
            // If email is not verified, don't consider them properly logged in
            return false;
        }
        
        return currentUser != null && isSessionActive;
    }
    
    /**
     * Check if user's email is verified
     */
    public boolean isEmailVerified() {
        currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            currentUser.reload();
            return currentUser.isEmailVerified();
        }
        return false;
    }
    
    /**
     * Get current user from Firebase
     */
    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }
    
    /**
     * Get current user ID
     */
    public String getUserId() {
        return preferences.getString(KEY_USER_ID, null);
    }
    
    /**
     * Get current user email
     */
    public String getUserEmail() {
        return preferences.getString(KEY_USER_EMAIL, null);
    }
    
    /**
     * Get current user display name
     */
    public String getUserName() {
        return preferences.getString(KEY_USER_NAME, null);
    }
    
    /**
     * Update user profile info in session
     */
    public void updateUserProfile(String displayName) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_USER_NAME, displayName);
        editor.apply();
    }
    
    /**
     * Clear user session data
     */
    public void logoutUser() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();
        
        firebaseAuth.signOut();
        currentUser = null;
        
        Log.d(TAG, "User logged out, session cleared");
    }
} 