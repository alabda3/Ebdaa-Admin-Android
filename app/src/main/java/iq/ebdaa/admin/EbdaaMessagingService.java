package iq.ebdaa.admin;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public final class EbdaaMessagingService extends FirebaseMessagingService {
    public static final String CHANNEL_ID = "orders";
    public static final String PREFS = "ebdaa_push";
    public static final String TOKEN_KEY = "fcm_token";

    @Override public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(TOKEN_KEY, token).apply();
    }

    // FCM with notification payload shows a notification automatically when backgrounded.
    // When in the foreground, show it ourselves.
    @Override public void onMessageReceived(@NonNull RemoteMessage message) {
        String title = "طلب جديد من متجر الإبداع";
        String body = "وصل طلب جديد — اضغط لفتح لوحة التحكم";
        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) title = message.getNotification().getTitle();
            if (message.getNotification().getBody() != null) body = message.getNotification().getBody();
        }
        showOrderNotification(title, body);
    }

    private void showOrderNotification(String title, String body) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "طلبات المتجر", NotificationManager.IMPORTANCE_HIGH);
            c.setDescription("تنبيهات وصول طلبات جديدة");
            nm.createNotificationChannel(c);
        }
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_ebdaa)
         .setContentTitle(title).setContentText(body).setAutoCancel(true)
         .setContentIntent(pi).setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
        nm.notify((int)(System.currentTimeMillis() % Integer.MAX_VALUE), b.build());
    }
}
