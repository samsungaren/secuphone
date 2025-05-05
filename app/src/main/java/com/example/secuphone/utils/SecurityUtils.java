package com.example.secuphone.utils;

import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Утилитарный класс для выполнения криптографических операций.
 * Используется для безопасного хранения и проверки паролей.
 */
public class SecurityUtils {
    
    private static final String TAG = "SecurityUtils";
    
    // Константы для хеширования паролей
    private static final String HASH_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int HASH_ITERATIONS = 65536; // Число итераций для PBKDF2
    private static final int KEY_LENGTH = 256; // Длина хеша в битах
    private static final int SALT_LENGTH = 32; // Длина соли в байтах
    
    private SecurityUtils() {
        // Приватный конструктор для предотвращения создания экземпляров
    }
    
    /**
     * Генерирует случайную соль для хеширования паролей.
     * @return строка с закодированной в Base64 солью
     */
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return Base64.encodeToString(salt, Base64.DEFAULT);
    }
    
    /**
     * Хеширует пароль с использованием PBKDF2WithHmacSHA256 и заданной соли.
     * @param password пароль для хеширования
     * @param saltStr строка с закодированной в Base64 солью
     * @return строка с закодированным в Base64 хешем пароля
     */
    public static String hashPassword(String password, String saltStr) {
        try {
            // Декодирование соли из Base64
            byte[] salt = Base64.decode(saltStr, Base64.DEFAULT);
            
            // Создание PBEKeySpec с паролем, солью, числом итераций и желаемой длиной ключа
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_ITERATIONS, KEY_LENGTH);
            
            // Получение SecretKeyFactory для PBKDF2WithHmacSHA256
            SecretKeyFactory factory = SecretKeyFactory.getInstance(HASH_ALGORITHM);
            
            // Генерация хеша
            byte[] hash = factory.generateSecret(spec).getEncoded();
            
            // Кодирование хеша в Base64 и возврат строки
            return Base64.encodeToString(hash, Base64.DEFAULT);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            Log.e(TAG, "Ошибка при хешировании пароля", e);
            // Если произошла ошибка, возвращаем пустую строку (ни один пароль не будет валидным)
            return "";
        }
    }
    
    /**
     * Проверяет пароль, сравнивая его хеш с сохраненным хешем.
     * @param password пароль для проверки
     * @param storedHash сохраненный хеш пароля
     * @param saltStr строка с закодированной в Base64 солью
     * @return true, если пароль совпадает с хешем, иначе false
     */
    public static boolean verifyPassword(String password, String storedHash, String saltStr) {
        String hashedPassword = hashPassword(password, saltStr);
        return storedHash.equals(hashedPassword);
    }
    
    /**
     * Вычисляет хеш SHA-256 для заданной строки.
     * Более простая альтернатива PBKDF2, когда соль не нужна.
     * @param input строка для хеширования
     * @return строка с хешем в шестнадцатеричном представлении
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // Преобразование массива байтов в шестнадцатеричную строку
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Ошибка при вычислении SHA-256", e);
            return "";
        }
    }
    
    /**
     * Генерирует случайную строку заданной длины.
     * Полезно для создания токенов, временных паролей и т.д.
     * @param length длина строки
     * @return случайная строка
     */
    public static String generateRandomString(int length) {
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return sb.toString();
    }
} 