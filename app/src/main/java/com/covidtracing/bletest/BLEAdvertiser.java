package com.covidtracing.bletest;

import android.Manifest;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class BLEAdvertiser {
    private final String TAG = "BLEAdvertiser";
    private final UUID serviceUuid;
    private final Toaster toaster;

    private BluetoothLeAdvertiser advertiser;
    private AdvertiseData.Builder advertiseData, scanResponse;
    private AdvertiseSettings.Builder settings;
    private AdvertiseCallback callback;
    private final Context context;
    private String message = "";

    ActivityResultLauncher<String> requestBLEAdvertiseLauncher;

    private boolean isAdvertising;

    public BLEAdvertiser (@NonNull Context context) {
        this.context = context;
        this.isAdvertising = false;
        this.serviceUuid = UUID.fromString(BuildConfig.BLE_SERVICE_UUID);
        toaster = new Toaster(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && this.context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_DENIED) {
            requestBLEAdvertiseLauncher = ((AppCompatActivity) this.context).registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> toaster.toast("BLUETOOTH_ADVERTISE Permission Granted!"));

            requestBLEAdvertiseLauncher.launch(Manifest.permission.BLUETOOTH_ADVERTISE);
        }

        initAdvertisingParams();
    }

    private void initAdvertisingParams() {
        advertiseData = new AdvertiseData.Builder();
        scanResponse = new AdvertiseData.Builder();
        settings = new AdvertiseSettings.Builder();

        try {
            advertiser = ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter().getBluetoothLeAdvertiser();
        } catch (NullPointerException ignored) {  }
        finally {
            if (advertiser == null) {
                toaster.toast("Bluetooth LE is not supported by this device!");
            }
        }

        advertiseData.addServiceUuid(new ParcelUuid(serviceUuid));

        scanResponse.setIncludeDeviceName(true);

        //advertiseData.addServiceData(new ParcelUuid(characteristicUuid), message.getBytes(StandardCharsets.UTF_8));
        Log.i(TAG, "Total payload: " + BuildConfig.BLE_SERVICE_UUID.getBytes(StandardCharsets.UTF_8).length);

        settings.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(60000); // Advertise for 10 seconds at most

        callback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                isAdvertising = true;
                toaster.toast("BLE advertising started! With message "
                        + new String(message.getBytes(StandardCharsets.UTF_8)));
                Log.v(TAG, "Advertising started!");
            }
            @Override
            public void onStartFailure(int errorCode) {
                super.onStartFailure(errorCode);

                // Advertising Errors
                if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED) {
                    toaster.toast("Advertising already started!");
                    Log.e(TAG, "Advertising already started!");
                } else if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE) {
                    toaster.toast("Advertising data is too large!");
                    Log.e(TAG, "Advertising data is too large!");
                } else if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED) {
                    toaster.toast("The device does not support BLE advertising!");
                    Log.e(TAG, "The device does not support BLE advertising!");
                } else if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS) {
                    toaster.toast("No available advertisers!");
                    Log.e(TAG, "No available advertisers!");
                } else if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR) {
                    toaster.toast("Unknown advertising error!");
                    Log.e(TAG, "Unknown error!");
                }
                isAdvertising = false;
            }
        };
    }

    public boolean startAdvertising() {
        if (advertiser != null) {
            if (!isAdvertising) {
                advertiser.startAdvertising(settings.build(), advertiseData.build(), scanResponse.build(), callback);
                return true;
            }
        } else {
            toaster.toast("BLE advertising is not supported!");
            return false;
        }
        return false;
    }
    public boolean stopAdvertising() {
        advertiser.stopAdvertising(callback);
        Log.i(TAG, "Advertising stopped!");
        isAdvertising = false;
        return true;
    }

    public void setMessage(String m) {
        message = m;
    }

    public void setTimeout(int t) {
        settings.setTimeout(t);
        toaster.toast("Advertising timeout set.");
    }

    public void setAdvertisingMode(String s) {
        switch (s) {
            case "MODE_LOW_POWER":
                settings.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER);
                break;
            case "MODE_LOW_LATENCY":
                settings.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
                break;
            case "MODE_BALANCED":
                settings.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
                break;
        }
    }

    public void setTxPowerLevel(String s) {
        switch (s) {
            case "TX_POWER_ULTRA_LOW":
                settings.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW);
                break;
            case "TX_POWER_LOW":
                settings.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW);
                break;
            case "TX_POWER_MEDIUM":
                settings.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);
                break;
            case "TX_POWER_HIGH":
                settings.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
                break;
        }
    }
}
