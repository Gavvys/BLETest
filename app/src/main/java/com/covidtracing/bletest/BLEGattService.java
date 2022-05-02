package com.covidtracing.bletest;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class BLEGattService {
    private final UUID serviceUuid;
    private final UUID characteristicUUID;

    BluetoothGattServer server;
    BluetoothGattService service;
    BluetoothGattServerCallback callback;

    BLEAdvertiser advertiser;

    private final Context context;
    private final String message = "";

    public BLEGattService (Context c, BLEAdvertiser advertiser) {
        this.context = c;
        this.advertiser = advertiser;
        this.serviceUuid = UUID.fromString(BuildConfig.BLE_SERVICE_UUID);
        this.characteristicUUID = UUID.fromString(BuildConfig.BLE_CHARACTERISTIC_UUID_1);

        callback = new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                super.onConnectionStateChange(device, status, newState);
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

                byte[] response = {};

                if(characteristic.getUuid() == characteristicUUID) {
                    response = message.getBytes(StandardCharsets.UTF_8);
                }

                server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, response);
            }

            @Override
            public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
                super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            }
        };

        server = ((BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE)).openGattServer(context, callback);
        service = new BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY);
    }
}
