package com.example.secuphone.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.secuphone.FindPhoneActivity;
import com.example.secuphone.R;

public class LoudSignalService extends Service {
    
    private static final String TAG = "LoudSignalService";
    private static final String CHANNEL_ID = "LoudSignalChannel";
    private static final int NOTIFICATION_ID = 3001;
    
    public static final String EXTRA_DURATION = "duration"; // in seconds
    public static final String EXTRA_VOLUME = "volume"; // 0-100
    public static final String ACTION_STOP_SIGNAL = "com.example.secuphone.STOP_SIGNAL";
    
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private int originalVolume;
    private CountDownTimer countDownTimer;
    private int durationSeconds = 30; // default
    private int volumeLevel = 100; // default is max
    
    private static boolean isRunning = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_STOP_SIGNAL.equals(intent.getAction())) {
                stopSignal();
                return START_NOT_STICKY;
            }
            
            // Get duration from intent, default is 30 seconds
            durationSeconds = intent.getIntExtra(EXTRA_DURATION, 30);
            // Get volume level from intent, default is 100 (max)
            volumeLevel = intent.getIntExtra(EXTRA_VOLUME, 100);
        }
        
        try {
            startForeground(NOTIFICATION_ID, createNotification(durationSeconds));
            startLoudSignal();
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand", e);
            stopSelf();
        }
        
        return START_NOT_STICKY;
    }
    
    private void startLoudSignal() {
        if (isRunning) {
            return; // Avoid starting multiple signals
        }
        
        try {
            isRunning = true;
            
            // Save original volume and set to max for both streams
            try {
                // Use STREAM_ALARM instead of MUSIC for louder sound
                originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
                int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0);
                
                // Also set RING volume to max
                audioManager.setStreamVolume(AudioManager.STREAM_RING, 
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_RING), 0);
            } catch (Exception e) {
                Log.e(TAG, "Error setting volume", e);
            }
            
            // Initialize MediaPlayer with a more attention-grabbing alarm sound
            try {
                // Try to use the system alarm sound first (more attention-grabbing than ringtone)
                Uri alarmSound = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI;
                mediaPlayer = MediaPlayer.create(this, alarmSound);
                
                if (mediaPlayer != null) {
                    mediaPlayer.setLooping(true);
                    mediaPlayer.setVolume(1.0f, 1.0f); // Max volume
                    mediaPlayer.start();
                } else {
                    // Fallback to ToneGenerator if MediaPlayer fails
                    fallbackToToneGenerator();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error with MediaPlayer, falling back to ToneGenerator", e);
                fallbackToToneGenerator();
            }
            
            // Start countdown for UI updates
            startCountdown(durationSeconds);
        } catch (Exception e) {
            Log.e(TAG, "Error starting loud signal", e);
            stopSelf();
        }
    }
    
    private void fallbackToToneGenerator() {
        try {
            final ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            
            // Play initial tone - use a more aggressive/attention-grabbing tone
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 3000);
            
            // Schedule repeating tones
            final Handler handler = new Handler(Looper.getMainLooper());
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    if (isRunning) {
                        // Alternate between different emergency tones for maximum attention
                        toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 800);
                        handler.postDelayed(() -> {
                            if (isRunning) {
                                toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800);
                                handler.postDelayed(this, 400);
                            }
                        }, 800);
                    } else {
                        toneGen.release();
                    }
                }
            };
            
            // Start repeating after initial tone
            handler.postDelayed(runnable, 3100);
        } catch (Exception e) {
            Log.e(TAG, "Error with ToneGenerator fallback", e);
        }
    }
    
    private void startCountdown(int seconds) {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        
        countDownTimer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                try {
                    int secondsLeft = (int) (millisUntilFinished / 1000);
                    updateNotification(secondsLeft);
                    
                    // Send broadcast to update UI
                    Intent tickIntent = new Intent("com.example.secuphone.SIGNAL_TICK");
                    tickIntent.putExtra("secondsLeft", secondsLeft);
                    sendBroadcast(tickIntent);
                } catch (Exception e) {
                    Log.e(TAG, "Error in countdown tick", e);
                }
            }
            
            @Override
            public void onFinish() {
                try {
                    stopSignal();
                } catch (Exception e) {
                    Log.e(TAG, "Error in countdown finish", e);
                    stopSelf();
                }
            }
        };
        
        countDownTimer.start();
    }
    
    private void stopSignal() {
        try {
            // Stop MediaPlayer if it's running
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.release();
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing media player", e);
                } finally {
                    mediaPlayer = null;
                }
            }
            
            // Restore original volume
            if (audioManager != null) {
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0);
                } catch (Exception e) {
                    Log.e(TAG, "Error restoring volume", e);
                }
            }
            
            // Cancel countdown timer
            if (countDownTimer != null) {
                try {
                    countDownTimer.cancel();
                } catch (Exception e) {
                    Log.e(TAG, "Error cancelling timer", e);
                } finally {
                    countDownTimer = null;
                }
            }
            
            isRunning = false;
            
            // Send broadcast that signal has stopped
            try {
                Intent stopIntent = new Intent("com.example.secuphone.SIGNAL_STOPPED");
                sendBroadcast(stopIntent);
            } catch (Exception e) {
                Log.e(TAG, "Error sending broadcast", e);
            }
            
            try {
                stopForeground(true);
                stopSelf();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping service", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Fatal error in stopSignal", e);
            stopSelf();
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Loud Signal Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Channel for loud signal notifications");
            
            // Disable notification sound so only the signal sound plays
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.enableLights(false);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private Notification createNotification(int secondsLeft) {
        Intent notificationIntent = new Intent(this, FindPhoneActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        
        // Create stop action
        Intent stopIntent = new Intent(this, LoudSignalService.class);
        stopIntent.setAction(ACTION_STOP_SIGNAL);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.signal_active))
                .setContentText(getString(R.string.seconds_left, secondsLeft))
                .setSmallIcon(R.drawable.ic_volume)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_close, getString(R.string.stop_signal), stopPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build();
    }
    
    private void updateNotification(int secondsLeft) {
        NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, createNotification(secondsLeft));
    }
    
    @Override
    public void onDestroy() {
        stopSignal();
        super.onDestroy();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    public static boolean isSignalRunning() {
        return isRunning;
    }
} 