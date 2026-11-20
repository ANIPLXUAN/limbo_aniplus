/*
Copyright (C) Max Kastanas 2012

 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package com.max2idea.android.limbo.machine;

import android.content.Context;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.install.Installer;
import com.max2idea.android.limbo.main.Config;
import com.max2idea.android.limbo.main.LimboApplication;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A simple utility class to retrieved often long architecture attribute lists
 */
public class ArchDefinitions {
    private static String TAG = "ArchDefinitions";

    public static ArrayList<String> getSoundcards(Context context) {
        ArrayList<String> commonSoundcards = new ArrayList<>();
        commonSoundcards.addAll(Arrays.asList(Installer.getAttrs(context, R.raw.common_soundcards)));
        return commonSoundcards;
    }

    public static ArrayList<String> getNetworkDevices(Context context) {
        ArrayList<String> networkCards = new ArrayList<>();
        switch (LimboApplication.arch) {
            case x86:
            case x86_64:
                networkCards.add(context.getString(R.string.defaulttext));
                networkCards.addAll(Arrays.asList(
                        Installer.getAttrs(context, R.raw.x86_nic_cards)));
                break;
            case arm:
            case arm64:
                networkCards.add(context.getString(R.string.defaulttext));
                networkCards.addAll(Arrays.asList(
                        Installer.getAttrs(context, R.raw.common_nic_cards)));
                networkCards.addAll(Arrays.asList(
                        Installer.getAttrs(context, R.raw.arm_nic_cards)));
                break;
            case inferno_aarch64:
                networkCards.add(context.getString(R.string.defaulttext));
                networkCards.addAll(Arrays.asList(
                        Installer.getAttrs(context, R.raw.inferno_nic_cards)));
                break;
            case ppc:
            case ppc64:
                networkCards.add(context.getString(R.string.defaulttext));
                networkCards.addAll(Arrays.asList(
                        Installer.getAttrs(context, R.raw.ppc_nic_cards)));
                break;
            case sparc:
            case sparc64:
                networkCards.add(context.getString(R.string.defaulttext));
                networkCards.addAll(Arrays.asList(
                        Installer.getAttrs(context, R.raw.sparc_nic_cards)));
                break;
        }
        return networkCards;
    }
    public static ArrayList<String> getVGAValues(Context context) {
        ArrayList<String> vgaValues = new ArrayList<>();

        if (LimboApplication.arch == Config.Arch.x86 || LimboApplication.arch == Config.Arch.x86_64
                || LimboApplication.arch == Config.Arch.ppc || LimboApplication.arch == Config.Arch.ppc64) {
            vgaValues.add("std");
        }

        if (LimboApplication.arch == Config.Arch.x86 || LimboApplication.arch == Config.Arch.x86_64) {
            vgaValues.add("none");
            vgaValues.add("cirrus");
            vgaValues.add("vmware");
            vgaValues.add("virtio");
            vgaValues.add("virtio-gpu-pci");
            vgaValues.add("ramfb");
            vgaValues.add("virtio-gpu-gl-pci");
            vgaValues.add("virtio-gpu-gl");
            vgaValues.add("virtio-vga-gl");
            vgaValues.add("virtio-gpu-gl-pci-zink");
            vgaValues.add("virtio-gpu-gl-zink");
            vgaValues.add("virtio-vga-gl-zink");
        }

        if (LimboApplication.arch == Config.Arch.inferno_aarch64) {
            vgaValues.add("std");
            vgaValues.add("cirrus");
            vgaValues.add("ramfb");
            vgaValues.add("virtio-gpu-pci");
            vgaValues.add("none");
        }

        return vgaValues;
    }
    public static ArrayList<String> getKeyboardValues(Context context) {
        ArrayList<String> arrList = new ArrayList<>();
        arrList.add("en-us");
        arrList.add("ar");
        arrList.add("de-ch");
        arrList.add("es");
        arrList.add("fo");
        arrList.add("fr-ca");
        arrList.add("hu");
        arrList.add("ja");
        arrList.add("mk");
        arrList.add("no");
        arrList.add("pt-br");
        arrList.add("sv");
        arrList.add("da");
        arrList.add("en-gb");
        arrList.add("et");
        arrList.add("fr");
        arrList.add("fr-ch");
        arrList.add("is");
        arrList.add("lt");
        arrList.add("nl");
        arrList.add("pl");
        arrList.add("ru");
        arrList.add("th");
        arrList.add("de");
        arrList.add("fi");
        arrList.add("fr-be");
        arrList.add("hr");
        arrList.add("it");
        arrList.add("lv");
        arrList.add("nl-be");
        arrList.add("pt");
        arrList.add("sl");
        arrList.add("tr");
        return arrList;
    }

    public static ArrayList<String> getKeyboardLabels(Context context) {
        ArrayList<String> arrList = new ArrayList<>();
        arrList.add("en-us");
        arrList.add("ar");
        arrList.add("de-ch");
        arrList.add("es");
        arrList.add("fo");
        arrList.add("fr-ca");
        arrList.add("hu");
        arrList.add("ja");
        arrList.add("mk");
        arrList.add("no");
        arrList.add("pt-br");
        arrList.add("sv");
        arrList.add("da");
        arrList.add("en-gb");
        arrList.add("et");
        arrList.add("fr");
        arrList.add("fr-ch");
        arrList.add("is");
        arrList.add("lt");
        arrList.add("nl");
        arrList.add("pl");
        arrList.add("ru");
        arrList.add("th");
        arrList.add("de");
        arrList.add("fi");
        arrList.add("fr-be");
        arrList.add("hr");
        arrList.add("it");
        arrList.add("lv");
        arrList.add("nl-be");
        arrList.add("pt");
        arrList.add("sl");
        arrList.add("tr");
        return arrList;
    }

    // Values（保存和传给 QEMU 用的）
    public static ArrayList<String> getMouseValues(Context context) {
        ArrayList<String> arrList = new ArrayList<>();
        arrList.add("ps2");
        arrList.add("usb-mouse");
        arrList.add("usb-tablet");
        arrList.add("virtio-mouse-pci");
        arrList.add("virtio-tablet-pci");
        return arrList;
    }

    public static ArrayList<String> getMouseLabels(Context context) {
        ArrayList<String> arrList = new ArrayList<>();
        arrList.add("ps2");
        arrList.add("usb-mouse");
        arrList.add("usb-tablet");
        arrList.add("virtio-mouse-pci");
        arrList.add("virtio-tablet-pci");
        return arrList;
    }

    public static ArrayList<String> getUIValues() {
        ArrayList<String> arrList = new ArrayList<>();
        arrList.add("VNC");
        if (Config.enable_SDL)
            arrList.add("SDL");
        return arrList;
    }

    public static ArrayList<String> getMachineValues(Context context) {
        ArrayList<String> machinesList = new ArrayList<>();
        machinesList.add(context.getString(R.string.none));
        machinesList.add(context.getString(R.string.newtext));
        return machinesList;
    }

    public static ArrayList<String> getCpuValues(Context context) {
        ArrayList<String> arrList = new ArrayList<>();
        switch (LimboApplication.arch) {
            case x86:
            case x86_64:
                arrList.add(context.getString(R.string.defaulttext));
                arrList.addAll(Arrays.asList(
                        Installer.getAttrs(context, R.raw.x86_cpu)));
                break;
            case arm:
            case arm64:
                arrList.add(context.getString(R.string.defaulttext));
                arrList.addAll(Arrays.asList(
                        Installer.getAttrs(context, R.raw.arm_cpu)));
                break;
            case inferno_aarch64:
                arrList.addAll(Arrays.asList(
                        Installer.getAttrs(context, R.raw.inferno_cpu)));
                break;
            case ppc:
            case ppc64:
                arrList.add(context.getString(R.string.defaulttext));
                arrList.addAll(Arrays.asList(
                        Installer.getAttrs(context, R.raw.ppc_cpu)));
                break;
            case sparc:
            case sparc64:
                arrList.add(context.getString(R.string.defaulttext));
                arrList.addAll(Arrays.asList(
                        Installer.getAttrs(context, R.raw.sparc_cpu)));
                break;
        }
        return arrList;
    }
    public static ArrayList<String> getMachineTypeValues(Context context) {
        android.util.Log.d("ArchDebug", "getMachineTypeValues: arch=" + LimboApplication.arch);
        ArrayList<String> arrList = new ArrayList<>();
        switch (LimboApplication.arch) {
            case x86:
            case x86_64:
                arrList.add(context.getString(R.string.defaulttext));
                arrList.addAll(Arrays.asList(
                        Installer.getAttrs(context, R.raw.x86_machine_types)));
                break;
            case arm:
            case arm64:
                arrList.addAll(Arrays.asList(
                        Installer.getAttrs(context, R.raw.arm_machine_types)));
                break;
            case inferno_aarch64:
                arrList.add(context.getString(R.string.defaulttext));
                arrList.addAll(Arrays.asList(
                        Installer.getAttrs(context, R.raw.inferno_machine_types)));
                break;
            case ppc:
            case ppc64:
                arrList.add(context.getString(R.string.defaulttext));
                arrList.addAll(Arrays.asList(
                        Installer.getAttrs(context, R.raw.ppc_machine_types)));
                break;
            case sparc:
            case sparc64:
                arrList.add(context.getString(R.string.defaulttext));
                arrList.addAll(Arrays.asList(
                        Installer.getAttrs(context, R.raw.sparc_machine_types)));
                break;
        }
        android.util.Log.d("ArchDebug", "getMachineTypeValues returns " + arrList.size() + " items");
        return arrList;
    }
    public static ArrayList<String> getUSBValues(Context context) {
        ArrayList<String> usbValues = new ArrayList<>();

        // ===== Inferno AArch64 分支 =====
        if (LimboApplication.arch == Config.Arch.inferno_aarch64) {
            usbValues.add("pci-ohci");
            usbValues.add("dwc2-usb-device");
            usbValues.add("dwc3-usb-device");
            usbValues.add("usb-tcp-remote");
            usbValues.add("usb-ehci");
            usbValues.add("ich9-usb-ehci1");
            usbValues.add("ich9-usb-ehci2");
            usbValues.add("ich9-usb-uhci1");
            usbValues.add("ich9-usb-uhci2");
            usbValues.add("ich9-usb-uhci3");
            usbValues.add("ich9-usb-uhci4");
            usbValues.add("ich9-usb-uhci5");
            usbValues.add("ich9-usb-uhci6");
            usbValues.add("piix3-usb-uhci");
            usbValues.add("piix4-usb-uhci");
            usbValues.add("qemu-xhci");
            usbValues.add("nec-usb-xhci");
            return usbValues;
        }

        // ===== 原有逻辑 =====
        usbValues.add(context.getString(R.string.usbehci));
        usbValues.add(context.getString(R.string.qemuxhci));
        usbValues.add(context.getString(R.string.usbohci));
        return usbValues;
    }
}