package com.example.bluetoothtest;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.print.PrinterId;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class DeviceControlActivity extends AppCompatActivity {

    private final static String TAG = "DeviceControlActivity";

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    public static BluetoothGatt mBluetoothGatt;
    private BluetoothService mBluetoothService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<>();

    private boolean mConnected;
    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";
    StringBuffer sb = new StringBuffer();
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBluetoothService = ((BluetoothService.LocalBinder) service).getService();
            if (!mBluetoothService.initialize()) {
                Log.d("xubin", " Unable to initialize Bluetooth");
                finish();
            }
            mBluetoothService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBluetoothService = null;
        }
    };

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                displayGattServices(mBluetoothService.getSupportedGattServices());
            } else if (BluetoothService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothService.EXTRA_DATA));
            }
        }
    };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_control);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = findViewById(R.id.connection_state);
        mDataField = findViewById(R.id.data_value);

        Objects.requireNonNull(getActionBar()).setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        Intent serviceIntent = new Intent(this, BluetoothService.class);
        boolean bll = bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE);
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String s = BodyCHOLRead(sb.toString());
                Log.d("xubin", "Onclick ==" + s);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(broadcastReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothService != null) {
            final boolean result = mBluetoothService.connect(mDeviceAddress);
            Log.d("xubin", " result==" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(broadcastReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothService.disconnect();
                return true;
            case R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        sb.append(data);
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) {
            return;
        }
        String uuid = null;

        for (BluetoothGattService bluetoothService : gattServices) {
            uuid = bluetoothService.getUuid().toString();
            List<BluetoothGattCharacteristic> bluetoothGattCharacteristics = bluetoothService.getCharacteristics();
            for (BluetoothGattCharacteristic bluetoothGattCharacteristic : bluetoothGattCharacteristics) {
                uuid = bluetoothGattCharacteristic.getUuid().toString();
                if (uuid.contains("fff4")) {
                    mBluetoothService.setCharacteristicNotification(bluetoothGattCharacteristic, true);
                    mBluetoothService.readCharacteristic(bluetoothGattCharacteristic);
                }
            }
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothService.EXTRA_DATA);
        return intentFilter;
    }

    public static String print10(String str) {
        StringBuffer buffer = new StringBuffer();
        String[] array = str.split("");
        for (int i = 0; i < array.length; i++) {
            int num = Integer.parseInt(array[i], 16);
            buffer.append(num);
        }
        return buffer.toString();
    }

    public static String byte2HexStr(byte[] b) {

        String stmp = "";
        StringBuilder sb = new StringBuilder("");
        for (int n = 0; n < b.length; n++) {
            stmp = Integer.toHexString(b[n] & 0xFF);
            sb.append((stmp.length() == 1) ? "0" + stmp : stmp);
            sb.append(" ");
        }
        return sb.toString().toUpperCase().trim();
    }


    /**
     * 分析胆固醇数据
     *
     * @param data
     * @return
     */
    public static String BodyCHOLRead(String data) {
        // 根据换行符分割
        String[] datas = data.split(print10("0A"));
        for (int i = 0; i < datas.length; i++) {
            Log.d(TAG, String.format("split[%s]:%s", i, datas[i]));
        }
        String unit = "";
        String data7 = datas[7].split("\"")[1].split(":")[1].trim();
        if (data7.contains("mmol/L")) {
            unit = "mmol/L";
        }

        StringBuilder sbr = new StringBuilder();
        for (int i = 7, j = 0; i < 11; i++, j++) {
            String values = datas[i].split("\"")[1].split(":")[1].trim();//207 mg/dL
            String[] results = values.split(" +");
            System.out.println("值~~~~~" + values + "分割长度:" + results.length);
            String value = "----";

            if (results.length == 3) {
                sbr.append(results[0]);
                value = results[1];
            } else if (results.length == 2) {
                value = results[0];
            }

            if ("----".equals(value)) {
                sbr.append(value).append(",");
            } else if ("mmol/L".equals(unit)) {
                sbr.append(value).append(",");
            } else {
                sbr.append(value).append(",");
            }
        }
        Log.d(TAG, "血脂4项测量结果:" + sbr);
        return sbr.substring(0, sbr.length() - 1);
    }

    private static String unitConversion(String input, int type) {
        double value = Double.parseDouble(input);
        NumberFormat df = NumberFormat.getNumberInstance();
        df.setMaximumFractionDigits(2);
        //*胆固醇、高密度脂蛋白、低密度脂蛋白的换算都一样：1mmol/L=38.7mg/dL；
        //*甘油三脂是1mmol/L=88.6mg/dL

        if (type == 0) {
            return df.format(value / 38.7);
        }
        if (type == 1) {
            return df.format(value / 88.6);
        }
        if (type == 2) {
            return df.format(value / 38.7);
        }
        if (type == 3) {
            return df.format(value / 38.7);
        }
        return null;
    }
}
