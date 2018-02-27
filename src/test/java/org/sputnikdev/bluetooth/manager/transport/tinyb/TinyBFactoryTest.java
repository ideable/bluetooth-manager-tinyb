package org.sputnikdev.bluetooth.manager.transport.tinyb;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;
import org.sputnikdev.bluetooth.URL;
import tinyb.BluetoothGattCharacteristic;
import tinyb.BluetoothGattService;
import tinyb.BluetoothManager;
import tinyb.BluetoothType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

@RunWith(PowerMockRunner.class)
@SuppressStaticInitializationFor({"tinyb.BluetoothManager", "tinyb.BluetoothObject"})
@PrepareForTest({ NativesLoader.class, TinyBFactory.class })
public class TinyBFactoryTest {

    private static final URL CHARACTERISTIC = new URL("tinyb://11:22:33:44:55:66/10:20:30:40:50:60/0180/aa11");
    private static final URL DEVICE = CHARACTERISTIC.getDeviceURL();
    private static final URL ADAPTER = DEVICE.getAdapterURL();

    private tinyb.BluetoothAdapter adapter = mock(tinyb.BluetoothAdapter.class);
    private tinyb.BluetoothDevice device = mock(tinyb.BluetoothDevice.class);
    private BluetoothGattService service = mock(BluetoothGattService.class);
    private BluetoothGattCharacteristic characteristic = mock(BluetoothGattCharacteristic.class);
    private BluetoothManager bluetoothManager = mock(BluetoothManager.class);

    @Mock
    private ExecutorService fakeExecutorService;

    @InjectMocks
    private TinyBFactory tinyBFactory;

    @Before
    public void setUp() throws Exception {
        PowerMockito.mockStatic(BluetoothManager.class);
        PowerMockito.spy(TinyBFactory.class);
        PowerMockito.doCallRealMethod().when(TinyBFactory.class, "notifySafely", any(), any(), anyString());
        PowerMockito.doReturn(fakeExecutorService).when(TinyBFactory.class, "getNotificationService");
        when(fakeExecutorService.submit(any(Runnable.class))).thenAnswer((Answer<Future<?>>) invocation -> {
            invocation.getArgumentAt(0, Runnable.class).run();
            return null;
        });

        when(BluetoothManager.getBluetoothManager()).thenReturn(bluetoothManager);
        when(adapter.getAddress()).thenReturn(ADAPTER.getAdapterAddress());
        when(device.getAddress()).thenReturn(DEVICE.getDeviceAddress());
        when(service.getUUID()).thenReturn(CHARACTERISTIC.getServiceUUID());
        when(characteristic.getUUID()).thenReturn(CHARACTERISTIC.getCharacteristicUUID());

        when(device.getAdapter()).thenReturn(adapter);
        when(service.getDevice()).thenReturn(device);
        when(characteristic.getService()).thenReturn(service);

        when(bluetoothManager.getObject(BluetoothType.ADAPTER, null,
                ADAPTER.getAdapterAddress(), null)).thenReturn(adapter);
        when(bluetoothManager.getObject(BluetoothType.DEVICE, null,
                DEVICE.getDeviceAddress(), adapter)).thenReturn(device);
        when(bluetoothManager.getObject(BluetoothType.GATT_SERVICE, null,
                CHARACTERISTIC.getServiceUUID(), device)).thenReturn(service);
        when(bluetoothManager.getObject(BluetoothType.GATT_CHARACTERISTIC, null,
                CHARACTERISTIC.getCharacteristicUUID(), service)).thenReturn(characteristic);

        when(bluetoothManager.getAdapters()).thenReturn(Arrays.asList(adapter));
        when(bluetoothManager.getDevices()).thenReturn(Arrays.asList(device));
        when(bluetoothManager.getServices()).thenReturn(Arrays.asList(service));
    }

    @Test
    public void testGetAdapter() throws Exception {
        assertEquals(ADAPTER, tinyBFactory.getAdapter(ADAPTER).getURL());

        when(bluetoothManager.getObject(BluetoothType.ADAPTER, null,
                ADAPTER.getAdapterAddress(), null)).thenReturn(null);
        assertNull(tinyBFactory.getAdapter(ADAPTER));
    }

    @Test
    public void testGetDevice() throws Exception {
        assertEquals(DEVICE, tinyBFactory.getDevice(DEVICE).getURL());

        when(bluetoothManager.getObject(BluetoothType.ADAPTER, null,
                ADAPTER.getAdapterAddress(), null)).thenReturn(null);
        assertNull(tinyBFactory.getDevice(DEVICE));

        when(bluetoothManager.getObject(BluetoothType.ADAPTER, null,
                ADAPTER.getAdapterAddress(), null)).thenReturn(adapter);
        when(bluetoothManager.getObject(BluetoothType.DEVICE, null,
                DEVICE.getDeviceAddress(), adapter)).thenReturn(null);
        assertNull(tinyBFactory.getDevice(DEVICE));
    }

    @Test
    public void testGetCharacteristic() throws Exception {
        when(device.getConnected()).thenReturn(true);
        assertEquals(CHARACTERISTIC, tinyBFactory.getCharacteristic(CHARACTERISTIC).getURL());

        when(bluetoothManager.getObject(BluetoothType.ADAPTER, null,
                ADAPTER.getAdapterAddress(), null)).thenReturn(null);
        assertNull(tinyBFactory.getCharacteristic(CHARACTERISTIC));

        when(bluetoothManager.getObject(BluetoothType.ADAPTER, null,
                ADAPTER.getAdapterAddress(), null)).thenReturn(adapter);
        when(bluetoothManager.getObject(BluetoothType.DEVICE, null,
                DEVICE.getDeviceAddress(), adapter)).thenReturn(null);
        assertNull(tinyBFactory.getCharacteristic(CHARACTERISTIC));

        when(bluetoothManager.getObject(BluetoothType.ADAPTER, null,
                ADAPTER.getAdapterAddress(), null)).thenReturn(adapter);
        when(bluetoothManager.getObject(BluetoothType.DEVICE, null,
                DEVICE.getDeviceAddress(), adapter)).thenReturn(device);
        when(bluetoothManager.getObject(BluetoothType.GATT_SERVICE, null,
                CHARACTERISTIC.getServiceUUID(), device)).thenReturn(null);
        assertNull(tinyBFactory.getCharacteristic(CHARACTERISTIC));

        when(bluetoothManager.getObject(BluetoothType.ADAPTER, null,
                ADAPTER.getAdapterAddress(), null)).thenReturn(adapter);
        when(bluetoothManager.getObject(BluetoothType.DEVICE, null,
                DEVICE.getDeviceAddress(), adapter)).thenReturn(device);
        when(bluetoothManager.getObject(BluetoothType.GATT_SERVICE, null,
                CHARACTERISTIC.getServiceUUID(), device)).thenReturn(service);
        when(bluetoothManager.getObject(BluetoothType.GATT_CHARACTERISTIC, null,
                CHARACTERISTIC.getCharacteristicUUID(), service)).thenReturn(null);
        assertNull(tinyBFactory.getCharacteristic(CHARACTERISTIC));
    }

    @Test
    public void testGetDiscoveredAdapters() throws Exception {
        assertEquals(1, tinyBFactory.getDiscoveredAdapters().size());
        assertEquals(ADAPTER, tinyBFactory.getDiscoveredAdapters().iterator().next().getURL());
    }

    @Test
    public void testGetDiscoveredDevices() throws Exception {
        assertEquals(1, tinyBFactory.getDiscoveredDevices().size());
        assertEquals(DEVICE, tinyBFactory.getDiscoveredDevices().iterator().next().getURL());
    }

    @Test
    public void testGetProtocolName() throws Exception {
        assertEquals(TinyBFactory.TINYB_PROTOCOL_NAME, tinyBFactory.getProtocolName());
    }

    @Test
    public void testLoadNativeLibraries() throws Exception {
        PowerMockito.mockStatic(NativesLoader.class);
        PowerMockito.when(NativesLoader.isSupportedEnvironment()).thenReturn(true);
        PowerMockito.when(NativesLoader.prepare(any())).thenReturn("/anypath/anylib");

        TinyBFactory.loadNativeLibraries();

        PowerMockito.verifyStatic(times(1));
    }

    @Test
    public void testLoadNativeLIbrariesEnvNotSupported() {
        PowerMockito.mockStatic(NativesLoader.class);
        PowerMockito.when(NativesLoader.isSupportedEnvironment()).thenReturn(false);

        assertFalse(TinyBFactory.loadNativeLibraries());

        PowerMockito.verifyStatic(times(1));
    }

    @Test
    public void testDispose() {
        doThrow(RuntimeException.class).when(adapter).close();
        doThrow(RuntimeException.class).when(device).close();
        doThrow(RuntimeException.class).when(service).close();

        tinyBFactory.dispose();

        verify(bluetoothManager).stopDiscovery();
        verify(adapter).close();
        verify(device).close();
        verify(service).close();
    }

    @Test
    public void testConfigure() {
        Map<String, Object> config = new HashMap<>();
        config.put("config1", "value1");
        tinyBFactory.configure(config);

        // there is nothing happening for the current implementation
        verifyNoMoreInteractions(bluetoothManager, adapter, device, service);
    }

    @Test
    public void testNotifySafely() {
        Runnable notification = mock(Runnable.class);
        Logger logger = mock(Logger.class);
        String message = "Error!";
        TinyBFactory.notifySafely(notification, logger, message);
        verify(notification, times(1)).run();
        verifyNoMoreInteractions(logger);

        RuntimeException ex = new RuntimeException();
        doThrow(ex).when(notification).run();
        TinyBFactory.notifySafely(notification, logger, message);
        verify(notification, times(2)).run();
        verify(logger, times(1)).error(message, ex);
    }

    @Test
    public void testGetNotificationService() {
        //assertNotNull(TinyBFactory.getNotificationService());
    }
}
