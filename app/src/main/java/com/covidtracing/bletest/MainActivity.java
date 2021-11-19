package com.covidtracing.bletest;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private final int ENABLE_BLUETOOTH_REQUEST_CODE = 1;
    private final int LOCATION_PERMISSION_REQUEST_CODE = 2;
    private final int ALLOW_BLUETOOTH_SCAN_REQUEST_CODE = 3;
    BluetoothAdapter blAdapter;
    ActivityResultLauncher<Intent> activityResultLauncher;
    boolean isLocPermissionGranted;

    Button btnStartScan;

    // ScanFilter.Builder filter;
    ScanSettings.Builder scanSettings;
    ScanCallback scanCallback;
    BluetoothLeScanner scanner;

    ArrayList<Device> devices;
    RecyclerView recyclerView;
    DeviceListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        devices = new ArrayList<>();
        adapter = new DeviceListAdapter(devices);

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        btnStartScan = findViewById(R.id.btn_scan);
        btnStartScan.setOnClickListener(view -> {
            if(btnStartScan.getText().toString().equals("Start BLE Scan")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (!((LocationManager)getSystemService(LOCATION_SERVICE)).isLocationEnabled()) {
                        promptEnableLoc();
                    } else startBLEScan();
                } else startBLEScan();
            } else if (btnStartScan.getText().toString().equals("Stop BLE Scan")) {
                stopBLEScan();
            }
        });
        BluetoothManager blManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        blAdapter = blManager.getAdapter();

        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() != Activity.RESULT_OK) {
                        promptEnableBL();
                    } else  {
                        scanner = blAdapter.getBluetoothLeScanner();
                    }
                });

        // checkSelfPermission is from API 23 (M)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            isLocPermissionGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        scanSettings = new ScanSettings.Builder();
        scanSettings.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        scanCallback = new ScanCallback() {
            BluetoothDevice resultDevice;
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                int rssi = result.getRssi();

                // TODO: Calculate distance from RSSI
                // TODO: Get/Compute for txPower of device
                // RSSI Correction
                // rssi_correction(device) = AVG_RSSI(ref1 -> iphone) - AVG_RSSI(ref1 -> pixel4) + AVG_RSSI(ref2 -> pixel4) - AVG_RSSI(ref2 -> device)
                float dist = 9f; // Well rip

                if(!Device.contains(devices, result.getDevice().toString())) {
                    resultDevice = result.getDevice();
                    devices.add(new Device(result.getScanRecord().getDeviceName(), resultDevice.toString(), Integer.toString(rssi)));
                    adapter.notifyItemInserted(devices.size()-1);
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If bluetooth is disabled, enable it lmao. If already on, then get instance of BLE Scanner
        if (!blAdapter.isEnabled()) {
            promptEnableBL();
        } else scanner = blAdapter.getBluetoothLeScanner();
    }

    // Shows dialog asking to turn on Bluetooth
    private void promptEnableBL() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        activityResultLauncher.launch(intent);
    }

    // Show dialog that asks to turn on GPS
    private void promptEnableLoc() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Location Services Not Active")
                .setMessage("COVIDTracing wants to enable GPS and use location services.")
                .setPositiveButton("Allow", (dialogInterface, i) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                }).setNegativeButton("Deny", null)
                .setCancelable(false).create().show();
    }

    // Starts a thread to scan for BLE devices
    private void startBLEScan () {
        btnStartScan.setText("Stop BLE Scan");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isLocPermissionGranted)
                requestLocationPermission();

            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED) {
                requestBluetoothScanPermission();
            }
        }
        Toast.makeText(this, "BLE Scan started", Toast.LENGTH_SHORT).show();
        scanner.startScan(null, scanSettings.build(), scanCallback);
    }

    private void stopBLEScan() {
        btnStartScan.setText("Start BLE Scan");
        scanner.stopScan(scanCallback);
    }

    // Ask for permission to perform Bluetooth scans
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestBluetoothScanPermission() {
        runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN}, ALLOW_BLUETOOTH_SCAN_REQUEST_CODE);
            }
        });
    }

    // Ask for permission to use ACCESS_FINE_LOCATION
    private void requestLocationPermission() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle("Location Permission Granted")
                .setMessage("Starting from Android M (6.0), the system requires apps to be granted location access in order to scan for BLE devices.")
                .setPositiveButton("OK", (dialogInterface, i) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocPermissionGranted) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
                    }
                });
        alertDialog.show();
    }

    // Execute on permission prompt
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                requestLocationPermission();
            } else  {
                startBLEScan();
            }
        }
    }
}