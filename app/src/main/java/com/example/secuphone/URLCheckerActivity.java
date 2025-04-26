package com.example.secuphone;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private static final String PREF_URL_HISTORY = "url_history";
    private static final String PREF_URL_RESULTS = "url_results";
    private static final int MAX_HISTORY_ITEMS = 10;

    private TextInputEditText urlInput;
    private TextInputLayout urlInputLayout;
    private TextView warningText;
    private ImageView warningIcon;
    private TextView browserUrl;
    private MaterialButton checkUrlButton;
    private ImageButton clipboardButton;
    private View browserPreview;
    private ProgressBar progressBar;
    private ImageView browserContent;
    private LinearLayout resultsSection;
    private LinearLayout historySection;
    private TextView noHistoryText;
    private RecyclerView urlHistoryList;
    private Button clearHistoryButton;
    
    private OkHttpClient client;
    private ExecutorService executorService;
    private Handler mainHandler;
    private SharedPreferences prefs;
    private List<UrlHistoryItem> historyItems = new ArrayList<>();

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
        
        // Initialize preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        // Initialize networking components
        client = new OkHttpClient();
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize views
        urlInput = findViewById(R.id.url_input);
        urlInputLayout = (TextInputLayout) urlInput.getParent().getParent();
        warningText = findViewById(R.id.warning_text);
        warningIcon = findViewById(R.id.warning_icon);
        browserUrl = findViewById(R.id.browser_url);
        checkUrlButton = findViewById(R.id.check_url_button);
        clipboardButton = findViewById(R.id.clipboard_button);
        browserPreview = findViewById(R.id.browser_preview);
        progressBar = findViewById(R.id.progress_bar);
        browserContent = findViewById(R.id.browser_content);
        resultsSection = findViewById(R.id.results_section);
        historySection = findViewById(R.id.history_section);
        noHistoryText = findViewById(R.id.no_history_text);
        urlHistoryList = findViewById(R.id.url_history_list);
        clearHistoryButton = findViewById(R.id.clear_history_button);
        
        // Setup back navigation
        ImageButton menuButton = findViewById(R.id.menu_button);
        menuButton.setOnClickListener(v -> finish());
        
        // Initially hide the results section until URL is checked
        resultsSection.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        
        // Setup clipboard button
        setupClipboardButton();
        
        // Setup check URL button
        setupCheckUrlButton();
        
        // Add text change listener to URL input
        setupUrlInputListener();
        
        // Setup URL history list
        setupUrlHistoryList();
        
        // Setup clear history button
        clearHistoryButton.setOnClickListener(v -> {
            showClearHistoryConfirmation();
        });
        
        // Load URL history
        loadUrlHistory();
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
        
        // Setup TextInputLayout end icon
        if (urlInputLayout != null) {
            urlInputLayout.setEndIconOnClickListener(v -> {
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
    }
    
    private void setupCheckUrlButton() {
        checkUrlButton.setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            if (url.isEmpty()) {
                urlInputLayout.setError("Please enter a URL");
                return;
            } else {
                urlInputLayout.setError(null);
            }
            
            // Validate URL format
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
                urlInput.setText(url);
            }
            
            // Further validate the URL structure
            try {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if (host == null || host.isEmpty()) {
                    urlInputLayout.setError("Invalid URL format");
                    return;
                }
                
                // Basic domain validation (must have at least one dot)
                if (!host.contains(".")) {
                    urlInputLayout.setError("Invalid domain");
                    return;
                }
            } catch (Exception e) {
                urlInputLayout.setError("Invalid URL");
                return;
            }
            
            // Show progress and disable button during API call
            progressBar.setVisibility(View.VISIBLE);
            checkUrlButton.setEnabled(false);
            resultsSection.setVisibility(View.GONE);
            
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
                urlInputLayout.setError(null);
                resultsSection.setVisibility(View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Not needed
            }
        });
    }
    
    private void setupUrlHistoryList() {
        urlHistoryList.setLayoutManager(new LinearLayoutManager(this));
    }
    
    private void loadUrlHistory() {
        historyItems.clear();
        
        // Get saved URLs from SharedPreferences
        Set<String> urlHistory = prefs.getStringSet(PREF_URL_HISTORY, new HashSet<>());
        
        if (urlHistory.isEmpty()) {
            noHistoryText.setVisibility(View.VISIBLE);
            urlHistoryList.setVisibility(View.GONE);
            return;
        }
        
        for (String urlEntry : urlHistory) {
            String[] parts = urlEntry.split("\\|");
            if (parts.length >= 3) {
                String url = parts[0];
                boolean isSafe = Boolean.parseBoolean(parts[1]);
                long timestamp = Long.parseLong(parts[2]);
                
                historyItems.add(new UrlHistoryItem(url, isSafe, timestamp));
            }
        }
        
        // Sort by most recent first
        Collections.sort(historyItems, (a, b) -> Long.compare(b.timestamp, a.timestamp));
        
        // Update UI
        if (historyItems.isEmpty()) {
            noHistoryText.setVisibility(View.VISIBLE);
            urlHistoryList.setVisibility(View.GONE);
        } else {
            noHistoryText.setVisibility(View.GONE);
            urlHistoryList.setVisibility(View.VISIBLE);
            
            // Create adapter and set to RecyclerView
            UrlHistoryAdapter adapter = new UrlHistoryAdapter(historyItems, url -> {
                urlInput.setText(url);
                urlInput.setSelection(url.length());
            });
            urlHistoryList.setAdapter(adapter);
        }
    }
    
    private void saveUrlToHistory(String url, boolean isSafe) {
        // Create the data to save
        String urlEntry = url + "|" + isSafe + "|" + System.currentTimeMillis();
        
        // Get existing history
        Set<String> urlHistory = new HashSet<>(prefs.getStringSet(PREF_URL_HISTORY, new HashSet<>()));
        
        // Remove existing entries with the same URL to avoid duplicates
        Set<String> filteredHistory = new HashSet<>();
        for (String entry : urlHistory) {
            String storedUrl = entry.split("\\|")[0];
            if (!storedUrl.equals(url)) {
                filteredHistory.add(entry);
            }
        }
        
        // Add new entry
        filteredHistory.add(urlEntry);
        
        // If history is too large, remove oldest entries
        if (filteredHistory.size() > MAX_HISTORY_ITEMS) {
            // Convert to list for sorting
            List<String> urlList = new ArrayList<>(filteredHistory);
            
            // Sort by timestamp (oldest first)
            Collections.sort(urlList, (a, b) -> {
                try {
                    long timestampA = Long.parseLong(a.split("\\|")[2]);
                    long timestampB = Long.parseLong(b.split("\\|")[2]);
                    return Long.compare(timestampA, timestampB);
                } catch (Exception e) {
                    Log.e(TAG, "Error sorting history entries", e);
                    return 0;
                }
            });
            
            // Remove oldest entries
            urlList = urlList.subList(urlList.size() - MAX_HISTORY_ITEMS, urlList.size());
            
            // Convert back to set
            filteredHistory = new HashSet<>(urlList);
        }
        
        // Apply the edit with commit (more reliable than apply for SharedPreferences sets)
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(PREF_URL_HISTORY, filteredHistory);
        editor.commit(); // Use commit instead of apply for immediate write
        
        // Reload history list on main thread
        mainHandler.post(this::loadUrlHistory);
    }
    
    private void showClearHistoryConfirmation() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Clear History")
            .setMessage("Are you sure you want to clear your URL check history?")
            .setPositiveButton("Clear", (dialog, which) -> {
                prefs.edit().remove(PREF_URL_HISTORY).apply();
                loadUrlHistory();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void checkUrlWithVirusTotal(String url) {
        // Update browser preview URL immediately
        browserUrl.setText(url);
        
        executorService.execute(() -> {
            try {
                // Step 1: Submit URL for scanning
                String urlId = submitUrlToVirusTotal(url);
                if (urlId == null) {
                    // Fallback to basic checks when API fails
                    performBasicUrlCheck(url);
                    return;
                }
                
                // Step 2: Allow time for processing (VirusTotal needs a moment)
                Thread.sleep(2000);
                
                // Step 3: Get scan results
                JSONObject scanResult = getVirusTotalResults(urlId);
                if (scanResult == null) {
                    // Fallback to basic checks when API fails
                    performBasicUrlCheck(url);
                    return;
                }
                
                // Step 4: Process the results
                processVirusTotalResults(scanResult, url);
                
            } catch (InterruptedException e) {
                showError("Scan was interrupted");
                Log.e(TAG, "URL checking interrupted", e);
            } catch (Exception e) {
                // Fallback to basic checks when any exception occurs
                performBasicUrlCheck(url);
                Log.e(TAG, "Error checking URL with VirusTotal, falling back to basic check", e);
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
                    String errorBody = response.body() != null ? response.body().string() : "No response body";
                    Log.e(TAG, "API error: " + response.code() + " - " + errorBody);
                    
                    if (response.code() == 401) {
                        showError("API key invalid or expired");
                    } else if (response.code() == 429) {
                        showError("Rate limit exceeded. Please try again later.");
                    } else {
                        showError("API error: " + response.code());
                    }
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
            int harmless = stats.getInt("harmless");
            int undetected = stats.getInt("undetected");
            int totalEngines = malicious + suspicious + harmless + undetected;
            
            // Determine if the URL is dangerous
            final boolean isDangerous = (malicious > 0 || suspicious >= SUSPICIOUS_THRESHOLD);
            
            // Save to history
            saveUrlToHistory(url, !isDangerous);
            
            // Update UI on main thread
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                checkUrlButton.setEnabled(true);
                resultsSection.setVisibility(View.VISIBLE);
                
                if (isDangerous) {
                    // Update UI for dangerous URL
                    warningText.setText(getString(R.string.url_dangerous));
                    warningText.setTextColor(ContextCompat.getColor(this, R.color.warning_red));
                    warningIcon.setImageResource(R.drawable.ic_warning_triangle);
                    browserContent.setBackgroundColor(ContextCompat.getColor(this, R.color.warning_red));
                    browserContent.setImageResource(R.drawable.ic_warning_triangle);
                    
                    // Show detailed warning message
                    String dangerMessage = String.format(
                        "DANGER: %d of %d security engines detected this URL as malicious", 
                        malicious + suspicious, 
                        totalEngines
                    );
                    Toast.makeText(URLCheckerActivity.this, dangerMessage, Toast.LENGTH_LONG).show();
                } else {
                    // Update UI for safe URL
                    warningText.setText(getString(R.string.url_safe));
                    warningText.setTextColor(ContextCompat.getColor(this, R.color.power_button_green));
                    warningIcon.setImageResource(R.drawable.ic_shield_logo);
                    browserContent.setBackgroundColor(ContextCompat.getColor(this, R.color.power_button_green));
                    browserContent.setImageResource(R.drawable.ic_shield_logo);
                    
                    // Show safe message
                    String safeMessage = String.format(
                        "SAFE: %d of %d security engines confirmed this URL is harmless", 
                        harmless, 
                        totalEngines
                    );
                    Toast.makeText(URLCheckerActivity.this, safeMessage, Toast.LENGTH_LONG).show();
                }
                
                // Add a TextView to show detailed scan statistics
                TextView scanStatsText = findViewById(R.id.scan_stats_text);
                if (scanStatsText != null) {
                    String statsDetail = String.format(
                        "Scan Results: %d malicious, %d suspicious, %d harmless, %d undetected", 
                        malicious, suspicious, harmless, undetected
                    );
                    scanStatsText.setText(statsDetail);
                    scanStatsText.setVisibility(View.VISIBLE);
                }
            });
        } catch (JSONException e) {
            showError("Error processing scan results");
            Log.e(TAG, "Error processing scan results", e);
        }
    }
    
    private void performBasicUrlCheck(String url) {
        // Perform simple heuristic checks on the URL when VirusTotal API fails
        boolean isSuspicious = false;
        String suspiciousReason = "";
        
        // Check for suspicious keywords in the URL
        String[] suspiciousKeywords = {"phishing", "login", "verify", "account", "secure", "banking", 
                                      "paypal", "signin", "ebay", "apple", "microsoft", "google", 
                                      "facebook", "password", "verify"};
        
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        String path = uri.getPath() != null ? uri.getPath().toLowerCase() : "";
        
        // Check for IP address instead of domain name (potential phishing sign)
        if (host != null && host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            isSuspicious = true;
            suspiciousReason = "Uses IP address instead of domain name";
        }
        
        // Check for suspicious domain
        if (!isSuspicious && host != null) {
            for (String keyword : suspiciousKeywords) {
                if (host.contains(keyword) && !isKnownSafeDomain(host)) {
                    isSuspicious = true;
                    suspiciousReason = "Suspicious domain name";
                    break;
                }
            }
        }
        
        // Check for suspicious URL path
        if (!isSuspicious && !path.isEmpty()) {
            for (String keyword : suspiciousKeywords) {
                if (path.contains(keyword)) {
                    isSuspicious = true;
                    suspiciousReason = "Suspicious URL path";
                    break;
                }
            }
        }
        
        // Update UI based on basic check results
        boolean finalIsSuspicious = isSuspicious;
        String finalSuspiciousReason = suspiciousReason;
        
        mainHandler.post(() -> {
            progressBar.setVisibility(View.GONE);
            checkUrlButton.setEnabled(true);
            resultsSection.setVisibility(View.VISIBLE);
            
            if (finalIsSuspicious) {
                // Update UI for suspicious URL
                warningText.setText("Potentially Unsafe URL");
                warningText.setTextColor(ContextCompat.getColor(this, R.color.warning_red));
                warningIcon.setImageResource(R.drawable.ic_warning_triangle);
                browserContent.setBackgroundColor(ContextCompat.getColor(this, R.color.warning_red));
                browserContent.setImageResource(R.drawable.ic_warning_triangle);
                
                Toast.makeText(URLCheckerActivity.this, 
                               "Warning: " + finalSuspiciousReason + " (Basic check only)", 
                               Toast.LENGTH_LONG).show();
            } else {
                // Update UI for potentially safe URL
                warningText.setText("Potentially Safe URL");
                warningText.setTextColor(ContextCompat.getColor(this, R.color.power_button_green));
                warningIcon.setImageResource(R.drawable.ic_shield_logo);
                browserContent.setBackgroundColor(ContextCompat.getColor(this, R.color.power_button_green));
                browserContent.setImageResource(R.drawable.ic_shield_logo);
                
                Toast.makeText(URLCheckerActivity.this, 
                               "No obvious threats detected (Basic check only)", 
                               Toast.LENGTH_LONG).show();
            }
            
            // Add a note that this was a basic check
            TextView scanStatsText = findViewById(R.id.scan_stats_text);
            if (scanStatsText != null) {
                scanStatsText.setText("VirusTotal API unavailable. Basic check performed instead.");
                scanStatsText.setVisibility(View.VISIBLE);
            }
            
            // Save to history
            saveUrlToHistory(url, !finalIsSuspicious);
        });
    }
    
    private boolean isKnownSafeDomain(String host) {
        String[] knownSafeDomains = {
            "google.com", "microsoft.com", "apple.com", "amazon.com", "facebook.com",
            "twitter.com", "instagram.com", "youtube.com", "linkedin.com", "github.com",
            "stackoverflow.com", "reddit.com", "wikipedia.org", "yahoo.com", "netflix.com"
        };
        
        for (String domain : knownSafeDomains) {
            if (host.endsWith(domain)) {
                return true;
            }
        }
        
        return false;
    }
    
    private void showError(String message) {
        mainHandler.post(() -> {
            progressBar.setVisibility(View.GONE);
            checkUrlButton.setEnabled(true);
            Toast.makeText(URLCheckerActivity.this, message, Toast.LENGTH_LONG).show();
        });
    }
    
    /**
     * Class to represent a URL history item
     */
    private static class UrlHistoryItem {
        String url;
        boolean isSafe;
        long timestamp;
        
        UrlHistoryItem(String url, boolean isSafe, long timestamp) {
            this.url = url;
            this.isSafe = isSafe;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Adapter for URL history list
     */
    private static class UrlHistoryAdapter extends RecyclerView.Adapter<UrlHistoryAdapter.ViewHolder> {
        
        private final List<UrlHistoryItem> items;
        private final OnUrlClickListener listener;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        
        interface OnUrlClickListener {
            void onUrlClick(String url);
        }
        
        UrlHistoryAdapter(List<UrlHistoryItem> items, OnUrlClickListener listener) {
            this.items = items;
            this.listener = listener;
        }
        
        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            // Get the activity context from parent
            Context context = parent.getContext();
            LayoutInflater inflater = LayoutInflater.from(context);
            View view = inflater.inflate(R.layout.item_url_history, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            UrlHistoryItem item = items.get(position);
            Context context = holder.itemView.getContext();
            
            holder.urlText.setText(item.url);
            holder.dateText.setText(dateFormat.format(new Date(item.timestamp)));
            
            // Set status icon and color
            if (item.isSafe) {
                holder.statusIcon.setImageResource(R.drawable.ic_shield_logo);
                holder.statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.power_button_green));
                holder.statusText.setText("Safe");
                holder.statusText.setTextColor(ContextCompat.getColor(context, R.color.power_button_green));
            } else {
                holder.statusIcon.setImageResource(R.drawable.ic_warning_triangle);
                holder.statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.warning_red));
                holder.statusText.setText("Unsafe");
                holder.statusText.setTextColor(ContextCompat.getColor(context, R.color.warning_red));
            }
            
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onUrlClick(item.url);
                }
            });
        }
        
        @Override
        public int getItemCount() {
            return items.size();
        }
        
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView urlText;
            TextView dateText;
            ImageView statusIcon;
            TextView statusText;
            
            ViewHolder(View itemView) {
                super(itemView);
                urlText = itemView.findViewById(R.id.url_text);
                dateText = itemView.findViewById(R.id.date_text);
                statusIcon = itemView.findViewById(R.id.status_icon);
                statusText = itemView.findViewById(R.id.status_text);
            }
        }
    }
} 