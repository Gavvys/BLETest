package com.covidtracing.bletest;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class Device {
    private String name;
    private String address;
    private int rssi;
    private int txPowerLvl;
    private int hits;
    private long firstHit, latestHit;

    final String filename;
    private File csvFile;
    private FileOutputStream writer;

    private LogManager manager;

    public Device (String nam, String add, int rssi, int txPowerLvl, boolean temp) {
        this.name = nam;
        this.address = add;
        this.rssi = rssi;
        this.txPowerLvl = txPowerLvl;
        this.hits = 1;
        firstHit = System.currentTimeMillis();
        latestHit = firstHit;

        String f = firstHit + "_" + this.address + ".csv";
        filename = f.replace(":", "-");

        if (!temp) {
            createNewCsv();
        }
    }

    public void createNewCsv () {
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "/covidtracer/");
        if (!folder.exists()) folder.mkdir();
        csvFile = new File(folder, filename);
        try {
            csvFile.createNewFile();
            Log.i("Device::createNewFile", "New file created: " + filename);
            manager = new LogManager(csvFile);
            writer = new FileOutputStream(csvFile, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addNewHit(String text) {
        try {
            writer.write((text + "," + (System.currentTimeMillis()-latestHit)).getBytes(StandardCharsets.UTF_8));
            writer.write(10);
            latestHit = System.currentTimeMillis();
                    writer.flush();
        } catch (IOException e) {
            Log.e("Device Class", "Failed to create file.");
            e.printStackTrace();
        }
    }

    public static boolean contains(ArrayList<Device> deviceList, String address) {
        if(deviceList.size() > 0) {
            for (Device y : deviceList) {
                if (y.address.equals(address)) return true;
            }
        }

        return false;
    }

    public void recordNewEntry() {

    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAddress(String addr) {
        this.address = addr;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public void setTxPowerLvl(int lvl) {
        this.txPowerLvl = lvl;
    }

    public void addHit() { this.hits++; }

    public String getName() {
        return this.name;
    }

    public String getAddress() {
        return this.address;
    }

    public int getRssi() { return this.rssi; }

    public int getTxPowerLvl() {
        return this.txPowerLvl;
    }

    public int getHits() { return this.hits; }
}
