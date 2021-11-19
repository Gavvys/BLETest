package com.covidtracing.bletest;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder>{

    private final ArrayList<Device> deviceList;

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvAddress, tvRssi;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            tvName = itemView.findViewById(R.id.tv_device_name);
            tvAddress = itemView.findViewById(R.id.tv_address);
            tvRssi = itemView.findViewById(R.id.tv_rssi);

            itemView.setOnClickListener(view -> {
                int position = this.getLayoutPosition();
                DeviceListAdapter.this.deviceList.remove(position);
                DeviceListAdapter.this.notifyItemRemoved(position);
            });
        }

        @NonNull
        private TextView getTextView(int i) {
            if (i == 0) return tvName;
            else if (i == 1) return tvAddress;
            else if (i == 2) return tvRssi;

            return tvName;
        }
    }

    public DeviceListAdapter(ArrayList<Device> list) {
        deviceList = list;
    }

    @NonNull
    @Override
    public DeviceListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        View deviceView = inflater.inflate(R.layout.recycler_view_layout, parent, false);

        return new ViewHolder(deviceView);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceListAdapter.ViewHolder holder, int position) {
        holder.getTextView(0).setText(deviceList.get(position).getName());
        holder.getTextView(1).setText(deviceList.get(position).getAddress());
        holder.getTextView(2).setText(deviceList.get(position).getRssi());
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }
}
