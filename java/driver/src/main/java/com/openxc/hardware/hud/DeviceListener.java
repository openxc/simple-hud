package com.openxc.hardware.hud;

import java.io.IOException;
import java.util.jar.Attributes.Name;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;

import com.intel.bluetooth.RemoteDeviceHelper;

public class DeviceListener implements DiscoveryListener {
	private LocalDevice localdevice;
	private DiscoveryAgent agent;
	private HudMonitor app;
	private String target;
	private RemoteDevice foundDev;
	private String service;
	private String mac;
	private boolean resolveNames;
	public Object searchComplete;

	public DeviceListener(LocalDevice localBTDevice, HudMonitor callingObject){
		app = callingObject;
		searchComplete = new Object();
		localdevice = localBTDevice;
		agent = localBTDevice.getDiscoveryAgent();
	}

	//Where all the magic happens:
	public RemoteDevice discoverDevice(String search, boolean resolveName){
		foundDev = null;
		target = search;
		resolveNames = resolveName;
		//Kick off a new discovery of bluetooth devices
		try {
			if (!agent.startInquiry(DiscoveryAgent.GIAC, this)){
				app.elog("Unable to start inquiry with system bluetooth agent");
				return null;
			}
		} catch (BluetoothStateException e) {
			app.elog("Bluetooth adapter is in invalid state: "+e);
			return null;
		}
		app.dlog("Waiting for discovery to complete");
		synchronized(searchComplete){
			try {
				searchComplete.wait();
			} catch (InterruptedException e) {
				app.elog("Discovery interrupted!");
				return null;
			}
		}
		if (foundDev == null){
			app.elog("Did not find any devices containing "+target);
			return null;
		}
		return foundDev;
	}

	public String getSPPService(RemoteDevice foundDev){
		service = null;
		app.dlog("Scanning services on "+foundDev.getBluetoothAddress());
		//This this combination of UUID and attr was found experimentally
		//only confirmed working for the RN41 and RN42 chipsets
		UUID[] uuidSet = new UUID[1];
		uuidSet[0] = new UUID(0x0003); //RFCOMM
		int[] attrIDs = new int [1];
		attrIDs[0] = 0x0100;
		try {
			agent.searchServices(attrIDs, uuidSet, foundDev, this);
		} catch (BluetoothStateException e) {
			app.elog("Bluetooth adapter is in invalid state: "+e);
			return null;
		}
		app.dlog("Waiting for service scan to complete");
		synchronized(searchComplete){
			try {
				searchComplete.wait();
			} catch (InterruptedException e) {
				app.elog("Service scan interrupted!");
				return null;
			}
		}
		if (service == null){
			app.elog("Could not find required service on "+foundDev.getBluetoothAddress());
			return null;
		}
		return service;
	}

	@Override
	public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
		String name = "";
		app.dlog("Found "+btDevice.getBluetoothAddress());
		if (resolveNames){
			try {
				name = btDevice.getFriendlyName(false);
			} catch (IOException e) {
				app.dlog("Unable to get name of device "+btDevice.getBluetoothAddress());
			}
			if (name.contains(target)){
				foundDev = btDevice;
				app.dlog("Found matching device "+name);
			}
		} else {
			if (btDevice.getBluetoothAddress().contains(target.toUpperCase())){
				foundDev = btDevice;
				app.dlog("Found matching device "+target);
			}
		}

	}

	@Override
	public void inquiryCompleted(int discType) {
		synchronized(searchComplete){
			searchComplete.notifyAll();
		}
	}

	@Override
	public void serviceSearchCompleted(int transID, int respCode) {
		synchronized(searchComplete){
			searchComplete.notifyAll();
		}
	}

	@Override
	public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
		if (servRecord.length > 0){		//We are only checking for one service
			service = servRecord[0].getConnectionURL(0, false);
		}
	}

}
