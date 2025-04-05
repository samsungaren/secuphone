package com.example.secuphone_bycoursor.authentication;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.secuphone_bycoursor.MainActivity;
import com.example.secuphone_bycoursor.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

public class SignUpActivity extends AppCompatActivity {
    private static final String TAG = "SignUpActivity";
    
    private TextInputLayout nameLayout, emailLayout, passwordLayout, confirmPasswordLayout;
    private TextInputEditText nameEditText, emailEditText, passwordEditText, confirmPasswordEditText;
    private CheckBox termsCheckBox;
    private TextView termsText, signInText;
    private Button signUpButton;
    private ProgressBar progressBar;
    
    private FirebaseAuth firebaseAuth;
    private UserSessionManager sessionManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);
        
        initializeViews();
        setupListeners();
        
        firebaseAuth = FirebaseAuth.getInstance();
        sessionManager = UserSessionManager.getInstance(this);
    }
    
    private void initializeViews() {
        nameLayout = findViewById(R.id.full_name_layout);
        emailLayout = findViewById(R.id.email_layout);
        passwordLayout = findViewById(R.id.password_layout);
        confirmPasswordLayout = findViewById(R.id.confirm_password_layout);
        
        nameEditText = findViewById(R.id.full_name_input);
        emailEditText = findViewById(R.id.email_input);
        passwordEditText = findViewById(R.id.password_input);
        confirmPasswordEditText = findViewById(R.id.confirm_password_input);
        
        termsCheckBox = findViewById(R.id.terms_checkbox);
        termsText = findViewById(R.id.terms_link);
        signInText = findViewById(R.id.sign_in_link);
        signUpButton = findViewById(R.id.sign_up_button);
        progressBar = findViewById(R.id.loading_indicator);
    }
    
    private void setupListeners() {
        signUpButton.setOnClickListener(v -> signUp());
        signInText.setOnClickListener(v -> navigateToSignIn());
        termsText.setOnClickListener(v -> showTermsAndConditions());
    }
    
    private void signUp() {
        if (!validateInputs()) {
            return;
        }
        
        showProgress(true);
        
        String name = nameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "createUserWithEmail:success");
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        
                        if (user != null) {
                            // Update user profile with name
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();
                            
                            user.updateProfile(profileUpdates)
                                    .addOnCompleteListener(profileTask -> {
                                        if (profileTask.isSuccessful()) {
                                            Log.d(TAG, "User profile updated.");
                                        } else {
                                            Log.w(TAG, "User profile update failed.", profileTask.getException());
                                        }
                                    });
                            
                            // Send email verification
                            sendEmailVerification(user);
                        }
                    } else {
                        showProgress(false);
                        handleSignUpError(task.getException());
                    }
                });
    }
    
    private boolean validateInputs() {
        boolean isValid = true;
        
        String name = nameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();
        
        // Validate name
        if (TextUtils.isEmpty(name)) {
            nameLayout.setError(getString(R.string.error_name_required));
            isValid = false;
        } else {
            nameLayout.setError(null);
        }
        
        // Validate email
        if (TextUtils.isEmpty(email)) {
            emailLayout.setError(getString(R.string.error_email_required));
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError(getString(R.string.error_invalid_email));
            isValid = false;
        } else {
            emailLayout.setError(null);
        }
        
        // Validate password
        if (TextUtils.isEmpty(password)) {
            passwordLayout.setError(getString(R.string.error_password_required));
            isValid = false;
        } else if (password.length() < 6) {
            passwordLayout.setError(getString(R.string.error_password_too_short));
            isValid = false;
        } else {
            passwordLayout.setError(null);
        }
        
        // Validate confirm password
        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordLayout.setError(getString(R.string.error_confirm_password_required));
            isValid = false;
        } else if (!password.equals(confirmPassword)) {
            confirmPasswordLayout.setError(getString(R.string.error_passwords_do_not_match));
            isValid = false;
        } else {
            confirmPasswordLayout.setError(null);
        }
        
        // Validate terms and conditions
        if (!termsCheckBox.isChecked()) {
            Toast.makeText(this, getString(R.string.error_accept_terms), Toast.LENGTH_SHORT).show();
            isValid = false;
        }
        
        return isValid;
    }
    
    private void handleSignUpError(Exception exception) {
        String errorMessage = getString(R.string.error_signup_failed);
        
        if (exception instanceof FirebaseAuthUserCollisionException) {
            errorMessage = getString(R.string.error_email_already_in_use);
        }
        
        Toast.makeText(SignUpActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
        Log.e(TAG, "createUserWithEmail:failure", exception);
    }
    
    private void sendEmailVerification(FirebaseUser user) {
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Email verification sent.");
                        
                        // Create user session
                        sessionManager.createUserSession(user);
                        
                        // Navigate to email verification activity
                        goToEmailVerificationActivity();
                    } else {
                        showProgress(false);
                        Log.e(TAG, "sendEmailVerification:failure", task.getException());
                        Toast.makeText(SignUpActivity.this, 
                                getString(R.string.error_verification_email), 
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    private void showTermsAndConditions() {
        // Show terms and conditions dialog or navigate to terms page
        Toast.makeText(this, getString(R.string.terms_and_conditions), Toast.LENGTH_SHORT).show();
    }
    
    private void navigateToSignIn() {
        finish(); // Close signup and return to sign in
    }
    
    private void goToEmailVerificationActivity() {
        Intent intent = new Intent(SignUpActivity.this, EmailVerificationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        signUpButton.setEnabled(!show);
    }
    
    @Override
    public void onBackPressed() {
        // Allow going back to MainActivity or SignInActivity
        super.onBackPressed();
    }
} 