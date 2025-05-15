package com.example.secuphone;

import android.app.Application;

import com.google.firebase.FirebaseApp;

public class SecuPhoneApplication extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this);
    }
} 