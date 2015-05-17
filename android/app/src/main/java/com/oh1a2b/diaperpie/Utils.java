package com.oh1a2b.diaperpie;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class Utils {
    private static String TAG = "Utils";

    public static final int POSE_FACEUP = 0;
    public static final int POSE_FACEDOWN = 1;
    public static final int POSE_STANDING = 2;
    public static final int POSE_UPSIZEDOWN = 3;
    public static final int POSE_SIDERIGHT = 4;
    public static final int POSE_SIDELEFT = 5;

    public static final int TEMPERATURE_THRESHOLD = 38;

    public static final int NOTIFICATION_ID_WARNING_POSE = 100;
    public static final int NOTIFICATION_ID_WARNING_WET = 101;
    public static final int NOTIFICATION_ID_WARNING_TEMPERATURE = 102;

    public static final int BT_REQUEST_ENABLE = 1001;
    public static final int BT_SELECT_DEVICE = 1002;

    public static final String EXTRA_KEY_DEVICE_NAME = "device_name";
    public static final String EXTRA_KEY_DEVICE_ADDRESS = "device_address";
    public static final String EXTRA_KEY_RECONNECT = "reconnect";
    public static final String EXTRA_KEY_RAW_DATA = "raw_data";

    public static class SensorData {
        public int pose;
        public int wet;
        public float temperature;
        public SensorData(int p, int w, float t) {
            pose = p;
            wet = w;
            temperature = t;
        }
    }

    // command ex: "0,0,37", "1,1,40"
    static public SensorData parseRawSensorData(String cmd) {
        String[] cmds = cmd.split(",");
        if (cmds.length != 3) {
            Log.d(TAG, "Unknown command: " + cmd);
            return null;
        }
        try {
            int pose = Integer.parseInt(cmds[0]);
            int wet = Integer.parseInt(cmds[1]);
            float temperature = Float.parseFloat(cmds[2]);
            return new SensorData(pose, wet, temperature);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed to parse command: " + cmd + ", error: " + e);
        }
        return null;
    }

    static final void pushNotification(Context context, int notId, int titleId, int msgId) {
        pushNotification(context, notId, context.getString(titleId), context.getString(msgId));
    }

    static final void pushNotification(Context context, int notId, String title, String msg) {
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent notificationPendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(context);
        builder.setSmallIcon(R.mipmap.ic_launcher)
                .setDefaults(Notification.DEFAULT_ALL)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(msg)
                .setContentIntent(notificationPendingIntent);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notId, builder.build());
    }

    static final void notifyWarnings(Context context, Utils.SensorData sensorData) {
        if (sensorData == null) {
            return;
        }
        if (sensorData.pose == Utils.POSE_FACEDOWN) {
            Utils.pushNotification(context, Utils.NOTIFICATION_ID_WARNING_POSE, R.string.warning, R.string.notify_facedown);
        }
        if (sensorData.wet > 0) {
            Utils.pushNotification(context, Utils.NOTIFICATION_ID_WARNING_WET, R.string.warning, R.string.notify_wet);
        }
        if (sensorData.temperature > Utils.TEMPERATURE_THRESHOLD) {
            Utils.pushNotification(context, Utils.NOTIFICATION_ID_WARNING_TEMPERATURE, R.string.warning, R.string.notify_hot);
        }
    }
}
