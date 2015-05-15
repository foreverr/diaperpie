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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.UUID;


public class MainActivity extends Activity {
    private final String TAG = "MainActivity";

    private static final String PREF_BLE = "ble";
    private static final int MAX_TEMPERRATURE_COUNT = 10;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_SELECT_DEVICE = 2;

    public static final String EXTRA_KEY_DEVICE_NAME = "device_name";
    public static final String EXTRA_KEY_DEVICE_ADDRESS = "device_address";

    private static final String BLE_ALERT_SERVICE_UUID = "00001811-0000-1000-8000-00805f9b34fb";
    private static final String BLE_ALERT_CHARACTERISTIC = "00002a46-0000-1000-8000-00805f9b34fb";

    private static final int DIRECTION_UPWARD = 1;
    private static final int DIRECTION_SIDELEFT = 2;
    private static final int DIRECTION_SIDERIGHT = 3;
    private static final int DIRECTION_PRONE = 4;

    private static final int DIAPER_WET_COLOR = Color.parseColor("#FFEB3B");

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

        // FIXME: for test only
        mBabyImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int direction = ((int) (Math.random() * 10) % 4) + 1;
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
    }

    private void setBabyImage(int direction, int wet) {
        int resourceId;
        switch (direction) {
            case DIRECTION_UPWARD:
                resourceId = R.drawable.baby_upward;
                break;
            case DIRECTION_SIDELEFT:
                resourceId = R.drawable.baby_side_left;
                break;
            case DIRECTION_SIDERIGHT:
                resourceId = R.drawable.baby_side_right;
                break;
            case DIRECTION_PRONE:
                resourceId = R.drawable.baby_prone;
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
}
