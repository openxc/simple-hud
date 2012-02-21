package com.openxc.hardware.hud.test;

import java.util.Map;
import android.util.Log;
import android.app.Activity;
import com.openxc.hardware.hud.BTLedBar;

public class HudTestActivity extends Activity {
    private final static String TAG = "HudTest";

	final long PERIOD = 500;
	private BTLedBar hud;
	private blinker blink;

	public class blinker implements Runnable {
		private Thread t;

		public blinker(){
			t = new Thread(this, "blinker");
			t.start();
		}

		public void stop(){
			t.interrupt();
		}

		@Override
		public void run() {
			while(true){
				for (int i=0;i<5;i++){
					if (i == 0) {
						hud.fade(4, PERIOD, 0.0);
                    } else {
						hud.fade(i-1, PERIOD, 0.0);
                    }

					hud.fade(i, PERIOD, 1.0);
					try {
						Thread.sleep(PERIOD+Math.round(PERIOD/10));
					} catch (InterruptedException e) {return;}
				}
				Log.d(TAG, "Raw battery level: "+hud.rawBatteryLevel());
			}
		}
	}

    @Override
    public void onResume() {
        hud = new BTLedBar();
        Log.i(TAG, "Connecting to \"RN42\"");

        //repeatedly call setName until a device containing the text
        //"FireFly" is found.
        while(!hud.setMac("000666430D08")){
            Log.i(TAG, "Couldn't find device, retrying");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) { return; }
        }

        Log.i(TAG, "Connected, toggling LED");
        blink = new blinker();
    }

    @Override
    public void onPause() {
        blink.stop();
        Log.d(TAG, "nuked thread, setting all off");
        hud.setAll(0.0);
        Log.i(TAG, "Disconnecting...");
        //Disconnect will cease automatically reconnecting to device
        hud.disconnect();
    }
}
