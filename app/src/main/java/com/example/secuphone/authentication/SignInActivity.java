package com.example.secuphone.authentication;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.secuphone.MainActivity;
import com.example.secuphone.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;

public class SignInActivity extends AppCompatActivity {
    private static final String TAG = "SignInActivity";
    
    private TextInputLayout emailLayout, passwordLayout;
    private TextInputEditText emailEditText, passwordEditText;
    private Button signInButton;
    private Button testAccountButton;
    private TextView forgotPasswordText, signUpText;
    private ProgressBar progressBar;
    
    private FirebaseAuth firebaseAuth;
    private UserSessionManager sessionManager;
    
    private static final String TEST_EMAIL = "individualproject2025@gmail.com";
    private static final String TEST_PASSWORD = "Samsung2025";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);
        
        initializeViews();
        setupListeners();
        
        firebaseAuth = FirebaseAuth.getInstance();
        sessionManager = UserSessionManager.getInstance(this);
    }
    
    private void initializeViews() {
        emailLayout = findViewById(R.id.email_layout);
        passwordLayout = findViewById(R.id.password_layout);
        emailEditText = findViewById(R.id.email_input);
        passwordEditText = findViewById(R.id.password_input);
        signInButton = findViewById(R.id.sign_in_button);
        testAccountButton = findViewById(R.id.test_account_button);
        forgotPasswordText = findViewById(R.id.forgot_password);
        signUpText = findViewById(R.id.sign_up_link);
        progressBar = findViewById(R.id.loading_indicator);
    }
    
    private void setupListeners() {
        signInButton.setOnClickListener(v -> signIn());
        forgotPasswordText.setOnClickListener(v -> forgotPassword());
        signUpText.setOnClickListener(v -> navigateToSignUp());
        testAccountButton.setOnClickListener(v -> fillTestAccountCredentials());
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        
        // Check if user is already signed in
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            if (currentUser.isEmailVerified()) {
                goToMainActivity();
            } else {
                goToEmailVerificationActivity();
            }
        }
    }
    
    private void signIn() {
        if (!validateInputs()) {
            return;
        }
        
        showProgress(true);
        
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithEmail:success");
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        
                        if (user != null) {
                            sessionManager.createUserSession(user);
                            
                            if (user.isEmailVerified()) {
                                goToMainActivity();
                            } else {
                                goToEmailVerificationActivity();
                            }
                        }
                    } else {
                        showProgress(false);
                        handleLoginError(task.getException());
                    }
                });
    }
    
    private boolean validateInputs() {
        boolean isValid = true;
        
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        
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
        } else {
            passwordLayout.setError(null);
        }
        
        return isValid;
    }
    
    private void handleLoginError(Exception exception) {
        String errorMessage = getString(R.string.error_login_failed);
        
        if (exception instanceof FirebaseAuthInvalidUserException) {
            errorMessage = getString(R.string.error_user_not_found);
        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            errorMessage = getString(R.string.error_invalid_credentials);
        }
        
        Toast.makeText(SignInActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
        Log.e(TAG, "signInWithEmail:failure", exception);
    }
    
    private void forgotPassword() {
        String email = emailEditText.getText().toString().trim();
        
        if (TextUtils.isEmpty(email)) {
            emailLayout.setError(getString(R.string.error_email_required_reset));
            return;
        }
        
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError(getString(R.string.error_invalid_email));
            return;
        }
        
        showProgress(true);
        
        firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    showProgress(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(SignInActivity.this, 
                                getString(R.string.reset_email_sent), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(SignInActivity.this, 
                                getString(R.string.error_reset_email), Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    private void navigateToSignUp() {
        Intent intent = new Intent(SignInActivity.this, SignUpActivity.class);
        startActivity(intent);
    }
    
    private void goToMainActivity() {
        Intent intent = new Intent(SignInActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
    
    private void goToEmailVerificationActivity() {
        Intent intent = new Intent(SignInActivity.this, EmailVerificationActivity.class);
        startActivity(intent);
    }
    
    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        signInButton.setEnabled(!show);
    }
    
    private void fillTestAccountCredentials() {
        emailEditText.setText(TEST_EMAIL);
        passwordEditText.setText(TEST_PASSWORD);
    }
    
    @Override
    public void onBackPressed() {
        // Allow going back to MainActivity instead of preventing back navigation
        super.onBackPressed();
    }
} 