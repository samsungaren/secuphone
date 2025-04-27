package com.example.secuphone;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.secuphone.adapters.PasswordAdapter;
import com.example.secuphone.models.PasswordEntry;
import com.example.secuphone.utils.PasswordGenerator;
import com.example.secuphone.utils.PasswordManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class PasswordManagerActivity extends AppCompatActivity implements PasswordAdapter.PasswordAdapterListener {

    private PasswordManager passwordManager;
    private PasswordAdapter passwordAdapter;
    private RecyclerView passwordRecyclerView;
    private LinearLayout emptyStateContainer;
    private FloatingActionButton addPasswordFab;
    
    // Current state
    private PasswordEntry currentEditingEntry = null;
    private String generatedPassword = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_manager);
        
        // Initialize toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.password_manager_title);
        
        // Initialize views
        passwordRecyclerView = findViewById(R.id.password_recycler_view);
        emptyStateContainer = findViewById(R.id.empty_state_container);
        addPasswordFab = findViewById(R.id.add_password_fab);
        
        // Initialize password manager
        passwordManager = new PasswordManager(this);
        
        // Set up RecyclerView
        passwordRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // Set click listeners
        addPasswordFab.setOnClickListener(v -> {
            if (passwordManager.isAuthenticated()) {
                showAddPasswordDialog(null);
            }
        });
        
        // Check for master password or create one
        checkMasterPassword();
    }
    
    private void checkMasterPassword() {
        if (passwordManager.isMasterPasswordSet()) {
            // If master password exists, prompt for authentication
            showMasterPasswordDialog();
        } else {
            // If no master password, prompt to create one
            showCreateMasterPasswordDialog();
        }
    }
    
    private void showMasterPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_master_password, null);
        builder.setView(view);
        
        TextInputEditText passwordInput = view.findViewById(R.id.master_password_input);
        Button submitButton = view.findViewById(R.id.submit_button);
        Button cancelButton = view.findViewById(R.id.cancel_button);
        TextView errorMessage = view.findViewById(R.id.error_message);
        
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false); // Prevent dismissing with back button
        
        submitButton.setOnClickListener(v -> {
            String password = passwordInput.getText().toString().trim();
            
            if (TextUtils.isEmpty(password)) {
                errorMessage.setText(R.string.master_password_required);
                errorMessage.setVisibility(View.VISIBLE);
                return;
            }
            
            if (passwordManager.verifyMasterPassword(password)) {
                dialog.dismiss();
                loadPasswords();
            } else {
                errorMessage.setText(R.string.master_password_incorrect);
                errorMessage.setVisibility(View.VISIBLE);
            }
        });
        
        cancelButton.setOnClickListener(v -> finish()); // Exit activity if canceled
        
        dialog.show();
    }
    
    private void showCreateMasterPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_master_password, null);
        builder.setView(view);
        
        TextInputEditText passwordInput = view.findViewById(R.id.master_password_input);
        TextInputEditText confirmPasswordInput = view.findViewById(R.id.confirm_master_password_input);
        Button saveButton = view.findViewById(R.id.save_button);
        Button cancelButton = view.findViewById(R.id.cancel_button);
        TextView errorMessage = view.findViewById(R.id.error_message);
        
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false); // Prevent dismissing with back button
        
        saveButton.setOnClickListener(v -> {
            String password = passwordInput.getText().toString().trim();
            String confirmPassword = confirmPasswordInput.getText().toString().trim();
            
            if (TextUtils.isEmpty(password)) {
                errorMessage.setText(R.string.master_password_required);
                errorMessage.setVisibility(View.VISIBLE);
                return;
            }
            
            if (!password.equals(confirmPassword)) {
                errorMessage.setText(R.string.passwords_not_match);
                errorMessage.setVisibility(View.VISIBLE);
                return;
            }
            
            if (passwordManager.setMasterPassword(password)) {
                Toast.makeText(this, R.string.master_password_set, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                loadPasswords();
            } else {
                errorMessage.setText("Failed to set master password");
                errorMessage.setVisibility(View.VISIBLE);
            }
        });
        
        cancelButton.setOnClickListener(v -> finish()); // Exit activity if canceled
        
        dialog.show();
    }
    
    private void loadPasswords() {
        List<PasswordEntry> passwords = passwordManager.getAllPasswords();
        
        if (passwords.isEmpty()) {
            emptyStateContainer.setVisibility(View.VISIBLE);
            passwordRecyclerView.setVisibility(View.GONE);
        } else {
            emptyStateContainer.setVisibility(View.GONE);
            passwordRecyclerView.setVisibility(View.VISIBLE);
            
            // Initialize adapter if needed
            if (passwordAdapter == null) {
                passwordAdapter = new PasswordAdapter(this, passwords, this);
                passwordRecyclerView.setAdapter(passwordAdapter);
            } else {
                passwordAdapter.updateData(passwords);
            }
        }
    }
    
    /**
     * Show dialog to add or edit a password entry
     */
    private void showAddPasswordDialog(PasswordEntry entry) {
        currentEditingEntry = entry;
        
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_password, null);
        builder.setView(dialogView);
        
        // Get views
        TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        TextInputEditText titleInput = dialogView.findViewById(R.id.title_input);
        TextInputEditText usernameInput = dialogView.findViewById(R.id.username_input);
        TextInputEditText passwordInput = dialogView.findViewById(R.id.password_input);
        Button generateButton = dialogView.findViewById(R.id.generate_password_button);
        Button cancelButton = dialogView.findViewById(R.id.cancel_button);
        Button saveButton = dialogView.findViewById(R.id.save_button);
        
        // Set initial values if editing
        if (entry != null) {
            dialogTitle.setText(R.string.edit_password);
            titleInput.setText(entry.getTitle());
            usernameInput.setText(entry.getUsername());
            passwordInput.setText(entry.getPassword());
        }
        
        // Create the dialog
        AlertDialog dialog = builder.create();
        
        // Set click listeners
        generateButton.setOnClickListener(v -> {
            showGeneratePasswordDialog(passwordInput);
        });
        
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        
        saveButton.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String username = usernameInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            
            // Validate fields
            if (TextUtils.isEmpty(title)) {
                titleInput.setError("Please enter a website or app name");
                return;
            }
            
            if (TextUtils.isEmpty(username)) {
                usernameInput.setError("Please enter a username or email");
                return;
            }
            
            if (TextUtils.isEmpty(password)) {
                passwordInput.setError("Please enter a password");
                return;
            }
            
            // Save the password
            boolean success;
            if (entry != null) {
                // Update existing entry
                entry.setTitle(title);
                entry.setUsername(username);
                entry.setPassword(password);
                success = passwordManager.savePassword(entry);
            } else {
                // Create new entry
                PasswordEntry newEntry = new PasswordEntry(title, username, password);
                success = passwordManager.savePassword(newEntry);
            }
            
            if (success) {
                dialog.dismiss();
                loadPasswords();
                Toast.makeText(PasswordManagerActivity.this, R.string.password_saved, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(PasswordManagerActivity.this, R.string.password_not_saved, Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.show();
    }
    
    /**
     * Show dialog to generate a secure password
     */
    private void showGeneratePasswordDialog(TextInputEditText targetPasswordInput) {
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_generate_password, null);
        builder.setView(dialogView);
        
        // Get views
        TextView generatedPasswordText = dialogView.findViewById(R.id.generated_password_text);
        SeekBar lengthSlider = dialogView.findViewById(R.id.password_length_slider);
        TextView lengthText = dialogView.findViewById(R.id.password_length_text);
        CheckBox uppercaseCheckbox = dialogView.findViewById(R.id.include_uppercase_checkbox);
        CheckBox lowercaseCheckbox = dialogView.findViewById(R.id.include_lowercase_checkbox);
        CheckBox numbersCheckbox = dialogView.findViewById(R.id.include_numbers_checkbox);
        CheckBox symbolsCheckbox = dialogView.findViewById(R.id.include_symbols_checkbox);
        Button generateButton = dialogView.findViewById(R.id.generate_button);
        Button usePasswordButton = dialogView.findViewById(R.id.use_password_button);
        
        // Initialize with a default generated password
        PasswordGenerator passwordGenerator = new PasswordGenerator();
        generatedPassword = passwordGenerator.generate();
        generatedPasswordText.setText(generatedPassword);
        
        // Set initial length value
        lengthText.setText(String.valueOf(lengthSlider.getProgress()));
        
        // Set up listeners
        lengthSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int length = Math.max(8, progress); // Minimum length of 8
                lengthText.setText(String.valueOf(length));
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        
        // Create dialog
        AlertDialog dialog = builder.create();
        
        // Generate button click
        generateButton.setOnClickListener(v -> {
            int length = lengthSlider.getProgress();
            if (length < 8) length = 8; // Minimum length of 8
            
            // Make sure at least one option is selected
            if (!uppercaseCheckbox.isChecked() && !lowercaseCheckbox.isChecked() && 
                !numbersCheckbox.isChecked() && !symbolsCheckbox.isChecked()) {
                Toast.makeText(PasswordManagerActivity.this, 
                        "Please select at least one character type", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Generate password
            generatedPassword = new PasswordGenerator()
                    .length(length)
                    .useUpperCase(uppercaseCheckbox.isChecked())
                    .useLowerCase(lowercaseCheckbox.isChecked())
                    .useDigits(numbersCheckbox.isChecked())
                    .useSpecialChars(symbolsCheckbox.isChecked())
                    .generate();
            
            generatedPasswordText.setText(generatedPassword);
        });
        
        // Use password button click
        usePasswordButton.setOnClickListener(v -> {
            if (generatedPassword != null) {
                targetPasswordInput.setText(generatedPassword);
                dialog.dismiss();
            }
        });
        
        dialog.show();
    }
    
    /**
     * Show dialog to confirm password deletion
     */
    private void showDeleteConfirmationDialog(PasswordEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_password)
                .setMessage(R.string.delete_password_confirmation)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    boolean success = passwordManager.deletePassword(entry.getId());
                    if (success) {
                        loadPasswords();
                        Toast.makeText(PasswordManagerActivity.this, R.string.password_deleted, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(PasswordManagerActivity.this, R.string.password_not_deleted, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }
    
    @Override
    public void onEditPassword(PasswordEntry entry) {
        showAddPasswordDialog(entry);
    }
    
    @Override
    public void onDeletePassword(PasswordEntry entry) {
        showDeleteConfirmationDialog(entry);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Log out of the password manager when activity is not visible for security
        passwordManager.logout();
    }
} 