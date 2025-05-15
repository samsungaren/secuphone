package com.example.secuphone.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Utility class to manage Firebase Authentication operations
 */
public class FirebaseAuthManager {
    
    private static final String TAG = "FirebaseAuthManager";
    
    private FirebaseAuth firebaseAuth;
    private static FirebaseAuthManager instance;
    
    // Interface for auth callbacks
    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onFailure(Exception e);
    }
    
    private FirebaseAuthManager() {
        firebaseAuth = FirebaseAuth.getInstance();
    }
    
    public static synchronized FirebaseAuthManager getInstance() {
        if (instance == null) {
            instance = new FirebaseAuthManager();
        }
        return instance;
    }
    
    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }
    
    public boolean isUserLoggedIn() {
        return getCurrentUser() != null;
    }
    
    public void signIn(String email, String password, final AuthCallback callback) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        Log.d(TAG, "signInWithEmail:success");
                        callback.onSuccess(user);
                    } else {
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        callback.onFailure(task.getException());
                    }
                });
    }
    
    public void signUp(String email, String password, final AuthCallback callback) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        Log.d(TAG, "createUserWithEmail:success");
                        callback.onSuccess(user);
                    } else {
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        callback.onFailure(task.getException());
                    }
                });
    }
    
    public void signOut() {
        firebaseAuth.signOut();
    }
} 