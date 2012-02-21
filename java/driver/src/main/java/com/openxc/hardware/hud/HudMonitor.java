package com.openxc.hardware.hud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Map;

import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;

import com.intel.bluetooth.RemoteDeviceHelper;

public class HudMonitor implements BTLedBar, Runnable {
	final long RETRY_DELAY = 1000;		//wait (ms) before retrying
	final long POLL_DELAY = 3000;		//poll remote device every (ms)

	private LocalDevice localdevice;
	private DeviceListener listener;
	private Thread myThread;
	private StreamConnection conn;
	private PrintWriter out;
	private BufferedReader in;
	private Object readyEvent;
	private RemoteDevice dev;
	private String service;
	private boolean connected;

	public void run(Map<Object, Object> services) {
		//Get imported service objects
		readyEvent = new Object();
		localdevice = (LocalDevice) services.get(LocalDevice.class.getName());
		ilog("start");
		//myThread calls run() NOT run(Map<Object, Object> services)
		myThread = new Thread(this, this.getClass().getSimpleName());
		myThread.start();
		listener = new DeviceListener(localdevice, this);
		connected = false;
	}

	public void shutdown() {
		//Force main connection thread to return
		myThread.interrupt();
		disconnect();		//Disconnect if connected
		ilog("stop");
	}

	@Override
	public void disconnect() {
		if (connected){
			//De-asserting connected will stop the main thread from retrying
			connected = false;
			try {
				out.close();
				in.close();
				conn.close();
			} catch (IOException e) {
				elog("Exception trying to disconnect "+e);
			}
		}
	}

	@Override
	public boolean online() {
		//Using readRSSI() provides a faster way to see if the device is gone
		//Checking connectedDevices() would wait until a read timed out...
		try {
			RemoteDeviceHelper.readRSSI(dev);	//Will effectively ping the device
		} catch (IOException e) {				//Throws IOException if device is gone
			return false;
		}
		return true;
	}

	@Override
	public boolean quickReconnect(){
		if (connected){
			//Disconnect, but allow thread to automatically reconnect
			try {
				out.close();
				in.close();
				conn.close();
			} catch (IOException e) {
				elog("Exception trying to disconnect "+e);
			}
			//TODO - need a delay here?
			//OR, omit following line and let main thread doit?
			return connect(service);
		}
		return false;
	}

	@Override
	public boolean set(int chan, double value) {
		//Abort quietly if not connected
		if (!connected)
			return false;
		out.write("S"+chan+Math.round(value*255)+"M");
		//inFlush(in);		//Flush so we know the next line is new
		out.flush();
		//return verifyResponse(in);	//essentially, just look for "OK"
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
		out.write("F"+chan+duration+","+ Math.round(value*255)+"M");
		//inFlush(in);		//Flush so we know the next line is new
		out.flush();
		//return verifyResponse(in);	//essentially, just look for "OK"
		return true;
	}

	@Override
	public int rawBatteryLevel() {
		//Abort quietly if not connected
		if (!connected)
			return -1;
		out.write("BM");
		inFlush(in);		//Flush so we know the next line is new
		out.flush();
		String line = getResponse(in);
		if ((line != null)&&(line.indexOf("VAL:") >= 0)){
			return Integer.parseInt(line.substring(4));
		}
		return -1;
	}

	private boolean authWith(RemoteDevice dev){
		try {
			//Only authenticate if we have not paired the device before.
			if (!RemoteDeviceHelper.implIsAuthenticated(dev))
				RemoteDeviceHelper.authenticate(dev,"1234");
		} catch (IOException e) {
			elog("Unable to authenticate "+dev.getBluetoothAddress());
			return false;
		}
		dlog("Attempting to find service string");
		//Check to make sure the device supports the necessary service
		service = listener.getSPPService(dev);
		if (service == null)
			return false;
		//Inform the main thread we are ready to connect
		synchronized(readyEvent){
			readyEvent.notifyAll();
		}
		return true;
	}

	@Override
	public boolean setName(String fullName) {
		//You need to disconnect() if you want to change to a different device
		if (connected){
			elog("Already connected - please disconnect first");
			return false;
		}
		dlog("Attempting to find device");
		//This will preform bluetooth discovery, looking at the device name
		dev = listener.discoverDevice(fullName,true);
		if (dev == null)
			return false;
		return authWith(dev);
	}

	@Override
	public boolean setMac(String macAddr) {
		//You need to disconnect() if you want to change to a different device
		if (connected){
			elog("Already connected - please disconnect first");
			return false;
		}
		dlog("Attempting to find device");
		//This will preform bluetooth discovery, looking at the device name
		dev = listener.discoverDevice(macAddr,false);
		if (dev == null)
			return false;
		return authWith(dev);
	}

	private boolean connect(String service){
		//Get the input and output streams/writer/reader
		try {
			conn =(StreamConnection)Connector.open(service);
			out = new PrintWriter(new OutputStreamWriter( conn.openOutputStream()));
			in = new BufferedReader( new InputStreamReader( conn.openInputStream()));
		} catch (IOException e) {
			//We are expecting to see "host is down" when repeatedly autoconnecting
			if (!(e.toString().contains("Host is Down"))){
				dlog("Error opening streams "+e);
			} else {
				elog("Error opening streams "+e);
			}
			return false;
		}
		connected = true;
		return true;
	}

	//Clear any waiting characters from the input buffer.
	private void inFlush(BufferedReader reader){
		try {
			while(reader.ready()){
				reader.skip(1024);	//A guess at something larger than the buffer size of the BT adapter
			}
		} catch (IOException e) {}	//Disregard exceptions... This is only an attempt to clear the buffer
	}

	private String getResponse(BufferedReader reader){
		String line = "";
		try {
			line = in.readLine();
		} catch (IOException e) {
			elog("unable to read response");
			return null;
		}
		if (line ==  null){
			elog("device has dropped offline");
			return null;
		}
		return line;
	}

	//Wrappers for the log service (to standardize logged messages)
    // TODO replace this with standard logging or Android logging?
	void ilog(String message) { }
	void dlog(String message) { }
	void elog(String message) { }
	void wlog(String message) { }

	@Override
	public void run() {
		dlog("Main thread start - wait for readyEvent");
		//Wait until some other service has found a device to connect
		synchronized(readyEvent){
			try {
				readyEvent.wait();
			} catch (InterruptedException e) {return;}
		}
		while(true){
			dlog("Attempting to connect to "+service);
			//If unable to connect
			if (!connect(service)){
				//wait and retry
				try { Thread.sleep(RETRY_DELAY);
				} catch (InterruptedException e) {return;}
				continue;
			}
			ilog("Connected to "+service);
			//ping device while we are connected
			while(online()){
				try { Thread.sleep(POLL_DELAY);
				} catch (InterruptedException e) {return;}
			}
			ilog("Device "+dev.getBluetoothAddress()+" has been disconnected!");
			//If connected was not updated, autoretry
			//connected = true implies the device disconnected on it's own accord
			if (connected)
				continue;
			//If we were specifically disconnected by the service
			else {
				ilog("Disconnected, waiting for setName() to reconnect");
				//Wait until we are instructed to re-discover
				synchronized(readyEvent){
					try {
						readyEvent.wait();
					} catch (InterruptedException e) {return;}
				}
			}
		}
	}
}
