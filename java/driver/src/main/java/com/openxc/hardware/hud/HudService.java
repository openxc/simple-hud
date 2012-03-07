package com.openxc.hardware.hud;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import android.app.Service;

import android.bluetooth.BluetoothSocket;

import android.content.Intent;

import android.os.Binder;
import android.os.IBinder;

import android.util.Log;

public class HudService extends Service implements BluetoothHudInterface {
    private final String TAG = "HudService";
    private final long RETRY_DELAY = 1000;
    private final long POLL_DELAY = 3000;

    private boolean connected;
    private DeviceManager mDeviceManager;
    private PrintWriter mOutStream;
    private BufferedReader mInStream;
    private BluetoothSocket mSocket;

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public HudService getService() {
            return HudService.this;
        }
    }

    @Override
    public void onCreate() {
        try {
            mDeviceManager = new DeviceManager(this);
        } catch(BluetoothException e) {
            Log.w(TAG, "Unable to open Bluetooth device manager", e);
        }
        connected = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Being destroyed");
        disconnect();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binding from " + intent);
        return mBinder;
    }

    @Override
    public void disconnect() {
        if(connected) {
            Log.d(TAG, "Disconnecting from the socket " + mSocket);
            connected = false;
            mOutStream.close();
            try {
                mInStream.close();
            } catch(IOException e) { }

            try {
                mSocket.close();
            } catch(IOException e) { }
        }
    }

    @Override
    public boolean set(int chan, double value) {
        //Abort quietly if not connected
        if (!connected)
            return false;
        mOutStream.write("S"+chan+Math.round(value*255)+"M");
        //inFlush(in);      //Flush so we know the next line is new
        mOutStream.flush();
        //return verifyResponse(in);    //essentially, just look for "OK"
        return true;
    }

    @Override
    public boolean setAll(double value) {
        //Abort quietly if not connected
        if (!connected)
            return false;
        boolean success = true;
        for (int i=0;i<5;i++)
            if (!set(i,value))
                success = false;
        return success;
    }

    @Override
    public boolean fade(int chan, long duration, double value) {
        //Abort quietly if not connected
        if (!connected)
            return false;
        mOutStream.write("F"+chan+duration+","+ Math.round(value*255)+"M");
        //inFlush(in);      //Flush so we know the next line is new
        mOutStream.flush();
        //return verifyResponse(in);    //essentially, just look for "OK"
        return true;
    }

    @Override
    public int rawBatteryLevel() throws BluetoothException {
        if (!connected) {
            Log.w(TAG, "Must be connected to check battery level");
            throw new BluetoothException();
        }
        mOutStream.write("BM");
        inFlush(mInStream);     //Flush so we know the next line is new
        mOutStream.flush();
        String line = getResponse(mInStream);
        if ((line != null)&&(line.indexOf("VAL:") >= 0)){
            return Integer.parseInt(line.substring(4));
        }
        return -1;
    }

    @Override
    public void connect(String targetAddress) throws BluetoothException {
        if(mConnectionKeepalive != null) {
            mConnectionKeepalive.stop();
        }
        mConnectionKeepalive = new ConnectionKeepalive(targetAddress);
        new Thread(mConnectionKeepalive).start();

        mDeviceManager.connect(targetAddress);
        try {
            mSocket = mDeviceManager.setupSocket();
            mOutStream = new PrintWriter(new OutputStreamWriter(mSocket.getOutputStream()));
            mInStream = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
            connected = true;
        } catch (IOException e) {
            // We are expecting to see "host is down" when repeatedly
            // autoconnecting
            if (!(e.toString().contains("Host is Down"))){
                Log.d(TAG, "Error opening streams "+e);
            } else {
                Log.e(TAG, "Error opening streams "+e);
            }
            connected = false;
            throw new BluetoothException();
        }
    }

    @Override
    public boolean online() {
        // TODO
        return mSocket != null; // && mSocket.isConnected();
    }

    private ConnectionKeepalive mConnectionKeepalive;
    private class ConnectionKeepalive implements Runnable {
        private String mTargetAddress;
        private boolean mRunning;

        public ConnectionKeepalive(String targetAddress) {
            mTargetAddress = targetAddress;
            mRunning = true;
        }

        public void stop() {
            mRunning = false;
        }

        public void run() {
            while(mRunning) {
                try {
                    connect(mTargetAddress);
                } catch(BluetoothException e) {
                    Log.w(TAG, "Unable to connect", e);
                    try {
                        Thread.sleep(RETRY_DELAY);
                    } catch(InterruptedException e2) {
                        return;
                    }
                    continue;
                }

                // ping device while we are connected
                while(online()){
                    try {
                        Thread.sleep(POLL_DELAY);
                    } catch (InterruptedException e) {return;}
                }

                Log.i(TAG, "Socket " + mSocket + " has been disconnected!");
            }
        }
    }

    //Clear any waiting characters from the input buffer.
    private void inFlush(BufferedReader reader){
        try {
            while(reader.ready()){
                reader.skip(1024);  //A guess at something larger than the buffer size of the BT adapter
            }
        } catch (IOException e) {}  //Disregard exceptions... This is only an attempt to clear the buffer
    }

    private String getResponse(BufferedReader reader){
        String line = "";
        try {
            line = mInStream.readLine();
        } catch (IOException e) {
            Log.e(TAG, "unable to read response");
            return null;
        }
        if (line ==  null){
            Log.e(TAG, "device has dropped offline");
            return null;
        }
        return line;
    }
}
