package com.example.secuphone_bycoursor;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class URLCheckerActivity extends AppCompatActivity {

    private EditText urlInput;
    private TextView warningText;
    private View warningIcon;
    private TextView browserUrl;
    private Button checkUrlButton;
    private ImageButton clipboardButton;
    private View browserPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_url_checker);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.url_checker_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        
        // Initialize views
        urlInput = findViewById(R.id.url_input);
        warningText = findViewById(R.id.warning_text);
        warningIcon = findViewById(R.id.warning_icon);
        browserUrl = findViewById(R.id.browser_url);
        checkUrlButton = findViewById(R.id.check_url_button);
        clipboardButton = findViewById(R.id.clipboard_button);
        browserPreview = findViewById(R.id.browser_preview);
        
        // Setup back navigation
        ImageButton menuButton = findViewById(R.id.menu_button);
        menuButton.setOnClickListener(v -> finish());
        
        // Initially hide the warning elements until URL is checked
        setWarningVisibility(false);
        
        // Setup clipboard button
        setupClipboardButton();
        
        // Setup check URL button
        setupCheckUrlButton();
        
        // Add text change listener to URL input
        setupUrlInputListener();
    }
    
    private void setupClipboardButton() {
        clipboardButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip()) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                String pastedText = item.getText().toString();
                urlInput.setText(pastedText);
                Toast.makeText(this, "URL pasted from clipboard", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void setupCheckUrlButton() {
        checkUrlButton.setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
                return;
            }
            
            checkUrl(url);
        });
    }
    
    private void setupUrlInputListener() {
        urlInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Reset warning when text changes
                setWarningVisibility(false);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Not needed
            }
        });
    }
    
    private void checkUrl(String url) {
        // For demo purposes, we'll consider URLs with certain keywords as dangerous
        // In a real app, this would involve API calls to security services
        boolean isDangerous = url.toLowerCase().contains("badsite") || 
                             url.toLowerCase().contains("malware") || 
                             url.toLowerCase().contains("phishing") ||
                             url.toLowerCase().contains("virus");
        
        // Update UI based on check result
        if (isDangerous) {
            warningText.setText(R.string.url_dangerous);
            warningText.setTextColor(getResources().getColor(R.color.warning_red, null));
        } else {
            warningText.setText(R.string.url_safe);
            warningText.setTextColor(getResources().getColor(R.color.power_button_green, null));
        }
        
        // Show warning elements
        setWarningVisibility(true);
        
        // Update browser preview URL
        browserUrl.setText(url);
    }
    
    private void setWarningVisibility(boolean isVisible) {
        int visibility = isVisible ? View.VISIBLE : View.GONE;
        warningText.setVisibility(visibility);
        warningIcon.setVisibility(visibility);
        browserPreview.setVisibility(visibility);
    }
} 