package com.openxc.hardware.hud.test;

import com.openxc.hardware.hud.BluetoothException;
import com.openxc.hardware.hud.HudService;

import android.bluetooth.BluetoothAdapter;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import android.os.IBinder;
import android.util.Log;
import android.app.Activity;

public class HudTestActivity extends Activity {
    private final static String TAG = "HudTest";
	private final long PERIOD = 500;
	private final long ERROR_PERIOD = 2000;
    private final String HUD_MAC_ADDRESS = "00:06:66:43:0D:08";
    private final static int REQUEST_ENABLE_BT = 42;

    private HudService mService;
	private Blinker mBlinker;
    private boolean mIsBound;

    private class Blinker implements Runnable {
        private boolean mRunning = true;

		public void stop() {
            mRunning = false;
            if(mService != null) {
                try {
                    mService.setAll(0.0);
                    mService.disconnect();
                } catch(BluetoothException e) {
                    Log.d(TAG, "An error ocurred while shutting down", e);
                }
            }
		}

		@Override
		public void run() {
			while(mRunning) {
				for(int i=0;i<5;i++){
                    int channel;
					if(i == 0) {
                        channel = 4;
                    } else {
                        channel = i - 1;
                    }

                    try {
                        mService.fade(channel, PERIOD, 0.0);
                        mService.fade(i, PERIOD, 1.0);
                    } catch(BluetoothException e) {
                        Log.w(TAG, "Unable to blink", e);
                        try {
                            Thread.sleep(ERROR_PERIOD);
                        } catch(InterruptedException er2) {
                            return;
                        }

                        if(!mRunning) {
                            return;
                        }
                    }

					try {
						Thread.sleep(PERIOD + Math.round(PERIOD/10));
					} catch(InterruptedException e) {
                        return;
                    }
				}

                try {
                    Log.d(TAG, "Raw battery level: " +
                            mService.rawBatteryLevel());
                } catch(BluetoothException e) {
                    Log.w(TAG, "Unable to get raw battery level", e);
                    // TODO we should sleep if we're still getting errors, but
                    // because the buffer on the Bluetooth device isn't getting
                    // flushed properly, sometimes the battery level gets a mix
                    // of errors and responses to ping.
                }
			}
		}
	};

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            mService = ((HudService.LocalBinder)service).getService();
            new Thread(new Runnable() {
                public void run() {
                    try {
                        mService.connect(HUD_MAC_ADDRESS);
                    } catch(BluetoothException e) {
                        Log.w(TAG, "Unable to connect to Bluetooth device " +
                            "with address: " + HUD_MAC_ADDRESS);
                    }
                    new Thread(mBlinker).start();
                }
            }).start();
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
            mBlinker.stop();
        }
    };

    private void bindService() {
        Log.d(TAG, "Binding to HudService");
        bindService(new Intent(this, HudService.class),
                mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    private void unbindService() {
        if(mIsBound) {
            Log.d(TAG, "Unbinding from HudService");
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "Resuming");
        mBlinker = new Blinker();

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if(adapter == null) {
            Log.w(TAG, "Unable to open Bluetooth adapter");
        } else {
            if(!adapter.isEnabled()) {
                Intent enableBluetoothIntent = new Intent(
                        BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBluetoothIntent,
                        REQUEST_ENABLE_BT);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mBlinker.stop();
    }

    @Override
    public void onStart() {
        super.onStart();
        bindService();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService();
    }
}
