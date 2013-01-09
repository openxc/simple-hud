package com.openxc.hardware.hud;

public interface BluetoothHudInterface {

    /**
     * Queries the remote device, updating it's online status
     *
     * @return
     *  Returns true if the device responds to an RSSI request.
     */
    public boolean ping();

    /**
     * Immediately set an LED channel to a given intensity
     *
     * @param chan
     *  The LED channel to set (currently 0-4 are supported)
     * @param value
     *  The intensity to set, given as a double between 0.0 and 1.0
     *  No guarantee of reception.
     */
    public void set(int chan, double value) throws BluetoothException;

    /**
     * Immediately set all LED channels to a given intensity
     *
     * @param value
     *  The intensity to set, given as a double between 0.0 and 1.0
     * @return
     *  Returns true if device was connected
     *  No guarantee of reception.
     */
    public void setAll(double value) throws BluetoothException;

    /**
     * Will read the current battery level of the device
     *
     * @return
     *  Returns the raw voltage ADC value, ranging from 0-1023
     *  More testing is needed, but rawBatteryLevel()/158.7 is
     *  approximately the current battery voltage.
     */
    public int rawBatteryLevel() throws BluetoothException;

    /**
     * Fade an LED channel to a given intensity
     * This will fade from the current channel intensity to
     * the specified destination intensity
     *
     * @param chan
     *  The LED channel to set (currently 0-4 are supported)
     * @param duration
     *  The fade duration, in ms
     * @param value
     *  The intensity to set, given as a double between 0.0 and 1.0
     * @return
     *  Returns true if device was connected
     *  No guarantee of reception.
     */
    public void fade(int chan, long duration, double value)
            throws BluetoothException;

    /**
     * Disconnect the bluetooth device.
     * In order to re-establish a connection, connect() must be called.
     */
    public void disconnect() throws BluetoothException;

    public void connect(String targetAddress) throws BluetoothException;
}
