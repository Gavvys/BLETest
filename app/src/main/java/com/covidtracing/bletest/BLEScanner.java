package com.covidtracing.bletest;

import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.S;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Collections;
import java.util.UUID;

public class BLEScanner {
    private final String TAG = "BLEScanner";
    private final UUID serviceUUID;
    private final UUID characteristicUUID;
    private final Toaster toaster;

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private ScanSettings scanSettings;
    private ScanFilter scanFilter;
    private ScanCallback scanCallback;
    private BluetoothLeScanner scanner = null;

    private NewBluetoothDeviceListener newDeviceListener;
    ActivityResultLauncher<String> requestLocLauncher;
    ActivityResultLauncher<String> requestBLEScanLauncher;
    ActivityResultLauncher<String> requestBLEAdvertiseLauncher;

    private boolean isFineLocPermissionGranted;
    private boolean isScanning;

    public BLEScanner (@NonNull Context context) {
        this.context = context;
        this.bluetoothAdapter = ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        this.serviceUUID = UUID.fromString(com.covidtracing.bletest.BuildConfig.BLE_SERVICE_UUID);
        this.characteristicUUID = UUID.fromString(com.covidtracing.bletest.BuildConfig.BLE_CHARACTERISTIC_UUID_1);
        this.toaster = new Toaster(context);

        try {
            this.scanner = bluetoothAdapter.getBluetoothLeScanner();
        } catch (NullPointerException e) { e.printStackTrace(); }
        finally {
            if (scanner == null) {
                toaster.toast("Bluetooth LE is not supported by this device!");
            }
        }

        if (Build.VERSION.SDK_INT >= M){
            isFineLocPermissionGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else isFineLocPermissionGranted = true;

        initScanningParams();

        requestLocLauncher = ((AppCompatActivity) context).registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        isFineLocPermissionGranted = true;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            if (!((LocationManager) context.getSystemService(Context.LOCATION_SERVICE)).isLocationEnabled()) {
                                promptEnableLoc();
                            } else {
                                startBLEScan();
                            }
                        }
                        startBLEScan();
                    });

        if (Build.VERSION.SDK_INT >= S && context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED) {
            requestBLEScanLauncher = ((AppCompatActivity) context).registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> toaster.toast("BLUETOOTH_SCAN Permission Granted!"));

            requestBLEScanLauncher.launch(Manifest.permission.BLUETOOTH_SCAN);
        }

        checkLocPermission();
        if (Build.VERSION.SDK_INT >= S) {
            checkBLEScanPermission();
        }
    }

    // Start BLE scan
    public void startBLEScan () {
        // Check if Bluetooth is enabled
        if (bluetoothAdapter.isEnabled()) {
            // If not scanning for BlE devices, commence scanning
            if (!isScanning) {
                // Android Lollipop immediately allows permissions on launch
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    checkLocPermission();
                    checkBLEScanPermission();
                }

                // ACCESS_FINE_LOCATION permission is denied
                if (!isFineLocPermissionGranted) {
                    toaster.toast("Fine location permission required!");
                } else {
                    Thread startBLEScanThread = new Thread("Scanning Thread") {
                        @Override
                        public void run() {
                            // Sometimes app crashes because getBluetoothLEScanner() sometimes returns null, better catch the error
                            try { // Collections.singletonList(scanFilter)
                                scanner.startScan(Collections.singletonList(scanFilter), scanSettings, scanCallback);
                                Log.i(TAG, "Scanning started!");
                            } catch (NullPointerException e) {
                                e.printStackTrace();
                            }
                        }
                    };
                    startBLEScanThread.start();
                    toaster.toast("BLE scan started!");
                    isScanning = true;
                }
            } else {
                stopBLEScan();
                isScanning = false;
            }
        }
    }
    // Stop BLE scan
    public void stopBLEScan() {
        if (isScanning) {
            try {
                scanner.stopScan(scanCallback);
                isScanning = false;
                toaster.toast("BLE Scan stopped");
                Log.i(TAG, "Scanning stopped!");
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isScanning() {
        return isScanning;
    }

    private void initScanningParams() {
        scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanFilter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(serviceUUID)).build();
        scanCallback = new ScanCallback() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                newDeviceListener.onNewBluetoothDeviceDetected(new Device(
                        result.getDevice().getName(),
                        result.getDevice().getAddress(),
                        result.getRssi(),
                        result.getTxPower(),
                        true
                ));

                byte[] message = result.getScanRecord().getServiceData().get(new ParcelUuid(characteristicUUID));
                Log.w(TAG, "Found device! MAC Address: " + result.getDevice().getAddress()
                        + "\nResponse Length: " + (message == null ? "null" : message.length));
                if (message != null)
                    ((MainActivity)context).displayMessage(new String(message));

                /*stopBLEScan();
                result.getDevice().connectGatt(context, false, new BluetoothGattCallback() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                        super.onConnectionStateChange(gatt, status, newState);

                        if (newState == BluetoothGatt.STATE_CONNECTED) {
                            //stopBLEScan();
                            Log.i(TAG, "GATT connection successful!");
                        } else if (newState == BluetoothGatt.STATE_CONNECTING) {
                            //stopBLEScan();
                            Log.i(TAG, "Connecting to GATT server...");
                        } else if (newState == BluetoothGatt.STATE_DISCONNECTING) {
                            Log.i(TAG, "Disconnecting to GATT server...");
                        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                            Log.i(TAG, "GATT disconnected successfully!");
                        }
                    }

                    @Override
                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                        super.onServicesDiscovered(gatt, status);

                        for(BluetoothGattService gattService : gatt.getServices()) {
                            if (gattService.getUuid().equals(serviceUUID)) {
                                gattService.getCharacteristic(characteristicUUID);
                            }
                        }
                    }

                    @Override
                    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                        super.onCharacteristicRead(gatt, characteristic, status);
                    }
                });
*/

                // TODO: Calculate distance from RSSI
                // TODO: Get/Compute for txPower of device
                // RSSI Correction
                // rssi_correction(device) = AVG_RSSI(ref1 -> iphone) - AVG_RSSI(ref1 -> pixel4) + AVG_RSSI(ref2 -> pixel4) - AVG_RSSI(ref2 -> device)
            }
        };
    }

    private void checkLocPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED)
                requestLocPermission();
        }
    }

    // Starting Android S, permission to perform BLE scan is now required
    private void checkBLEScanPermission() {
        if (Build.VERSION.SDK_INT >= S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED) {
                requestBluetoothScanPermission();
            }
        }
    }

    @RequiresApi(api = S)
    private void checkBLEAdvertisePermission() {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED)
            requestBluetoothAdvertisePermission();
    }

    private void requestLocPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            requestLocLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        /*AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
        alertDialog.setTitle("Location Permission Granted")
                .setMessage("Starting from Android M (6.0), the system requires apps to be granted location access in order to scan for BLE devices.")
                .setPositiveButton("OK", (dialogInterface, i) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocPermissionGranted) {
                        ((AppCompatActivity)context).requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
                    }
                });
        alertDialog.show();*/
    }

    private void promptEnableLoc() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(context);
        dialog.setTitle("Location Services Not Active")
                .setMessage("COVIDTracing wants to enable GPS and use location services.")
                .setPositiveButton("Allow", (dialogInterface, i) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    context.startActivity(intent);
                }).setNegativeButton("Deny", null)
                .setCancelable(false).create().show();
    }

    // For Android 12+
    @RequiresApi(api = S)
    private void requestBluetoothScanPermission() {
        requestBLEScanLauncher.launch(Manifest.permission.BLUETOOTH_SCAN);
    }

    @RequiresApi(api = S)
    private void requestBluetoothAdvertisePermission() {
        requestBLEAdvertiseLauncher.launch(Manifest.permission.BLUETOOTH_ADVERTISE);
    }

    public void setNewDeviceListener(NewBluetoothDeviceListener listener) { this.newDeviceListener = listener; }
}
