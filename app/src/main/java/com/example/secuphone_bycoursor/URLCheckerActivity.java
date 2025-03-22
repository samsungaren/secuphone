package com.example.secuphone_bycoursor;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class URLCheckerActivity extends AppCompatActivity {

    private static final String TAG = "URLCheckerActivity";
    private static final String VIRUSTOTAL_API_KEY = "f07bcbac1cd67fd448a98464a2a694bc62aa61003a1ef7604e49b0ed10247988";
    private static final String VIRUSTOTAL_API_URL = "https://www.virustotal.com/api/v3/urls";
    private static final int SUSPICIOUS_THRESHOLD = 1; // Number of engines needed to flag as suspicious

    private EditText urlInput;
    private TextView warningText;
    private ImageView warningIcon;
    private TextView browserUrl;
    private Button checkUrlButton;
    private ImageButton clipboardButton;
    private View browserPreview;
    private ProgressBar progressBar;
    private ImageView browserContent;
    
    private OkHttpClient client;
    private ExecutorService executorService;
    private Handler mainHandler;

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
        
        // Initialize networking components
        client = new OkHttpClient();
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize views
        urlInput = findViewById(R.id.url_input);
        warningText = findViewById(R.id.warning_text);
        warningIcon = findViewById(R.id.warning_icon);
        browserUrl = findViewById(R.id.browser_url);
        checkUrlButton = findViewById(R.id.check_url_button);
        clipboardButton = findViewById(R.id.clipboard_button);
        browserPreview = findViewById(R.id.browser_preview);
        progressBar = findViewById(R.id.progress_bar);
        browserContent = findViewById(R.id.browser_content);
        
        // Setup back navigation
        ImageButton menuButton = findViewById(R.id.menu_button);
        menuButton.setOnClickListener(v -> finish());
        
        // Initially hide the warning elements until URL is checked
        setWarningVisibility(false);
        progressBar.setVisibility(View.GONE);
        
        // Setup clipboard button
        setupClipboardButton();
        
        // Setup check URL button
        setupCheckUrlButton();
        
        // Add text change listener to URL input
        setupUrlInputListener();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown executor service to prevent memory leaks
        executorService.shutdown();
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
            
            // Add http:// prefix if not present
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
                urlInput.setText(url);
            }
            
            // Show progress and disable button during API call
            progressBar.setVisibility(View.VISIBLE);
            checkUrlButton.setEnabled(false);
            
            // Check URL using VirusTotal API
            checkUrlWithVirusTotal(url);
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
    
    private void checkUrlWithVirusTotal(String url) {
        // Update browser preview URL immediately
        browserUrl.setText(url);
        
        executorService.execute(() -> {
            try {
                // Step 1: Submit URL for scanning
                String urlId = submitUrlToVirusTotal(url);
                if (urlId == null) {
                    showError("Failed to submit URL for analysis");
                    return;
                }
                
                // Step 2: Allow time for processing (VirusTotal needs a moment)
                Thread.sleep(2000);
                
                // Step 3: Get scan results
                JSONObject scanResult = getVirusTotalResults(urlId);
                if (scanResult == null) {
                    showError("Failed to retrieve scan results");
                    return;
                }
                
                // Step 4: Process the results
                processVirusTotalResults(scanResult, url);
                
            } catch (InterruptedException e) {
                showError("Scan was interrupted");
                Log.e(TAG, "URL checking interrupted", e);
            } catch (Exception e) {
                showError("Error checking URL: " + e.getMessage());
                Log.e(TAG, "Error checking URL", e);
            }
        });
    }
    
    private String submitUrlToVirusTotal(String url) {
        try {
            // Create the POST request to submit URL
            HttpUrl httpUrl = HttpUrl.parse(VIRUSTOTAL_API_URL).newBuilder().build();
            
            // Form encoded body for the URL submission
            okhttp3.FormBody formBody = new okhttp3.FormBody.Builder()
                    .add("url", url)
                    .build();
            
            Request request = new Request.Builder()
                    .url(httpUrl)
                    .addHeader("x-apikey", VIRUSTOTAL_API_KEY)
                    .post(formBody)
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "API error: " + response.code());
                    return null;
                }
                
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);
                
                // Extract the analysis ID
                String analysisId = jsonResponse.getJSONObject("data").getString("id");
                return analysisId;
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error submitting URL to VirusTotal", e);
            return null;
        }
    }
    
    private JSONObject getVirusTotalResults(String analysisId) {
        try {
            // Create the URL for retrieving analysis
            String analysisUrl = "https://www.virustotal.com/api/v3/analyses/" + analysisId;
            
            Request request = new Request.Builder()
                    .url(analysisUrl)
                    .addHeader("x-apikey", VIRUSTOTAL_API_KEY)
                    .get()
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "API error: " + response.code());
                    return null;
                }
                
                String responseBody = response.body().string();
                return new JSONObject(responseBody);
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error getting VirusTotal results", e);
            return null;
        }
    }
    
    private void processVirusTotalResults(JSONObject result, String url) {
        try {
            JSONObject attributes = result.getJSONObject("data").getJSONObject("attributes");
            JSONObject stats = attributes.getJSONObject("stats");
            
            // Get the number of engines that flagged this as malicious
            int malicious = stats.getInt("malicious");
            int suspicious = stats.getInt("suspicious");
            int totalEngines = malicious + suspicious + stats.getInt("harmless") + stats.getInt("undetected");
            
            // Determine if the URL is dangerous
            final boolean isDangerous = (malicious > 0 || suspicious >= SUSPICIOUS_THRESHOLD);
            
            // Update UI on main thread
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                checkUrlButton.setEnabled(true);
                
        if (isDangerous) {
                    // Update UI for dangerous URL
                    warningText.setText(getString(R.string.url_dangerous));
            warningText.setTextColor(getResources().getColor(R.color.warning_red, null));
                    warningIcon.setImageResource(R.drawable.ic_warning_triangle);
                    browserContent.setBackgroundColor(getResources().getColor(R.color.warning_red, null));
                    browserContent.setImageResource(R.drawable.ic_warning_triangle);
        } else {
                    // Update UI for safe URL
                    warningText.setText(getString(R.string.url_safe));
            warningText.setTextColor(getResources().getColor(R.color.power_button_green, null));
                    warningIcon.setImageResource(R.drawable.ic_shield_logo);
                    browserContent.setBackgroundColor(getResources().getColor(R.color.power_button_green, null));
                    browserContent.setImageResource(R.drawable.ic_shield_logo);
        }
        
                // Show the safety result and browser preview
        setWarningVisibility(true);
        
                // Show detailed stats in a toast
                String stats_message = "Report: " + malicious + " malicious, " + 
                                       suspicious + " suspicious out of " + totalEngines + " engines";
                Toast.makeText(URLCheckerActivity.this, stats_message, Toast.LENGTH_LONG).show();
            });
        } catch (JSONException e) {
            showError("Error processing scan results");
            Log.e(TAG, "Error processing scan results", e);
        }
    }
    
    private void showError(String message) {
        mainHandler.post(() -> {
            progressBar.setVisibility(View.GONE);
            checkUrlButton.setEnabled(true);
            Toast.makeText(URLCheckerActivity.this, message, Toast.LENGTH_LONG).show();
        });
    }
    
    private void setWarningVisibility(boolean isVisible) {
        int visibility = isVisible ? View.VISIBLE : View.GONE;
        warningText.setVisibility(visibility);
        warningIcon.setVisibility(visibility);
        browserPreview.setVisibility(visibility);
    }
} 