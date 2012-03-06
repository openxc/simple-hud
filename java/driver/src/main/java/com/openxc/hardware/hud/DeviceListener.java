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

public class DeviceListener {
    private final static String TAG = "DeviceListener";
    private final static int REQUEST_ENABLE_BT = 42;
    private final static UUID RFCOMM_UUID = new UUID(0x00, 0x03);
	private String mTargetMac;
    private Activity mActivity;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mSocket;
    private BluetoothDevice mTargetDevice;
    private BroadcastReceiver mReceiver;

	public DeviceListener(Activity activity, String targetMac)
            throws BluetoothException {
        mTargetMac = targetMac;
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

	public void discoverDevices(String search){
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for(BluetoothDevice device : pairedDevices) {
            deviceDiscovered(device);
        }

        mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if(BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                    BluetoothDevice device = intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE);
                    deviceDiscovered(device);
                }
            }
        };

        if(mTargetDevice == null) {
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            mActivity.registerReceiver(mReceiver, filter);
        }
	}

	public BluetoothSocket setupSocket(BluetoothDevice device)
            throws BluetoothException {
		Log.d(TAG, "Scanning services on " + device);
        try {
            mSocket = device.createRfcommSocketToServiceRecord(RFCOMM_UUID);
        } catch(IOException e) {}

        mActivity.unregisterReceiver(mReceiver);
        mBluetoothAdapter.cancelDiscovery();

        try {
            mSocket.connect();
            return mSocket;
        } catch(IOException e) {
			Log.e(TAG, "Could not find required service on " + device);
            try {
                mSocket.close();
            } catch(IOException e2) {}
            throw new BluetoothException();
        }
	}

	public void deviceDiscovered(BluetoothDevice device) {
		Log.d(TAG, "Found Bluetooth device: " + device);
        if(device.getAddress() == mTargetMac) {
            Log.d(TAG, "Found matching device: " + device);
            mTargetDevice = device;
            try {
            setupSocket(device);
            } catch(BluetoothException e) {
                Log.w(TAG, "Couldn't open socket with " + device, e);
            }
        }
	}
}
