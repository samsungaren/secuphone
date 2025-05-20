package com.example.secuphone;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Logger;

/**
 * Main Application class for SecuPhone
 * Handles global initialization of Firebase and other services
 */
public class SecuPhoneApplication extends Application {
    private static final String TAG = "SecuPhoneApp";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        try {
            // Initialize Firebase
            FirebaseApp.initializeApp(this);
            
            // Enable Firebase database persistence for offline capability
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
            
            // Enable detailed logging for debugging
            FirebaseDatabase.getInstance().setLogLevel(Logger.Level.DEBUG);
            
            Log.d(TAG, "Firebase initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase", e);
        }
    }
} 