package com.oh1a2b.diaperpie;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class BluetoothSPPService extends Service {
    private final String TAG = "BluetoothSPPService";

    public final static String ACTION_SPP_CONNECTED =
            "com.ob1a2b.diaperpie.BluetoothSPPService.ACTION_SPP_CONNECTED";
    public final static String ACTION_SPP_DISCONNECTED =
            "com.ob1a2b.diaperpie.BluetoothSPPService.ACTION_SPP_DISCONNECTED";
    public final static String ACTION_SPP_DATA_RECEIVED =
            "com.ob1a2b.diaperpie.BluetoothSPPService.ACTION_SPP_DATA_RECEIVED";

    private String mDeviceName;
    private String mDeviceAddress;

    public class LocalBinder extends Binder {
        BluetoothSPPService getService() {
            return BluetoothSPPService.this;
        }
    }

    public BluetoothSPPService() {
        IBluetoothServiceEventReceiver eventReceiver = new IBluetoothServiceEventReceiver() {
            @Override
            public void bluetoothEnabling() {
                Log.d(TAG, "bluetoothEnabling");
            }

            @Override
            public void bluetoothEnabled() {
                Log.d(TAG, "bluetoothEnabled");
            }

            @Override
            public void bluetoothDisabling() {
                Log.d(TAG, "bluetoothDisabling");
            }

            @Override
            public void bluetoothDisabled() {
                Log.d(TAG, "bluetoothDisabled");
            }

            @Override
            public void connectedTo(String name, String address) {
                mDeviceName = name;
                mDeviceAddress = address;
                broadcastConnectedEvent(mDeviceName, mDeviceAddress);
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        boolean reconnect = false;
                        Log.d(TAG, "bt is connected: " + BluetoothService.isConnected());
                        if (!BluetoothService.isConnected()) {
                            for (int i = 0; i < 10; i++) {
                                Log.d(TAG, "bt is connected: " + BluetoothService.isConnected());
                                if (BluetoothService.isConnected()) {
                                    break;
                                }
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                Log.d(TAG, "Check bt connection after 1 second");
                            }
                        }
                        if (BluetoothService.isConnected()) {
                            reconnect = true;
                            try {
                                InputStreamReader in = new InputStreamReader(BluetoothService.getInputSteam());
                                BufferedReader buf = new BufferedReader(in);
                                while (true) {
                                    BluetoothService.sendToTarget("req");
                                    String data = buf.readLine();
                                    Log.d(TAG, "Bluetooth spp receive data: " + data);
                                    broadcastDataReceivedEvent(data);
                                    handleSensorData(data);
                                    Thread.sleep(1000);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        broadcastDisconnectedEvent(reconnect);
                    }
                };
                Thread th = new Thread(runnable);
                th.start();
            }
        };
        BluetoothService.initialize(this, eventReceiver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mDeviceAddress = intent.getStringExtra(Utils.EXTRA_KEY_DEVICE_ADDRESS);
        Log.d(TAG, "Connect to device address: " + mDeviceAddress);
        if (BluetoothService.isConnected()) {
            BluetoothService.disconnect();
        }
        BluetoothService.connectToDevice(mDeviceAddress);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (BluetoothService.isConnected()) {
            BluetoothService.disconnect();
        }
        broadcastDisconnectedEvent(false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    private void broadcastConnectedEvent(String deviceName, String deviceAddress) {
        final Intent intent = new Intent(ACTION_SPP_CONNECTED);
        intent.putExtra(Utils.EXTRA_KEY_DEVICE_NAME, deviceName);
        intent.putExtra(Utils.EXTRA_KEY_DEVICE_ADDRESS, deviceAddress);
        sendBroadcast(intent);
    }

    private void broadcastDisconnectedEvent(boolean reconnect) {
        final Intent intent = new Intent(ACTION_SPP_DISCONNECTED);
        intent.putExtra(Utils.EXTRA_KEY_RECONNECT, reconnect);
        sendBroadcast(intent);
    }

    private void broadcastDataReceivedEvent(String data) {
        final Intent intent = new Intent(ACTION_SPP_DATA_RECEIVED);
        intent.putExtra(Utils.EXTRA_KEY_RAW_DATA, data);
        sendBroadcast(intent);
    }

    private void handleSensorData(String raw) {
        Utils.SensorData sensorData = Utils.parseRawSensorData(raw);
        if (sensorData == null) {
            return;
        }
        Utils.notifyWarnings(this, sensorData);
    }
}
