package com.limbo.emu.plus;

import android.os.Bundle;

import com.max2idea.android.limbo.links.LinksManager2;
import com.max2idea.android.limbo.log.Logger;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.max2idea.android.limbo.main.Config;
import com.max2idea.android.limbo.main.LimboActivity;
import com.max2idea.android.limbo.links.LinksManager;
import com.max2idea.android.limbo.main.LimboApplication;
public class LimboEmuActivity extends LimboActivity {
    public void onCreate(Bundle bundle) {
        //LimboApplication.arch = Config.Arch.x86_64;
        Config.clientClass = this.getClass();
        Config.enableKVM = true;
        Config.enableEmulatedSDCard = true;
        Config.downloadLinks.put(getString(com.limbo.emu.lib.R.string.GoToWiki), new LinksManager2.LinkInfo(getString(com.limbo.emu.lib.R.string.GoToWiki),
                "",
                "https://github.com/ANIPLXUAN",
                LinksManager2.LinkType.SITE));
        Config.downloadLinks.put("GitHub", new LinksManager2.LinkInfo("GitHub",
                "GitHub Releases",
                "https://github.com/ANIPLXUAN",
                LinksManager2.LinkType.GITHUB));
        //XXX; only for 64bit hosts, also make sure you have qemu 11.1.1 x86_64-softmmu and above compiled
        if (LimboApplication.isHost64Bit() && Config.enableMTTCG)
            Config.enableMTTCG = true;
        else
            Config.enableMTTCG = false;
        Config.osImages.put(getString(R.string.DebianLinux), new LinksManager.LinkInfo(getString(R.string.DebianLinux),
                getString(R.string.DebianLinuxDescr),
                "https://www.debian.org/distrib/",
                LinksManager.LinkType.ISO));
        Config.osImages.put("Ubuntu", new LinksManager.LinkInfo(getString(R.string.Ubuntu),
                getString(R.string.UbuntuDescr),
                "https://ubuntu.com/",
                LinksManager.LinkType.ISO));
        Config.osImages.put(getString(R.string.Windows), new LinksManager.LinkInfo(getString(R.string.Windows),
                getString(R.string.WindowsDescr),
                "https://pan.yukaidi.com/s/BwdySl",
                LinksManager.LinkType.ISO));
        Config.osImages.put("VirtIO", new LinksManager.LinkInfo("VirtIO",
                getString(R.string.VirtIODescr),
                "https://fedorapeople.org/groups/virt/virtio-win/direct-downloads/latest-virtio/",
                LinksManager.LinkType.TOOL));
        super.onCreate(bundle);
        //TODO: change location to something that the user will have access outside of limbo
        //  like internal storage
        Logger.setupLogFile("/limbo/limbo-x86-log.txt");
    }

    protected void loadQEMULib() {
        String libName;
        switch (LimboApplication.arch) {
            case inferno_aarch64:
                libName = "qemu-system-aarch64-v2";   // 对应 libqemu-system-aarch64-v2.so
                break;
            case arm64:
                libName = "qemu-system-aarch64";
                break;
            case arm:
                libName = "qemu-system-arm";
                break;
            case x86:
                libName = "qemu-system-i386";
                break;
            case x86_64:
                libName = "qemu-system-x86_64-11.1.1";
                break;
            case sparc:
                libName = "qemu-system-sparc";
                break;
            case sparc64:
                libName = "qemu-system-sparc64";
                break;
            case ppc:
                libName = "qemu-system-ppc";
                break;
            case ppc64:
                libName = "qemu-system-ppc64";
                break;
            default:
                libName = "qemu-system-x86_64-11.1.1";
                break;
        }

        try {
            System.loadLibrary(libName);
        } catch (Error ex) {
            ex.printStackTrace();
        }
    }
}
