package com.openxc.hardware.hud;

import java.io.IOException;

import java.util.Set;
import java.util.UUID;

import com.openxc.hardware.hud.BluetoothException;

import android.app.Activity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;

import android.util.Log;

public class DeviceManager {
    private final static String TAG = "DeviceManager";
    private final static int REQUEST_ENABLE_BT = 42;
    private final static UUID RFCOMM_UUID = new UUID(0x00, 0x03);
    private Activity mActivity;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mSocket;
    private BluetoothDevice mTargetDevice;
    private BroadcastReceiver mReceiver;

    public DeviceManager(Activity activity) throws BluetoothException {
        mActivity = activity;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null) {
            throw new BluetoothException();
        }

        if(!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetoothIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mActivity.startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT);
        }
    }

    public void discoverDevices(final String targetAddress) {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for(BluetoothDevice device : pairedDevices) {
            if(deviceDiscovered(device, targetAddress)) {
                captureDevice(device);
            }
        }

        mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if(BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                    BluetoothDevice device = intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE);
                    if(deviceDiscovered(device, targetAddress)) {
                        captureDevice(device);
                    }
                }
            }
        };

        if(mTargetDevice == null) {
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            mActivity.registerReceiver(mReceiver, filter);
        }
    }

    public BluetoothSocket setupSocket() throws BluetoothException {
        if(mTargetDevice == null) {
            Log.w(TAG, "Can't setup socket -- device is " + mTargetDevice);
            throw new BluetoothException();
        }

        Log.d(TAG, "Scanning services on " + mTargetDevice);
        try {
            mSocket = mTargetDevice.createRfcommSocketToServiceRecord(RFCOMM_UUID);
        } catch(IOException e) {}


        try {
            mSocket.connect();
            return mSocket;
        } catch(IOException e) {
            Log.e(TAG, "Could not find required service on " + mTargetDevice);
            try {
                mSocket.close();
            } catch(IOException e2) {}
            throw new BluetoothException();
        }
    }

    public void waitForDevice() {
        synchronized(mTargetDevice) {
            while(mTargetDevice == null) {
                try {
                    mTargetDevice.wait();
                } catch(InterruptedException e) {}
            }
        }
    }

    private void captureDevice(BluetoothDevice device) {
        mTargetDevice = device;
        mActivity.unregisterReceiver(mReceiver);
        mBluetoothAdapter.cancelDiscovery();
    }

    private boolean deviceDiscovered(BluetoothDevice device,
            String targetAddress) {
        Log.d(TAG, "Found Bluetooth device: " + device);
        if(device.getAddress() == targetAddress) {
            Log.d(TAG, "Found matching device: " + device);
            return true;
        }
        return false;
    }
}
