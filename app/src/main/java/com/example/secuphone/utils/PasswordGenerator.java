package com.example.secuphone.utils;

import java.security.SecureRandom;

/**
 * Utility class for generating secure random passwords
 */
public class PasswordGenerator {
    private static final String LOWER_CASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER_CASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL_CHARS = "!@#$%^&*()_-+=<>?/[]{}|";
    
    private final SecureRandom random;
    private boolean useLowerCase;
    private boolean useUpperCase;
    private boolean useDigits;
    private boolean useSpecialChars;
    private int length;
    
    public PasswordGenerator() {
        this.random = new SecureRandom();
        this.useLowerCase = true;
        this.useUpperCase = true;
        this.useDigits = true;
        this.useSpecialChars = true;
        this.length = 16;
    }
    
    public PasswordGenerator useLowerCase(boolean useLowerCase) {
        this.useLowerCase = useLowerCase;
        return this;
    }
    
    public PasswordGenerator useUpperCase(boolean useUpperCase) {
        this.useUpperCase = useUpperCase;
        return this;
    }
    
    public PasswordGenerator useDigits(boolean useDigits) {
        this.useDigits = useDigits;
        return this;
    }
    
    public PasswordGenerator useSpecialChars(boolean useSpecialChars) {
        this.useSpecialChars = useSpecialChars;
        return this;
    }
    
    public PasswordGenerator length(int length) {
        this.length = length;
        return this;
    }
    
    public String generate() {
        // Build the character pool based on selected options
        StringBuilder charPool = new StringBuilder();
        
        if (useLowerCase) {
            charPool.append(LOWER_CASE);
        }
        
        if (useUpperCase) {
            charPool.append(UPPER_CASE);
        }
        
        if (useDigits) {
            charPool.append(DIGITS);
        }
        
        if (useSpecialChars) {
            charPool.append(SPECIAL_CHARS);
        }
        
        // If no character types are selected, use lowercase as default
        if (charPool.length() == 0) {
            charPool.append(LOWER_CASE);
        }
        
        // Generate the password
        StringBuilder password = new StringBuilder(length);
        String pool = charPool.toString();
        
        // Ensure at least one character from each selected type
        if (useLowerCase) {
            password.append(LOWER_CASE.charAt(random.nextInt(LOWER_CASE.length())));
        }
        
        if (useUpperCase) {
            password.append(UPPER_CASE.charAt(random.nextInt(UPPER_CASE.length())));
        }
        
        if (useDigits) {
            password.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        }
        
        if (useSpecialChars) {
            password.append(SPECIAL_CHARS.charAt(random.nextInt(SPECIAL_CHARS.length())));
        }
        
        // Fill the rest of the password with random characters
        for (int i = password.length(); i < length; i++) {
            password.append(pool.charAt(random.nextInt(pool.length())));
        }
        
        // Shuffle the password to avoid predictable patterns
        char[] passwordArray = password.toString().toCharArray();
        for (int i = 0; i < passwordArray.length; i++) {
            int randomIndex = random.nextInt(passwordArray.length);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[randomIndex];
            passwordArray[randomIndex] = temp;
        }
        
        return new String(passwordArray);
    }
} 