package org.sputnikdev.bluetooth.manager.transport.tinyb;

/*-
 * #%L
 * org.sputnikdev:bluetooth-manager-tinyb
 * %%
 * Copyright (C) 2017 Sputnik Dev
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.DiscoveredDevice;
import org.sputnikdev.bluetooth.manager.transport.Adapter;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObjectFactory;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.Device;
import tinyb.BluetoothAdapter;
import tinyb.BluetoothDevice;
import tinyb.BluetoothGattCharacteristic;
import tinyb.BluetoothGattService;
import tinyb.BluetoothManager;
import tinyb.BluetoothType;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * A Bluetooth Manager Transport abstraction layer implementation based on TinyB library.
 * @author Vlad Kolotov
 */
public class TinyBFactory implements BluetoothObjectFactory {

    public static final String TINYB_PROTOCOL_NAME = "tinyb";

    private static final Logger LOGGER = LoggerFactory.getLogger(TinyBFactory.class);

    private static final ExecutorService NOTIFICATION_SERVICE = Executors.newCachedThreadPool();

    /**
     * Loads TinyB bundled native libraries from classpath by copying them to a temp folder.
     * @return true if all libraries successfully loaded, false otherwise
     */
    private static boolean loadBundeledNativeLibraries() {
        LOGGER.debug("Loading native libraries from the bundle. Environment: {} : {}",
                NativesLoader.getOsName(), NativesLoader.getOsArch());
        if (!NativesLoader.isSupportedEnvironment()) {
            LOGGER.debug("Environment is not supported: {} : {}", NativesLoader.getOsName(), NativesLoader.getOsArch());
            return false;
        }
        LOGGER.debug("The environment is supported.");
        final String[] libs = {"libtinyb.so", "libjavatinyb.so"};
        for (String lib : libs) {
            try {
                LOGGER.debug("Preparing {} native library", lib);
                String tempFile = NativesLoader.prepare(lib);
                LOGGER.debug("Loading {} native library: {}", lib, tempFile);
                System.load(tempFile); // $COVERAGE-IGNORE$
            } catch (Throwable e) {
                LOGGER.debug("Could not load bundled TinyB native libraries.", e);
                return false;
            }
        }
        LOGGER.debug("Native libraries has been successfully loaded from the bundle.");
        return true; // $COVERAGE-IGNORE$
    }

    /**
     * Loads TinyB native libraries from system paths.
     * @return true if all libraries successfully loaded, false otherwise
     */
    private static boolean loadSystemNativeLibraries() {
        LOGGER.info("TinyB: environment is not supported out of the box. Attempting to load system libs.");

        final String[] libs = {"tinyb", "javatinyb"};
        for (String lib : libs) {
            try {
                LOGGER.debug("Loading native library from class path: {}", lib);
                System.loadLibrary(lib); // $COVERAGE-IGNORE$
            } catch (Throwable e) {
                LOGGER.debug("TinyB: Could not load system libraries. Thus, environemnt is not supported. "
                    + "Only Linux OS; x86, x86_64 and arm6 architectures are supported out of the box. Consider "
                    + "providing own " + lib + ". in one of " + System.getProperty("java.library.path"), e);
                return false;
            }
        }
        LOGGER.debug("Native libraries has been successfully loaded from class path.");
        return true; // $COVERAGE-IGNORE$
    }

    /**
     * Loads TinyB native libraries (either bundeled or system ones).
     * @return true if all libraries successfully loaded, false otherwise
     */
    public static boolean loadNativeLibraries() {
        return loadBundeledNativeLibraries() || loadSystemNativeLibraries();
    }

    @Override
    public Adapter getAdapter(URL url) {
        BluetoothAdapter adapter = (BluetoothAdapter) BluetoothManager.getBluetoothManager().getObject(
                BluetoothType.ADAPTER, null, url.getAdapterAddress(), null);
        return adapter != null ? new TinyBAdapter(url.getAdapterURL(), adapter) : null;
    }

    @Override
    public Device getDevice(URL url) {
        BluetoothAdapter adapter = (BluetoothAdapter) BluetoothManager.getBluetoothManager().getObject(
                BluetoothType.ADAPTER, null, url.getAdapterAddress(), null);
        if (adapter == null) {
            return null;
        }
        BluetoothDevice device = (BluetoothDevice) BluetoothManager.getBluetoothManager().getObject(
                BluetoothType.DEVICE, null, url.getDeviceAddress(), adapter);
        return device != null ? new TinyBDevice(url.getDeviceURL(), device) : null;
    }

    @Override
    public Characteristic getCharacteristic(URL url) {
        BluetoothAdapter adapter = (BluetoothAdapter) BluetoothManager.getBluetoothManager().getObject(
                BluetoothType.ADAPTER, null, url.getAdapterAddress(), null);
        if (adapter == null) {
            return null;
        }
        BluetoothDevice device = (BluetoothDevice) BluetoothManager.getBluetoothManager().getObject(
                BluetoothType.DEVICE, null, url.getDeviceAddress(), adapter);
        if (device == null || !device.getConnected()) {
            return null;
        }
        BluetoothGattService service = (BluetoothGattService) BluetoothManager.getBluetoothManager().getObject(
                        BluetoothType.GATT_SERVICE, null, url.getServiceUUID(), device);
        if (service == null) {
            return null;
        }
        BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic)
                BluetoothManager.getBluetoothManager().getObject(
                        BluetoothType.GATT_CHARACTERISTIC, null, url.getCharacteristicUUID(), service);
        return characteristic != null ? new TinyBCharacteristic(url.getCharacteristicURL(), characteristic) : null;
    }

    @Override
    public Set<DiscoveredAdapter> getDiscoveredAdapters() {
        try {
            return BluetoothManager.getBluetoothManager().getAdapters().stream().map(
                    TinyBFactory::convert).collect(Collectors.toSet());
        } catch (tinyb.BluetoothException ex) {
            if ("No adapter installed or not recognized by system".equals(ex.getMessage())) {
                return Collections.emptySet();
            }
            throw ex;
        }
    }

    @Override
    public Set<DiscoveredDevice> getDiscoveredDevices() {
        try {
            return BluetoothManager.getBluetoothManager().getDevices().stream().map(
                    TinyBFactory::convert).collect(Collectors.toSet());
        } catch (tinyb.BluetoothException ex) {
            if ("No adapter installed or not recognized by system".equals(ex.getMessage())) {
                return Collections.emptySet();
            }
            throw ex;
        }
    }

    @Override
    public String getProtocolName() {
        return TINYB_PROTOCOL_NAME;
    }

    @Override
    public void configure(Map<String, Object> config) { /* do nothing for now */ }

    /**
     * Disposing TinyB factory by closing/disposing all adapters, devices and services.
     */
    public void dispose() {
        try {
            BluetoothManager bluetoothManager = BluetoothManager.getBluetoothManager();
            bluetoothManager.stopDiscovery();
            bluetoothManager.getServices().forEach(TinyBFactory::closeSilently);
            bluetoothManager.getDevices().forEach(TinyBFactory::closeSilently);
            bluetoothManager.getAdapters().forEach(TinyBFactory::closeSilently);
        } catch (Exception ex) {
            LOGGER.debug("Error occurred while disposing TinyB manager: {}", ex.getMessage());
        }
    }

    @Override
    public void dispose(URL url) {
        LOGGER.debug("Bluetooth object disposal requested: {}", url);
        BluetoothAdapter adapter = (BluetoothAdapter) BluetoothManager.getBluetoothManager().getObject(
                BluetoothType.ADAPTER, null, url.getAdapterAddress(), null);
        if (url.isAdapter()) {
            TinyBAdapter.dispose(adapter);
        } else if (url.isDevice() || url.isCharacteristic()) {
            BluetoothDevice device = (BluetoothDevice) BluetoothManager.getBluetoothManager().getObject(
                    BluetoothType.DEVICE, null, url.getDeviceAddress(), adapter);
            if (device != null) {
                if (url.isDevice()) {
                    TinyBDevice.dispose(device);
                } else {
                    runSilently(device::disconnect);
                }
            }
        }
    }

    static void runSilently(Runnable func) {
        try {
            func.run();
        } catch (Exception ignore) { /* do nothing */ }
    }

    static void notifySafely(Runnable noticator, Logger logger, String errorMessage) {
        getNotificationService().submit(() -> {
            try {
                noticator.run();
            } catch (Exception ex) {
                logger.error(errorMessage, ex);
            }
        });
    }

    private static ExecutorService getNotificationService() {
        return NOTIFICATION_SERVICE;
    }

    private static void closeSilently(AutoCloseable autoCloseable) {
        try {
            autoCloseable.close();
        } catch (Exception ignore) { /* do nothing */ }
    }

    private static DiscoveredDevice convert(BluetoothDevice device) {
        return new DiscoveredDevice(new URL(TINYB_PROTOCOL_NAME,
                device.getAdapter().getAddress(), device.getAddress()),
                device.getName(), device.getAlias(), device.getRSSI(),
                device.getBluetoothClass(),
                //TODO implement proper determination of the device type
                device.getBluetoothClass() == 0);
    }

    private static DiscoveredAdapter convert(BluetoothAdapter adapter) {
        return new DiscoveredAdapter(new URL(TINYB_PROTOCOL_NAME,
                adapter.getAddress(), null),
                adapter.getName(), adapter.getAlias());
    }
}
