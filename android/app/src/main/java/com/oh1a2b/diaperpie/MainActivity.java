package com.oh1a2b.diaperpie;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.UUID;


public class MainActivity extends Activity {
    private final String TAG = "MainActivity";

    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private static final String SENDER_ID = "351837143960";

    private static final String PREF_BLE = "ble";
    private static final int MAX_TEMPERRATURE_COUNT = 10;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_SELECT_DEVICE = 2;

    public static final String EXTRA_KEY_DEVICE_NAME = "device_name";
    public static final String EXTRA_KEY_DEVICE_ADDRESS = "device_address";

    private static final String BLE_ALERT_SERVICE_UUID = "00001811-0000-1000-8000-00805f9b34fb";
    private static final String BLE_ALERT_CHARACTERISTIC = "00002a46-0000-1000-8000-00805f9b34fb";

    private static final int DIAPER_WET_COLOR = Color.parseColor("#FFEB3B");

    private static final int RECORD_TEMPERATURE_INTERVAL = 60 * 1000; // 1min

    private Context mContext;
    private Handler mHandler = new Handler();
    private TextView mTemperatureTextView;
    private ImageView mBabyImageView;
    private TextView mConnectionStatusTextView;
    private LinearLayout mChartLinearLayout;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeService mBluetoothLeService;
    private ProgressDialog mWaitingDialog;
    private String mDeviceName;
    private String mDeviceAddress;
    private boolean mServiceConnected = false;
    private boolean mConnected = false;
    private LineChart mChart;
    private GoogleCloudMessaging mGcm;
    private String mGcmRegId;
    private long mPrevRecordTime = 0;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                Toast.makeText(mContext, R.string.msg_unable_connect_ble, Toast.LENGTH_SHORT).show();
                return;
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "Get gatt action: " + action);
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(String.format(mContext.getString(R.string.msg_ble_connected), mDeviceName));
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                mServiceConnected = false;
                if (mServiceConnection != null) {
                    unbindService(mServiceConnection);
                    mBluetoothLeService = null;
                }
                updateConnectionState(mContext.getString(R.string.msg_ble_disconnected));
                invalidateOptionsMenu();
                dismissWaitingDialog();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                for (BluetoothGattService service : mBluetoothLeService.getSupportedGattServices()) {
                    Log.d(TAG, "Find gatt service: " + service.getUuid());
                    if (service.getUuid().compareTo(UUID.fromString(BLE_ALERT_SERVICE_UUID)) == 0) {
                        registerService(service);
                    }
                }
                dismissWaitingDialog();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.d(TAG, "BLE receiving data: " + intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                handleCommand(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;
        mTemperatureTextView = (TextView) findViewById(R.id.temperature_textview);
        mBabyImageView = (ImageView) findViewById(R.id.baby_imageview);
        mConnectionStatusTextView = (TextView) findViewById(R.id.connection_status_textview);
        mChartLinearLayout = (LinearLayout) findViewById(R.id.chart_linearlayout);

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mChart = new LineChart(mContext);
        //mChart.setTouchEnabled(false);
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
        mChart.setPinchZoom(false);
        mChart.setBackgroundColor(Color.TRANSPARENT);
        mChart.setDrawGridBackground(false);
        mChart.setHighlightEnabled(true);
        mChart.setDescription("");
        mChart.setDescriptionColor(Color.BLACK);
        mChart.setNoDataTextDescription("No any temperature data");

        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);

        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setStartAtZero(false);
        leftAxis.setAxisMaxValue(42.0f);
        leftAxis.setAxisMinValue(32.0f);

        XAxis xAxis = mChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setAvoidFirstLastClipping(true);

        Legend l = mChart.getLegend();
        l.setForm(Legend.LegendForm.LINE);
        l.setTextColor(Color.WHITE);

        LineData data = new LineData();
        data.addDataSet(createSet());
        mChart.setData(data);
        mChartLinearLayout.addView(mChart);

        // connect to paired device if exist
        mDeviceName = mContext.getSharedPreferences(PREF_BLE, 0).getString(EXTRA_KEY_DEVICE_NAME, null);
        mDeviceAddress = mContext.getSharedPreferences(PREF_BLE, 0).getString(EXTRA_KEY_DEVICE_ADDRESS, null);
        if (mDeviceName != null && mDeviceAddress != null) {
            connectToBleService();
        }

        // gcm
        if (checkPlayServices()) {
            mGcm = GoogleCloudMessaging.getInstance(this);
            mGcmRegId = getRegistrationId(mContext);
            Log.d(TAG, "Device gcm id: " + mGcmRegId);
            if (mGcmRegId.isEmpty()) {
                registerInBackground();
            }
        } else {
            Log.i(TAG, "No valid Google Play Services APK found.");
        }

        // FIXME: for test only
        mBabyImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int direction = (int) (Math.random() * 10) % 6;
                int wet = (int) (Math.random() * 10) % 2;
                Log.d(TAG, "Direction: " + direction + ", wet: " + wet);
                setBabyImage(direction, wet);
                appendSensorData(getRandom(2, 35), 0);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mServiceConnected && mBluetoothLeService != null) {
            unbindService(mServiceConnection);
            mBluetoothLeService = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        } else if (requestCode == REQUEST_SELECT_DEVICE) {
            if (data != null) {
                mDeviceName = data.getStringExtra(EXTRA_KEY_DEVICE_NAME);
                mDeviceAddress = data.getStringExtra(EXTRA_KEY_DEVICE_ADDRESS);
                Log.d(TAG, "Selected device: " + mDeviceName + ", address: " + mDeviceAddress);
                if (mDeviceName != null && mDeviceAddress != null) {
                    SharedPreferences.Editor editor = mContext.getSharedPreferences(PREF_BLE, 0).edit();
                    editor.putString(EXTRA_KEY_DEVICE_NAME, mDeviceName);
                    editor.putString(EXTRA_KEY_DEVICE_ADDRESS, mDeviceAddress);
                    editor.commit();
                    connectToBleService();
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_select_device:
                final Intent intent = new Intent(this, DeviceScanActivity.class);
                startActivityForResult(intent, REQUEST_SELECT_DEVICE);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private void showWaitingDialog(String msg) {
        if (mWaitingDialog != null) {
            mWaitingDialog.setMessage(msg);
            mWaitingDialog.show();
        } else {
            mWaitingDialog = new ProgressDialog(this);
            mWaitingDialog.setIndeterminate(true);
            mWaitingDialog.setMessage(msg);
            mWaitingDialog.show();
        }
    }

    private void dismissWaitingDialog() {
        if (mWaitingDialog != null) {
            mWaitingDialog.dismiss();
            mWaitingDialog = null;
        }
    }

    private void updateConnectionState(final String status_msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionStatusTextView.setText(status_msg);
            }
        });
    }

    private void connectToBleService() {
        mConnected = false;
        showWaitingDialog(String.format(mContext.getString(R.string.msg_ble_connecting), mDeviceName));
        if (!mServiceConnected) {
            final Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
            bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
            mServiceConnected = true;
        }
    }

    private void registerService(BluetoothGattService service) {
        for (BluetoothGattCharacteristic chara : service.getCharacteristics()) {
            Log.d(TAG, "BLE characteristic: " + chara.getUuid());
            if (chara.getUuid().compareTo(UUID.fromString(BLE_ALERT_CHARACTERISTIC)) == 0) {
                final int charaProp = chara.getProperties();
                //if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                //    Log.d(TAG, "Characteristic support read");
                //    mBluetoothLeService.readCharacteristic(chara);
                //}
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mBluetoothLeService.setCharacteristicNotification(chara, true);
                    Log.d(TAG, "Set notification for chara: " + chara.getUuid());
                }
            }
        }
    }

    private void handleCommand(String cmd) {
        //TODO: parse command
        updateConnectionState(cmd);
        Utils.SensorData sensorData = Utils.parseRawSensorData(cmd);
        if (sensorData != null) {
            updateSensorData(sensorData);
        }
    }

    private void updateSensorData(Utils.SensorData sensorData) {
        setBabyImage(sensorData.pose, sensorData.wet);
        appendSensorData(sensorData.temperature, sensorData.wet);
    }

    private void setBabyImage(int direction, int wet) {
        int resourceId;
        switch (direction) {
            case Utils.POSE_FACEUP:
                resourceId = R.drawable.baby_faceup;
                break;
            case Utils.POSE_FACEDOWN:
                resourceId = R.drawable.baby_facedown;
                break;
            case Utils.POSE_STANDING:
                resourceId = R.drawable.baby_standing;
                break;
            case Utils.POSE_UPSIZEDOWN:
                resourceId = R.drawable.baby_upsidedown;
                break;
            case Utils.POSE_SIDELEFT:
                resourceId = R.drawable.baby_sideleft;
                break;
            case Utils.POSE_SIDERIGHT:
                resourceId = R.drawable.baby_sideright;
                break;
            default:
                Log.d(TAG, "Could not set baby image, unknown direction: " + direction);
                return;
        }
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resourceId);
        if (wet > 0) {
            int [] allPixels = new int [bitmap.getHeight() * bitmap.getWidth()];
            bitmap.getPixels(allPixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
            for(int i = 0; i < bitmap.getHeight() * bitmap.getWidth(); i++){
                if (allPixels[i] == Color.WHITE) {
                    allPixels[i] = DIAPER_WET_COLOR;
                }
            }
            Bitmap newBitmap = Bitmap.createBitmap(allPixels, bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
            bitmap.recycle();
            bitmap = newBitmap;
        }
        mBabyImageView.setImageBitmap(bitmap);
    }

    private float getRandom(float range, float startsfrom) {
        return (float) (Math.random() * range) + startsfrom;
    }

    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, "Temperatures");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleSize(3f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }

    private void appendSensorData(float temp, int wet) {
        // update temperature
        mTemperatureTextView.setText(String.format("%.1f\u00B0C", temp));

        long now = System.currentTimeMillis();
        if ((now - mPrevRecordTime) < RECORD_TEMPERATURE_INTERVAL) {
            return;
        }
        mPrevRecordTime = now;
        // update history
        LineData data = mChart.getData();
        if (data != null) {
            LineDataSet set = data.getDataSetByIndex(0);
            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }
            Calendar c = Calendar.getInstance();
            SimpleDateFormat df = new SimpleDateFormat("HH:mm");
            String formattedDate = df.format(c.getTime());

            data.addXValue(formattedDate);
            data.addEntry(new Entry(temp, set.getEntryCount()), 0);
            mChart.setVisibleXRange(MAX_TEMPERRATURE_COUNT);
            // always try to move to latest entry
            mChart.moveViewToX(data.getXValCount());
            mChart.notifyDataSetChanged();
            mChart.invalidate();
        }
    }

    private void pushNotification(int notId, String title, String msg) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent notificationPendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(this);
        builder.setSmallIcon(R.mipmap.ic_launcher)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(msg)
                .setContentIntent(notificationPendingIntent);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notId, builder.build());
    }

    // GCM function

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }

    /**
     * Stores the registration ID and the app versionCode in the application's
     * {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId registration ID
     */
    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGcmPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }

    /**
     * Gets the current registration ID for application on GCM service, if there is one.
     * <p>
     * If result is empty, the app needs to register.
     *
     * @return registration ID, or empty string if there is no existing
     *         registration ID.
     */
    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGcmPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
            Log.i(TAG, "Registration not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed.");
            return "";
        }
        Log.d(TAG, "registration ID=" + registrationId);
        return registrationId;
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * <p>
     * Stores the registration ID and the app versionCode in the application's
     * shared preferences.
     */
    private void registerInBackground() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                try {
                    if (mGcm == null) {
                        mGcm = GoogleCloudMessaging.getInstance(mContext);
                    }
                    mGcmRegId = mGcm.register(SENDER_ID);
                    Log.d(TAG, "Device registered, registration ID=" + mGcmRegId);

                    // You should send the registration ID to your server over HTTP, so it
                    // can use GCM/HTTP or CCS to send messages to your app.
                    sendRegistrationIdToBackend();

                    // For this demo: we don't need to send it because the device will send
                    // upstream messages to a server that echo back the message using the
                    // 'from' address in the message.

                    // Persist the regID - no need to register again.
                    storeRegistrationId(mContext, mGcmRegId);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                }
                return msg;
            }

            @Override
            protected void onPostExecute(String msg) {
                Log.d(TAG, "GCM error: " + msg);
            }
        }.execute(null, null, null);
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGcmPreferences(Context context) {
        // This sample app persists the registration ID in shared preferences, but
        // how you store the regID in your app is up to you.
        return getSharedPreferences(MainActivity.class.getSimpleName(),
                Context.MODE_PRIVATE);
    }
    /**
     * Sends the registration ID to your server over HTTP, so it can use GCM/HTTP or CCS to send
     * messages to your app. Not needed for this demo since the device sends upstream messages
     * to a server that echoes back the message using the 'from' address in the message.
     */
    private void sendRegistrationIdToBackend() {
        // Your implementation here.
    }
}
