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
package com.max2idea.android.limbo.jni;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.files.FileUtils;
import com.max2idea.android.limbo.machine.MachineAction;
import com.max2idea.android.limbo.machine.MachineController;
import com.max2idea.android.limbo.machine.MachineExecutor;
import com.max2idea.android.limbo.machine.MachineProperty;
import com.max2idea.android.limbo.main.Config;
import com.max2idea.android.limbo.main.LimboApplication;
import com.max2idea.android.limbo.main.LimboSDLActivity;
import com.max2idea.android.limbo.main.LimboSettingsManager;
import com.max2idea.android.limbo.qmp.QmpClient;
import com.max2idea.android.limbo.toast.ToastUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;


/**
 * Class is used to start and stop the qemu process and communicate file descriptions, mouse,
 * and keyboard events.
 */
class VMExecutor extends MachineExecutor {
    private static final String TAG = "Limbo_ANIPLXUAN";

    private static final String cdDeviceName = "ide1-cd0";
    private static final String fdaDeviceName = "floppy0";
    private static final String fdbDeviceName = "floppy1";
    private static final String sdDeviceName = "sd0";
    private static int sVsockCid = 3;

    private static synchronized int nextVsockCid() {
        if (sVsockCid > 1000) {
            sVsockCid = 3;
        }
        return sVsockCid++;
    }
    private static int vm_width;
    private static int vm_height;
    //TODO: make this a proper singleton but the views should not be able to access it
    private static VMExecutor mInstance;

    VMExecutor(MachineController machineController) {
        super(machineController);
        mInstance = this;
    }

    /**
     * This function is called when the machine resolution changes. This is called from SDL compat
     * extensions, see folder jni/compat/sdl-extensions
     *
     * @param width  Width
     * @param height Height
     */
    public static void onVMResolutionChanged(int width, int height) {
        vm_width = width;
        vm_height = height;
        mInstance.onResolutionChanged(vm_width, vm_height);
    }

    //JNI Methods
    private native String start(String storage_dir, String base_dir,
                                String lib_filename, String lib_path,
                                int sdl_scale_hint, Object[] params);

    private native String stop(int restart);

    public native void setSDLRefreshRateDefault(int value);

    public native void setSDLRefreshRateIdle(int value);
    public static final int RENDER_BACKEND_SYSTEM_GLES = 0;
    public static final int RENDER_BACKEND_ANGLE_VULKAN = 1;

    public native int getSDLRefreshRateDefault();
    private native void nativeSetVirglRenderBackend(int backend, String nativeLibDir);

    public native int getSDLRefreshRateIdle();

    public native void nativeIgnoreBreakpointInvalidate(int value);

    public native void nativeMouseEvent(int button, int action, int relative, int x, int y);

    public native void nativeMouseBounds(int xmin, int xmax, int ymin, int ymax);

    public native void nativeFullscreen();

    public native void nativeRefreshScreen(int value);

    public native void nativeEnableAaudio(int value, String aaudioLibName, String aaudioLibPath);

    /**
     * Prints parameters in qemu format
     *
     * @param params Parameters to be printed
     */
    public void printParams(String[] params) {
        Log.d(TAG, "Params:");
        for (int i = 0; i < params.length; i++) {
            Log.d(TAG, i + ": " + params[i]);
        }
    }

    // Translate to QEMU format
    private String getSoundCard() {
        if (Config.enableSDLSound && getMachine().getSoundCard() != null
                && !getMachine().getSoundCard().toLowerCase().equals("none"))
            return getMachine().getSoundCard();
        return null;
    }



    private String getQemuLibrary() {
        switch (LimboApplication.arch) {
            case x86:
                return "libqemu-system-i386.so";
            case x86_64:
                return "libqemu-system-x86_64.so";
            case arm:
                return "libqemu-system-arm.so";
            case arm64:
                return "libqemu-system-aarch64.so";
            case inferno_aarch64:
                return "libqemu-system-aarch64-v2.so";
            case ppc:
                return "libqemu-system-ppc.so";
            case ppc64:
                return "libqemu-system-ppc64.so";
            case sparc:
                return "libqemu-system-sparc.so";
            case sparc64:
                return "libqemu-system-sparc64.so";
            default:
                throw new IllegalStateException("Unexpected value: " + LimboApplication.arch);
        }
    }

    private String getSaveStateName() {
        String machineSaveDirectory = MachineController.getInstance().getMachineSaveDir();
        if (machineSaveDirectory == null || machineSaveDirectory.isEmpty()) {
            return "";
        }
        return machineSaveDirectory + "/" + Config.stateFilename;
    }

    private String[] prepareParams(Context context) throws Exception {
        ArrayList<String> paramsList = new ArrayList<>();
        paramsList.add(getQemuLibrary());
        addUIOptions(context, paramsList);
        addCpuBoardOptions(paramsList);
        addDrives(paramsList);
        addRemovableDrives(paramsList);
        addBootOptions(paramsList);
        addGraphicsOptions(paramsList);
        addAudioOptions(paramsList);
        addNetworkOptions(paramsList);
        addGenericOptions(context, paramsList);
        addVirtioOptions(context, paramsList);
        addStateOptions(paramsList);
        addAdvancedOptions(paramsList);
        addAccelerationOptions(paramsList);
        return paramsList.toArray(new String[0]);
    }

    private void addVirtioOptions(Context context, ArrayList<String> paramsList) {

        // 1. virtio-balloon
        if (LimboSettingsManager.getEnableVirtioBalloon(context)) {
            paramsList.add("-device");
            paramsList.add("virtio-balloon-pci,id=balloon0");
        }

        // 2. virtio-serial
        if (LimboSettingsManager.getEnableVirtioSerial(context)) {
            paramsList.add("-device");
            paramsList.add("virtio-serial-pci,id=virtio-serial0");
        }

        // 3. virtio-rng
        if (LimboSettingsManager.getEnableVirtioRng(context)) {
            paramsList.add("-object");
            paramsList.add("rng-random,filename=/dev/urandom,id=rng0");
            paramsList.add("-device");
            paramsList.add("virtio-rng-pci,rng=rng0");
        }

    }


    /**
     * Adds the vm state file description to the qemu parameters for resuming the vm
     *
     * @param paramsList Existing parameter list to be passed to qemu
     */
    private void addStateOptions(ArrayList<String> paramsList) {
        if (MachineController.getInstance().isPaused() && !getSaveStateName().equals("")) {
            int fd_tmp = FileUtils.get_fd(getSaveStateName());
            if (fd_tmp < 0) {
                Log.e(TAG, "获取文件描述符时出错：" + getSaveStateName());
            } else {
                Log.d(TAG, "检索到 fd： " + fd_tmp + " for: " + getSaveStateName());
                paramsList.add("-incoming");
                paramsList.add("fd:" + fd_tmp);
            }
        }
    }

    private void addUIOptions(Context context, ArrayList<String> paramsList) {

        String vga = getMachine().getVga();
        boolean hasGl = vga != null && vga.contains("gl");

        if (MachineController.getInstance().isVNCEnabled()) {
            paramsList.add("-vnc");
            String vncParam = "";
            if (LimboSettingsManager.getVNCEnablePassword(context)) {
                vncParam += ":1";
            } else {
                vncParam += Config.defaultVNCHost + ":" + Config.defaultVNCPort;
            }
            if (LimboSettingsManager.getVNCEnablePassword(context))
                vncParam += ",password";

            paramsList.add(vncParam);

            if (hasGl) {
                paramsList.add("-display");
                paramsList.add("egl-headless");
            }

            // Monitor：Inferno 不支持，跳过
            if (LimboApplication.arch != Config.Arch.inferno_aarch64) {
                paramsList.add("-monitor");
                paramsList.add("vc");
            }

            paramsList.add("-device");
            paramsList.add(getMachine().getUsb() + ",id=usbctrl");
            paramsList.add("-device");
            paramsList.add("sdhci-pci,id=sdpci");
        } else {
            if (hasGl) {
                paramsList.add("-display");
                paramsList.add("sdl,gl=on");
            }

            paramsList.add("-serial");
            paramsList.add("none");

            // Inferno 对 -parallel 支持不稳定，跳过
            if (LimboApplication.arch != Config.Arch.inferno_aarch64) {
                paramsList.add("-parallel");
                paramsList.add("none");
            }

            paramsList.add("-device");
            paramsList.add(getMachine().getUsb() + ",id=usbctrl");
            paramsList.add("-device");
            paramsList.add("sdhci-pci,id=sdpci");
        }

        if (getMachine().getKeyboard() != null) {
            paramsList.add("-k");
            paramsList.add(getMachine().getKeyboard());
        }

        if (getMachine().getMouse() != null && !getMachine().getMouse().equals("ps2")) {
            String mouse = getMachine().getMouse();
            paramsList.add("-device");
            if (mouse.equals("virtio-mouse-pci") || mouse.equals("virtio-tablet-pci")) {
                paramsList.add(mouse);
            } else {
                paramsList.add(mouse + ",bus=usbctrl.0");
            }
        }
    }
    private void addAdvancedOptions(ArrayList<String> paramsList) {

        if (getMachine().getExtraParams() != null && !getMachine().getExtraParams().trim().equals("")) {
            String[] paramsTmp = getMachine().getExtraParams().split(" ");
            paramsList.addAll(Arrays.asList(paramsTmp));
        }
    }

    private void addAudioOptions(ArrayList<String> paramsList) {
        String soundCard = getSoundCard();
        if (soundCard != null && !Objects.equals(soundCard, "无") && !Objects.equals(soundCard, "默认")) {
            paramsList.add("-audiodev");
            paramsList.add("sdl,id=audio0");

            if (soundCard.equals("hda")) {
                paramsList.add("-device");
                paramsList.add("intel-hda");
                paramsList.add("-device");
                paramsList.add("hda-duplex,audiodev=audio0");
            } else {
                paramsList.add("-device");
                paramsList.add(soundCard + ",audiodev=audio0");
            }
        }
    }

    private void addGenericOptions(Context context, ArrayList<String> paramsList) {
        paramsList.add("-L");
        paramsList.add(LimboApplication.getBasefileDir());
        if (LimboSettingsManager.getEnableQmp(context)
                && LimboApplication.arch != Config.Arch.inferno_aarch64) {
            paramsList.add("-qmp");
            if (getQMPAllowExternal()) {
                String qmpParams = "tcp:";
                qmpParams += (":" + Config.QMPPort);
                qmpParams += ",server,nowait";
                paramsList.add(qmpParams);
            } else {
                //Specify a unix local domain as localhost to limit to local connections only
                String qmpParams = "unix:";
                qmpParams += LimboApplication.getLocalQMPSocketPath();
                qmpParams += ",server,nowait";
                paramsList.add(qmpParams);
            }
        }

        // Monitor socket（Inferno 不支持 -monitor，跳过）
        if (LimboApplication.arch != Config.Arch.inferno_aarch64) {
            paramsList.add("-monitor");
            paramsList.add("unix:" + LimboApplication.getLocalMonitorSocketPath() + ",server,nowait");
        }

        //Enable Tracing log
        if (Config.enableTracingLog) {
            paramsList.add("-D");
            paramsList.add(Config.traceLogFile);
            paramsList.add("--trace");
            paramsList.add("events=" + Config.traceEventsFile);
            paramsList.add("--trace");
            paramsList.add("file=" + Config.traceDir);
        }

        if (Config.overrideTbSize) {
            paramsList.add("-tb-size");
            paramsList.add(Config.tbSize); //Don't increase it crashes
        }

        if (LimboApplication.getQemuVersion() == 20901) {
            paramsList.add("-realtime");
            paramsList.add("mlock=off");
        } else {
            paramsList.add("-overcommit");
            paramsList.add("mem-lock=off");
        }

        paramsList.add("-rtc");
        paramsList.add("base=localtime");

        if (!Config.enableDefaultDevices)
            paramsList.add("-nodefaults");
    }

    private void addCpuBoardOptions(ArrayList<String> paramsList) {

        // SMP
        if (getMachine().getCpuNum() > 1) {
            paramsList.add("-smp");
            paramsList.add(getMachine().getCpuNum() + ",cores=" + getMachine().getCpuNum() + "");
        }

        // ===== Inferno 分支 =====
        if (LimboApplication.arch == Config.Arch.inferno_aarch64) {
            String machineType = getMachine().getMachineType();
            if (machineType == null || machineType.equals("Default") || machineType.equals("默认")) {
                machineType = "t8030";
            }

            String machineOpts = machineType;

            // 只有 t8030 / s8000（苹果机器）才需要 SEP 等参数
            if (machineType.equals("t8030") || machineType.equals("s8000")) {
                String machineDir = LimboApplication.getMachineDir() + getMachine().getName();
                machineOpts += ",trustcache=" + machineDir + "/trustcache"
                        + ",ticket=" + machineDir + "/ticket"
                        + ",sep-fw=" + machineDir + "/sep-fw"
                        + ",sep-rom=" + machineDir + "/sep-rom"
                        + ",kaslr-off=true";
            }

            paramsList.add("-machine");
            paramsList.add(machineOpts);

            // CPU：用户选什么拼什么
            String cpu = getMachine().getCpu();
            if (cpu != null && !cpu.equals("Default") && !cpu.equals("默认") && !cpu.isEmpty()) {
                paramsList.add("-cpu");
                Log.d("CPUDEBUG", "Inferno cpu from machine = [" + cpu + "]");
                paramsList.add(cpu);
            }

            paramsList.add("-m");
            paramsList.add(getMachine().getMemory() + "");
            return;
        }

        // ===== 原有逻辑（x86 / ppc / sparc） =====
        String machineType = null;
        StringBuilder machineOpts = new StringBuilder();

        if (getMachineType() != null && !getMachineType().equals("Default") && !getMachineType().equals("默认")) {
            machineType = getMachineType();
        }
        if (getMachine().getDisableAcpi() != 0) {
            machineOpts.append(",acpi=off");
        }
        if (getMachine().getDisableHPET() != 0) {
            machineOpts.append(",hpet=off");
        }

        if (machineType != null && machineOpts.length() > 0) {
            paramsList.add("-machine");
            paramsList.add(machineType + machineOpts.toString());
        } else if (machineType != null) {
            paramsList.add("-machine");
            paramsList.add(machineType);
        } else if (machineOpts.length() > 0) {
            paramsList.add("-machine");
            paramsList.add(machineOpts.substring(1));
        }

        String cpu = getMachine().getCpu();
        if (getMachine().getCpu() != null && getMachine().getCpu().contains(" ")) {
            Log.d("CPUDEBUG", "Inferno cpu from machine = [" + cpu + "]");
            cpu = "'" + getMachine().getCpu() + "'";
        }

        if (getMachine().getDisableTSC() == 1 && (LimboApplication.arch == Config.Arch.x86 || LimboApplication.arch == Config.Arch.x86_64)) {
            if (cpu == null || (cpu.equals("Default") && cpu.equals("默认"))) {
                if (LimboApplication.arch == Config.Arch.x86) cpu = "qemu32";
                else if (LimboApplication.arch == Config.Arch.x86_64) cpu = "qemu64";
            }
            cpu += ",-mds-no,-fma,-pcid,-x2apic,-tsc-deadline,-avx,-f16c,-avx2,-invpcid,-rdseed,-spec-ctrl,-arch-capabilities,-ssbd,-3dnowprefetch,-xsavec,-rdctl-no,-ibrs-all,-skip-l1dfl-vmentry";
        }

        if (cpu != null && !cpu.equals("Default") && !cpu.equals("默认") && !cpu.isEmpty()) {
            paramsList.add("-cpu");
            paramsList.add(cpu);
        }

        paramsList.add("-m");
        paramsList.add(getMachine().getMemory() + "");
    }


    private void addAccelerationOptions(ArrayList<String> paramsList) {

        // XXX: we add the acceleration options after the extra params
        // this is due to QEMU applying the first instance of this option
        // so the extra params cannot override it.
        if (getMachine().getEnableKVM() != 0) {
            paramsList.add("-enable-kvm");
        } else {
            paramsList.add("-accel");
            String tcgParams = "tcg";
            if (getMachine().getEnableMTTCG() != 0) {
                tcgParams += ",thread=multi,tb-size=1024";
            } else {
                tcgParams += ",thread=single,tb-size=1024";
            }
            paramsList.add(tcgParams);
        }
    }

    private String getMachineType() {
        String machineType = getMachine().getMachineType();
        if ((LimboApplication.arch == Config.Arch.x86 || LimboApplication.arch == Config.Arch.x86_64)
                && machineType == null) {
            machineType = "pc";
        } else if ((LimboApplication.arch == Config.Arch.ppc || LimboApplication.arch == Config.Arch.ppc64)
                && machineType.equals("Default") && machineType.equals("默认")) {
            machineType = null;
        } else if ((LimboApplication.arch == Config.Arch.sparc || LimboApplication.arch == Config.Arch.sparc64)
                && machineType.equals("Default") && machineType.equals("默认")) {
            machineType = null;
        }
        return machineType;
    }

    private void addNetworkOptions(ArrayList<String> paramsList) throws Exception {
        String network = getNetCfg();
        if (network != null) {

            paramsList.add("-nic");

            // 读取用户选择的网卡型号
            String nicCard = getNicCard();

            // Inferno 下如果没选或选了 Default，兜底用 e1000
            if (LimboApplication.arch == Config.Arch.inferno_aarch64) {
                if (nicCard == null || nicCard.isEmpty()
                        || nicCard.equals("Default") || nicCard.equals("默认")) {
                    nicCard = "e1000";
                }
            }

            String modelParam = (nicCard != null && !nicCard.isEmpty()
                    && !nicCard.equals("Default") && !nicCard.equals("默认"))
                    ? ",model=" + nicCard : "";

            if (network.equals("user")) {
                String netParams = network + modelParam;
                String hostFwd = getHostFwd();
                if (hostFwd != null) {
                    // hostfwd=[tcp|udp]:[hostaddr]:hostport-[guestaddr]:guestport{,hostfwd=...}
                    if (hostFwd.startsWith("hostfwd")) {
                        throw new Exception("Invalid format for Host Forward, should be: tcp:hostport1:guestport1,udp:hostport2:questport2,...");
                    }
                    String[] hostFwdParams = hostFwd.split(",");
                    for (int i = 0; i < hostFwdParams.length; i++) {
                        netParams += ",";
                        String[] hostfwdparam = hostFwdParams[i].split(":");
                        netParams += ("hostfwd=" + hostfwdparam[0] + "::" + hostfwdparam[1] + "-:" + hostfwdparam[2]);
                    }
                }
                paramsList.add(netParams);
            } else if (network.equals("tap")) {
                paramsList.add("tap,ifname=tap0,script=no" + modelParam);
            } else {
                paramsList.add("none");
            }
        }
    }

    private String getHostFwd() {
        if (getMachine().getNetwork().equals("User")) {
            if (getMachine().getHostFwd() != null && !getMachine().getHostFwd().equals(""))
                return getMachine().getHostFwd();
        }
        return null;
    }

    private String getNicCard() {
        if (getMachine().getNetwork() == null || (getMachine().getNetwork().equals("None") && getMachine().getNetwork().equals("无"))) {
            return null;
        } else if (getMachine().getNetwork().equals("User")) {
            return getMachine().getNetworkCard();
        } else if (getMachine().getNetwork().equals("TAP")) {
            return getMachine().getNetworkCard();
        }
        return null;
    }

    private String getNetCfg() {
        if (getMachine().getNetwork() == null || (getMachine().getNetwork().equals("None") && getMachine().getNetwork().equals("无"))) {
            return "none";
        } else if (getMachine().getNetwork().equals("User")) {
            return "user";
        } else if (getMachine().getNetwork().equals("TAP")) {
            return "tap";
        }
        return null;
    }

    private void addGraphicsOptions(ArrayList<String> paramsList) {
        if (getMachine().getVga() != null) {
            String vga = getMachine().getVga();

            // ===== Inferno 专用：t8030 没有 virtio-gpu =====
            if (LimboApplication.arch == Config.Arch.inferno_aarch64) {
                if (vga.equals("Default") || vga.equals("默认")) {
                    // 默认不额外加设备
                } else if (vga.equals("none") || vga.equals("无")) {
                    paramsList.add("-vga");
                    paramsList.add("none");
                } else if (vga.equals("std")) {
                    paramsList.add("-device");
                    paramsList.add("VGA,vgamem_mb=" + getMachine().getVideoMemory());
                } else if (vga.equals("cirrus")) {
                    paramsList.add("-device");
                    paramsList.add("cirrus-vga,vgamem_mb=" + getMachine().getVideoMemory());
                } else if (vga.equals("ati-vga")) {
                    paramsList.add("-device");
                    paramsList.add("ati-vga");
                } else if (vga.equals("bochs")) {
                    paramsList.add("-device");
                    paramsList.add("bochs-display");
                } else if (vga.equals("ramfb")) {
                    paramsList.add("-device");
                    paramsList.add("ramfb");
                } else if (vga.equals("secondary-vga")) {
                    paramsList.add("-device");
                    paramsList.add("secondary-vga");
                } else if (vga.equals("virtio-gpu-pci")) {
                    paramsList.add("-device");
                    paramsList.add("virtio-gpu-pci");
                } else if (vga.equals("virtio-gpu-device")) {
                    paramsList.add("-device");
                    paramsList.add("virtio-gpu-device");
                }
                return;
            }

            // ===== ANGLE 模式下 venus 会冲突，需要去掉 venus=on =====
            boolean useAngle = "angle".equals(
                    LimboSettingsManager.getGlesMode(LimboApplication.getInstance()));

            // ===== 原有逻辑 =====
            if (vga.equals("Default") || vga.equals("默认")) {
                //do nothing
            } else if (vga.equals("none") || vga.equals("无")) {
                paramsList.add("-vga");
                paramsList.add("none");
            } else if (vga.equals("nographic")) {
                paramsList.add("-nographic");
            } else if (vga.equals("std")) {
                paramsList.add("-device");
                paramsList.add("VGA,vgamem_mb=" + getMachine().getVideoMemory());
            } else if (vga.equals("vmware")) {
                paramsList.add("-device");
                paramsList.add("vmware-svga,vgamem_mb=" + getMachine().getVideoMemory());
            } else if (vga.equals("cirrus")) {
                paramsList.add("-device");
                paramsList.add("cirrus-vga,vgamem_mb=" + getMachine().getVideoMemory());
            } else if (vga.equals("ramfb")) {
                paramsList.add("-device");
                paramsList.add("virtio-gpu-pci");
                paramsList.add("-device");
                paramsList.add("ramfb");
            } else if (vga.contains("gl") && vga.contains("zink")) {
                String baseVga = vga.replace("-zink", "");
                String vram = getMachine().getVideoMemory();
                if (vram == null || vram.isEmpty()) vram = "1G";
                paramsList.add("-device");
                if (useAngle) {
                    // ANGLE 走标准 GLES 路径，不能开 venus
                    paramsList.add(baseVga + ",hostmem=" + vram + ",blob=on");
                } else {
                    paramsList.add(baseVga + ",hostmem=" + vram + ",blob=on,venus=on");
                }
            } else if (vga.contains("gl")) {
                String vram = getMachine().getVideoMemory();
                if (vram == null || vram.isEmpty()) vram = "1G";
                paramsList.add("-device");
                paramsList.add(vga + ",hostmem=" + vram + ",blob=on");
            } else if (vga.equals("virtio-gpu-pci")) {
                paramsList.add("-device");
                paramsList.add("virtio-gpu-pci");
            } else if (vga.equals("virtio")) {
                paramsList.add("-device");
                paramsList.add("virtio-vga");
            } else {
                paramsList.add("-device");
                paramsList.add(vga + "-vga");
            }
        }
    }
    private void addBootOptions(ArrayList<String> paramsList) {
        if (getBootDevice() != null) {
            paramsList.add("-boot");
            paramsList.add(getBootDevice());
        }

        if (getBios() != null) {
            paramsList.add("-bios");
            paramsList.add(getBios());
        }

        String kernel = getKernel();
        if (kernel != null && !kernel.equals("")) {
            paramsList.add("-kernel");
            paramsList.add(kernel);
        }

        String initrd = getInitRd();
        if (initrd != null && !initrd.equals("")) {
            paramsList.add("-initrd");
            paramsList.add(initrd);
        }

        if (getMachine().getAppend() != null && !getMachine().getAppend().equals("")) {
            paramsList.add("-append");
            paramsList.add(getMachine().getAppend());
        }
    }

    private String getBootDevice() {
        if (LimboApplication.arch == Config.Arch.arm || LimboApplication.arch == Config.Arch.arm64) {
            return null;
        } else if (getMachine().getBootDevice().equals("Default")) {
            return null;
        } else if (getMachine().getBootDevice().equals("CDROM")) {
            return "d";
        } else if (getMachine().getBootDevice().equals("Floppy")) {
            return "a";
        } else if (getMachine().getBootDevice().equals("Hard Disk")) {
            return "c";
        }
        return null;
    }

    private String getBios() {
        if (LimboApplication.arch == Config.Arch.arm || LimboApplication.arch == Config.Arch.arm64) {
            return null;
        } else if (getMachine().getBios().equals("Default")) {
            return null;
        } else if (getMachine().getBios().equals("SeaBios(x86)")) {
            return "bios.bin";
        } else if (getMachine().getBios().equals("SeaBios(x64)")) {
            return "bios-256k.bin";
        } else if (getMachine().getBios().equals("vmwareEFI(x64)")) {
            return "VMWARE_EFI.fd";
        } else if (getMachine().getBios().equals("ovmfEFI(x86)")) {
            return "OVMF-pure-efi.fd";
        } else if (getMachine().getBios().equals("ovmfEFI(x64)")) {
            return "OVMF-pure-efi64.fd";
        }
        return null;
    }

    private String getInitRd() {
        return FileUtils.encodeDocumentFilePath(getMachine().getInitRd());
    }

    private String getKernel() {
        return FileUtils.encodeDocumentFilePath(getMachine().getKernel());
    }

    public String getDriveFilePath(String driveFilePath) {
        String imgPath = driveFilePath;
        if (imgPath == null || (imgPath.equals("None") || imgPath.equals("无")))
            return null;
        imgPath = FileUtils.encodeDocumentFilePath(imgPath);
        return imgPath;
    }

    public void addDrives(ArrayList<String> paramsList) {
        boolean needScsi = "SCSI".equalsIgnoreCase(getMachine().getHdaInterface())
                || "SCSI".equalsIgnoreCase(getMachine().getHdbInterface())
                || "SCSI".equalsIgnoreCase(getMachine().getHdcInterface())
                || "SCSI".equalsIgnoreCase(getMachine().getHddInterface());

        if (!Config.legacyDrives && needScsi) {
            paramsList.add("-device");
            paramsList.add("virtio-scsi-pci,id=scsi0");
        }

        addHardDisk(paramsList, getDriveFilePath(getMachine().getHdaImagePath()),
                0, getMachine().getHdaInterface());
        addHardDisk(paramsList, getDriveFilePath(getMachine().getHdbImagePath()),
                1, getMachine().getHdbInterface());
        addHardDisk(paramsList, getDriveFilePath(getMachine().getHdcImagePath()),
                2, getMachine().getHdcInterface());
        addHardDisk(paramsList, getDriveFilePath(getMachine().getHddImagePath()),
                3, getMachine().getHddInterface());
        addSharedFolder(paramsList, getDriveFilePath(getMachine().getSharedFolderPath()));
    }
    public void addHardDisk(ArrayList<String> paramsList, String imagePath, int index, String hdInterface) {
        if (imagePath != null && !imagePath.trim().equals("")) {
            if (Config.legacyDrives) {
                switch (index) {
                    case 0: paramsList.add("-hda"); break;
                    case 1: paramsList.add("-hdb"); break;
                    case 2: paramsList.add("-hdc"); break;
                    case 3: paramsList.add("-hdd"); break;
                }
                paramsList.add(imagePath);
            } else if ("SCSI".equalsIgnoreCase(hdInterface)) {
                paramsList.add("-drive");
                String param = "if=none,id=scsidisk" + index;
                param += ",media=disk";
                if (imagePath.toLowerCase().endsWith(".qcow2")) {
                    param += ",format=qcow2";
                }
                param += ",file=" + imagePath;
                String cache = LimboSettingsManager.getDiskCache(LimboApplication.getInstance());
                if (cache != null && !cache.equals("default")) {
                    param += ",cache=" + cache;
                }
                paramsList.add(param);
                paramsList.add("-device");
                paramsList.add("scsi-hd,drive=scsidisk" + index + ",bus=scsi0.0");
            } else if ("NVME".equalsIgnoreCase(hdInterface)) {
                paramsList.add("-drive");
                String param = "if=none,id=nvme" + index;
                param += ",media=disk";
                param += ",file=" + imagePath;
                String cache = LimboSettingsManager.getDiskCache(LimboApplication.getInstance());
                if (cache != null && !cache.equals("default")) {
                    param += ",cache=" + cache;
                }
                paramsList.add(param);
                paramsList.add("-device");
                paramsList.add("nvme,drive=nvme" + index + ",serial=nvme" + index);
            } else {
                paramsList.add("-drive");
                String param = "index=" + index;
                param += ",if=";
                param += hdInterface.toLowerCase();
                param += ",media=disk";
                if (imagePath.toLowerCase().endsWith(".qcow2")) {
                    param += ",format=qcow2";
                }
                if (!imagePath.equals("")) {
                    param += ",file=" + imagePath;
                }
                String cache = LimboSettingsManager.getDiskCache(LimboApplication.getInstance());
                if (cache != null && !cache.equals("default")) {
                    param += ",cache=" + cache;
                }
                paramsList.add(param);
            }
        }
    }

    public void addSharedFolder(ArrayList<String> paramsList, String sharedFolderPath) {
        if (sharedFolderPath != null) {
            //XXX; We use hdd to mount any virtual fat drives
            paramsList.add("-drive"); //empty
            String driveParams = "index=3";
            driveParams += ",media=disk";
            driveParams += ",if=ide";
            driveParams += ",format=raw";
            driveParams += ",file=fat:";
            driveParams += "rw:"; //Always Read/Write
            driveParams += sharedFolderPath;
            paramsList.add(driveParams);
        }
    }

    public void addRemovableDrives(ArrayList<String> paramsList) {
        String cdImagePath = getDriveFilePath(getMachine().getCdImagePath());
        if (cdImagePath != null) {
            if (Config.legacyDrives) {
                paramsList.add("-cdrom");
                paramsList.add(cdImagePath);
            } else {
                paramsList.add("-drive"); //empty
                String param = "index=2";
                param += ",if=";
                param += getMachine().getCDInterface();
                param += ",media=cdrom";
                if (!cdImagePath.equals("")) {
                    param += ",file=" + cdImagePath;
                }
                paramsList.add(param);
            }
        }
        // 下面继续你原来的软盘 / SD 卡逻辑，不要动


        String fdaImagePath = getDriveFilePath(getMachine().getFdaImagePath());
        if (Config.enableEmulatedFloppy && fdaImagePath != null) {
            if (Config.legacyDrives) {
                paramsList.add("-fda");
                paramsList.add(fdaImagePath);
            } else {
                paramsList.add("-drive"); //empty
                String param = "index=0,if=floppy";
                if (!fdaImagePath.equals("")) {
                    param += ",file=" + fdaImagePath;
                }
                paramsList.add(param);
            }
        }

        String fdbImagePath = getDriveFilePath(getMachine().getFdbImagePath());
        if (Config.enableEmulatedFloppy && fdbImagePath != null) {
            if (Config.legacyDrives) {
                paramsList.add("-fdb");
                paramsList.add(fdbImagePath);
            } else {
                paramsList.add("-drive"); //empty
                String param = "index=1,if=floppy";
                if (!fdbImagePath.equals("")) {
                    param += ",file=" + fdbImagePath;
                }
                paramsList.add(param);
            }
        }

        String sdImagePath = getDriveFilePath(getMachine().getSdImagePath());
        if (Config.enableEmulatedSDCard && sdImagePath != null) {
            if (Config.legacyDrives) {
                paramsList.add("-sd");
                paramsList.add(sdImagePath);
            } else {
                paramsList.add("-device");
                paramsList.add("sd-card,drive=sd0,bus=sdpci.0");
                paramsList.add("-drive");
                String param = "if=none,id=sd0";
                if (!sdImagePath.equals("")) {
                    param += ",file=" + sdImagePath;
                }
                paramsList.add(param);
            }
        }

    }


    /**
     * change the vnc password before we connect
     * The user is also prompted to create a certificate
     *
     * @param vncPassword The VNC password to be send to QEMU
     */
    protected void vncchangepassword(String vncPassword) throws Exception {
        String res = QmpClient.sendCommand(QmpClient.getChangeVncPasswdCommand(vncPassword));
        String desc = null;
        if (res != null && !res.equals("")) {
            JSONObject resObj = new JSONObject(res);
            if (resObj != null && !resObj.equals("") && res.contains("error")) {
                String resInfo = resObj.getString("error");
                if (resInfo != null && !resInfo.equals("")) {
                    JSONObject resInfoObj = new JSONObject(resInfo);
                    desc = resInfoObj.getString("desc");
                    Log.e(TAG, desc);
                }
            }
        }
    }

    protected String changedev(String dev, String value) {
        String response = QmpClient.sendCommand(QmpClient.getChangeDeviceCommand(dev, value));
        String displayDevValue = FileUtils.getFullPathFromDocumentFilePath(value);
        if (Config.debug)
            ToastUtils.toastLong(LimboApplication.getInstance(), Gravity.BOTTOM,
                    LimboApplication.getInstance().getString(R.string.ChangedDevice) + ": "
                            + dev + ": " + displayDevValue);
        return response;
    }

    protected String ejectdev(String dev) {
        String response = QmpClient.sendCommand(QmpClient.getEjectDeviceCommand(dev));
        if (Config.debug)
            ToastUtils.toastLong(LimboApplication.getInstance(), Gravity.BOTTOM,
                    LimboApplication.getInstance().getString(R.string.EjectedDevice) + ": " + dev);
        return response;
    }


    /**
     * Starts the service that will later start the qemu process
     */
    public void startService() {
        Intent i = new Intent(Config.ACTION_START, null, LimboApplication.getInstance(),
                MachineController.getInstance().getServiceClass());
        Bundle b = new Bundle();
        i.putExtras(b);
        Log.d(TAG, "启动虚拟机");
        LimboApplication.getInstance().startService(i);
    }

    /**
     * Starts the native process. This should be called from a background thread from a
     * foreground service in order to prevent the process from being killed
     *
     * @return String from the native code vm-executor-jni.cpp
     */
    public String start() {
        String res = null;
        try {
            // ===== 先设置 VirGL 渲染后端（必须早于 QEMU 的 EGL 初始化） =====
            try {
                String glesMode = LimboSettingsManager.getGlesMode(
                        LimboApplication.getInstance());
                int backend = "angle".equals(glesMode)
                        ? RENDER_BACKEND_ANGLE_VULKAN
                        : RENDER_BACKEND_SYSTEM_GLES;
                String nativeLibDir = LimboApplication.getInstance()
                        .getApplicationInfo().nativeLibraryDir;
                nativeSetVirglRenderBackend(backend, nativeLibDir);
                Log.d(TAG, "VirGL render backend = " + glesMode + " (" + backend + ")");
            } catch (Throwable t) {
                // 旧 .so 未实现 native 方法时不阻断启动
                Log.e(TAG, "nativeSetVirglRenderBackend failed", t);
            }
            // ===== 后端设置结束 =====

            String[] params = prepareParams(LimboApplication.getInstance());
            printParams(params);
            // XXX: for VNC we need to resume manually after a reasonable amount of time
            if (getMachine().getPaused() == 1 && MachineController.getInstance().isVNCEnabled()) {
                continueVM(5000);
            }

            if (MachineController.getInstance().isVNCEnabled() && LimboSettingsManager.getVNCEnablePassword(LimboApplication.getInstance())) {
                changeVncPass(LimboApplication.getInstance(), 2000);
            }

            ignoreBreakpointInvalidation(LimboSettingsManager.getIgnoreBreakpointInvalidation(LimboApplication.getInstance())?1:0, 2000);
            QmpClient.setExternal(LimboSettingsManager.getEnableExternalQMP(LimboApplication.getInstance()));
            String libFilename = getQemuLibrary();
            res = start(Config.storagedir, LimboApplication.getBasefileDir(),
                    libFilename, FileUtils.getNativeLibDir(LimboApplication.getInstance()) + "/" + libFilename,
                    Config.SDLHintScale, params);
        } catch (Exception ex) {
            ToastUtils.toastLong(LimboApplication.getInstance(), ex.getMessage());
            return res;
        }
        return res;
    }

    private void changeVncPass(final Context context, final long delay) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                try {
                    vncchangepassword(LimboSettingsManager.getVNCPass(context));
                } catch (Exception e) {
                    ToastUtils.toastLong(LimboApplication.getInstance(),
                            context.getString(R.string.CouldNotSetVNCPass) + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void continueVM(final int delay) {
        // TODO: We shouldn't have to go through the view dispatcher
        LimboApplication.getViewListener().onAction(MachineAction.CONTINUE_VM, delay);
    }

    public void stopvm(final int restart) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (restart != 0) {
                    QmpClient.sendCommand(QmpClient.getResetCommand());
                } else {
                    //XXX: Qmp command only halts the VM but doesn't exit so we use force close
//            QmpClient.sendCommand(QmpClient.powerDown());
                    stop(restart);
                }
            }
        }).start();
    }

    @Override
    public int getSdlRefreshRate(boolean idle) {
        if (idle)
            return getSDLRefreshRateIdle();
        else
            return getSDLRefreshRateDefault();
    }

    @Override
    public void setSdlRefreshRate(int value, boolean idle) {
        if (idle)
            setSDLRefreshRateIdle(value);
        else
            setSDLRefreshRateDefault(value);
    }

    @Override
    public String getDeviceName(MachineProperty driveProperty) {
        switch (driveProperty) {
            case CDROM:
                return cdDeviceName;
            case FDA:
                return fdaDeviceName;
            case FDB:
                return fdbDeviceName;
            case SD:
                return sdDeviceName;
        }
        return null;
    }

    @Override
    public synchronized void updateDisplay(int width, int height, int orientation) {
        if (!LimboSettingsManager.getPreventMouseOutOfBounds(LimboApplication.getInstance())) {
            return;
        }
        String mouse = getMachine().getMouse();
        // If we use absolute pointer devices in the guest os (usb-tablet, virtio-tablet-pci)
        // we need to prevent the mouse from going out of bounds. This case happens when we use
        // trackpad and when the guest display doesn't fit inside the Android Surface which is
        // pretty much all the time.
        // we could use SurfaceHolder.setFixedSize() to bound the surfaceview but it creates
        // problems with refreshing the surfaceview plus we would still need this fix for trackpad
        if (mouse != null && (mouse.equals("usb-tablet") || mouse.equals("virtio-tablet-pci"))) {
            int xmin = 0;
            int xmax = width;
            int ymin = 0;
            int ymax = height;
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                ymin = (int) (height - width * vm_height / (float) vm_width) / 2;
                ymax = (int) (height + width * vm_height / (float) vm_width) / 2;
            } else {
                xmin = (int) (width - height * vm_width / (float) vm_height) / 2;
                xmax = (int) (width + height * vm_width / (float) vm_height) / 2;
            }
            nativeMouseBounds(xmin, xmax, ymin, ymax);
        }
    }

    @Override
    public void setFullscreen() {
        nativeFullscreen();
        //TODO: sparc doesn't not have vga so we need to
        // see if we can apply similar call to the cg3
        if(LimboApplication.arch == Config.Arch.x86
                || LimboApplication.arch == Config.Arch.x86_64
                || LimboApplication.arch == Config.Arch.arm
                || LimboApplication.arch == Config.Arch.arm64
                || LimboApplication.arch == Config.Arch.ppc
                || LimboApplication.arch == Config.Arch.ppc64
        ) {
            nativeRefreshScreen(1);
        }
    }

    @Override
    public void enableAaudio(int value) {
        nativeEnableAaudio(value, Config.aaudioLibName,
                FileUtils.getNativeLibDir(LimboApplication.getInstance())
                        + "/" + Config.aaudioLibName);
    }

    @Override
    public void ignoreBreakpointInvalidation(int value){
        ignoreBreakpointInvalidation(value, 0);
    }

    private void ignoreBreakpointInvalidation(final int value, final long delay) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                nativeIgnoreBreakpointInvalidate(value);
            }
        }).start();
    }

    //TODO: re-enable getting status from the vm
    public String getVmState() {
        String res = QmpClient.sendCommand(QmpClient.getStateCommand());
        String state = "";
        if (res != null && !res.equals("")) {
            try {
                JSONObject resObj = new JSONObject(res);
                String resInfo = resObj.getString("return");
                JSONObject resInfoObj = new JSONObject(resInfo);
                state = resInfoObj.getString("status");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return state;
    }

    /**
     * Function sends a command via qmp to change or eject the removable device
     *
     * @param drive     The device to be changed
     * @param imagePath If its null it ejects the drive otherwise it uses the disk file at that path
     */
    public boolean changeRemovableDevice(final MachineProperty drive, final String imagePath) {
        if (!LimboSettingsManager.getEnableQmp(LimboApplication.getInstance())) {
            ToastUtils.toastShort(LimboApplication.getInstance(), LimboApplication.getInstance().getString(R.string.EnableQMPForChangingDrives));
            return false;
        }
        String dev = getDeviceName(drive);

        //XXX: first we eject any previous media
        String response = VMExecutor.this.ejectdev(dev);

        // if there is no media there is nothing else to do
        if (imagePath == null || imagePath.trim().equals("")) {
            return true;
        }

        //XXX: we encode some characters from the document file path so it's processed
        // correctly by qemu
        String imagePathConverted = FileUtils.encodeDocumentFilePath(imagePath);

        if (!FileUtils.fileValid(imagePathConverted)) {
            String msg = LimboApplication.getInstance().getString(R.string.CouldNotOpenDocFile) + " "
                    + FileUtils.getFullPathFromDocumentFilePath(imagePathConverted)
                    + "\n" + LimboApplication.getInstance().getString(R.string.PleaseReassingYourDiskFiles);
            ToastUtils.toastLong(LimboApplication.getInstance(), msg);
            return false;
        }
        response = VMExecutor.this.changedev(dev, imagePathConverted);
        if (response == null)
            return false;

        return true;
    }

    /**
     * Fuction is a pass thru from the c get_fd() function called from native code
     * This is bridged to the java code because it's the only way to open a file descriptor
     * from the native code
     *
     * @param path File path
     * @return Return value of FileUtils.get_fd()
     */
    public int get_fd(String path) {
        return FileUtils.get_fd(path);
    }

    /**
     * Fuction is a pass thru from the c close_fd() function called from native code
     * This is similar to the above get_fd but perhaps not needed.
     *
     * @param fd File Descriptor to be closed
     * @return Return value of FileUtils.close_fd()
     */
    public int close_fd(int fd) {
        return FileUtils.close_fd(fd);
    }

    @Override
    public String saveVM() {
        if (getMachine() == null || getMachine().getName() == null) {
            return "机器未加载，无法保存状态";
        }
        // Delete any previous state file
        File file = new File(getSaveStateName());
        if (file.exists()) {
            if (!file.delete()) {
                return LimboApplication.getInstance().getString(R.string.CannotDeletePreviousStateFile);
            }
        }

        if (Config.showToast)
            ToastUtils.toastShort(LimboApplication.getInstance(), LimboApplication.getInstance().getString(R.string.PleaseWaitSavingVMState));

        int currentFd = get_fd(getSaveStateName());
        String uri = "fd:" + currentFd;
        String command = QmpClient.getStopVMCommand();
        String msg = QmpClient.sendCommand(command);
        command = QmpClient.getMigrateCommand(false, false, uri);
        msg = QmpClient.sendCommand(command);
        if (msg != null) {
            return processMigrationResponse(msg);
        }
        return null;
    }

    @Override
    public void continueVM() {
        String command = QmpClient.getContinueVMCommand();
        QmpClient.sendCommand(command);
    }

    @Override
    public MachineController.MachineStatus getSaveVMStatus() {
        String pauseState = "";
        String command = QmpClient.getQueryMigrationCommand();
        String res = QmpClient.sendCommand(command);

        if (res != null && !res.equals("")) {
            try {
                JSONObject resObj = new JSONObject(res);
                String resInfo = resObj.getString("return");
                JSONObject resInfoObj = new JSONObject(resInfo);
                pauseState = resInfoObj.getString("status");
            } catch (JSONException e) {
                if (Config.debug)
                    Log.e(TAG, "Error while checking saving vm: " + e.getMessage());
            }
            if (pauseState.toUpperCase().equals("FAILED")) {
                Log.e(TAG, "错误: " + res);
            }
        }
        if (pauseState.toUpperCase().equals("ACTIVE")) {
            return MachineController.MachineStatus.Saving;
        } else if (pauseState.toUpperCase().equals("COMPLETED")) {
            return MachineController.MachineStatus.SaveCompleted;
        } else if (pauseState.toUpperCase().equals("FAILED")) {
            return MachineController.MachineStatus.SaveFailed;
        }
        //TODO: proper error handling with user messages
        return MachineController.MachineStatus.Unknown;
    }

    private String processMigrationResponse(String response) {
        String errorStr = null;
        try {
            JSONObject object = new JSONObject(response);
            errorStr = object.getString("error");
        } catch (Exception ex) {
            if (Config.debug)
                ex.printStackTrace();
        }
        if (errorStr != null) {
            String descStr = null;

            try {
                JSONObject descObj = new JSONObject(errorStr);
                descStr = descObj.getString("desc");
            } catch (Exception ex) {
                if (Config.debug)
                    ex.printStackTrace();
            }
            return descStr;
        }
        return null;
    }

    public void sendMouseEvent(int button, int action, int relative, float x, float y) {
        //XXX: Make sure that mouse motion is not triggering crashes in SDL while resizing
        if (LimboSDLActivity.isResizing) {
            return;
        }

        nativeMouseEvent(button, action, relative, (int) x, (int) y);
    }

    public boolean getQMPAllowExternal() {
        return LimboSettingsManager.getEnableExternalQMP(LimboApplication.getInstance());
    }
}

