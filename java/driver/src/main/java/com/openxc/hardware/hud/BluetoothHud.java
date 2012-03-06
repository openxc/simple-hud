package com.openxc.hardware.hud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import android.app.Activity;

import android.bluetooth.BluetoothSocket;

import android.util.Log;

public class BluetoothHud implements BluetoothHudInterface, Runnable {
    private final static String TAG = "HudMonitor";
    private final long RETRY_DELAY = 1000;
    private final long POLL_DELAY = 3000;

    private DeviceManager mDeviceManager;
    private Thread myThread;
    private boolean connected;
    private BluetoothSocket mSocket;
    private PrintWriter mOutStream;
    private BufferedReader mInStream;

    public BluetoothHud(Activity activity, String targetAddress) throws BluetoothException {
        Log.i(TAG, "start");
        myThread = new Thread(this, this.getClass().getSimpleName());
        myThread.start();
        mDeviceManager = new DeviceManager(activity);
        mDeviceManager.discoverDevices(targetAddress);
        connected = false;
    }

    public void shutdown() {
        //Force main connection thread to return
        myThread.interrupt();
        disconnect();
        Log.i(TAG, "stop");
    }

    @Override
    public void disconnect() {
        if(connected) {
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
    public boolean online() {
        return mSocket != null && mSocket.isConnected();
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

    private boolean connect() throws BluetoothException {
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
        }
        return connected;
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

    @Override
    public void run() {
        Log.d(TAG, "Main thread start - wait for readyEvent");
        while(true) {
            mDeviceManager.waitForDevice();
            Log.d(TAG, "Connecting to remote service");
            try {
                if (!connect()){
                    try {
                        Thread.sleep(RETRY_DELAY);
                    } catch (InterruptedException e) {return;}
                    continue;
                }
            } catch(BluetoothException e) {
                Log.w(TAG, "Unable to connect", e);
            }

            //ping device while we are connected
            while(online()){
                try {
                    Thread.sleep(POLL_DELAY);
                } catch (InterruptedException e) {return;}
            }

            Log.i(TAG, "Socket " + mSocket + " has been disconnected!");
        }
    }
}
