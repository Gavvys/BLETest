package com.covidtracing.bletest;

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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity implements NewBluetoothDeviceListener, AdapterView.OnItemSelectedListener {
    BluetoothAdapter blAdapter;
    ActivityResultLauncher<Intent> activityResultLauncher;

    Button btnMore, btnSetAdvertiseTimeout;
    ToggleButton btnStartScan, btnAdvertise;
    EditText editMessage, editAdvertiseTimeout;
    TextView textPayload, textInbox;
    PopupMenu menuMore;
    Spinner spinAdvertise, spinTx, spinScan;

    BLEScanner scanner;
    BLEAdvertiser advertiser;

    AlertDialog.Builder dialog;
    Toaster toaster;

    ArrayList<Device> devices;
    ArrayList<BluetoothDevice> bluetoothDevices;
    RecyclerView recyclerView;

    DeviceListAdapter deviceAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //requestStoragePermissions(); commented out because the app doesn't require MANAGE_EXTERNAL_STORAGE permission

        dialog = new AlertDialog.Builder(this)
                .setMessage(R.string.startup_message)
                .setNegativeButton("OK", null);

        //region Initializations
        devices = new ArrayList<>();
        bluetoothDevices = new ArrayList<>();
        deviceAdapter = new DeviceListAdapter(devices);

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setAdapter(deviceAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        scanner = new BLEScanner(this);
        scanner.setNewDeviceListener(this);
        advertiser = new BLEAdvertiser(this);
        blAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

        toaster = new Toaster(this);
        //endregion

        btnStartScan = findViewById(R.id.btn_scan);
        btnStartScan.setChecked(false);
        btnStartScan.setOnCheckedChangeListener((compoundButton, checked) -> {
            if (checked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_DENIED) {
                        if (((LocationManager) getSystemService(LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            if (!scanner.isScanning())
                                scanner.startBLEScan();
                        } else {
                            btnStartScan.setChecked(false); // Because GPS is not enabled
                            toaster.toast("Location services are not enabled!");
                        }
                    } else btnStartScan.setChecked(false);
                }
            } else {
                if (scanner.isScanning()) scanner.stopBLEScan();
            }
        });

        btnAdvertise = findViewById(R.id.btn_advertise);
        btnAdvertise.setOnCheckedChangeListener((compoundButton, status) -> {
            if (((LocationManager)getSystemService(LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                if (status) advertiser.startAdvertising();
                else advertiser.stopAdvertising();
            } else {
                btnStartScan.setChecked(false); // Because GPS is not enabled
                toaster.toast("Location services are not enabled!");
            }
        });

        ArrayList<String> spinAdvertiseList = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.str_array_advertise_modes)));
        ArrayAdapter<String> spinAdvertiseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, spinAdvertiseList);
        spinAdvertise = findViewById(R.id.spinner_advertise_mode);
        spinAdvertise.setAdapter(spinAdvertiseAdapter);
        spinAdvertise.setOnItemSelectedListener(this);

        ArrayList<String> spinTxList = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.str_array_tx_power_level)));
        ArrayAdapter<String> spinTxAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, spinTxList);
        spinTx = findViewById(R.id.spinner_tx_power_level);
        spinTx.setAdapter(spinTxAdapter);
        spinTx.setOnItemSelectedListener(this);

        ArrayList<String> spinScanList = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.str_array_scan_modes)));
        ArrayAdapter<String> spinScanAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, spinScanList);
        spinScan = findViewById(R.id.spinner_scan_mode);
        spinScan.setAdapter(spinScanAdapter);
        spinScan.setOnItemSelectedListener(this);

        btnMore = findViewById(R.id.btn_more);
        menuMore = new PopupMenu(this, btnMore);
        menuMore.inflate(R.menu.menu_file_ops);
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

        textPayload = findViewById(R.id.text_payload);
        editMessage = findViewById(R.id.edit_message);
        editMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                textPayload.setText(String.format(Locale.getDefault(), "%d / 37", charSequence.toString().length()));
                advertiser.setMessage(editMessage.getText().toString());
            }
            @Override public void afterTextChanged(Editable editable) { }
        });
        editAdvertiseTimeout = findViewById(R.id.edit_advertise_timeout);
        editAdvertiseTimeout.setText(String.format(Locale.getDefault(), "%s", 60000));

        btnSetAdvertiseTimeout = findViewById(R.id.btn_set_advertise_timeout);
        btnSetAdvertiseTimeout.setOnClickListener(view -> {
            if (editAdvertiseTimeout.getText().length() != 0) {
                int input = Integer.parseInt(editAdvertiseTimeout.getText().toString());
                if (input <= 180000) {
                    advertiser.setTimeout(Integer.parseInt(editAdvertiseTimeout.getText().toString()));
                } else toaster.toast("Input is too large! (>180000)");
            } else toaster.toast("Input cannot be blank!");
        });

        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        dialog.show();
                    }
                });
    }

    private void saveOrLoadToFile(int op) {
        try {
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "DEMO.txt");
            if (op == 0) { // Save
                boolean isNewFileCreateSuccess = file.createNewFile();

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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_action_bar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected (MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_check_ble_support) {
            checkBLESupport();
        } else if (id == R.id.menu_check_loc_perms) {
            checkFineLocationPermissions();
        } else if (id == R.id.menu_check_storage_perms) {
            checkStoragePerms();
        } else if (id == R.id.menu_check_bluetooth_perms) {
            checkBluetoothPerms();
        }
        return false;
    }

    private void checkBluetoothPerms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
            }, 7);
        }
    }

    private void checkFineLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 2);
            else toaster.toast("Location permissions already granted.");
        } else {
            toaster.toast("Location permissions already granted.");
        }
    }

    private void checkBLESupport() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            btnStartScan.setEnabled(false);
            btnAdvertise.setEnabled(false);
        }

        BluetoothLeAdvertiser LE = null;
        try {
            LE = ((BluetoothManager)getApplicationContext().getSystemService(BLUETOOTH_SERVICE)).getAdapter().getBluetoothLeAdvertiser();
        } catch (NullPointerException e) {
            e.printStackTrace();
        } finally {
            if (LE != null) toaster.toast("Bluetooth LE is supported!");
            else toaster.toast("Bluetooth LE is not supported!");
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 6);
            }
        }
    }

    private void requestBtScanPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN}, 7);
            }
        }
    }

    private void checkStoragePerms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED )
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
            else toaster.toast("WRITE_EXTERNAL_STORAGE is already granted.");

            if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED)
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 3);
            else toaster.toast("READ_EXTERNAL_STORAGE is already granted.");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (checkSelfPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.MANAGE_EXTERNAL_STORAGE}, 4);
                else toaster.toast("MANAGE_EXTERNAL_STORAGE is already granted.");
            }
        }

        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()){
                Intent getPermission = new Intent();
                getPermission.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(getPermission);
            }
        }
    }

    public void displayMessage(String s) { textInbox.setText(s); }

    @Override
    public void onNewBluetoothDeviceDetected(Device device) {
        AtomicBoolean isNewDevice = new AtomicBoolean(true);

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

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        String temp = adapterView.getItemAtPosition(i).toString();
        if (adapterView.getId() == R.id.spinner_advertise_mode) {
            advertiser.setAdvertisingMode(adapterView.getItemAtPosition(i).toString());
            toaster.toast("Set advertising mode to " + temp);
        } else if (adapterView.getId() == R.id.spinner_tx_power_level) {
            advertiser.setTxPowerLevel(adapterView.getItemAtPosition(i).toString());
            toaster.toast("Set tx power level to " + temp);
        } else if (adapterView.getId() == R.id.spinner_scan_mode) {
            if (adapterView.getItemAtPosition(i).toString().equals("MODE_OPPORTUNISTIC")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    scanner.setScanMode(adapterView.getItemAtPosition(i).toString());
                    toaster.toast("Set scan mode to " + temp);
                } else toaster.toast("MODE_OPPORTUNISTIC requires at least Android 6.0.");
            } else {
                scanner.setScanMode(adapterView.getItemAtPosition(i).toString());
                toaster.toast("Set scan mode to " + temp);
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) { } // Unused
}