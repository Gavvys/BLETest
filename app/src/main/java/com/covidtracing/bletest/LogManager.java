package com.covidtracing.bletest;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class LogManager {
    private final String TAG = "FileCreator";

    private final File file;
    private FileOutputStream fileOutputStream;

    public LogManager (File file) {
        this.file = file;
        try {
            fileOutputStream = new FileOutputStream(file, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public boolean addEntry (String text) {
        try {
            fileOutputStream.write(text.getBytes(StandardCharsets.UTF_8));
            Log.i(TAG, "Entry added!");
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
