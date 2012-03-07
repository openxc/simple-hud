package com.openxc.hardware.hud.test;

import java.util.Map;

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
    private final String HUD_MAC_ADDRESS = "000666430D08";
    private HudService mService;
	private Blinker mBlinker;
    private boolean mIsBound;

    private class Blinker implements Runnable {
        private boolean mRunning = true;

		public void stop() {
            mRunning = false;
            mService.setAll(0.0);
            mService.disconnect();
		}

		@Override
		public void run() {
			while(mRunning){
				for (int i=0;i<5;i++){
					if (i == 0) {
						mService.fade(4, PERIOD, 0.0);
                    } else {
						mService.fade(i-1, PERIOD, 0.0);
                    }

					mService.fade(i, PERIOD, 1.0);
					try {
						Thread.sleep(PERIOD+Math.round(PERIOD/10));
					} catch (InterruptedException e) {return;}
				}
				Log.d(TAG, "Raw battery level: "+mService.rawBatteryLevel());
			}
		}
	};

    private ServiceConnection mConnection = new ServiceConnection () {
        public void onServiceConnected(ComponentName classNAme, IBinder service) {
            mService = ((HudService.LocalBinder)service).getService();
            mService.setMac(HUD_MAC_ADDRESS);
            mBlinker.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
            mBlinker.stop();
        }
    };

    private void bindService() {
        bindService(new Intent(this, HudService.class),
                mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    private void unbindService() {
        if(mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    public void onResume() {
        mBlinker = new Blinker();
    }

    @Override
    public void onPause() {
        mBlinker.stop();
    }

    @Override
    public void onStart() {
        bindService();
    }

    @Override
    public void onDestroy() {
        unbindService();
    }
}
