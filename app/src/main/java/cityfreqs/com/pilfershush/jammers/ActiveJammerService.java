package cityfreqs.com.pilfershush.jammers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import cityfreqs.com.pilfershush.MainActivity;
import cityfreqs.com.pilfershush.R;
import cityfreqs.com.pilfershush.assist.AudioSettings;

public class ActiveJammerService extends Service {
    public static final String ACTION_START_ACTIVE = "cityfreqs.com.pilfershushjammer.action.START_ACTIVE";
    public static final String ACTION_STOP_ACTIVE = "cityfreqs.com.pilfershushjammer.action.STOP_ACTIVE";

    private static final String CHANNEL_ID = "PilferShush";
    private static final String CHANNEL_NAME = "Active Jammer";

    private ActiveJammer activeJammer;
    private Bundle audioBundle;

    private NotificationCompat.Builder notifyActiveBuilder;

    public ActiveJammerService() {
        //default, for the manifest
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // called when app removed from running apps list
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        audioBundle = new Bundle();
        if (intent != null) {
            audioBundle = intent.getExtras();
            String action = intent.getAction();
            if (action != null) {
                if (action.equals(ACTION_START_ACTIVE)) {
                    createNotification();
                    startActiveService();
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.service_state_2),
                            Toast.LENGTH_SHORT).show();
                } else if (action.equals(ACTION_STOP_ACTIVE)) {
                    stopActiveService();
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.service_state_3),
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        //
    }

    private void createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent,0);

        NotificationManager notifyManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(getResources().getString(R.string.service_state_1));
            notifyManager.createNotificationChannel(channel);
        }

        notifyActiveBuilder = new NotificationCompat.Builder(this, CHANNEL_ID);

        notifyActiveBuilder.setSmallIcon(R.mipmap.ic_stat_logo_notify_jammer)
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(getResources().getString(R.string.app_status_11))
                .setContentText(getResources().getString(R.string.app_status_12))
                .setContentIntent(pendingIntent)
                .setWhen(System.currentTimeMillis())
                .setPriority(Notification.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false);

    }

    private void startActiveService() {
        activeJammer = new ActiveJammer(getApplicationContext(), audioBundle);
        activeJammer.play(audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[7]) ? 1 : 0);
        //
        Notification notification = notifyActiveBuilder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        startForeground(1, notification);
    }

    private void stopActiveService() {
        if (activeJammer != null) {
            activeJammer.stop();
        }
        //
        stopForeground(true);
        stopSelf();
    }
}

