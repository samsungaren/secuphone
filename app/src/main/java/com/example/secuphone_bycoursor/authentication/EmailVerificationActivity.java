package com.example.secuphone_bycoursor.authentication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.secuphone_bycoursor.MainActivity;
import com.example.secuphone_bycoursor.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class EmailVerificationActivity extends AppCompatActivity {
    private static final String TAG = "EmailVerificationActivity";
    private static final int VERIFICATION_CHECK_INTERVAL = 3000; // 3 seconds
    
    private TextView emailText, continueText;
    private Button resendButton;
    private ProgressBar progressBar;
    
    private FirebaseAuth firebaseAuth;
    private UserSessionManager sessionManager;
    private Handler handler;
    private Runnable verificationChecker;
    private boolean isCheckingVerification = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_verification);
        
        initializeViews();
        setupToolbar();
        setupListeners();
        
        firebaseAuth = FirebaseAuth.getInstance();
        sessionManager = UserSessionManager.getInstance(this);
        
        // Set user email in UI
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            emailText.setText(user.getEmail());
        } else {
            // No user is signed in, go back to sign in
            goToSignInActivity();
            return;
        }
        
        handler = new Handler(Looper.getMainLooper());
        verificationChecker = new Runnable() {
            @Override
            public void run() {
                checkEmailVerification();
                if (isCheckingVerification) {
                    handler.postDelayed(this, VERIFICATION_CHECK_INTERVAL);
                }
            }
        };
    }
    
    private void initializeViews() {
        emailText = findViewById(R.id.email_address);
        continueText = findViewById(R.id.continue_to_app);
        resendButton = findViewById(R.id.resend_button);
        progressBar = findViewById(R.id.loading_indicator);
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }
    
    private void setupListeners() {
        resendButton.setOnClickListener(v -> resendVerificationEmail());
        continueText.setOnClickListener(v -> checkEmailVerificationManually());
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            goToSignInActivity();
            return;
        }
        
        // Start verification checking
        startVerificationCheck();
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        // Stop verification checking
        stopVerificationCheck();
    }
    
    private void startVerificationCheck() {
        if (!isCheckingVerification) {
            isCheckingVerification = true;
            verificationChecker.run();
        }
    }
    
    private void stopVerificationCheck() {
        isCheckingVerification = false;
        handler.removeCallbacks(verificationChecker);
    }
    
    private void resendVerificationEmail() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            goToSignInActivity();
            return;
        }
        
        showProgress(true);
        user.reload().addOnCompleteListener(reloadTask -> {
            if (reloadTask.isSuccessful()) {
                user.sendEmailVerification()
                        .addOnCompleteListener(task -> {
                            showProgress(false);
                            if (task.isSuccessful()) {
                                Toast.makeText(EmailVerificationActivity.this,
                                        getString(R.string.verification_email_resent),
                                        Toast.LENGTH_LONG).show();
                                Log.d(TAG, "Verification email resent.");
                            } else {
                                Toast.makeText(EmailVerificationActivity.this,
                                        getString(R.string.error_verification_email),
                                        Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "sendEmailVerification:failure", task.getException());
                            }
                        });
            } else {
                showProgress(false);
                Toast.makeText(EmailVerificationActivity.this,
                        getString(R.string.error_reload_user),
                        Toast.LENGTH_SHORT).show();
                Log.e(TAG, "userReload:failure", reloadTask.getException());
            }
        });
    }
    
    private void checkEmailVerification() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            stopVerificationCheck();
            goToSignInActivity();
            return;
        }
        
        user.reload().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Get fresh user data
                user.reload();
                if (user.isEmailVerified()) {
                    stopVerificationCheck();
                    sessionManager.createUserSession(user); // Update session
                    goToMainActivity();
                }
            }
        });
    }
    
    private void checkEmailVerificationManually() {
        showProgress(true);
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            user.reload().addOnCompleteListener(task -> {
                showProgress(false);
                if (task.isSuccessful()) {
                    if (user.isEmailVerified()) {
                        sessionManager.createUserSession(user); // Update session
                        goToMainActivity();
                    } else {
                        Toast.makeText(EmailVerificationActivity.this,
                                getString(R.string.email_not_verified_yet),
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(EmailVerificationActivity.this,
                            getString(R.string.error_verification_check),
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "checkEmailVerification:failure", task.getException());
                }
            });
        } else {
            showProgress(false);
            goToSignInActivity();
        }
    }
    
    @Override
    public void onBackPressed() {
        // Sign out and go back to sign in
        firebaseAuth.signOut();
        goToSignInActivity();
    }
    
    private void goToSignInActivity() {
        Intent intent = new Intent(EmailVerificationActivity.this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void goToMainActivity() {
        Intent intent = new Intent(EmailVerificationActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        resendButton.setEnabled(!show);
    }
} 