package fr.yoanndiquelou.jeelight.ssdp;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fr.yoanndiquelou.jeelight.model.Light;

/**
 * SSDP Client.
 * 
 * @author Y0annD
 * 
 *         <p>
 *         Simple SSDP implementation to retrieve Yeelight devices.
 *         </p>
 *
 */
public class SSDPClient {
	/** Add device type. */
	public static final String ADD = "ADD";
	/** SSDP client logger. */
	private static final Logger logger = LogManager.getLogger(SSDPClient.class);
	/** Is discovering. */
	private boolean mIsDiscovering;
	/** Socket Timeout. */
	private int mTimeout;
	/** Socket port. */
	private int mPort;
	/** Service to listen. */
	private String mServiceType;
	/** Listening thread. */
	private Thread mListeningThread;
	/** Arriving observable. */
	private Map<Long, Light> mDevices;
	/** Listeners to devices change. */
	private List<PropertyChangeListener> mListeners;

	/**
	 * Simple SSDP client, that only manage discovery.
	 * 
	 * @param timeout     discoverty timeout
	 * @param serviceType type of service
	 *                    <p>
	 *                    <b>wifi_bulb</b> for yeelight
	 *                    </p>
	 * @param port        port to listen
	 *                    <p>
	 *                    <b>1982</b> for yeelight
	 *                    </p>
	 * @throws SocketException exception
	 */
	public SSDPClient(int timeout, String serviceType, int port) throws SocketException {
		this(timeout, serviceType, port, new DatagramSocket());
	}

	/**
	 * Simple SSDP client, that only manage discovery.
	 * <p>
	 * Mainly for testing purposes
	 * </p>
	 * 
	 * @param timeout      discoverty timeout
	 * @param serviceType  type of service
	 *                     <p>
	 *                     <b>wifi_bulb</b> for yeelight
	 *                     </p>
	 * @param port         port to listen
	 *                     <p>
	 *                     <b>1982</b> for yeelight
	 *                     </p>
	 * @param clientSocket socket
	 * @throws SocketException exception
	 */
	public SSDPClient(int timeout, String serviceType, int port, DatagramSocket clientSocket) {
		mTimeout = timeout;
		mServiceType = serviceType;
		mPort = port;
		mDevices = new HashMap<>();
		mListeners = new ArrayList<>();
		mListeningThread = new Thread(() -> {
			try {
				byte[] sendData;
				byte[] receiveData = new byte[1024];

				/* Send the request */
				sendData = buildRequestString().getBytes();
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
						InetAddress.getByName("239.255.255.250"), mPort);
				logger.debug(buildRequestString());
				clientSocket.setSoTimeout(mTimeout);
				clientSocket.send(sendPacket);

				/* Receive all responses */
				while (mIsDiscovering) {
					try {
						DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
						clientSocket.receive(receivePacket);
						logger.debug(new String(receivePacket.getData()));
						addDevice(Light.fromDatagramPacket(receivePacket.getAddress(), receivePacket.getData()));

					} catch (SocketTimeoutException e) {
						// Do nothing
					}
				}
				clientSocket.close();
			} catch (IOException e) {
				logger.error("Error during discovery", e);
			}
		});
	}

	/**
	 * build ssdp request.
	 * 
	 * @return ssdp request string
	 */
	public String buildRequestString() {
		/* Create the search request */
		StringBuilder search = new StringBuilder("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:").append(mPort)
				.append("\r\nMAN: \"ssdp:discover\"\r\n");
		if (mServiceType == null) {
			search.append("ST: ssdp:all\r\n");
		} else {
			search.append("ST: ").append(mServiceType).append("\r\n");
		}
		return search.toString();
	}

	/**
	 * Get devices.
	 * 
	 * @return known devices
	 */
	public List<Light> getDevices() {
		return new ArrayList<>(mDevices.values());
	}

	/**
	 * Is discovery enabled.
	 * 
	 * @return true if discovery is enabled
	 */
	public boolean isDiscovering() {
		return mIsDiscovering;
	}

	/**
	 * Add a device Listener.
	 * <p>
	 * Be notified when a new device appears
	 * </p>
	 * 
	 * @param listener device listener
	 */
	public void addListener(PropertyChangeListener listener) {
		if (!mListeners.contains(listener)) {
			mListeners.add(listener);
		}
	}

	/**
	 * Remove a device listener.
	 * 
	 * @param listener device listener
	 */
	public void removeListener(PropertyChangeListener listener) {
		mListeners.remove(listener);
	}

	/**
	 * Add a device.
	 * <p>
	 * Notify every listeners that a new device appears.
	 * </p>
	 * 
	 * @param device new device
	 */
	private void addDevice(Light device) {
		if (!mDevices.containsKey(device.getId())) {
			mDevices.put(device.getId(), device);
			notifyListeners(this, ADD, mDevices.values(), device);
		}
	}

	/**
	 * Notify listeners that new devices appears.
	 * 
	 * @param object   object
	 * @param property change property
	 * @param devices  devices list
	 * @param device   new device
	 */
	private void notifyListeners(Object object, String property, Collection<Light> devices, Light device) {
		for (PropertyChangeListener listener : mListeners) {
			listener.propertyChange(new PropertyChangeEvent(this, property, devices, device));
		}
	}

	/**
	 * Start discovering.
	 */
	public void startDiscovering() {
		mIsDiscovering = true;
		mListeningThread.start();
	}

	/**
	 * Stop discovering.
	 */
	public void stopDiscovering() {
		mIsDiscovering = false;
	}

}
