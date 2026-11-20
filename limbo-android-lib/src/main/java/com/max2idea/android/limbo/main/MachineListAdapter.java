package com.max2idea.android.limbo.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.machine.Machine;

import java.util.ArrayList;

public class MachineListAdapter extends BaseAdapter {

    public interface OnMachineActionListener {
        void onStart(String machineName);
        void onConfig(String machineName);
    }

    private final Context context;
    private final ArrayList<Machine> machines;
    private final OnMachineActionListener listener;

    public MachineListAdapter(Context context, ArrayList<Machine> machines,
                              OnMachineActionListener listener) {
        this.context = context;
        this.machines = machines;
        this.listener = listener;
    }

    @Override
    public int getCount() { return machines.size(); }

    @Override
    public Object getItem(int position) { return machines.get(position); }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.machine_list_item, parent, false);
        }

        Machine m = machines.get(position);
        String name = m.getName();
        String arch = m.getArch();
        if (arch == null || arch.isEmpty()) {
            arch = "x86_64";
        }

        TextView nameView = convertView.findViewById(R.id.machine_item_name);
        TextView archView = convertView.findViewById(R.id.machine_item_arch);
        ImageView startView = convertView.findViewById(R.id.machine_item_start);
        ImageView moreView = convertView.findViewById(R.id.machine_item_more);

        nameView.setText(name);
        if (archView != null) {
            archView.setText(arch);
        }

        final String machineName = name;

        convertView.setOnClickListener(v -> {
            if (listener != null) listener.onStart(machineName);
        });
        startView.setOnClickListener(v -> {
            if (listener != null) listener.onStart(machineName);
        });
        moreView.setOnClickListener(v -> {
            if (listener != null) listener.onConfig(machineName);
        });

        return convertView;
    }
}