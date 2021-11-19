package com.covidtracing.bletest;

import java.util.ArrayList;

public class Device {

    private String name;
    private String address;
    private String rssi;

    public Device (String n, String add, String rssi) {
        this.name = n;
        this.address = add;
        this.rssi = rssi;
    }

    public static boolean contains(ArrayList<Device> deviceList, String address) {
        if(deviceList.size() > 0) {
            for (Device y : deviceList) {
                if (y.address.equals(address)) return true;
            }
        }

        return false;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAddress(String addr) {
        this.address = addr;
    }

    public void setRssi(String rssi) {
        this.rssi = rssi;
    }

    public String getName() {
        return this.name;
    }

    public String getAddress() {
        return this.address;
    }

    public String getRssi() {
        return this.rssi;
    }
}
