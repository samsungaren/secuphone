package com.example.secuphone.adapters;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.secuphone.R;
import com.example.secuphone.models.DeviceInfo;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {
    
    private final Context context;
    private final List<DeviceInfo> devices;
    private final OnDeviceClickListener listener;
    
    public DeviceAdapter(Context context, OnDeviceClickListener listener) {
        this.context = context;
        this.devices = new ArrayList<>();
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_device, parent, false);
        return new DeviceViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        DeviceInfo device = devices.get(position);
        
        // Set device name
        holder.deviceName.setText(device.getDisplayName());
        
        // Set device status
        if (device.isOnline()) {
            holder.deviceStatus.setText(R.string.device_online);
            holder.deviceStatus.setTextColor(ContextCompat.getColor(context, R.color.green));
            holder.statusIcon.setImageResource(R.drawable.ic_online);
        } else {
            holder.deviceStatus.setText(R.string.device_offline);
            holder.deviceStatus.setTextColor(ContextCompat.getColor(context, R.color.red));
            holder.statusIcon.setImageResource(R.drawable.ic_offline);
        }
        
        // Set last seen
        if (device.getLastSeen() > 0) {
            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                    device.getLastSeen(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS);
            holder.lastSeen.setText(context.getString(R.string.last_seen, timeAgo));
            holder.lastSeen.setVisibility(View.VISIBLE);
        } else {
            holder.lastSeen.setVisibility(View.GONE);
        }
        
        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeviceClick(device);
            }
        });
        
        // Long click for more options
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onDeviceLongClick(device, v);
                return true;
            }
            return false;
        });
    }
    
    @Override
    public int getItemCount() {
        return devices.size();
    }
    
    /**
     * Update the device list
     */
    public void updateDevices(List<DeviceInfo> newDevices) {
        this.devices.clear();
        this.devices.addAll(newDevices);
        notifyDataSetChanged();
    }
    
    /**
     * Add a new device to the list
     */
    public void addDevice(DeviceInfo device) {
        if (!this.devices.contains(device)) {
            this.devices.add(device);
            notifyItemInserted(this.devices.size() - 1);
        }
    }
    
    /**
     * Remove a device from the list
     */
    public void removeDevice(String deviceId) {
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getDeviceId().equals(deviceId)) {
                devices.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }
    
    /**
     * Update a device in the list
     */
    public void updateDevice(DeviceInfo device) {
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getDeviceId().equals(device.getDeviceId())) {
                devices.set(i, device);
                notifyItemChanged(i);
                break;
            }
        }
    }
    
    /**
     * ViewHolder for device items
     */
    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        final TextView deviceName;
        final TextView deviceStatus;
        final TextView lastSeen;
        final ImageView statusIcon;
        
        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.device_name);
            deviceStatus = itemView.findViewById(R.id.device_status);
            lastSeen = itemView.findViewById(R.id.last_seen);
            statusIcon = itemView.findViewById(R.id.status_icon);
        }
    }
    
    /**
     * Interface for device click callbacks
     */
    public interface OnDeviceClickListener {
        void onDeviceClick(DeviceInfo device);
        void onDeviceLongClick(DeviceInfo device, View view);
    }
} 