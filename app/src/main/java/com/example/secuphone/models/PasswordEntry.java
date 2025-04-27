package com.example.secuphone.models;

/**
 * Model class representing a password entry
 */
public class PasswordEntry {
    private String id;
    private String title;
    private String username;
    private String password;
    private long timestamp;

    public PasswordEntry() {
        // Required empty constructor for Firebase
        this.timestamp = System.currentTimeMillis();
    }

    public PasswordEntry(String title, String username, String password) {
        this.title = title;
        this.username = username;
        this.password = password;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
} 