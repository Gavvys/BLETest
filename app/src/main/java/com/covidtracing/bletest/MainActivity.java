package com.covidtracing.bletest;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.covidtracing.bletest.Toaster;

public class MainActivity extends AppCompatActivity implements NewBluetoothDeviceListener {
    private final int ALLOW_BLUETOOTH_SCAN_REQUEST_CODE = 3;
    BluetoothAdapter blAdapter;
    ActivityResultLauncher<Intent> activityResultLauncher;
    boolean isLocPermissionGranted;

    Button btnStartScan, btnAdvertise, btnFineLocPermissions, btnCheckBleSupport, btnMore, btnNow;
    EditText editMessage;
    TextView textPayload, textInbox, textNow;
    PopupMenu menuMore;

    BLEScanner scanner;
    BLEAdvertiser advertiser;
    Toaster toaster;

    ArrayList<Device> devices;
    ArrayList<BluetoothDevice> bluetoothDevices;
    RecyclerView recyclerView;
    ListView listView;

    DeviceListAdapter deviceAdapter;
    ArrayAdapter<BluetoothDevice> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestBtConnectPermissions();
        requestStoragePermissions();

        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setMessage(R.string.startup_message)
                .setNegativeButton("OK", null);

        devices = new ArrayList<>();
        bluetoothDevices = new ArrayList<>();
        deviceAdapter = new DeviceListAdapter(devices);

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setAdapter(deviceAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // --
        listView = findViewById(R.id.list_view);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, bluetoothDevices);
        listView.setAdapter(adapter);

        scanner = new BLEScanner(this);
        advertiser = new BLEAdvertiser(this);
        toaster = new Toaster(this);

        scanner.setNewDeviceListener(this);

        btnStartScan = findViewById(R.id.btn_scan);
        btnStartScan.setOnClickListener(view -> {
            if(((LocationManager)getSystemService(LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                if (!scanner.isScanning()) {
                    btnStartScan.setText("Stop scanning");
                    scanner.startBLEScan();
                } else {
                    btnStartScan.setText("Start scanning");
                    scanner.stopBLEScan();
                }
            } else {
                Toast.makeText(this, "Location services are not enabled!", Toast.LENGTH_SHORT).show();
            }
        });

        btnAdvertise = findViewById(R.id.btn_advertise);
        btnAdvertise.setOnClickListener(view -> {
            if(btnAdvertise.getText().toString().equals("Start Advertising")) {
                if(advertiser.startAdvertising())
                    btnAdvertise.setText("Stop Advertising");
            } else if (btnAdvertise.getText().toString().equals("Stop Advertising")) {
                if (advertiser.stopAdvertising())
                    btnAdvertise.setText("Start Advertising");
            }
        });

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            btnStartScan.setEnabled(false);
            btnAdvertise.setEnabled(false);
        }

        btnFineLocPermissions = findViewById(R.id.btn_fine_location_permissions);
        btnFineLocPermissions.setOnClickListener(view1 -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 2);
                else
                    Toast.makeText(this, "Location permissions already granted.", Toast.LENGTH_SHORT).show();
            }
        });

        btnCheckBleSupport = findViewById(R.id.btn_check_ble_support);
        btnCheckBleSupport.setOnClickListener(view -> {
            BluetoothLeAdvertiser LE = null;
            try {
                LE = ((BluetoothManager)getApplicationContext().getSystemService(BLUETOOTH_SERVICE)).getAdapter().getBluetoothLeAdvertiser();
            } catch (NullPointerException e) {
                e.printStackTrace();
            } finally {
                if (LE != null) Toast.makeText(this, "Bluetooth LE is supported!", Toast.LENGTH_SHORT).show();
                else Toast.makeText(this, "Bluetooth LE is not supported!", Toast.LENGTH_SHORT).show();
            }
        });

        btnMore = findViewById(R.id.btn_more);
        menuMore = new PopupMenu(this, btnMore);
        menuMore.inflate(R.menu.file_menu);
        menuMore.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.file_load) {
                saveOrLoadToFile(1);
            } else if (id == R.id.file_save) {
                saveOrLoadToFile(0);
            }
            return true;
        });

        btnMore.setOnClickListener(view -> menuMore.show());

        textNow = findViewById(R.id.text_now);
        btnNow = findViewById(R.id.btn_now);
        btnNow.setOnClickListener(view -> {
            textNow.setText(String.format("%s", System.currentTimeMillis()));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 3);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        requestPermissions(new String[]{Manifest.permission.MANAGE_EXTERNAL_STORAGE}, 4);
                    }
                } else if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    toaster.toast("WRITE_EXTERNAL_STORAGE is granted.");
                }
            }
        });

        textPayload = findViewById(R.id.text_payload);
        editMessage = findViewById(R.id.edit_message);
        editMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                textPayload.setText(charSequence.toString().getBytes(StandardCharsets.UTF_8).length + " / 37");
                advertiser.setMessage(editMessage.getText().toString());
            }
            @Override public void afterTextChanged(Editable editable) { }
        });

        BluetoothManager blManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        blAdapter = blManager.getAdapter();

        registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                result -> {}).launch(Manifest.permission.ACCESS_FINE_LOCATION);

        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        dialog.show();
                    }
                });
        // ACCESS_FINE_LOCATION is required starting Android M
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            isLocPermissionGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void saveOrLoadToFile(int op) {
        try {
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "DEMO.txt");
            if (op == 0) { // Save
                file.createNewFile();
                file.setReadable(true);

                FileOutputStream stream = new FileOutputStream(file, false);
                stream.write(editMessage.getText().toString().getBytes(StandardCharsets.UTF_8));
                stream.flush();
                stream.close();
                Toast.makeText(this, "DEMO.txt saved!", Toast.LENGTH_SHORT).show();
            } else if (op == 1) { // Load
                if (file.exists()) {
                    FileInputStream stream = new FileInputStream(file);
                    InputStreamReader reader = new InputStreamReader(stream);
                    BufferedReader bufferedReader = new BufferedReader(reader);
                    String temp;
                    StringBuilder stringBuilder = new StringBuilder();

                    while ((temp = bufferedReader.readLine()) != null)
                        stringBuilder.append("\n").append(temp);

                    bufferedReader.close();
                    reader.close();
                    stream.close();
                    editMessage.setText(stringBuilder.toString());

                    Toast.makeText(this, "DEMO.txt loaded!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "DEMO.txt doesn't exist!", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If bluetooth is disabled, enable it. If already on, then get instance of BLE Scanner
        if (blAdapter != null) {
            if (!blAdapter.isEnabled()) {
                promptEnableBL();
            }
        }
    }

    // Shows dialog asking to turn on Bluetooth
    private void promptEnableBL() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        activityResultLauncher.launch(intent);
    }

    private void requestBtConnectPermissions() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        2);
            }
        }
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()){
                Intent getPermission = new Intent();
                getPermission.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(getPermission);
            }
        }
    }

    public void displayMessage(String s) {
        textInbox.setText(s);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 3) {
            toaster.toast("Write permissions just have been granted!");
        }
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
    // Execute on permission prompt

    @Override
    public void onNewBluetoothDeviceDetected(Device device) {
        AtomicBoolean isNewDevice = new AtomicBoolean(true);

        requestStoragePermissions();

        runOnUiThread(()-> {
            for(int q = 0; q < devices.size(); q++) {
                Device foc = devices.get(q);
                if (device.getAddress().equals(foc.getAddress())) { // Device with that address is already in the list
                    foc.addHit();
                    foc.addNewHit(Integer.toString(foc.getRssi()));
                    foc.setRssi(device.getRssi());
                    isNewDevice.set(false);
                    deviceAdapter.notifyItemChanged(q);
                    break;
                }
            }
        });

        if (isNewDevice.get()) { // New Device
            device.createNewCsv();
            devices.add(device);
            deviceAdapter.notifyItemInserted(devices.size()-1);
        }
    }
}