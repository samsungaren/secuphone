package com.example.secuphone;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.secuphone.authentication.SignInActivity;
import com.example.secuphone.authentication.UserSessionManager;
import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 3500;

    private ImageView logoImage;
    private TextView appNameText;
    private TextView taglineText;
    private LinearLayout securityDots;
    private View[] dots;
    private UserSessionManager sessionManager;
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.splash_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize session manager
        sessionManager = UserSessionManager.getInstance(this);
        firebaseAuth = FirebaseAuth.getInstance();
        
        logoImage = findViewById(R.id.splash_logo);
        appNameText = findViewById(R.id.splash_app_name);
        taglineText = findViewById(R.id.splash_tagline);
        securityDots = findViewById(R.id.security_dots);
        
        dots = new View[5];
        dots[0] = findViewById(R.id.dot1);
        dots[1] = findViewById(R.id.dot2);
        dots[2] = findViewById(R.id.dot3);
        dots[3] = findViewById(R.id.dot4);
        dots[4] = findViewById(R.id.dot5);

        startAnimations();
        
        new Handler(Looper.getMainLooper()).postDelayed(this::checkUserAndNavigate, SPLASH_DURATION);
    }

    private void startAnimations() {
        Animation scaleAnim = AnimationUtils.loadAnimation(this, R.anim.scale_animation);
        logoImage.startAnimation(scaleAnim);
        
        scaleAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                Animation rotateAnim = AnimationUtils.loadAnimation(SplashActivity.this, R.anim.rotate_animation);
                logoImage.startAnimation(rotateAnim);
                
                fadeInTextElements();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
    }
    
    private void fadeInTextElements() {
        Animation fadeAnim = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        appNameText.setAlpha(1f);
        appNameText.startAnimation(fadeAnim);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            taglineText.setAlpha(1f);
            taglineText.startAnimation(fadeAnim);
            
            new Handler(Looper.getMainLooper()).postDelayed(this::animateDots, 300);
        }, 200);
    }
    
    private void animateDots() {
        securityDots.setAlpha(1f);
        
        for (int i = 0; i < dots.length; i++) {
            final int index = i;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                pulseAnimation(dots[index]);
            }, i * 150);
        }
    }
    
    private void pulseAnimation(View dot) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.5f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.5f);
        
        scaleX.setDuration(300);
        scaleY.setDuration(300);
        scaleX.setRepeatMode(ValueAnimator.REVERSE);
        scaleY.setRepeatMode(ValueAnimator.REVERSE);
        scaleX.setRepeatCount(1);
        scaleY.setRepeatCount(1);
        
        scaleX.start();
        scaleY.start();
    }

    /**
     * Check if user is logged in and navigate to the appropriate screen
     */
    private void checkUserAndNavigate() {
        // Always navigate to MainActivity, regardless of authentication status
        navigateToMainActivity();
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        startActivity(intent);
        
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        
        finish();
    }
    
    private void navigateToSignInActivity() {
        Intent intent = new Intent(SplashActivity.this, SignInActivity.class);
        startActivity(intent);
        
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        
        finish();
    }
} 