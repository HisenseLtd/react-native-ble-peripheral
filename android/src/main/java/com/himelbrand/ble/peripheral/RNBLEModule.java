package com.himelbrand.ble.peripheral;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;
import java.util.ArrayList;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.himelbrand.ble.peripheral.data.DataManager;
import com.himelbrand.ble.peripheral.data.Util;


/**
 * {@link NativeModule} that allows JS to open the default browser
 * for an url.
 */
public class RNBLEModule extends ReactContextBaseJavaModule{

    private static final String TAG = "RNBLEModule";
    ReactApplicationContext reactContext;
    HashMap<String, BluetoothGattService> servicesMap;
    HashMap<String, BluetoothDevice> mBluetoothDevices;
    BluetoothManager mBluetoothManager;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothGattServer mGattServer;
    BluetoothLeAdvertiser advertiser;
    AdvertiseCallback advertisingCallback;
    String name;
    String prevName;
    boolean advertising;
    private Context context;
    private HashMap<String, Callback> RNCallbacks;

    public RNBLEModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.context = reactContext;
        this.servicesMap = new HashMap<String, BluetoothGattService>();
        this.RNCallbacks = new HashMap<>();
        this.advertising = false;
        this.name = "RN_BLE";
        this.prevName = "RN_BLE";
    }

    protected void sendEvent(ReactContext reactContext,
                             String eventName,
                             @Nullable WritableMap params) {
        if(reactContext==null)
        {
            Log.e(TAG,"sendEvent with Null Context");
            return;
        }

        if(!reactContext.hasActiveCatalystInstance()) {
            Log.e(TAG,"sendEvent with hasActiveCatalystInstance=False");
            return;
        }
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private WritableMap getWritableMap(Bundle value) {
        WritableMap args = Arguments.createMap();
        for (String bundleKey : value.keySet()) {
            String val = value.getString(bundleKey);
            args.putString(String.format("%s",bundleKey), String.format("%s",val));
        }
        return args;
    }

    @Override
    public String getName() {
        return "BLEPeripheral";
    }

    @ReactMethod
    public void setName(String name) {
        this.name = name;
        Log.i("RNBLEModule", "name set to " + name);
    }

    @ReactMethod
    public void addService(String uuid, Boolean primary) {
        UUID SERVICE_UUID = UUID.fromString(uuid);
        int type = primary ? BluetoothGattService.SERVICE_TYPE_PRIMARY : BluetoothGattService.SERVICE_TYPE_SECONDARY;
        BluetoothGattService tempService = new BluetoothGattService(SERVICE_UUID, type);
        if(!this.servicesMap.containsKey(uuid))
            this.servicesMap.put(uuid, tempService);
    }

    @ReactMethod
    public void addCharacteristicToService(String serviceUUID, String uuid, Integer permissions, Integer properties) {
        UUID CHAR_UUID = UUID.fromString(uuid);
        BluetoothGattCharacteristic tempChar = new BluetoothGattCharacteristic(CHAR_UUID, properties, permissions);
        this.servicesMap.get(serviceUUID).addCharacteristic(tempChar);
    }

    @ReactMethod
    public void addCharacteristicDescriptor(String serviceUUID, String charUUID, String charDescUUID, Integer permissions) {
        UUID CHAR_UUID = UUID.fromString(charUUID);
        UUID CHAR_DESC_UUID = UUID.fromString(charDescUUID);
        //Descriptor for read notifications
        BluetoothGattDescriptor CHAR_DESC = new BluetoothGattDescriptor(CHAR_DESC_UUID, permissions);
        this.servicesMap.get(serviceUUID).getCharacteristic(CHAR_UUID).addDescriptor(CHAR_DESC);
    }

    @ReactMethod
    public void setCallbacks(Callback onConnectionStateChangeCallback, Callback onMessageCallback) {
        this.RNCallbacks.put("onConnectionStateChange", onConnectionStateChangeCallback);
        this.RNCallbacks.put("onMessage", onMessageCallback);
    }

    @ReactMethod
    public void getConnectedDevices(Promise promise) {
        List<BluetoothDevice> devices = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
        List<String> devicesAddress = new ArrayList<String>();

        for (BluetoothDevice device : devices) {
            devicesAddress.add(device.getAddress());
        }
        
       WritableMap map = Arguments.createMap();
       String[] arr = new String[devicesAddress.size()];
       arr = devicesAddress.toArray(arr);
       map.putArray("connectedDevices", Arguments.fromArray(arr));

        promise.resolve(map);
    }

    @ReactMethod
    public void sendNotifyMessage(String deviceStr, String serviceUUID, String readCharUUID, String message) {
        UUID SERVICE_UUID = UUID.fromString(serviceUUID);
        UUID CHAR_UUID = UUID.fromString(readCharUUID);

        BluetoothDevice device = mBluetoothDevices.get(deviceStr);
        if (device == null) {
            Log.i("RNBLEModule", "sendNotifyMessage failure, device is null");
            return;
        }

        BluetoothGattCharacteristic readCharacteristic = mGattServer.getService(SERVICE_UUID)
                .getCharacteristic(CHAR_UUID);


        List messages = Util.createPacketsToSend(message.getBytes(StandardCharsets.UTF_8));

        for(int i =0; i < messages.size(); i++){
            byte[] msg =(byte[]) messages.get(i);
            readCharacteristic.setValue(msg);
            mGattServer.notifyCharacteristicChanged(device, readCharacteristic, false);
        }
    }

    @ReactMethod
    public void connectGatt(String deviceStr) {
        BluetoothDevice device = mBluetoothDevices.get(deviceStr);
        if (device == null) {
            Log.i("RNBLEModule", "connectGatt Failed, device is null");
            return;
        }

        mGattServer.connect(device, false);
    }


    private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, final int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    if(!mBluetoothDevices.containsKey(device.toString()))
                        mBluetoothDevices.put(device.toString(), device);
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    mBluetoothDevices.remove(device.toString());
                }
            } else {
                mBluetoothDevices.remove(device.toString());
            }

           Bundle b = new Bundle();
           b.putString("device", device.toString());
           b.putString("status", getStatusDescription(status));
           b.putString("newState",getStateDescription(newState));
           WritableMap map = getWritableMap(b);

           sendEvent(reactContext, "ConnectionStateChange", map);
            // RNCallbacks.get("onConnectionStateChange").invoke(device.toString(), getStatusDescription(status), getStateDescription(newState));
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

            Bundle b = new Bundle();
            b.putString("device", device.toString());
            b.putString("requestId", Integer.toString(requestId));
            b.putString("offset", Integer.toString(offset));
            b.putString("characteristic", characteristic.toString());

            WritableMap map = getWritableMap(b);

            sendEvent(reactContext, "onCharacteristicReadRequest", map);

            if (offset != 0) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset,
                        /* value (optional) */ null);
                return;
            }
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                    offset, characteristic.getValue());
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite,
                    responseNeeded, offset, value);

            if (responseNeeded) {
                mGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        value);
            }
            characteristic.setValue(value);

//            WritableMap map = Arguments.createMap();
//            WritableArray data = Arguments.createArray();
//            for (byte b : value) {
//                data.pushInt((int) b);
//            }
//            map.putArray("data", data);
//            map.putString("device", device.toString());

//            DataManager dataManager = DataManager.getInstance();
//            dataManager.addPacket(value);
//            if (dataManager.isMessageComplete()) {
//                byte[] completeMessage = dataManager.getTheCompleteMesssage();
//                dataManager.clear();

           Bundle b = new Bundle();
           b.putString("device", device.toString());
           b.putString("requestId", Integer.toString(requestId));
           b.putString("characteristic", characteristic.toString());
           b.putString("preparedWrite", Boolean.toString(preparedWrite));
           b.putString("offset", Integer.toString(offset));
           b.putString("value",new String(value, StandardCharsets.UTF_8));
//            b.putString("value",new String(completeMessage, StandardCharsets.UTF_8));

               WritableMap map = getWritableMap(b);

               sendEvent(reactContext, "onMessage", map);
//            }
            // String message = new String(value, StandardCharsets.UTF_8);
            // RNCallbacks.get("onMessage").invoke(device, requestId, characteristic, preparedWrite, offset, message);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);

            //NOTE: Its important to send response. It expects response else it will disconnect
            if (responseNeeded) {
                mGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        value);
            }
        }
    };

    @ReactMethod
    public void start(){
        mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        // Ensures Bluetooth is available on the device and it is enabled. If not,
// displays a dialog requesting user permission to enable Bluetooth.
    }

    @ReactMethod
    public void startAdvertising(final Promise promise) {
        this.prevName = mBluetoothAdapter.getName();
        mBluetoothAdapter.setName(this.name);
        mBluetoothDevices = new HashMap<>();
        mGattServer = mBluetoothManager.openGattServer(reactContext, mGattServerCallback);
        for (BluetoothGattService service : this.servicesMap.values()) {
            mGattServer.addService(service);
        }
        advertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();


        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder()
                .setIncludeDeviceName(true);
        for (BluetoothGattService service : this.servicesMap.values()) {
            dataBuilder.addServiceUuid(new ParcelUuid(service.getUuid()));
        }
        AdvertiseData data = dataBuilder.build();

        advertisingCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                advertising = true;
                promise.resolve("Success, Started Advertising");
            }

            @Override
            public void onStartFailure(int errorCode) {
                advertising = false;
                Log.e("RNBLEModule", "Advertising onStartFailure: " + errorCode);
                promise.reject("Advertising onStartFailure: " + errorCode);
                super.onStartFailure(errorCode);
                mBluetoothAdapter.setName(prevName);
            }
        };

        advertiser.startAdvertising(settings, data, advertisingCallback);

    }
    @ReactMethod
    public void stop(){
        if (mGattServer != null) {
            mGattServer.close();
        }
        stopAdvertising();
    }

    @ReactMethod
    public void stopAdvertising(){
        if (mBluetoothAdapter !=null && mBluetoothAdapter.isEnabled() && advertiser != null) {
            // If stopAdvertising() gets called before close() a null
            // pointer exception is raised.
            advertiser.stopAdvertising(advertisingCallback);
                mBluetoothAdapter.setName(this.prevName);
            
        }
        advertising = false;
    }
    
    @ReactMethod
    public void sendNotificationToDevices(String serviceUUID,String charUUID,ReadableArray message) {
        byte[] decoded = new byte[message.size()];
        for (int i = 0; i < message.size(); i++) {
            decoded[i] = new Integer(message.getInt(i)).byteValue();
        }
        BluetoothGattCharacteristic characteristic = servicesMap.get(serviceUUID).getCharacteristic(UUID.fromString(charUUID));
        characteristic.setValue(decoded);
        boolean indicate = (characteristic.getProperties()
                & BluetoothGattCharacteristic.PROPERTY_INDICATE)
                == BluetoothGattCharacteristic.PROPERTY_INDICATE;
        for (BluetoothDevice device : mBluetoothDevices.values()) {
            // true for indication (acknowledge) and false for notification (un-acknowledge).
            mGattServer.notifyCharacteristicChanged(device, characteristic, indicate);
        }
    }
    @ReactMethod
    public void isAdvertising(Promise promise){
        promise.resolve(this.advertising);
    }


    public static String getStateDescription(int state) {
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
                return "Connected";
            case BluetoothProfile.STATE_CONNECTING:
                return "Connecting";
            case BluetoothProfile.STATE_DISCONNECTED:
                return "Disconnected";
            case BluetoothProfile.STATE_DISCONNECTING:
                return "Disconnecting";
            default:
                return "Unknown State " + state;
        }
    }


    public static String getStatusDescription(int status) {
        switch (status) {
            case BluetoothGatt.GATT_SUCCESS:
                return "SUCCESS";
            default:
                return "Unknown Status " + status;
        }
    }
}
