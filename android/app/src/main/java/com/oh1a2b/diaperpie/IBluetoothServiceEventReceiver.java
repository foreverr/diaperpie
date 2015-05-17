package com.oh1a2b.diaperpie;

/**
 * Interface für Bluetooth-Ereignisreceiver
 */
public interface IBluetoothServiceEventReceiver {

	/**
	 * Bluetooth wird aktiviert
	 */
	public void bluetoothEnabling();

	/**
	 * Bluetooth wurde aktiviert
	 */
	public void bluetoothEnabled();

	/**
	 * Bluetooth wird deaktiviert
	 */
	public void bluetoothDisabling();

	/**
	 * Bluetooth wurde deaktiviert
	 */
	public void bluetoothDisabled();

	/**
	 * Bluetooth verbunden mit einem Gerät
	 * @param name Der Name des Gerätes
	 * @param address Die MAC-Adresse des Gerätes
	 */
	public void connectedTo(final String name, final String address);
}
