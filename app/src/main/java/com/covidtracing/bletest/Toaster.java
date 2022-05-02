package com.covidtracing.bletest;

import android.content.Context;
import android.widget.Toast;

public class Toaster extends Toast {
    private final Context c;

    public Toaster(Context context) {
        super(context);
        c = context;
    }

    public void toast (String toastMessage) {
        Toast.makeText(c, toastMessage, LENGTH_SHORT).show();
    }
}
