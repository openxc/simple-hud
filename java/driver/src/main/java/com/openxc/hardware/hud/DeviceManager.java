package com.openxc.hardware.hud;

import java.io.IOException;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import java.util.Set;
import java.util.UUID;

import com.openxc.hardware.hud.BluetoothException;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;

import android.util.Log;

/**
 * The DeviceManager collects the functions required to discover and open a
 * socket to the Bluetooth device.
 */
public class DeviceManager {
    private final static String TAG = "DeviceManager";
    private final static UUID RFCOMM_UUID = UUID.fromString(
            "00001101-0000-1000-8000-00805f9b34fb");

    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mSocket;
    private BluetoothDevice mTargetDevice;
    private final Lock mDeviceLock = new ReentrantLock();
    private final Condition mDeviceChangedCondition =
            mDeviceLock.newCondition();
    private BroadcastReceiver mReceiver;

    /**
     * The DeviceManager requires an Android Context in order to send the intent
     * to enable Bluetooth if it isn't already on.
     */
    public DeviceManager(Context context) throws BluetoothException {
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null) {
            throw new BluetoothException();
        }
    }

    /**
     * Open an RFCOMM socket to the connected Bluetooth device.
     *
     * The DeviceManager must already have a device connected, so
     * discoverDevices needs to be called.
     */
    public BluetoothSocket setupSocket() throws BluetoothException {
        if(mTargetDevice == null) {
            Log.w(TAG, "Can't setup socket -- device is " + mTargetDevice);
            throw new BluetoothException();
        }

        Log.d(TAG, "Scanning services on " + mTargetDevice);
        try {
            mSocket = mTargetDevice.createRfcommSocketToServiceRecord(
                    RFCOMM_UUID);
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

    /**
     * Discover and connect to the target device.
     *
     * This method is asynchronous - after a device is connected, the user
     * should call setupSocket() to get a socket connection.
     */
    public void connect(String targetAddress) {
        discoverDevices(targetAddress);
        mDeviceLock.lock();
        while(mTargetDevice == null) {
            try {
                mDeviceChangedCondition.await();
            } catch(InterruptedException e) {}
        }
        mDeviceLock.unlock();
    }

    private void captureDevice(BluetoothDevice device) {
        mDeviceLock.lock();
        mTargetDevice = device;
        mDeviceChangedCondition.signal();
        mDeviceLock.unlock();

        if(mReceiver != null) {
            mContext.unregisterReceiver(mReceiver);
            mBluetoothAdapter.cancelDiscovery();
        }
    }

    private boolean deviceDiscovered(BluetoothDevice device,
            String targetAddress) {
        Log.d(TAG, "Found Bluetooth device: " + device);
        if(device.getAddress().equals(targetAddress)) {
            Log.d(TAG, "Found matching device: " + device);
            return true;
        }
        return false;
    }

    /**
     * Check the list of previously paired devices and any discoverable devices
     * for one matching the target address. Once a matching device is found,
     * calls captureDevice to connect with it.
     */
    private void discoverDevices(final String targetAddress) {
        Log.d(TAG, "Starting device discovery");
        Set<BluetoothDevice> pairedDevices =
            mBluetoothAdapter.getBondedDevices();
        for(BluetoothDevice device : pairedDevices) {
            Log.d(TAG, "Found already paired device: " + device);
            if(deviceDiscovered(device, targetAddress)) {
                captureDevice(device);
                return;
            }
        }

        mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if(BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                    BluetoothDevice device = intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE);
                    if (device.getBondState() != BluetoothDevice.BOND_BONDED
                            && deviceDiscovered(device, targetAddress)) {
                        captureDevice(device);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        mContext.registerReceiver(mReceiver, filter);

        if(mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        mBluetoothAdapter.startDiscovery();
    }
}
