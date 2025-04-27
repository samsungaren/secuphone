package com.example.secuphone.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.secuphone.R;
import com.example.secuphone.models.PasswordEntry;

import java.util.List;

/**
 * Adapter for displaying password entries in a RecyclerView
 */
public class PasswordAdapter extends RecyclerView.Adapter<PasswordAdapter.PasswordViewHolder> {
    
    private List<PasswordEntry> passwordEntries;
    private Context context;
    private PasswordAdapterListener listener;
    
    public interface PasswordAdapterListener {
        void onEditPassword(PasswordEntry entry);
        void onDeletePassword(PasswordEntry entry);
    }
    
    public PasswordAdapter(Context context, List<PasswordEntry> passwordEntries, PasswordAdapterListener listener) {
        this.context = context;
        this.passwordEntries = passwordEntries;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public PasswordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_password, parent, false);
        return new PasswordViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull PasswordViewHolder holder, int position) {
        PasswordEntry entry = passwordEntries.get(position);
        holder.bind(entry);
    }
    
    @Override
    public int getItemCount() {
        return passwordEntries != null ? passwordEntries.size() : 0;
    }
    
    /**
     * Update the adapter data
     */
    public void updateData(List<PasswordEntry> newEntries) {
        this.passwordEntries = newEntries;
        notifyDataSetChanged();
    }
    
    class PasswordViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView usernameText;
        TextView passwordHiddenText;
        ImageButton copyUsernameButton;
        ImageButton copyPasswordButton;
        ImageButton editButton;
        ImageButton deleteButton;
        ImageButton toggleVisibilityButton;
        
        boolean isPasswordVisible = false;
        
        public PasswordViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.password_title);
            usernameText = itemView.findViewById(R.id.password_username);
            passwordHiddenText = itemView.findViewById(R.id.password_hidden);
            copyUsernameButton = itemView.findViewById(R.id.copy_username_button);
            copyPasswordButton = itemView.findViewById(R.id.copy_password_button);
            editButton = itemView.findViewById(R.id.edit_button);
            deleteButton = itemView.findViewById(R.id.delete_button);
            toggleVisibilityButton = itemView.findViewById(R.id.toggle_visibility_button);
        }
        
        public void bind(PasswordEntry entry) {
            titleText.setText(entry.getTitle());
            usernameText.setText(entry.getUsername());
            
            // Initially set password as hidden
            updatePasswordVisibility(entry);
            
            // Set click listeners
            copyUsernameButton.setOnClickListener(v -> copyToClipboard("Username", entry.getUsername()));
            copyPasswordButton.setOnClickListener(v -> copyToClipboard("Password", entry.getPassword()));
            editButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditPassword(entry);
                }
            });
            deleteButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeletePassword(entry);
                }
            });
            toggleVisibilityButton.setOnClickListener(v -> {
                isPasswordVisible = !isPasswordVisible;
                updatePasswordVisibility(entry);
            });
        }
        
        private void updatePasswordVisibility(PasswordEntry entry) {
            if (isPasswordVisible) {
                passwordHiddenText.setText(entry.getPassword());
                toggleVisibilityButton.setImageResource(R.drawable.ic_visibility_off);
            } else {
                // Create a string of dots with the same length as the password
                StringBuilder hiddenPassword = new StringBuilder();
                for (int i = 0; i < entry.getPassword().length(); i++) {
                    hiddenPassword.append("•");
                }
                passwordHiddenText.setText(hiddenPassword.toString());
                toggleVisibilityButton.setImageResource(R.drawable.ic_visibility);
            }
        }
        
        private void copyToClipboard(String label, String text) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(label, text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, label + " copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }
} 