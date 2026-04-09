package com.kardos.tvads.boot;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.kardos.tvads.MainActivity;
import com.kardos.tvads.R;

public class DreamListenerService extends Service {
    private static final String TAG = DreamListenerService.class.getSimpleName();

    private static final int ONGOING_NOTIFICATION_ID = 1;

    private final BroadcastReceiver dreamHandler = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received service event: " + intent.getAction());
            BootReceiver.processEvent(context, intent);
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            String channelId = getString(R.string.app_name);
            NotificationChannel notificationChannel = new NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setDescription(channelId);
            notificationChannel.setSound(null, null);
            notificationManager.createNotificationChannel(notificationChannel);

            notification = new Notification.Builder(this, channelId)
                    .setContentTitle(getText(R.string.app_name))
                    .setContentText(getText(R.string.notification_text))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.banner))
                    .setContentIntent(pendingIntent)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setPriority(Notification.PRIORITY_MIN)
                    .build();
        } else {
            notification = new Notification.Builder(this)
                    .setContentTitle(getText(R.string.app_name))
                    .setContentText(getText(R.string.notification_text))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.banner))
                    .setContentIntent(pendingIntent)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setPriority(Notification.PRIORITY_MIN)
                    .build();
        }

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ONGOING_NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, notification);
        }

        Log.d(TAG, "Deploy notification");

        IntentFilter filter = new IntentFilter(Intent.ACTION_DREAMING_STOPPED);
        registerReceiver(dreamHandler, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(dreamHandler);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
}
