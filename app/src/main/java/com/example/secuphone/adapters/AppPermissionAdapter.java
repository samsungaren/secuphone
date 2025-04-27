package com.example.secuphone.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.secuphone.R;

import java.util.List;
import java.util.Map;

public class AppPermissionAdapter extends RecyclerView.Adapter<AppPermissionAdapter.AppViewHolder> {

    private final List<ApplicationInfo> appList;
    private final Context context;
    private final PackageManager packageManager;
    private final Map<String, Boolean> hasCameraPermission;
    private final Map<String, Boolean> hasMicPermission;
    private final Map<String, Boolean> hasLocationPermission;
    private final AppPermissionListener listener;

    public interface AppPermissionListener {
        void onPermissionSettingsChanged(String packageName, int position);
    }

    public AppPermissionAdapter(Context context, List<ApplicationInfo> appList, 
                               Map<String, Boolean> hasCameraPermission,
                               Map<String, Boolean> hasMicPermission,
                               Map<String, Boolean> hasLocationPermission,
                               AppPermissionListener listener) {
        this.context = context;
        this.appList = appList;
        this.packageManager = context.getPackageManager();
        this.hasCameraPermission = hasCameraPermission;
        this.hasMicPermission = hasMicPermission;
        this.hasLocationPermission = hasLocationPermission;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_app_permission, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        ApplicationInfo app = appList.get(position);
        final String packageName = app.packageName;
        
        // Set app name and package
        holder.appName.setText(app.loadLabel(packageManager));
        holder.appPackage.setText(packageName);
        
        // Set app icon
        try {
            holder.appIcon.setImageDrawable(packageManager.getApplicationIcon(packageName));
        } catch (PackageManager.NameNotFoundException e) {
            holder.appIcon.setImageResource(R.drawable.ic_android);
        }
        
        // Show permission icons
        Boolean hasCamera = hasCameraPermission.get(packageName);
        Boolean hasMic = hasMicPermission.get(packageName);
        Boolean hasLocation = hasLocationPermission.get(packageName);
        
        holder.cameraIcon.setVisibility(hasCamera != null && hasCamera ? View.VISIBLE : View.GONE);
        holder.micIcon.setVisibility(hasMic != null && hasMic ? View.VISIBLE : View.GONE);
        holder.locationIcon.setVisibility(hasLocation != null && hasLocation ? View.VISIBLE : View.GONE);
        
        // Set settings button click listener
        holder.settingsButton.setOnClickListener(v -> {
            // Open app details settings
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", packageName, null);
            intent.setData(uri);
            context.startActivity(intent);
            
            // Notify the activity that settings might have changed
            if (listener != null) {
                listener.onPermissionSettingsChanged(packageName, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }
    
    /**
     * Remove an item at the specified position
     */
    public void removeItem(int position) {
        if (position >= 0 && position < appList.size()) {
            appList.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, appList.size());
        }
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon, cameraIcon, micIcon, locationIcon;
        TextView appName, appPackage;
        Button settingsButton;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.app_icon);
            cameraIcon = itemView.findViewById(R.id.camera_icon);
            micIcon = itemView.findViewById(R.id.mic_icon);
            locationIcon = itemView.findViewById(R.id.location_icon);
            appName = itemView.findViewById(R.id.app_name);
            appPackage = itemView.findViewById(R.id.app_package);
            settingsButton = itemView.findViewById(R.id.btn_settings);
        }
    }
} 