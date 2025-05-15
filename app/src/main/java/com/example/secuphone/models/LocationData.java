package com.example.secuphone.models;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Data model class for storing and retrieving location data from Firebase
 */
@IgnoreExtraProperties
public class LocationData {
    private double latitude;
    private double longitude;
    private long timestamp;
    private String deviceId;
    private double accuracy;
    private double altitude;
    private float speed;
    private boolean isLost;
    private String batteryLevel;

    // Required empty constructor for Firebase
    public LocationData() {
    }

    public LocationData(double latitude, double longitude, long timestamp, String deviceId) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.deviceId = deviceId;
        this.isLost = false;
    }

    public LocationData(double latitude, double longitude, long timestamp, String deviceId,
                       double accuracy, double altitude, float speed, String batteryLevel) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.deviceId = deviceId;
        this.accuracy = accuracy;
        this.altitude = altitude;
        this.speed = speed;
        this.batteryLevel = batteryLevel;
        this.isLost = false;
    }

    // Convert to Map for Firebase
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("latitude", latitude);
        result.put("longitude", longitude);
        result.put("timestamp", timestamp);
        result.put("deviceId", deviceId);
        result.put("accuracy", accuracy);
        result.put("altitude", altitude);
        result.put("speed", speed);
        result.put("isLost", isLost);
        result.put("batteryLevel", batteryLevel);
        return result;
    }

    // Getters and setters
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

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public boolean isLost() {
        return isLost;
    }

    public void setLost(boolean lost) {
        isLost = lost;
    }

    public String getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(String batteryLevel) {
        this.batteryLevel = batteryLevel;
    }
} 