package com.example.secuphone.models;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Data model class for storing device information from Firebase
 */
@IgnoreExtraProperties
public class DeviceInfo {
    private String deviceId;
    private String model;
    private String manufacturer;
    private String name;
    private long lastSeen;
    private boolean online;
    private double latitude;
    private double longitude;
    private String deviceName;
    private String batteryLevel;

    // Required empty constructor for Firebase
    public DeviceInfo() {
    }

    public DeviceInfo(String deviceId, String model, String manufacturer, String name, long lastSeen, boolean online) {
        this.deviceId = deviceId;
        this.model = model;
        this.manufacturer = manufacturer;
        this.name = name;
        this.lastSeen = lastSeen;
        this.online = online;
    }

    // Convert to Map for Firebase
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);
        result.put("model", model);
        result.put("manufacturer", manufacturer);
        result.put("name", name);
        result.put("last_seen", lastSeen);
        result.put("online", online);
        return result;
    }

    // Getters and setters
    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(String batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    /**
     * Get a display name for the device
     */
    public String getDisplayName() {
        if (deviceName != null && !deviceName.isEmpty()) {
            return deviceName;
        } else if (name != null && !name.isEmpty()) {
            return name;
        } else {
            return model != null ? model : "Unknown Device";
        }
    }
} 