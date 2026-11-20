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
package com.max2idea.android.limbo.main;

import com.max2idea.android.limbo.ui.glass.GlassDialogs;
import android.app.Activity;
import android.widget.*;
import com.max2idea.android.limbo.machine.Machine;
import androidx.appcompat.app.AppCompatDelegate;
import android.view.View;
import android.view.ViewGroup;
import com.max2idea.android.limbo.install.Installer;
import android.app.AlertDialog;

import android.content.Intent;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Build;
import android.widget.EditText;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.dialog.DialogUtils;
import com.max2idea.android.limbo.downloads.Downloads;
import com.max2idea.android.limbo.files.FileInstaller;
import com.max2idea.android.limbo.files.FileUtils;
import com.max2idea.android.limbo.help.Help;
import com.max2idea.android.limbo.install.Installer;
import com.max2idea.android.limbo.keyboard.KeyboardUtils;
import com.max2idea.android.limbo.links.LinksManager;
import com.max2idea.android.limbo.links.LinksManager2;
import com.max2idea.android.limbo.log.Logger;
import com.max2idea.android.limbo.machine.ArchDefinitions;
import com.max2idea.android.limbo.machine.BIOSImporter;
import com.max2idea.android.limbo.machine.Machine;
import com.max2idea.android.limbo.machine.Machine.FileType;
import com.max2idea.android.limbo.machine.MachineAction;
import com.max2idea.android.limbo.machine.MachineController;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.max2idea.android.limbo.machine.MachineController.MachineStatus;
import com.max2idea.android.limbo.machine.MachineExporter;
import com.max2idea.android.limbo.machine.MachineFilePaths;
import com.max2idea.android.limbo.machine.MachineImporter;
import com.max2idea.android.limbo.machine.MachineProperty;
import com.max2idea.android.limbo.network.NetworkUtils;
import com.max2idea.android.limbo.toast.ToastUtils;
import com.max2idea.android.limbo.ui.SpinnerAdapter;
import com.max2idea.android.limbo.updates.UpdateChecker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Observable;
import java.util.Observer;

public class LimboActivity extends AppCompatActivity
        implements MachineController.OnMachineStatusChangeListener,
        MachineController.OnEventListener, Observer {

    private static final String TAG = "LimboActivity";


    private static final int HELP = 0;
    private String mPendingOpenMachine = null;
    private static final int QUIT = 1;
    private static final int INSTALL = 2;
    private static final int DELETE = 3;
    private static final int EXPORT = 4;
    private static final int IMPORT = 5;
    private static final int CHANGELOG = 6;
    private View mGuidePermissionPage;
    private static final int MENU_SAVE = 100;
    private static final int MENU_CANCEL = 101;
    private static final int MENU_DELETE = 102;
    private android.widget.Button mGuidePermissionSkip;
    private android.widget.Button mGuidePermissionGrant;
    private boolean mIsListMode = true;         // 默认显示列表
    private boolean mIsCreating = false;        // 是否在创建模式（新建虚拟机）
    private String mCreatingMachineName = null; // 正在创建/编辑的机器名
    private View mMachineListView;
    private View mMachineConfigView;
    private ListView mMachineList;
    private TextView mMachineEmpty;
    private View mNewMachineBtn;
    private MachineListAdapter mMachineListAdapter;
    private int mCurrentGuidePage = 0;
    private static final int LICENSE = 7;
    private static final int OSLIST = 8;
    private static final int DOWNLOAD = 9;
    private static final int VIEWLOG = 10;
    private static final int CREATE = 11;
    private static final int DISCARD_VM_STATE = 13;
    private static final int SETTINGS = 15;
    private static final int TOOLS = 16;
    private static final int IMPORT_BIOS_FILE = 17;

    // disk mapping
    private static final Hashtable<FileType, DiskInfo> diskMapping = new Hashtable<>();
    private static boolean sPendingFirstLaunchDialogs = false;

    private static boolean libLoaded;
    public View parent;
    private boolean machineLoaded;
    private FileType browseFileType = null;

    private View mGuideOverlay;
    private View mGuideCheckPage, mGuideLicensePage, mGuideBiosPage;
    private android.widget.ProgressBar mGuideCheckSpinner;
    private TextView mGuideCheckResult;
    private android.widget.Button mGuideCheckContinue;
    private TextView mGuideLicenseText;
    private android.widget.Button mGuideLicenseContinue;
    private boolean ignoreNextMachineSelect = false;
    private android.widget.ProgressBar mGuideBiosSpinner;
    private android.widget.ImageView mGuideBiosCheck;
    private TextView mGuideBiosText;
    private android.widget.Button mGuideBiosContinue;


    //Widgets
    private ImageView mStatus;
    private EditText mDNS;
    private EditText mHOSTFWD;
    private String mPendingCreateArch = null;
    private EditText mAppend;
    private EditText mExtraParams;
    private TextView mStatusText;
    private Spinner mMachine;
    private Spinner mCPU;
    private Spinner mMachineType;
    private Spinner mCPUNum;
    private Spinner mKernel;
    private Spinner mInitrd;
    // HDD
    private View mHDAOptions;
    private Spinner mHDA;
    private View mHDBOptions;
    private Spinner mHDB;
    private View mHDCOptions;
    private Spinner mHDC;
    private View mHDDOptions;
    private Spinner mHDD;


    //removable
    private Spinner mCD;
    private Spinner mFDA;
    private Spinner mFDB;
    private Spinner mSD;
    private CheckBox mCDenable;
    private CheckBox mFDAenable;
    private CheckBox mFDBenable;
    private CheckBox mSDenable;
    private View mCDOptions;

    // misc
    private Spinner mRamSize;


    private Spinner mVideoRamSize;
    private Spinner mBootDevices;
    private Spinner mBios;
    private Spinner mNetworkCard;
    private Spinner mNetConfig;
    private Spinner mVGAConfig;
    private Spinner mUSBConfig;
    private Spinner mSoundCard;
    private Spinner mUI;
    private CheckBox mDisableACPI;
    private CheckBox mDisableHPET;
    private CheckBox mDisableTSC;
    private CheckBox mEnableKVM;
    private CheckBox mEnableMTTCG;
    private Spinner mKeyboard;
    private Spinner mMouse;

    // buttons
    private ImageButton mStart;
    private ImageButton mPause;
    private ImageButton mStop;
    private ImageButton mRestart;

    //sections
    private LinearLayout mCPUSectionDetails;
    private LinearLayout mStorageSectionDetails;
    private LinearLayout mUserInterfaceSectionDetails;
    private static String sPendingOpenMachine = null;
    private LinearLayout mAdvancedSectionDetails;
    private LinearLayout mBootSectionDetails;
    private LinearLayout mGraphicsSectionDetails;
    private LinearLayout mRemovableStorageSectionDetails;
    private LinearLayout mNetworkSectionDetails;
    private LinearLayout mAudioSectionDetails;

    //summary
    private TextView mUISectionSummary;
    private TextView mCPUSectionSummary;
    private TextView mStorageSectionSummary;
    private TextView mRemovableStorageSectionSummary;
    private TextView mGraphicsSectionSummary;
    private TextView mAudioSectionSummary;
    private TextView mNetworkSectionSummary;
    private TextView mBootSectionSummary;
    private TextView mAdvancedSectionSummary;

    //layouts
    private NestedScrollView mScrollView;
    private boolean firstMTTCGCheck;
    private ViewListener viewListener;

    public void changeStatus(final MachineStatus status_changed) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (MachineController.getInstance().isRunning() || status_changed == MachineStatus.Running) {
                    mStatus.setImageResource(R.drawable.on);
                    if (mUI.getSelectedItemPosition() == 0) {
                        // VNC
                        mStatusText.setText(R.string.Running);
                        //XXX: we block the user from changing the drives
                        // from this activitybecause sdl is suspended and the thread will block
                        // so they have to change it from within the SDL Activity
                        enableRemovableDiskValues(true);
                    } else {
                        // SDL is always suspend in the background
                        mStatusText.setText(R.string.Suspended);
                        enableRemovableDiskValues(false);
                    }
                    unlockRemovableDevices(false);
                    enableNonRemovableDeviceOptions(false);
                    mMachine.setEnabled(false);
                } else if (status_changed == MachineStatus.Ready || status_changed == MachineStatus.Stopped) {
                    mStatus.setImageResource(R.drawable.off);
                    mStatusText.setText(R.string.Stopped);
                    unlockRemovableDevices(true);
                    enableRemovableDiskValues(true);
                    enableNonRemovableDeviceOptions(true);
                } else if (status_changed == MachineStatus.Saving) {
                    mStatus.setImageResource(R.drawable.on);
                    mStatusText.setText(R.string.savingState);
                    unlockRemovableDevices(false);
                    enableRemovableDiskValues(false);
                    enableNonRemovableDeviceOptions(false);
                } else if (status_changed == MachineStatus.Paused) {
                    mStatus.setImageResource(R.drawable.on);
                    mStatusText.setText(R.string.paused);
                    unlockRemovableDevices(false);
                    enableRemovableDiskValues(false);
                    enableNonRemovableDeviceOptions(false);
                }
            }
        });
    }

    private void onTap() {
        String userid = LimboApplication.getUserId(this);
        if (!(new File("/dev/net/tun")).exists()) {
            LimboActivityCommon.tapNotSupported(this, userid);
            return;
        }
        LimboActivityCommon.promptTap(this, userid);
    }

    public void setUserPressed(boolean pressed) {
        if (pressed) {
            setupMiscOptions();
            setupNonRemovableDiskListeners();
            enableRemovableDiskListeners();
        } else {
            disableListeners();
            disableRemovableDiskListeners();
        }
    }

    private void disableRemovableDiskListeners() {
        disableRemovableDiskListener(mCDenable, mCD);
        disableRemovableDiskListener(mCDenable, mCD);
        disableRemovableDiskListener(mCDenable, mCD);
        disableRemovableDiskListener(mCDenable, mCD);
    }

    private void disableRemovableDiskListener(CheckBox enableDrive, Spinner spinner) {
        enableDrive.setOnCheckedChangeListener(null);
        spinner.setOnItemSelectedListener(null);
    }

    private void enableRemovableDiskListeners() {
        enableRemovableDiskListener(mCD, mCDenable, mCDOptions, MachineProperty.CDROM, FileType.CDROM);
        enableRemovableDiskListener(mFDA, mFDAenable, null, MachineProperty.FDA, FileType.FDA);
        enableRemovableDiskListener(mFDB, mFDBenable, null, MachineProperty.FDB, FileType.FDB);
        enableRemovableDiskListener(mSD, mSDenable, null, MachineProperty.SD, FileType.SD);
    }

    private void enableRemovableDiskListener(final Spinner spinner, final CheckBox driveEnable,
                                             final View driveOptions,
                                             final MachineProperty driveName,
                                             final FileType fileType) {
        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String value = (String) ((ArrayAdapter<?>) spinner.getAdapter()).getItem(position);
                if (position == 1 && driveEnable.isChecked()) {
                    browseFileType = fileType;
                    LimboFileManager.browse(LimboActivity.this, browseFileType, Config.OPEN_IMAGE_FILE_REQUEST_CODE);
                    spinner.setSelection(0);
                } else {
                    notifyFieldChange(MachineProperty.REMOVABLE_DRIVE, new Object[]{driveName, value});
                }
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });
        driveEnable.setOnCheckedChangeListener(
                new OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton viewButton, boolean isChecked) {
                        spinner.setEnabled(isChecked);
                        notifyFieldChange(MachineProperty.DRIVE_ENABLED, new Object[]{driveName, isChecked});
                        triggerUpdateSpinner(spinner);
                    }

                }
        );
        if(driveOptions!=null) {
            driveOptions.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(driveEnable.isChecked())
                        promptDriveInterface(driveName);
                }
            });
        }
    }


    private void setupMiscOptions() {

        mCPU.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String cpu = (String) ((ArrayAdapter<?>) mCPU.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.CPU, cpu);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mMachineType.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String machineType = (String) ((ArrayAdapter<?>) mMachineType.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.MACHINETYPE, machineType);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mUI.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String ui = (String) ((ArrayAdapter<?>) mUI.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.UI, ui);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mCPUNum.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                final String cpuNum = (String) ((ArrayAdapter<?>) mCPUNum.getAdapter()).getItem(position);
                if (position > 0 && getMachine().getEnableMTTCG() != 1 && getMachine().getEnableKVM() != 1 && !firstMTTCGCheck) {
                    firstMTTCGCheck = true;
                    promptMultiCPU(cpuNum);
                } else {
                    notifyFieldChange(MachineProperty.CPUNUM, cpuNum);
                }
                mDisableTSC.setChecked(position > 0 && (LimboApplication.arch == Config.Arch.x86 ||
                        LimboApplication.arch == Config.Arch.x86_64));
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mRamSize.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String ram = (String) ((ArrayAdapter<?>) mRamSize.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.MEMORY, ram);
            }

            public void onNothingSelected(AdapterView<?> parentView) {

            }
        });
        mVideoRamSize.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String videoram = (String) ((ArrayAdapter<?>) mVideoRamSize.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.VIDEOMEMORY, videoram);
            }

            public void onNothingSelected(AdapterView<?> parentView) {

            }
        });
        mKernel.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String kernel = (String) ((ArrayAdapter<?>) mKernel.getAdapter()).getItem(position);
                if (position == 0) {
                    notifyFieldChange(MachineProperty.KERNEL, null);
                } else if (position == 1) {
                    browseFileType = FileType.KERNEL;
                    LimboFileManager.browse(LimboActivity.this, browseFileType, Config.OPEN_IMAGE_FILE_REQUEST_CODE);
                    mKernel.setSelection(0);
                } else if (position > 1) {
                    notifyFieldChange(MachineProperty.KERNEL, kernel);
                }
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mInitrd.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String initrd = (String) ((ArrayAdapter<?>) mInitrd.getAdapter()).getItem(position);
                if (position == 0) {
                    notifyFieldChange(MachineProperty.INITRD, initrd);
                } else if (position == 1) {
                    browseFileType = FileType.INITRD;
                    LimboFileManager.browse(LimboActivity.this, browseFileType, Config.OPEN_IMAGE_FILE_REQUEST_CODE);
                    mInitrd.setSelection(0);
                } else if (position > 1) {
                    notifyFieldChange(MachineProperty.INITRD, initrd);
                }
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mBootDevices.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;

                String bootDev = (String) ((ArrayAdapter<?>) mBootDevices.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.BOOT_CONFIG, bootDev);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mBios.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;

                String biosDev = (String) ((ArrayAdapter<?>) mBios.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.BIOS_CONFIG, biosDev);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });
        mNetConfig.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;

                String netcfg = (String) ((ArrayAdapter<?>) mNetConfig.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.NETCONFIG, netcfg);
                if (position > 0 && getMachine().getPaused() == 0
                        && MachineController.getInstance().getCurrStatus() != MachineStatus.Running) {
                    mNetworkCard.setEnabled(true);
                    mDNS.setEnabled(true);
                    mHOSTFWD.setEnabled(true);
                } else {
                    mNetworkCard.setEnabled(false);
                    mDNS.setEnabled(false);
                    mHOSTFWD.setEnabled(false);
                }

                if (netcfg.equals("TAP")) {
                    onTap();
                } else if (netcfg.equals("User")) {
                    LimboActivityCommon.onNetworkUser(LimboActivity.this);
                }
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mNetworkCard.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                if (position < 0 || position >= mNetworkCard.getCount()) {
                    mNetworkCard.setSelection(0);
                    return;
                }
                String niccfg = (String) ((ArrayAdapter<?>) mNetworkCard.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.NICCONFIG, niccfg);
            }

            public void onNothingSelected(final AdapterView<?> parentView) {
            }
        });

        mVGAConfig.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String vgacfg = (String) ((ArrayAdapter<?>) mVGAConfig.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.VGA, vgacfg);

                boolean canSetVram = vgacfg.equals("std")
                        || vgacfg.equals("vmware")
                        || vgacfg.equals("cirrus")
                        || vgacfg.contains("gl");

                if (canSetVram && getMachine().getPaused() == 0
                        && MachineController.getInstance().getCurrStatus() != MachineStatus.Running) {
                    mVideoRamSize.setEnabled(true);
                    populateVideoRAM();   // 切换显卡时刷新显存列表
                } else {
                    mVideoRamSize.setEnabled(false);
                }
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mUSBConfig.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String usbcfg = (String) ((ArrayAdapter<?>) mUSBConfig.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.USB, usbcfg);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mSoundCard.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String sndcfg = (String) ((ArrayAdapter<?>) mSoundCard.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.SOUNDCARD, sndcfg);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mDisableACPI.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton viewButton, boolean isChecked) {
                if (getMachine() == null)
                    return;
                notifyFieldChange(MachineProperty.DISABLE_ACPI, isChecked);
            }
        });

        mDisableHPET.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton viewButton, boolean isChecked) {
                if (getMachine() == null)
                    return;
                notifyFieldChange(MachineProperty.DISABLE_HPET, isChecked);
            }
        });

        mDisableTSC.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton viewButton, boolean isChecked) {
                if (getMachine() == null)
                    return;
                notifyFieldChange(MachineProperty.DISABLE_TSC, isChecked);
            }
        });

        mDNS.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (getMachine() == null)
                    return;
                if (!hasFocus) {
                    setDNSServer(mDNS.getText().toString());
                    LimboSettingsManager.setDNSServer(LimboActivity.this, mDNS.getText().toString());
                }
            }
        });

        mHOSTFWD.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (getMachine() == null)
                    return;
                if (!hasFocus) {
                    notifyFieldChange(MachineProperty.HOSTFWD, mHOSTFWD.getText().toString());
                }
            }
        });

        mAppend.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (getMachine() == null)
                    return;
                if (!hasFocus) {
                    notifyFieldChange(MachineProperty.APPEND, mAppend.getText().toString());
                }
            }
        });

        mExtraParams.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (getMachine() == null)
                    return;
                if (!hasFocus) {
                    notifyFieldChange(MachineProperty.EXTRA_PARAMS, mExtraParams.getText().toString());
                }
            }
        });

        OnClickListener resetClickListener = new OnClickListener() {
            @Override
            public void onClick(View view) {
                view.setFocusableInTouchMode(true);
                view.setFocusable(true);
            }
        };

        mDNS.setOnClickListener(resetClickListener);
        mAppend.setOnClickListener(resetClickListener);
        mHOSTFWD.setOnClickListener(resetClickListener);
        mExtraParams.setOnClickListener(resetClickListener);
        mEnableKVM.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton viewButton, boolean isChecked) {
                if (getMachine() == null)
                    return;
                if (isChecked) {
                    promptKVM();
                } else {
                    notifyFieldChange(MachineProperty.ENABLE_KVM, isChecked);
                }

            }

        });

        mEnableMTTCG.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton viewButton, boolean isChecked) {
                if (getMachine() == null)
                    return;
                if (isChecked) {
                    promptEnableMTTCG();
                } else {
                    notifyFieldChange(MachineProperty.ENABLE_MTTCG, isChecked);
                }
            }
        });

        mKeyboard.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;
                String keyboardCfg = (String) ((ArrayAdapter<?>) mKeyboard.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.KEYBOARD, keyboardCfg);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mMouse.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                String mouseCfg = (String) ((ArrayAdapter<?>) mMouse.getAdapter()).getItem(position);
                notifyFieldChange(MachineProperty.MOUSE, mouseCfg);
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });
    }

    private void setCPUOptions() {
        if (MachineController.getInstance().getCurrStatus() != MachineStatus.Running &&
                (LimboApplication.arch == Config.Arch.x86 || LimboApplication.arch == Config.Arch.x86_64)) {
            mDisableACPI.setEnabled(true);
            mDisableHPET.setEnabled(true);
            mDisableTSC.setEnabled(true);
        } else {
            mDisableACPI.setEnabled(false);
            mDisableHPET.setEnabled(false);
            mDisableTSC.setEnabled(false);
        }
    }

    private void setArchOptions() {
        // 机器已经完整加载过，不需要重新填充
        if (machineLoaded) {
            return;
        }

        // 新建 / 切换架构时 machine 可能为 null
        Machine m = getMachine();
        if (m == null) {
            android.util.Log.w("ArchDebug", "setArchOptions: machine is null, skip");
            return;
        }

        populateMachineType(m.getMachineType());
        populateCPUs(m.getCpu());
        populateNetDevices(m.getNetworkCard());
    }

    private void promptKVM() {
        DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                notifyFieldChange(MachineProperty.ENABLE_KVM, true);
                mEnableMTTCG.setChecked(false);
            }
        };

        DialogInterface.OnClickListener cancelListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mEnableKVM.setChecked(false);
                        notifyFieldChange(MachineProperty.ENABLE_KVM, false);
                    }
                };

        DialogInterface.OnClickListener helpListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mEnableKVM.setChecked(false);
                        notifyFieldChange(MachineProperty.ENABLE_KVM, false);
                        LimboActivityCommon.goToURL(LimboActivity.this, Config.kvmLink);
                    }
                };

        DialogUtils.UIAlert(LimboActivity.this, getString(R.string.EnableKVM),
                getString(R.string.EnableKVMWarning),
                16, false, getString(android.R.string.ok),
                okListener, getString(android.R.string.cancel),
                cancelListener, getString(R.string.KVMHelp), helpListener);
    }

    private void promptEnableMTTCG() {
        DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                notifyFieldChange(MachineProperty.ENABLE_MTTCG, true);
                mEnableKVM.setChecked(false);
            }
        };
        DialogInterface.OnClickListener cancelListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        notifyFieldChange(MachineProperty.ENABLE_MTTCG, false);
                        mEnableMTTCG.setChecked(false);
                    }
                };
        DialogInterface.OnClickListener helpListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mEnableMTTCG.setChecked(false);
                        notifyFieldChange(MachineProperty.ENABLE_MTTCG, false);
                        LimboActivityCommon.goToURL(LimboActivity.this, Config.faqLink);
                    }
                };
        DialogUtils.UIAlert(LimboActivity.this, getString(R.string.enableMTTCG),
                getString(R.string.enableMTTCGWarning),
                16, false, getString(android.R.string.ok), okListener,
                getString(android.R.string.cancel)
                , cancelListener, getString(R.string.mttcgHelp), helpListener);
    }

    private void promptMultiCPU(final String cpuNum) {
        DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                notifyFieldChange(MachineProperty.CPUNUM, cpuNum);
            }
        };
        DialogInterface.OnClickListener cancelListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mCPUNum.setSelection(0);
                    }
                };
        DialogInterface.OnClickListener helpListener =
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mCPUNum.setSelection(0);
                        LimboActivityCommon.goToURL(LimboActivity.this, Config.faqLink);
                    }
                };
        DialogUtils.UIAlert(LimboActivity.this, getString(R.string.multipleVCPU),
                getString(R.string.multipleVCPUWarning)
                        + ((LimboApplication.arch == Config.Arch.x86_64) ?
                        getString(R.string.disableTSCInstructions) : "")
                        + " " + getString(R.string.DoYouWantToContinue),
                16, false, getString(android.R.string.ok), okListener,
                getString(android.R.string.cancel), cancelListener, getString(R.string.vCPUHelp), helpListener);
    }

    private void setupNonRemovableDiskListeners() {
        setupNonRemovableDiskListener(mHDA, mHDAOptions, MachineProperty.HDA, FileType.HDA);
        setupNonRemovableDiskListener(mHDB, mHDBOptions, MachineProperty.HDB, FileType.HDB);
        setupNonRemovableDiskListener(mHDC, mHDCOptions, MachineProperty.HDC, FileType.HDC);
        setupNonRemovableDiskListener(mHDD, mHDDOptions, MachineProperty.HDD, FileType.HDD);

    }

    private void setupNonRemovableDiskListener(final Spinner diskSpinner, final View diskImage,
                                               final MachineProperty machineDriveName,
                                               final FileType diskFileType) {
        diskSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (getMachine() == null)
                    return;

                String hdName = (String) ((ArrayAdapter<?>) diskSpinner.getAdapter()).getItem(position);
                if (position == 1) {
                    promptImageName(LimboActivity.this, diskFileType);
                    diskSpinner.setSelection(0);
                } else if (position == 2) {
                    browseFileType = diskFileType;
                    LimboFileManager.browse(LimboActivity.this, browseFileType, Config.OPEN_IMAGE_FILE_REQUEST_CODE);
                    diskSpinner.setSelection(0);
                } else {
                    notifyFieldChange(MachineProperty.NON_REMOVABLE_DRIVE, new Object[]{machineDriveName, hdName});
                }
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        diskImage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                promptDriveInterface(machineDriveName);
            }
        });
    }

    private LinearLayout mVmContent;
    private View mOnlineContent;
    private LinearLayout mNavVm, mNavOnline, mNavSettings, mNavAbout;
    private TextView mNavVmText, mNavOnlineText;

    private void promptDriveInterface(final MachineProperty machineDriveName) {
        if(getMachine() == null)
            return;

        final String[] items = {
                "none",
                "ide",
                "scsi",
                "virtio",
                "NVMe"
        };
        final AlertDialog.Builder mBuilder = new AlertDialog.Builder(this);
        mBuilder.setTitle(machineDriveName + " " + getString(R.string.Interface));
        int driveInterface = getMachineInterface(machineDriveName, items);
        mBuilder.setSingleChoiceItems(items, driveInterface, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                notifyFieldChange(MachineProperty.MEDIA_INTERFACE, new Object[] {machineDriveName, items[i]});
                dialog.dismiss();
            }
        });
        final AlertDialog alertDialog = mBuilder.create();
        GlassDialogs.applyOnShow(alertDialog);
        alertDialog.show();
    }

    private int getMachineInterface(MachineProperty machineDriveName, String[] items) {
        String hdInterfaceStr = null;
        switch(machineDriveName) {
            case HDA:
                hdInterfaceStr = getMachine().getHdaInterface();
                break;
            case HDB:
                hdInterfaceStr = getMachine().getHdbInterface();
                break;
            case HDC:
                hdInterfaceStr = getMachine().getHdcInterface();
                break;
            case HDD:
                hdInterfaceStr = getMachine().getHddInterface();
                break;
            case CDROM:
                hdInterfaceStr = getMachine().getCDInterface();
                break;
        }
        for(int i=0; i<items.length; i++) {
            if(items[i].equals(hdInterfaceStr))
                return i;
        }
        return 0;
    }


    protected synchronized void setDNSServer(String string) {

        File resolvConf = new File(LimboApplication.getBasefileDir() + "/etc/resolv.conf");
        FileOutputStream fileStream = null;
        try {
            fileStream = new FileOutputStream(resolvConf);
            String str = "nameserver " + string + "\n\n";
            byte[] data = str.getBytes();
            fileStream.write(data);
        } catch (Exception ex) {
            Log.e(TAG, "Could not write DNS to file: " + ex);
        } finally {
            if (fileStream != null)
                try {
                    fileStream.close();
                } catch (IOException e) {

                    e.printStackTrace();
                }
        }
    }

    private void disableListeners() {
        if (mMachine == null)
            return;
        mUI.setOnItemSelectedListener(null);
        mKeyboard.setOnItemSelectedListener(null);
        mMouse.setOnItemSelectedListener(null);
        mMachineType.setOnItemSelectedListener(null);
        mCPU.setOnItemSelectedListener(null);
        mCPUNum.setOnItemSelectedListener(null);
        mRamSize.setOnItemSelectedListener(null);
        mDisableACPI.setOnCheckedChangeListener(null);
        mDisableHPET.setOnCheckedChangeListener(null);
        mDisableTSC.setOnCheckedChangeListener(null);
        mEnableKVM.setOnCheckedChangeListener(null);
        mEnableMTTCG.setOnCheckedChangeListener(null);
        mHDA.setOnItemSelectedListener(null);
        mHDB.setOnItemSelectedListener(null);
        mHDC.setOnItemSelectedListener(null);
        mHDD.setOnItemSelectedListener(null);
        mBootDevices.setOnItemSelectedListener(null);
        mBios.setOnItemSelectedListener(null);
        mKernel.setOnItemSelectedListener(null);
        mInitrd.setOnItemSelectedListener(null);
        mAppend.setOnFocusChangeListener(null);
        mVGAConfig.setOnItemSelectedListener(null);
        mVideoRamSize.setOnItemSelectedListener(null);
        mSoundCard.setOnItemSelectedListener(null);
        mNetConfig.setOnItemSelectedListener(null);
        mNetworkCard.setOnItemSelectedListener(null);
        mDNS.setOnFocusChangeListener(null);
        mHOSTFWD.setOnFocusChangeListener(null);
        mUSBConfig.setOnItemSelectedListener(null);
        mExtraParams.setOnFocusChangeListener(null);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 液态玻璃下拉：只作用于 Spinner 的下拉层，其余界面不受影响
        com.max2idea.android.limbo.ui.glass.LiquidGlassSpinnerHook.install(this);

        android.util.Log.d("ArchDebug", "onCreate START: arch=" + LimboApplication.arch);

        setupAppEnvironment();
        android.util.Log.d("ArchDebug", "after setupAppEnvironment: arch=" + LimboApplication.arch);

        clearNotifications();
        setupStrictMode();
        setContentView(getMainLayoutResId());

        setupWidgets();

        // 液态玻璃底部导航：只作用于 bottom_nav 这一根条，其余界面不动
        com.max2idea.android.limbo.ui.glass.LiquidGlassBottomNav.install(this);

        // 液态玻璃卡片：border / toolbar_rounded / rounded_button / machine_item_background
        // 这四个背景对应的 View 全部换成真 SDF 折射玻璃（一层全屏 GPU 层，一次 draw 画完）
        com.max2idea.android.limbo.ui.glass.GlassCards.install(this);

        android.util.Log.d("ArchDebug", "after setupWidgets: arch=" + LimboApplication.arch);

        setupController();
        android.util.Log.d("ArchDebug", "after setupController: arch=" + LimboApplication.arch);

        setupDiskMapping();
        createListeners();
        android.util.Log.d("ArchDebug", "after createListeners: arch=" + LimboApplication.arch);

        android.util.Log.d("ArchDebug", "onCreate before populateAttributesUI: arch=" + LimboApplication.arch);
        populateAttributesUI();
        android.util.Log.d("ArchDebug", "onCreate after populateAttributesUI: arch=" + LimboApplication.arch);
//        checkFirstLaunch();
        setupToolbar();
        checkLog();
        checkAndLoadLibs();
//        restore();
        setupListeners();
        addGenericOperatingSystems();
        int targetTab = getIntent().getIntExtra("showTab", -1);
        if (targetTab >= 0 && targetTab != mCurrentTab) {
            showTab(targetTab);
        }
        startGuide();
        showMachineList();
        if (sPendingOpenMachine != null) {
            final String name = sPendingOpenMachine;
            sPendingOpenMachine = null;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    showMachineConfig(false, name);
                    showOperatingSystems();
                    enableNonRemovableDeviceOptions(true);
                    enableRemovableDeviceOptions(true);
                    setArchOptions();
                }
            }, 300);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        int targetTab = intent.getIntExtra("showTab", -1);
        if (targetTab >= 0 && targetTab != mCurrentTab) {
            showTab(targetTab);
        }

        // 处理新建后跳转
        if (sPendingOpenMachine != null) {
            final String name = sPendingOpenMachine;
            sPendingOpenMachine = null;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    showMachineConfig(false, name);
                    showOperatingSystems();
                    enableNonRemovableDeviceOptions(true);
                    enableRemovableDeviceOptions(true);
                    setArchOptions();
                }
            }, 300);
        }
    }

    private void setEnabledRecursive(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setEnabledRecursive(group.getChildAt(i), enabled);
            }
        }
    }

    private void setupAppEnvironment() {
        LimboApplication.setupEnv(this);
    }

    private void setupController() {
        setViewListener(LimboApplication.getViewListener());
    }

    public void setViewListener(ViewListener viewListener) {
        this.viewListener = viewListener;
    }

    private void setupListeners() {
        android.util.Log.d("LimboDebug", "=== setupListeners called ===");
        MachineController.getInstance().addOnStatusChangeListener(this);
        MachineController.getInstance().addOnEventListener(this);

        mStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // 先启动 SDL 界面，SDL 初始化后才能跑 QEMU
                Intent i = new Intent(LimboActivity.this, LimboSDLActivity.class);
                startActivity(i);

                // 延迟启动 QEMU
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        notifyAction(MachineAction.START_VM, null);
                    }
                }, 1000);
            }
        });
    }

    private void restoreUI(final String machine) {
        int position = SpinnerAdapter.getItemPosition(mMachine, machine);
        mMachine.setSelection(position);
    }

    private void restore() {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (getMachine() == null) {
                    return;   // ← 加这行
                }
                postLoadMachineUI();
            }
        }, 1000);
    }

    private void checkAndLoadLibs() {
        if (Config.loadNativeLibsEarly)
            if (Config.loadNativeLibsMainThread)
                setupNativeLibs();
            else
                setupNativeLibsAsync();
    }

    private void clearNotifications() {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

    private void setupDiskMapping() {
        diskMapping.clear();
        addDiskMapping(FileType.HDA, mHDA, null, MachineProperty.HDA);
        addDiskMapping(FileType.HDB, mHDB, null, MachineProperty.HDB);
        addDiskMapping(FileType.HDC, mHDC, null, MachineProperty.HDC);
        addDiskMapping(FileType.HDD, mHDD, null, MachineProperty.HDD);


        addDiskMapping(FileType.CDROM, mCD, mCDenable, MachineProperty.CDROM);
        addDiskMapping(FileType.FDA, mFDA, mFDAenable, MachineProperty.FDA);
        addDiskMapping(FileType.FDB, mFDB, mFDBenable, MachineProperty.FDB);
        addDiskMapping(FileType.SD, mSD, mSDenable, MachineProperty.SD);

        addDiskMapping(FileType.KERNEL, mKernel, null, MachineProperty.KERNEL);
        addDiskMapping(FileType.INITRD, mInitrd, null, MachineProperty.INITRD);
    }

    private void addDiskMapping(FileType fileType, Spinner spinner,
                                CheckBox enableCheckBox, MachineProperty dbColName) {
        spinner.setTag(fileType);

        diskMapping.put(fileType, new DiskInfo(spinner, enableCheckBox, dbColName));
    }

    private void setupNativeLibsAsync() {

        Thread thread = new Thread(new Runnable() {
            public void run() {
                setupNativeLibs();
            }
        });
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();

    }

    private void createListeners() {
        if (mNewMachineBtn != null) {
            mNewMachineBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    promptNewMachineArch();
                }
            });
        } else {
            android.util.Log.e("ListDebug", "mNewMachineBtn is null!");
        }

        mMachine.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (ignoreNextMachineSelect) {
                    ignoreNextMachineSelect = false;
                    return;
                }

                if (position == 0) {
                    enableNonRemovableDeviceOptions(false);
                    enableRemovableDeviceOptions(false);
                    if (!MachineController.getInstance().isRunning())
                        notifyAction(MachineAction.LOAD_VM, null);
                } else if (position == 1) {
                    mMachine.setSelection(0);
                    promptMachineName(LimboActivity.this);
                } else {
                    final String machine = (String) ((ArrayAdapter<?>) mMachine.getAdapter()).getItem(position);
                    setUserPressed(false);
                    machineLoaded = true;
                    notifyAction(MachineAction.LOAD_VM, machine);
                }
            }

            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        // ==== 底部导航监听 ====

        // 虚拟机
        mNavVm.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTab(0);
            }
        });

        // 线上
        mNavOnline.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTab(1);
            }
        });

        // 设置
        mNavSettings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(LimboActivity.this, LimboSettingsManager.class);
                boolean fromRight = mCurrentTab < 2;
                i.putExtra("from_right", fromRight);
                i.putExtra("enter_tab", mCurrentTab);   // ← 加这行
                startActivity(i);

                if (fromRight) {
                    overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
                } else {
                    overridePendingTransition(R.anim.slide_in_left, R.anim.fade_out);
                }
            }
        });


        // 关于（跳 GitHub）
        // 关于
        mNavAbout.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTab(2);   // ← 改成 showTab(2)，不再直接跳 GitHub
            }
        });

        // 下载 VirtIO-Win
        // VirtIO-Win 驱动
        View downloadVirtioWin = findViewById(R.id.download_virtio_win);
        if (downloadVirtioWin != null) {
            downloadVirtioWin.setOnClickListener(v -> promptDownloadVirtioWin());
        }

        // Linux ISO
        View downloadLinux = findViewById(R.id.download_linux_iso);
        if (downloadLinux != null) {
            downloadLinux.setOnClickListener(v -> {
                AlertDialog dlgGlass7 = new AlertDialog.Builder(LimboActivity.this)
                        .setTitle("下载 Linux 系统iso镜像")
                        .setMessage("选择要下载的发行版：\n\n" +
                                "• Debian 12 (约 600MB)\n" +
                                "• Ubuntu Server (约 2GB)\n" +
                                "• Alpine Linux (约 200MB)")
                        .setPositiveButton("Debian", (d, w) ->
                                openUrl("https://cdimage.debian.org/debian-cd/current/amd64/iso-cd/"))
                        .setNeutralButton("Ubuntu", (d, w) ->
                                openUrl("https://releases.ubuntu.com/"))
                        .setNegativeButton("Alpine", (d, w) ->
                                openUrl("https://alpinelinux.org/downloads/"))
                        .create();
                GlassDialogs.applyOnShow(dlgGlass7);
                dlgGlass7.show();
            });
        }

        // BIOS / UEFI
        View downloadBios = findViewById(R.id.download_bios);
        if (downloadBios != null) {
            downloadBios.setOnClickListener(v -> {
                AlertDialog dlgGlass6 = new AlertDialog.Builder(LimboActivity.this)
                        .setTitle("下载 BIOS / UEFI 固件")
                        .setMessage("选择要获取的固件：\n\n" +
                                "• SeaBIOS：传统 x86 BIOS\n" +
                                "• OVMF：开源 UEFI 固件")
                        .setPositiveButton("SeaBIOS", (d, w) ->
                                openUrl("https://www.seabios.org/Releases"))
                        .setNeutralButton("OVMF", (d, w) ->
                                openUrl("https://github.com/tianocore/edk2/releases"))
                        .setNegativeButton("取消", null)
                        .create();
                GlassDialogs.applyOnShow(dlgGlass6);
                dlgGlass6.show();
            });
        }

        // QEMU 版本
        View downloadQemu = findViewById(R.id.download_qemu);
        if (downloadQemu != null) {
            downloadQemu.setOnClickListener(v -> {
                AlertDialog dlgGlass5 = new AlertDialog.Builder(LimboActivity.this)
                        .setTitle("下载Limbo ANIPLUS")
                        .setMessage("获取不同版本Limbo ANIPLUS。\n" +
                                "注意：替换后可能需要覆盖原APK。")
                        .setPositiveButton("打开 GitHub", (d, w) ->
                                openUrl("https://github.com/ANIPLXUAN/limbo_qemu/releases"))
                        .setNegativeButton("取消", null)
                        .create();
                GlassDialogs.applyOnShow(dlgGlass5);
                dlgGlass5.show();
            });
        }

        // Wiki
        View openWiki = findViewById(R.id.about_wiki);
        if (openWiki != null) {
            openWiki.setOnClickListener(v ->
                    openUrl("https://github.com/ANIPLXUAN/limbo_qemu/wiki"));
        }

        // 社区
        View openCommunity = findViewById(R.id.about_community);
        if (openCommunity != null) {
            openCommunity.setOnClickListener(v ->
                    openUrl("https://qm.qq.com/q/v43fiddffU"));
        }
    }

    private void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch (Exception e) {
            ToastUtils.toastShort(this, "无法打开浏览器");
        }
    }

    private View mAboutContent;
    private TextView mNavAboutText;


    private void showTab(int index) {
        if (mVmContent == null || mOnlineContent == null || mAboutContent == null) {
            android.util.Log.e("NavDebug", "tab views null");
            return;
        }

        // 同一个 tab，不切
        if (index == mCurrentTab) {
            return;
        }

        // 判断目标 tab 在当前位置的左侧还是右侧
        boolean slideFromRight = index > mCurrentTab;

        // 找出当前和目标 View
        View currentView;
        if (mCurrentTab == 0) currentView = mVmContent;
        else if (mCurrentTab == 1) currentView = mOnlineContent;
        else currentView = mAboutContent;

        View targetView;
        if (index == 0) targetView = mVmContent;
        else if (index == 1) targetView = mOnlineContent;
        else targetView = mAboutContent;

        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        // 当前 tab 往反方向滑出
        float exitTo = slideFromRight ? -screenWidth : screenWidth;
        currentView.animate()
                .translationX(exitTo)
                .setDuration(300)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        currentView.setVisibility(View.GONE);
                        currentView.setTranslationX(0);
                    }
                })
                .start();

        // 目标 tab 从对应方向滑入
        targetView.setVisibility(View.VISIBLE);
        targetView.setTranslationX(slideFromRight ? screenWidth : -screenWidth);
        targetView.animate()
                .translationX(0)
                .setDuration(300)
                .start();

        // 导航栏文字颜色
        if (mNavVmText != null)
            mNavVmText.setTextColor(getResources().getColor(R.color.text_secondary));
        if (mNavOnlineText != null)
            mNavOnlineText.setTextColor(getResources().getColor(R.color.text_secondary));
        if (mNavAboutText != null)
            mNavAboutText.setTextColor(getResources().getColor(R.color.text_secondary));

        if (index == 0 && mNavVmText != null)
            mNavVmText.setTextColor(getResources().getColor(R.color.colorPrimary));
        else if (index == 1 && mNavOnlineText != null)
            mNavOnlineText.setTextColor(getResources().getColor(R.color.colorPrimary));
        else if (index == 2 && mNavAboutText != null)
            mNavAboutText.setTextColor(getResources().getColor(R.color.colorPrimary));

        // 记录当前 tab
        mCurrentTab = index;
    }

    private int mCurrentTab = 0;


    private void promptDownloadVirtioWin() {
        AlertDialog dlgGlass4 = new AlertDialog.Builder(LimboActivity.this)
                .setTitle("下载 VirtIO-Win 驱动")
                .setMessage("是否自动下载 VirtIO-Win ISO 驱动？\n\n" +
                        "这是 Windows 虚拟机的 VirtIO 驱动，安装后能提升磁盘和网络性能。\n\n" +
                        "约 500MB，建议在 WiFi 环境下载。")
                .setPositiveButton("下载", (dialog, which) -> startDownloadVirtioWin())
                .setNegativeButton("取消", null)
                .create();
        GlassDialogs.applyOnShow(dlgGlass4);
        dlgGlass4.show();
    }

    private void startDownloadVirtioWin() {
        String url = "https://fedorapeople.org/groups/virt/virtio-win/direct-downloads/stable-virtio/virtio-win.iso";
        try {
            android.app.DownloadManager.Request req =
                    new android.app.DownloadManager.Request(Uri.parse(url));
            req.setTitle("VirtIO-Win 驱动");
            req.setDescription("正在下载 virtio-win.iso");
            req.setNotificationVisibility(
                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS, "virtio-win.iso");

            android.app.DownloadManager dm =
                    (android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(req);
                ToastUtils.toastShort(LimboActivity.this, "已开始下载到 Download 目录");
            }
        } catch (Exception e) {
            ToastUtils.toastShort(LimboActivity.this, "下载失败: " + e.getMessage());
        }
    }

    private void onPauseButton() {
        if (MachineController.getInstance().isRunning()) {
            if (MachineController.getInstance().isVNCEnabled())
                LimboActivityCommon.promptPause(this, viewListener);
            else {
                LimboSDLActivity.pendingPause = true;
                startSDL();
            }
        }

    }

    private void savePendingEditText() {
        View currentView = getCurrentFocus();
        if (currentView instanceof EditText) {
            currentView.setFocusable(false);
        }
    }

    private void checkFirstLaunch() {
        // 新引导流程已经包含许可证、关于、BIOS 安装，这里不再弹窗
        // 如需恢复旧弹窗，删掉下面这个 return 即可
        if (true) return;

        Thread t = new Thread(new Runnable() {
            public void run() {
                if (LimboSettingsManager.isFirstLaunch(LimboActivity.this)) {
                    onFirstLaunch();
                }
            }
        });
        t.start();
    }

    private void checkLog() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                if (LimboSettingsManager.getExitCode(LimboActivity.this) != Config.EXIT_SUCCESS) {
                    if (MachineController.getInstance().isRunning())
                        LimboSettingsManager.setExitCode(LimboActivity.this, Config.EXIT_UNKNOWN);
                    else
                        LimboSettingsManager.setExitCode(LimboActivity.this, Config.EXIT_SUCCESS);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            com.max2idea.android.limbo.log.Logger.promptShowLog(LimboActivity.this);
                        }
                    });
                }
            }
        });
        t.start();
    }

    //XXX: this needs to be called from the plus thread otherwise
    //  qemu crashes when it is started later
    public void setupNativeLibs() {
        if (libLoaded)
            return;
        //Compatibility lib
        System.loadLibrary("compat-limbo");

        //Glib deps
        System.loadLibrary("compat-musl");

        //Glib
        System.loadLibrary("glib-2.0");

        //Pixman for qemu
        System.loadLibrary("pixman-1");

        // SDL library
        if (Config.enable_SDL) {
            if (Build.VERSION.SDK_INT >= 26)
                System.loadLibrary("compat-SDL2-addons");
            System.loadLibrary("SDL2");
        }

        System.loadLibrary("compat-SDL2-ext");

        //Limbo needed for vmexecutor
        System.loadLibrary("limbo");

        // qemu arch specific lib
        loadQEMULib();

        libLoaded = true;
    }

    protected void loadQEMULib() {

    }

    public void setupToolbar() {
        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);

        final ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setHomeAsUpIndicator(R.drawable.limbo);
            ab.setDisplayShowHomeEnabled(true);
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setDisplayShowCustomEnabled(true);
            ab.setDisplayShowTitleEnabled(true);
            ab.setTitle(R.string.app_short_name);
        }
    }

    public void checkUpdate() {
        Thread tsdl = new Thread(new Runnable() {
            public void run() {
                UpdateChecker.checkNewVersion(LimboActivity.this);
            }
        });
        tsdl.start();
    }

    private void setupStrictMode() {
        if (Config.debugStrictMode) {
            StrictMode.setThreadPolicy(
                    new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork()
                            .penaltyLog().build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects().penaltyLog()
                    .build());
        }
    }

    private void populateAttributesUI() {
        populateMachines(null);
        populateMachineType(null);
        populateCPUs(null);
        populateCPUNum();
        populateRAM();
        populateDisks();
        populateBootDevices();
        populateBios();
        populateNet();
        populateNetDevices(null);
        populateVGA();
        populateVideoRAM();
        populateSoundcardConfig();
        populateUI();
        populateKeyboardLayout();
        populateMouse();
        populateUSB();
    }

    private void populateDisks() {

        //disks
        populateDiskAdapter(mHDA, FileType.HDA, true);
        populateDiskAdapter(mHDB, FileType.HDB, true);
        populateDiskAdapter(mHDC, FileType.HDC, true);
        populateDiskAdapter(mHDD, FileType.HDD, true);


        //removables drives
        populateDiskAdapter(mCD, FileType.CDROM, false);
        populateDiskAdapter(mFDA, FileType.FDA, false);
        populateDiskAdapter(mFDB, FileType.FDB, false);
        populateDiskAdapter(mSD, FileType.SD, false);

        //boot
        populateDiskAdapter(mKernel, FileType.KERNEL, false);
        populateDiskAdapter(mInitrd, FileType.INITRD, false);

    }

    public void onFirstLaunch() {
        promptLicense();
    }

    private void createMachine(String machineName) {
        notifyAction(MachineAction.CREATE_VM, machineName);
    }

    private void machineCreated() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (getMachine() == null) return;

                if (mPendingCreateArch != null) {
                    getMachine().setArch(mPendingCreateArch);
                    mPendingCreateArch = null;
                }

                sPendingOpenMachine = getMachine().getName();
                recreate();
            }
        });
    }

    protected void showOperatingSystems() {
        if (!Config.osImages.isEmpty()) {
            LinksManager manager = new LinksManager(this);
            GlassDialogs.applyOnShow(manager);
            manager.show();
        }
    }

    public void showDownloads() {
        if (!Config.downloadLinks.isEmpty()) {
            LinksManager2 manager = new LinksManager2(this);
            GlassDialogs.applyOnShow(manager);
            manager.show();
        }
    }
    private void onDeleteMachine() {
        if (getMachine() == null) {
            ToastUtils.toastShort(this, getString(R.string.SelectAMachineFirst));
            return;
        }
        Thread t = new Thread(new Runnable() {
            public void run() {
                final String name = getMachine().getName();
                notifyAction(MachineAction.DELETE_VM, getMachine());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        disableListeners();
                        disableRemovableDiskListeners();
                        mMachine.setSelection(0);
                        notifyAction(MachineAction.LOAD_VM, null);
                        populateAttributesUI();
                        ToastUtils.toastShort(LimboActivity.this, getString(R.string.MachineDeleted) + ": " + name);
                        setupMiscOptions();
                        setupNonRemovableDiskListeners();
                        enableRemovableDiskListeners();
                    }
                });
            }
        });
        t.start();

    }

    public void importMachines(String importFilePath) {
        disableListeners();
        disableRemovableDiskListeners();
        mMachine.setSelection(0);
        notifyAction(MachineAction.IMPORT_VMS, importFilePath);
    }


    private void promptLicense() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    LimboActivityCommon.promptLicense(LimboActivity.this,
                            Config.APP_NAME + " " + LimboApplication.getLimboVersionString()
                            + " " + "QEMU" + " " + LimboApplication.getQemuVersionString() ,
                            FileUtils.LoadFile(LimboActivity.this, "LICENSE", false));
                } catch (IOException e) {

                    e.printStackTrace();
                }
            }
        });

    }

    public void exit() {
        if (MachineController.getInstance().isRunning())
            onStopButton(true);
        else
            System.exit(0);
    }

    private void unlockRemovableDevices(boolean flag) {
        mCDenable.setEnabled(flag);
        mFDAenable.setEnabled(flag);
        mFDBenable.setEnabled(flag);
        mSDenable.setEnabled(flag);
    }

    private void enableRemovableDeviceOptions(boolean flag) {
        unlockRemovableDevices(flag);
        enableRemovableDiskValues(flag);
    }

    private void enableRemovableDiskValues(boolean flag) {
        mCD.setEnabled(flag && mCDenable.isChecked());
        mFDA.setEnabled(flag && mFDAenable.isChecked());
        mFDB.setEnabled(flag && mFDBenable.isChecked());
        mSD.setEnabled(flag && mSDenable.isChecked());
    }

    private void enableNonRemovableDeviceOptions(boolean flag) {
        if (MachineController.getInstance().isRunning())
            flag = false;

        //ui
        mUI.setEnabled(flag);
        mKeyboard.setEnabled(Config.enableKeyboardLayoutOption && flag);
        mMouse.setEnabled(Config.enableMouseOption && flag);

        // Enable everything except removable devices
        mMachineType.setEnabled(flag);
        mCPU.setEnabled(flag);
        mCPUNum.setEnabled(flag);
        mRamSize.setEnabled(flag);
        mEnableKVM.setEnabled(flag && Config.enableKVM);
        mEnableMTTCG.setEnabled(flag && Config.enableMTTCG);

        //drives
        mHDA.setEnabled(flag);
        mHDAOptions.setEnabled(flag);
        mHDB.setEnabled(flag);
        mHDBOptions.setEnabled(flag);
        mHDC.setEnabled(flag);
        mHDCOptions.setEnabled(flag);
        mHDD.setEnabled(flag);
        mHDDOptions.setEnabled(flag);


        //boot
        mBootDevices.setEnabled(flag);
        mBios.setEnabled(flag);
        mKernel.setEnabled(flag);
        mInitrd.setEnabled(flag);
        mAppend.setEnabled(flag);

        //graphics
        mVGAConfig.setEnabled(flag);
        mVideoRamSize.setEnabled(flag && mVGAConfig.getSelectedItemPosition() < 3);

        //audio
        if (Config.enableSDLSound && getMachine() != null
                && getMachine().getEnableVNC() != 1
                && getMachine().getPaused() == 0)
            mSoundCard.setEnabled(flag);
        else
            mSoundCard.setEnabled(false);

        //net
        mNetConfig.setEnabled(flag);
        mNetworkCard.setEnabled(flag && mNetConfig.getSelectedItemPosition() > 0);
        mDNS.setEnabled(flag && mNetConfig.getSelectedItemPosition() > 0);
        mHOSTFWD.setEnabled(flag && mNetConfig.getSelectedItemPosition() > 0);

        //advanced
        mUSBConfig.setEnabled(flag);
        mDisableACPI.setEnabled(flag);
        mDisableHPET.setEnabled(flag);
        mDisableTSC.setEnabled(flag);
        mExtraParams.setEnabled(flag);


    }

    // Main event function
    // Retrives values from saved preferences
    private void onStartButton() {

        if (mMachine.getSelectedItemPosition() == 0 || getMachine() == null) {
            ToastUtils.toastShort(LimboActivity.this, getString(R.string.SelectOrCreateVirtualMachineFirst));
            return;
        }
        // focus out of edit texts to make sure they are applied to the db
        mStart.requestFocus();

        if (!validateFiles()) {
            return;
        }

        try {
            createMachineDir(MachineController.getInstance().getMachineSaveDir());
        } catch (Exception ex) {
            ToastUtils.toastLong(LimboActivity.this, getString(R.string.Error) + ": " + ex);
            return;
        }

        // XXX: save the user defined dns server before we start the vm
        LimboSettingsManager.setDNSServer(this, mDNS.getText().toString());

        //XXX: make sure that bios files are installed in case we ran out of space in the last run
        FileInstaller.installFiles(LimboActivity.this, false);

        if (getMachine().getEnableVNC() == 1) {
            startVNC();
        } else {
            startSDL();
        }
    }

    private void createMachineDir(String dir) throws Exception {
        File destDir = new File(dir);
        if (!destDir.exists()) {
            if (!destDir.mkdirs())
                throw new Exception(getString(R.string.failToCreateMachineDirError));
        }
    }

    /**
     * Starts the SDL Activity that will later start the native process via the service.
     * This is done so that the java SDL part is initialized prior to starting the vm.
     */
    public void startSDL() {
        Intent intent = new Intent(LimboActivity.this, LimboSDLActivity.class);
        startActivityForResult(intent, Config.SDL_REQUEST_CODE);
    }

    /**
     * Start the vm with VNC Suport via the Controller which will later call the native process
     * via the service. We do this since we don't have a built-in VNC client anymore.
     */
    public void startVNC() {
        if (LimboSettingsManager.getEnableExternalVNC(this)) {
            // VNC external connections
            LimboActivityCommon.promptVNCServer(this,
                    getString(R.string.ExternalVNCEnabledWarning), viewListener);
        } else if (!LimboSettingsManager.getVNCEnablePassword(this)) {
            // VNC Password is not enabled
            LimboActivityCommon.promptVNCServer(this,
                    getString(R.string.VNCPasswordNotEnabledWarning), viewListener);
        } else if (LimboSettingsManager.getVNCEnablePassword(this)
                && LimboSettingsManager.getVNCPass(this) == null) {
            // VNC Password is missing
            ToastUtils.toastShort(this, getString(R.string.VNCPasswordMissing));
        } else {
            notifyAction(MachineAction.START_VM, null);
        }
    }

    private boolean validateFiles() {
        return FileUtils.fileValid(getMachine().getHdaImagePath())
                && FileUtils.fileValid(getMachine().getHdbImagePath())
                && FileUtils.fileValid(getMachine().getHdcImagePath())
                && FileUtils.fileValid(getMachine().getHddImagePath())
                && FileUtils.fileValid(getMachine().getFdaImagePath())
                && FileUtils.fileValid(getMachine().getFdbImagePath())
                && FileUtils.fileValid(getMachine().getSdImagePath())
                && FileUtils.fileValid(getMachine().getCdImagePath())
                && FileUtils.fileValid(getMachine().getKernel())
                && FileUtils.fileValid(getMachine().getInitRd());
    }

    private void onStopButton(boolean exitApp) {
        KeyboardUtils.hideKeyboard(this, mScrollView);
        if (MachineController.getInstance().isRunning()) {
            if (MachineController.getInstance().isVNCEnabled())
                LimboActivityCommon.promptStopVM(this, viewListener);
            else {
                LimboSDLActivity.pendingStop = true;
                startSDL();
            }
        } else {
            if (getMachine() != null
                    && MachineController.getInstance().isPaused() && !exitApp) {
                promptDiscardVMState();
            } else {
                ToastUtils.toastShort(LimboActivity.this, getString(R.string.vmNotRunning));
            }
        }
    }

    private void onRestartButton() {
        if (!MachineController.getInstance().isRunning()) {
            if (getMachine() != null && getMachine().getPaused() == 1) {
                promptDiscardVMState();
            } else {
                ToastUtils.toastShort(LimboActivity.this, getString(R.string.VMNotRunning));
            }
        }
        LimboActivityCommon.promptResetVM(this, viewListener);
    }

    public void toggleSectionVisibility(View view) {
        if (view.getVisibility() == View.VISIBLE) {
            view.setVisibility(View.GONE);
        } else if (view.getVisibility() == View.GONE || view.getVisibility() == View.INVISIBLE) {
            view.setVisibility(View.VISIBLE);
        }
    }

    public void setupWidgets() {
        setupSections();
        mMachineListView = findViewById(R.id.machine_list_view);
        mMachineConfigView = findViewById(R.id.machine_config_view);
        mMachineList = findViewById(R.id.machine_list);
        mMachineEmpty = findViewById(R.id.machine_empty);
        mNewMachineBtn = findViewById(R.id.new_machine_btn);
        mScrollView = findViewById(R.id.scroll_view);
        mStatus = findViewById(R.id.statusVal);
        mStatus.setImageResource(R.drawable.off);
        mStatusText = findViewById(R.id.statusStr);

        mStart = findViewById(R.id.startvm);
        mPause = findViewById(R.id.pausevm);
        mStop = findViewById(R.id.stopvm);
        mRestart = findViewById(R.id.restartvm);

        //Machine
        mMachine = findViewById(R.id.machineval);
        if (MachineController.getInstance().isRunning())
            mMachine.setEnabled(false);

        //ui
        if (!Config.enable_SDL)
            mUI.setEnabled(false);

        mKeyboard = findViewById(R.id.keyboardval);
        mMouse = findViewById(R.id.mouseval);

        //cpu/board
        mCPU = findViewById(R.id.cpuval);
        mMachineType = findViewById(R.id.machinetypeval);
        mCPUNum = findViewById(R.id.cpunumval);
        mUI = findViewById(R.id.uival);
        mRamSize = findViewById(R.id.rammemval);
        mEnableKVM = findViewById(R.id.enablekvmval);
        mEnableMTTCG = findViewById(R.id.enablemttcgval);
        mDisableACPI = findViewById(R.id.acpival);
        mDisableHPET = findViewById(R.id.hpetval);
        mDisableTSC = findViewById(R.id.tscval);
        //disks
        mHDA = findViewById(R.id.hdaimgval);
        mHDAOptions = findViewById(R.id.hdaoptions);
        mHDB = findViewById(R.id.hdbimgval);
        mHDBOptions = findViewById(R.id.hdboptions);
        mHDC = findViewById(R.id.hdcimgval);
        mHDCOptions = findViewById(R.id.hdcoptions);
        mHDD = findViewById(R.id.hddimgval);
        mHDDOptions = findViewById(R.id.hddoptions);
        //Removable storage
        mCD = findViewById(R.id.cdromimgval);
        mFDA = findViewById(R.id.floppyimgval);
        mFDB = findViewById(R.id.floppybimgval);
        mCDOptions = findViewById(R.id.cdromoptions);
        if (!Config.enableEmulatedFloppy) {
            LinearLayout mFDALayout = findViewById(R.id.floppyimgl);
            mFDALayout.setVisibility(View.GONE);
            LinearLayout mFDBLayout = findViewById(R.id.floppybimgl);
            mFDBLayout.setVisibility(View.GONE);
        }
        mSD = findViewById(R.id.sdcardimgval);
        if (!Config.enableEmulatedSDCard) {
            LinearLayout mSDCardLayout = findViewById(R.id.sdcardimgl);
            mSDCardLayout.setVisibility(View.GONE);
        }
        mCDenable = findViewById(R.id.cdromimgcheck);
        mFDAenable = findViewById(R.id.floppyimgcheck);
        mFDBenable = findViewById(R.id.floppybimgcheck);
        mSDenable = findViewById(R.id.sdcardimgcheck);

        //boot
        mBootDevices = findViewById(R.id.bootfromval);
        mBios = findViewById(R.id.biosval);
        mKernel = findViewById(R.id.kernelval);
        mInitrd = findViewById(R.id.initrdval);
        mAppend = findViewById(R.id.appendval);

        //display
        mVGAConfig = findViewById(R.id.vgacfgval);
        mVideoRamSize = findViewById(R.id.videorammemval);

        //sound
        mSoundCard = findViewById(R.id.soundcfgval);

        //network
        mNetConfig = findViewById(R.id.netcfgval);
        mNetworkCard = findViewById(R.id.netDevicesVal);
        mDNS = findViewById(R.id.dnsval);
        setDefaultDNServer();
        mHOSTFWD = findViewById(R.id.hostfwdval);

        // advanced
        mUSBConfig = findViewById(R.id.usbval);
        mExtraParams = findViewById(R.id.extraparamsval);

        disableFeatures();
        enableRemovableDeviceOptions(false);
        enableNonRemovableDeviceOptions(false);

        mVmContent = findViewById(R.id.vm_content);
        mOnlineContent = findViewById(R.id.online_content);
        mNavVm = findViewById(R.id.nav_vm);
        mNavOnline = findViewById(R.id.nav_online);
        mNavSettings = findViewById(R.id.nav_settings);
        mNavAbout = findViewById(R.id.nav_about);
        mNavVmText = findViewById(R.id.nav_vm_text);
        mNavOnlineText = findViewById(R.id.nav_online_text);
        mAboutContent = findViewById(R.id.about_content);
        mNavAbout = findViewById(R.id.nav_about);
        mNavAboutText = findViewById(R.id.nav_about_text);

        // 显示版本号
        TextView aboutVersion = findViewById(R.id.about_version);
        if (aboutVersion != null && LimboApplication.getQemuVersionString() != null) {
            aboutVersion.setText("Limbo " + LimboApplication.getLimboVersionString()
                    + " / QEMU " + LimboApplication.getQemuVersionString());
        }

        // GitHub 卡片点击
        View githubCard = findViewById(R.id.about_github);
        if (githubCard != null) {
            githubCard.setOnClickListener(v -> {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/ANIPLXUAN/limbo_qemu"));
                    startActivity(i);
                } catch (Exception e) {
                    ToastUtils.toastShort(LimboActivity.this, "无法打开浏览器");
                }
            });
        }

        // 给所有 Spinner 统一设置圆角弹出背景
        applyRoundedPopupToAllSpinners(findViewById(android.R.id.content));
    }

    private void applyRoundedPopupToAllSpinners(View view) {
        if (view instanceof Spinner) {
            try {
                Spinner spinner = (Spinner) view;
                spinner.setBackground(null);   // 去掉默认背景
                spinner.setPopupBackgroundResource(
                        R.drawable.rounded_spinner_popup);
            } catch (Exception ignored) {
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyRoundedPopupToAllSpinners(group.getChildAt(i));
            }
        }
    }

    private void disableFeatures() {

        LinearLayout mAudioSectionLayout = findViewById(R.id.audiosectionl);
        if (!Config.enableSDLSound) {
            mAudioSectionLayout.setVisibility(View.GONE);
        }

        LinearLayout mDisableTSCLayout = findViewById(R.id.tscl);
        LinearLayout mDisableACPILayout = findViewById(R.id.acpil);
        LinearLayout mDisableHPETLayout = findViewById(R.id.hpetl);
        LinearLayout mEnableKVMLayout = findViewById(R.id.kvml);

        if (LimboApplication.arch != Config.Arch.x86 && LimboApplication.arch != Config.Arch.x86_64) {
            mDisableTSCLayout.setVisibility(View.GONE);
            mDisableACPILayout.setVisibility(View.GONE);
            mDisableHPETLayout.setVisibility(View.GONE);
        }
        if (LimboApplication.arch != Config.Arch.x86 && LimboApplication.arch != Config.Arch.x86_64
                && LimboApplication.arch != Config.Arch.arm && LimboApplication.arch != Config.Arch.arm64) {
            mEnableKVMLayout.setVisibility(View.GONE);
        }
    }

    private void setDefaultDNServer() {

        Thread thread = new Thread(new Runnable() {
            public void run() {
                final String defaultDNSServer = LimboSettingsManager.getDNSServer(LimboActivity.this);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        // Code here will run in UI thread
                        mDNS.setText(defaultDNSServer);
                    }
                });
            }
        });
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();

    }

    private void setupSections() {

        if (Config.collapseSections) {
            mCPUSectionDetails = findViewById(R.id.cpusectionDetails);
            mCPUSectionDetails.setVisibility(View.GONE);
            mCPUSectionSummary = findViewById(R.id.cpusectionsummaryStr);
            LinearLayout mCPUSectionHeader = findViewById(R.id.cpusectionheaderl);
            mCPUSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mCPUSectionDetails);
                    enableListenersDelayed();
                }
            });

            mStorageSectionDetails = findViewById(R.id.storagesectionDetails);
            mStorageSectionDetails.setVisibility(View.GONE);
            mStorageSectionSummary = findViewById(R.id.storagesectionsummaryStr);
            LinearLayout mStorageSectionHeader = findViewById(R.id.storageheaderl);
            mStorageSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mStorageSectionDetails);
                    enableListenersDelayed();
                }
            });

            mUserInterfaceSectionDetails = findViewById(R.id.userInterfaceDetails);
            mUserInterfaceSectionDetails.setVisibility(View.GONE);
            mUISectionSummary = findViewById(R.id.uisectionsummaryStr);
            LinearLayout mUserInterfaceSectionHeader = findViewById(R.id.userinterfaceheaderl);
            mUserInterfaceSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mUserInterfaceSectionDetails);
                    enableListenersDelayed();
                }
            });


            mRemovableStorageSectionDetails = findViewById(R.id.removableStoragesectionDetails);
            mRemovableStorageSectionDetails.setVisibility(View.GONE);
            mRemovableStorageSectionSummary = findViewById(R.id.removablesectionsummaryStr);
            LinearLayout mRemovableStorageSectionHeader = findViewById(R.id.removablestorageheaderl);
            mRemovableStorageSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mRemovableStorageSectionDetails);
                    enableListenersDelayed();
                }
            });

            mGraphicsSectionDetails = findViewById(R.id.graphicssectionDetails);
            mGraphicsSectionDetails.setVisibility(View.GONE);
            mGraphicsSectionSummary = findViewById(R.id.graphicssectionsummaryStr);
            LinearLayout mGraphicsSectionHeader = findViewById(R.id.graphicsheaderl);
            mGraphicsSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mGraphicsSectionDetails);
                    enableListenersDelayed();
                }
            });
            mAudioSectionDetails = findViewById(R.id.audiosectionDetails);
            mAudioSectionDetails.setVisibility(View.GONE);
            mAudioSectionSummary = findViewById(R.id.audiosectionsummaryStr);
            LinearLayout mAudioSectionHeader = findViewById(R.id.audioheaderl);
            mAudioSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mAudioSectionDetails);
                    enableListenersDelayed();
                }
            });

            mNetworkSectionDetails = findViewById(R.id.networksectionDetails);
            mNetworkSectionDetails.setVisibility(View.GONE);
            mNetworkSectionSummary = findViewById(R.id.networksectionsummaryStr);
            View mNetworkSectionHeader = findViewById(R.id.networkheaderl);
            mNetworkSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mNetworkSectionDetails);
                    enableListenersDelayed();
                }
            });

            mBootSectionDetails = findViewById(R.id.bootsectionDetails);
            mBootSectionDetails.setVisibility(View.GONE);
            mBootSectionSummary = findViewById(R.id.bootsectionsummaryStr);
            View mBootSectionHeader = findViewById(R.id.bootheaderl);
            mBootSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mBootSectionDetails);
                    enableListenersDelayed();
                }
            });

            mAdvancedSectionDetails = findViewById(R.id.advancedSectionDetails);
            mAdvancedSectionDetails.setVisibility(View.GONE);
            mAdvancedSectionSummary = findViewById(R.id.advancedsectionsummaryStr);
            LinearLayout mAdvancedSectionHeader = findViewById(R.id.advancedheaderl);
            mAdvancedSectionHeader.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    disableListeners();
                    disableRemovableDiskListeners();
                    toggleSectionVisibility(mAdvancedSectionDetails);
                    enableListenersDelayed();
                }
            });
        }
    }

    private void enableListenersDelayed() {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                setupMiscOptions();
                setupNonRemovableDiskListeners();
                enableRemovableDiskListeners();
            }
        }, 500);
    }

    public void updateUISummary(boolean clear) {
        if (clear || getMachine() == null || mMachine.getSelectedItemPosition() < 2)
            mUISectionSummary.setText("");
        else {
            String text = getString(R.string.display) + ": " + (getMachine().getEnableVNC() == 1 ? "VNC" : "SDL");
            if (getMachine().getEnableVNC() == 1) {
                text += ", " + getString(R.string.server);
                text += ": " + NetworkUtils.getVNCAddress(this) + ":" + Config.defaultVNCPort;
            }
            if (getMachine().getKeyboard() != null) {
                text += ", " + getString(R.string.keyboard) + ": " + getMachine().getKeyboard();
            }
            if (getMachine().getMouse() != null) {
                text += ", " + getString(R.string.mouse) + ": " + getMachine().getMouse();
            }
            mUISectionSummary.setText(text);
        }
    }

    private Machine getMachine() {
        return MachineController.getInstance().getMachine();
    }

    public void updateCPUSummary(boolean clear) {
        if (clear || getMachine() == null || mMachine.getSelectedItemPosition() < 2)
            mCPUSectionSummary.setText("");
        else {
            String text = getString(R.string.MachineType) + getMachine().getMachineType()
                    + ", CPU: " + getMachine().getCpu()
                    + ", " + getMachine().getCpuNum() + " CPU" + ((getMachine().getCpuNum() > 1) ? "s" : "")
                    + ", " + getMachine().getMemory() + " MB";
            if (mEnableMTTCG.isChecked())
                text = appendOption(getString(R.string.enableMTTCG), text);
            if (mEnableKVM.isChecked())
                text = appendOption(getString(R.string.EnableKVM), text);
            if (mDisableACPI.isChecked())
                text = appendOption(getString(R.string.disableACPI), text);
            if (mDisableHPET.isChecked())
                text = appendOption(getString(R.string.disableHPET), text);
            if (mDisableTSC.isChecked())
                text = appendOption(getString(R.string.disableTSC), text);
            mCPUSectionSummary.setText(text);
        }
    }

    public void updateStorageSummary(boolean clear) {
        if (clear || getMachine() == null || mMachine.getSelectedItemPosition() < 2)
            mStorageSectionSummary.setText("");
        else {
            String text = null;
            text = appendDriveFilename(getMachine().getHdaImagePath(), text, "HDA", false);
            text = appendDriveFilename(getMachine().getHdbImagePath(), text, "HDB", false);
            text = appendDriveFilename(getMachine().getHdcImagePath(), text, "HDC", false);
            text = appendDriveFilename(getMachine().getHddImagePath(), text, "HDD", false);
            text = appendDriveFilename(getMachine().getSharedFolderPath(), text, getString(R.string.SharedFolder), false);


            if (text == null || text.equals("'"))
                text = getString(R.string.none);
            mStorageSectionSummary.setText(text);
        }
    }

    public void updateRemovableStorageSummary(boolean clear) {
        if (clear || getMachine() == null || mMachine.getSelectedItemPosition() < 2)
            mRemovableStorageSectionSummary.setText("");
        else {
            String text = null;

            text = appendDriveFilename(getMachine().getCdImagePath(), text, "CDROM", true);
            text = appendDriveFilename(getMachine().getFdaImagePath(), text, "FDA", true);
            text = appendDriveFilename(getMachine().getFdbImagePath(), text, "FDB", true);
            text = appendDriveFilename(getMachine().getSdImagePath(), text, "SD", true);


            if (text == null || text.equals(""))
                text = getString(R.string.none);

            mRemovableStorageSectionSummary.setText(text);
        }
    }

    public void updateBootSummary(boolean clear) {
        if (clear || getMachine() == null || mMachine.getSelectedItemPosition() < 2)
            mBootSectionSummary.setText("");
        else {
            String text = getString(R.string.boot_From) + getMachine().getBootDevice();
            text = appendDriveFilename(getMachine().getBios(), text, "bios", false);
            text = appendDriveFilename(getMachine().getKernel(), text, "kernel", false);
            text = appendDriveFilename(getMachine().getInitRd(), text, "initrd", false);
            text = appendDriveFilename(getMachine().getAppend(), text, "append", false);
            mBootSectionSummary.setText(text);
        }
    }

    private String appendDriveFilename(String driveFile, String text, String drive, boolean allowEmptyDrive) {

        String file = null;
        if (driveFile != null) {
            if ((driveFile.equals("") || driveFile.equals("None") && driveFile.equals("无")) && allowEmptyDrive) {
                file = drive + ": " + getString(R.string.empty);
            } else if (!driveFile.equals("") && !driveFile.equals("None") && !driveFile.equals("无"))
                file = drive + ": " + FileUtils.getFilenameFromPath(driveFile);
        }
        if (text == null && file != null)
            text = file;
        else if (file != null)
            text += (", " + file);
        return text;
    }

    public void updateGraphicsSummary(boolean clear) {
        if (clear || getMachine() == null || mMachine.getSelectedItemPosition() < 2)
            mGraphicsSectionSummary.setText("");
        else {
            String text = getString(R.string.video_card) + getMachine().getVga() + ", "
                    + getMachine().getVideoMemory() + " MB";
            mGraphicsSectionSummary.setText(text);
        }
    }

    public void updateAudioSummary(boolean clear) {
        if (clear || getMachine() == null
                || mMachine.getSelectedItemPosition() < 2)
            mAudioSectionSummary.setText("");
        else {
            String soundCard = getMachine().getSoundCard();
            String text = getString(R.string.AudioCard) + ": " + (soundCard != null ? soundCard : getString(R.string.none));
            mAudioSectionSummary.setText(text);
        }
    }

    public void updateNetworkSummary(boolean clear) {
        if (clear || getMachine() == null
                || mMachine.getSelectedItemPosition() < 2)
            mNetworkSectionSummary.setText("");
        else {
            String netCfg = getMachine().getNetwork();
            String text = getString(R.string.Network) + ": " + (netCfg != null ? netCfg : getString(R.string.none));
            if (netCfg != null && !netCfg.equals("None") && netCfg != null && !netCfg.equals("无")) {
                String nicCard = getMachine().getNetworkCard();
                text += ", " + getString(R.string.NicCard) + ": " + (nicCard != null ? nicCard : getString(R.string.none));
                text += ", " + getString(R.string.DNSServer) + ": " + mDNS.getText();
                String hostFWD = getMachine().getHostFwd();
                if (hostFWD != null && !hostFWD.equals(""))
                    text += ", " + getString(R.string.HostForward) + ": " + hostFWD;
            }
            mNetworkSectionSummary.setText(text);
        }
    }

    public void updateAdvancedSummary(boolean clear) {
        if (clear || getMachine() == null || mMachine.getSelectedItemPosition() < 2)
            mAdvancedSectionSummary.setText("");
        else {
            String
                text = getString(R.string.usbctrl) + getMachine().getUsb();
            if (getMachine().getExtraParams() != null
                    && !getMachine().getExtraParams().equals(""))
                text = appendOption( getString(R.string.ExtraParams) + ": " + getMachine().getExtraParams(), text);
            mAdvancedSectionSummary.setText(text);
        }
    }

    private String appendOption(String option, String text) {

        if (text == null && option != null)
            text = option;
        else if (option != null)
            text += (", " + option);
        return text;
    }

    private void triggerUpdateSpinner(final Spinner spinner) {

        final int position = (int) spinner.getSelectedItemId();
        spinner.setSelection(0);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                spinner.setSelection(position);
            }
        }, 100);
    }

    private void loadMachine() {

        setUserPressed(false);
        if (getMachine() == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                loadMachineUI();
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        postLoadMachineUI();
                    }
                }, 1000);
                setCPUOptions();
                getMachine().addObserver(LimboActivity.this);
            }
        });
    }

    private void postLoadMachineUI() {
        if (getMachine() == null) {
            return;
        }
        mFDAenable.setChecked(getMachine().getFdaImagePath() != null);
        mFDBenable.setChecked(getMachine().getFdbImagePath() != null);
        mCDenable.setChecked(getMachine().getCdImagePath() != null);
        mSDenable.setChecked(getMachine().getSdImagePath() != null);

        changeStatus(MachineController.getInstance().getCurrStatus());
        if (getMachine().getPaused() == 1) {
            enableNonRemovableDeviceOptions(false);
            enableRemovableDeviceOptions(false);
        } else {
            enableNonRemovableDeviceOptions(true);
            enableRemovableDeviceOptions(true);
        }
        setUserPressed(true);
        machineLoaded = false;
        mMachine.setEnabled(!MachineController.getInstance().isRunning());
    }

    private void loadMachineUI() {
        populateMachineType(getMachine().getMachineType());
        populateCPUs(getMachine().getCpu());
        populateNetDevices(getMachine().getNetworkCard());
        SpinnerAdapter.setDiskAdapterValue(mCPUNum, getMachine().getCpuNum() + "");
        SpinnerAdapter.setDiskAdapterValue(mRamSize, getMachine().getMemory() + "");
        SpinnerAdapter.setDiskAdapterValue(mVideoRamSize, getMachine().getVideoMemory() + "");
        seMachineDriveValue(FileType.KERNEL, getMachine().getKernel());
        seMachineDriveValue(FileType.INITRD, getMachine().getInitRd());
        if (getMachine().getAppend() != null)
            mAppend.setText(getMachine().getAppend());
        else
            mAppend.setText("");

        if (getMachine().getHostFwd() != null)
            mHOSTFWD.setText(getMachine().getHostFwd());
        else
            mHOSTFWD.setText("");

        if (getMachine().getExtraParams() != null)
            mExtraParams.setText(getMachine().getExtraParams());
        else
            mExtraParams.setText("");

        // CDROM
        seMachineDriveValue(FileType.CDROM, getMachine().getCdImagePath());

        // Floppy
        seMachineDriveValue(FileType.FDA, getMachine().getFdaImagePath());
        seMachineDriveValue(FileType.FDB, getMachine().getFdbImagePath());

        // SD Card
        seMachineDriveValue(FileType.SD, getMachine().getSdImagePath());

        // HDD
        seMachineDriveValue(FileType.HDA, getMachine().getHdaImagePath());
        seMachineDriveValue(FileType.HDB, getMachine().getHdbImagePath());
        seMachineDriveValue(FileType.HDC, getMachine().getHdcImagePath());
        seMachineDriveValue(FileType.HDD, getMachine().getHddImagePath());

        //sharedfolder
        seMachineDriveValue(FileType.SHARED_DIR, getMachine().getSharedFolderPath());

        // Advance
        SpinnerAdapter.setDiskAdapterValue(mBootDevices, getMachine().getBootDevice());
        SpinnerAdapter.setDiskAdapterValue(mBios, getMachine().getBios());
        SpinnerAdapter.setDiskAdapterValue(mNetConfig, getMachine().getNetwork());
        SpinnerAdapter.setDiskAdapterValue(mVGAConfig, getMachine().getVga());
        SpinnerAdapter.setDiskAdapterValue(mUSBConfig, getMachine().getUsb());
        SpinnerAdapter.setDiskAdapterValue(mSoundCard, getMachine().getSoundCard());
        SpinnerAdapter.setDiskAdapterValue(mUI, getMachine().getEnableVNC() == 1 ? "VNC" : "SDL");
        SpinnerAdapter.setDiskAdapterValue(mMouse, fixMouseValue(getMachine().getMouse()));
        SpinnerAdapter.setDiskAdapterValue(mKeyboard, getMachine().getKeyboard());

        // motherboard settings
        mDisableACPI.setChecked(getMachine().getDisableAcpi() == 1);
        mDisableHPET.setChecked(getMachine().getDisableHPET() == 1);
        if (LimboApplication.arch == Config.Arch.x86 || LimboApplication.arch == Config.Arch.x86_64)
            mDisableTSC.setChecked(getMachine().getDisableTSC() == 1);
        mEnableKVM.setChecked(getMachine().getEnableKVM() == 1);
        mEnableMTTCG.setChecked(getMachine().getEnableMTTCG() == 1);

        enableNonRemovableDeviceOptions(true);
        enableRemovableDeviceOptions(!MachineController.getInstance().isRunning());

        if (Config.enableSDLSound) {
            mSoundCard.setEnabled(getMachine().getEnableVNC() != 1 && getMachine().getPaused() == 0);
        } else
            mSoundCard.setEnabled(false);

        mMachine.setEnabled(false);
    }

    private String fixMouseValue(String mouse) {
        if (mouse != null) {
            if (mouse.startsWith("usb-tablet"))
                mouse += " " + getString(R.string.fixesMouseParen);
        }
        return mouse;
    }

    private synchronized void updateSummary() {
        updateUISummary(false);
        updateCPUSummary(false);
        updateStorageSummary(false);
        updateRemovableStorageSummary(false);
        updateGraphicsSummary(false);
        updateAudioSummary(false);
        updateNetworkSummary(false);
        updateBootSummary(false);
        updateAdvancedSummary(false);
    }

    public void promptMachineName(final Activity activity) {
        final AlertDialog alertDialog;
        alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setTitle(getString(R.string.NewMachineName));
        final EditText vmNameTextView = new EditText(activity);
        vmNameTextView.setPadding(20, 20, 20, 20);
        vmNameTextView.setEnabled(true);
        vmNameTextView.setVisibility(View.VISIBLE);
        vmNameTextView.setSingleLine();
        alertDialog.setView(vmNameTextView);
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.Create), (DialogInterface.OnClickListener) null);

        GlassDialogs.applyOnShow(alertDialog);

        alertDialog.show();

        Button button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (vmNameTextView.getText().toString().trim().equals(""))
                    ToastUtils.toastShort(activity, getString(R.string.MachineNameCannotBeEmpty));
                else {
                    createMachine(vmNameTextView.getText().toString());
                    alertDialog.dismiss();
                }
            }
        });
        alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(vmNameTextView.getWindowToken(), 0);
            }
        });
    }

    public void promptImageName(final Activity activity, final FileType fileType) {

        final AlertDialog alertDialog;
        alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setTitle(getString(R.string.ImageName));

        LinearLayout mLayout = new LinearLayout(this);
        mLayout.setPadding(20, 20, 20, 20);
        mLayout.setOrientation(LinearLayout.VERTICAL);

        final EditText imageNameView = new EditText(activity);
        imageNameView.setEnabled(true);
        imageNameView.setVisibility(View.VISIBLE);
        imageNameView.setSingleLine();
        LinearLayout.LayoutParams imageNameViewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mLayout.addView(imageNameView, imageNameViewParams);

        final Spinner size = new Spinner(this);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        String[] arraySpinner = new String[20];
        arraySpinner[0] = "1GB" + activity.getString(R.string.growable);
        arraySpinner[1] = "2GB" + activity.getString(R.string.growable);
        arraySpinner[2] = "4GB" + activity.getString(R.string.growable);
        arraySpinner[3] = "10GB" + activity.getString(R.string.growable);
        arraySpinner[4] = "20GB" + activity.getString(R.string.growable);
        arraySpinner[5] = "32GB" + activity.getString(R.string.growable);
        arraySpinner[6] = "64GB" + activity.getString(R.string.growable);
        arraySpinner[7] = "128GB" + activity.getString(R.string.growable);
        arraySpinner[8] = "256GB" + activity.getString(R.string.growable);
        arraySpinner[9] = "512GB" + activity.getString(R.string.growable);
        arraySpinner[10] = "1GB" + activity.getString(R.string.FAT32) + activity.getString(R.string.growable);
        arraySpinner[11] = "2GB" + activity.getString(R.string.FAT32) + activity.getString(R.string.growable);
        arraySpinner[12] = "4GB" + activity.getString(R.string.FAT32) + activity.getString(R.string.growable);
        arraySpinner[13] = "10GB" + activity.getString(R.string.FAT32) + activity.getString(R.string.growable);
        arraySpinner[14] = "20GB" + activity.getString(R.string.FAT32) + activity.getString(R.string.growable);
        arraySpinner[15] = "32GB" + activity.getString(R.string.FAT32) + activity.getString(R.string.growable);
        arraySpinner[16] = "64GB" + activity.getString(R.string.FAT32) + activity.getString(R.string.growable);
        arraySpinner[17] = "128GB" + activity.getString(R.string.FAT32) + activity.getString(R.string.growable);
        arraySpinner[18] = "256GB" + activity.getString(R.string.FAT32) + activity.getString(R.string.growable);
        arraySpinner[19] = "512GB" + activity.getString(R.string.FAT32) + activity.getString(R.string.growable);

        TextView textView = new TextView(this);
        textView.setText(R.string.NeedNTFS);
        textView.setClickable(true);
        textView.setTextColor(Color.GREEN);
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NetworkUtils.openURL(activity, Config.QemuPartitionedQcow2Link);
            }
        });
        mLayout.addView(textView);


        ArrayAdapter<?> sizeAdapter = new ArrayAdapter<Object>(this, R.layout.custom_spinner_item, arraySpinner);
        sizeAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        size.setAdapter(sizeAdapter);

        // 关键：动态创建的 Spinner 也要清背景 + 设弹出背景
        size.setBackground(null);
        size.setPopupBackgroundResource(R.drawable.rounded_spinner_popup);

        mLayout.addView(size, spinnerParams);

        alertDialog.setView(mLayout);

        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.Create), (DialogInterface.OnClickListener) null);
        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.ChangeDirectory), (DialogInterface.OnClickListener) null);


        GlassDialogs.applyOnShow(alertDialog);


        alertDialog.show();

        Button positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                if (LimboSettingsManager.getImagesDir(LimboActivity.this) == null) {
                    changeImagesDir();
                    return;
                }

                int sizeSel = size.getSelectedItemPosition();
                String templateImage = "hd1g.qcow2";
                if (sizeSel == 0) {
                    templateImage = "hd1g.qcow2";
                } else if (sizeSel == 1) {
                    templateImage = "hd2g.qcow2";
                } else if (sizeSel == 2) {
                    templateImage = "hd4g.qcow2";
                } else if (sizeSel == 3) {
                    templateImage = "hd10g.qcow2";
                } else if (sizeSel == 4) {
                    templateImage = "hd20g.qcow2";
                } else if (sizeSel == 5) {
                    templateImage = "hd32g.qcow2";
                } else if (sizeSel == 6) {
                    templateImage = "hd64g.qcow2";
                } else if (sizeSel == 7) {
                    templateImage = "hd128g.qcow2";
                } else if (sizeSel == 8) {
                    templateImage = "hd256g.qcow2";
                } else if (sizeSel == 9) {
                    templateImage = "hd512g.qcow2";
                } else if (sizeSel == 10) {
                    templateImage = "hdfat1g.qcow2";
                } else if (sizeSel == 11) {
                    templateImage = "hdfat2g.qcow2";
                } else if (sizeSel == 12) {
                    templateImage = "hdfat4g.qcow2";
                } else if (sizeSel == 13) {
                    templateImage = "hdfat10g.qcow2";
                } else if (sizeSel == 14) {
                    templateImage = "hdfat20g.qcow2";
                } else if (sizeSel == 15) {
                    templateImage = "hdfat32g.qcow2";
                } else if (sizeSel == 16) {
                    templateImage = "hdfat64g.qcow2";
                } else if (sizeSel == 17) {
                    templateImage = "hdfat128g.qcow2";
                } else if (sizeSel == 18) {
                    templateImage = "hdfat256g.qcow2";
                } else if (sizeSel == 19) {
                    templateImage = "hdfat512g.qcow2";
                }

                String image = imageNameView.getText().toString();
                if (image.trim().equals(""))
                    ToastUtils.toastShort(activity, getString(R.string.ImageFilenameCannotBeEmpty));
                else {
                    if (!image.endsWith(".qcow2")) {
                        image += ".qcow2";
                    }
                    String filePath = FileUtils.createImgFromTemplate(LimboActivity.this, templateImage, image, fileType);
                    if (filePath!=null) {
                        updateDrive(fileType, filePath);
                        alertDialog.dismiss();
                    }

                }
            }
        });

        Button negativeButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        negativeButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                changeImagesDir();

            }
        });
    }

    public void changeImagesDir() {
        ToastUtils.toastLong(LimboActivity.this, getString(R.string.chooseDirToCreateImage));
        LimboFileManager.browse(LimboActivity.this, FileType.IMAGE_DIR, Config.OPEN_IMAGE_DIR_REQUEST_CODE);
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(true);
            return true; // return
        }

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Config.SDL_QUIT_RESULT_CODE) {
            if (getParent() != null) {
                getParent().finish();
            }
            finish();
            if (MachineController.getInstance().isRunning()) {
                notifyAction(MachineAction.STOP_VM, null);
            }
        } else if (requestCode == Config.OPEN_IMPORT_FILE_REQUEST_CODE || requestCode == Config.OPEN_IMPORT_FILE_ASF_REQUEST_CODE) {
            String file;
            if (requestCode == Config.OPEN_IMPORT_FILE_ASF_REQUEST_CODE) {
                file = FileUtils.getFileUriFromIntent(this, data, false);
            } else {
                file = FileUtils.getFilePathFromIntent(this, data);
            }
            if (file != null)
                importMachines(file);
        } else if (requestCode == Config.OPEN_EXPORT_DIR_REQUEST_CODE || requestCode == Config.OPEN_EXPORT_DIR_ASF_REQUEST_CODE) {
            String exportDir;
            if (requestCode == Config.OPEN_EXPORT_DIR_ASF_REQUEST_CODE) {
                exportDir = FileUtils.getFileUriFromIntent(this, data, true);
            } else {
                exportDir = FileUtils.getDirPathFromIntent(this, data);
            }
            if (exportDir != null)
                LimboSettingsManager.setExportDir(this, exportDir);
        } else if (requestCode == Config.OPEN_IMAGE_FILE_REQUEST_CODE || requestCode == Config.OPEN_IMAGE_FILE_ASF_REQUEST_CODE) {
            String file;
            if (requestCode == Config.OPEN_IMAGE_FILE_ASF_REQUEST_CODE) {
                file = FileUtils.getFileUriFromIntent(this, data, true);
            } else {
                browseFileType = FileUtils.getFileTypeFromIntent(this, data);
                file = FileUtils.getFilePathFromIntent(this, data);
            }
            if (file != null)
                updateDrive(browseFileType, file);
        } else if (requestCode == Config.OPEN_IMAGE_DIR_REQUEST_CODE || requestCode == Config.OPEN_IMAGE_DIR_ASF_REQUEST_CODE) {
            String imageDir;
            if (requestCode == Config.OPEN_IMAGE_DIR_ASF_REQUEST_CODE) {
                imageDir = FileUtils.getFileUriFromIntent(this, data, true);
            } else {
                imageDir = FileUtils.getDirPathFromIntent(this, data);
            }
            if (imageDir != null)
                LimboSettingsManager.setImagesDir(this, imageDir);

        } else if (requestCode == Config.OPEN_SHARED_DIR_REQUEST_CODE || requestCode == Config.OPEN_SHARED_DIR_ASF_REQUEST_CODE) {
            String file;
            if (requestCode == Config.OPEN_SHARED_DIR_ASF_REQUEST_CODE) {
                file = FileUtils.getFileUriFromIntent(this, data, true);
            } else {
                browseFileType = FileUtils.getFileTypeFromIntent(this, data);
                file = FileUtils.getDirPathFromIntent(this, data);
            }
            if (file != null) {
                updateDrive(browseFileType, file);
                LimboSettingsManager.setSharedDir(this, file);
            }
        } else if (requestCode == Config.OPEN_LOG_FILE_DIR_REQUEST_CODE || requestCode == Config.OPEN_LOG_FILE_DIR_ASF_REQUEST_CODE) {
            String file;
            if (requestCode == Config.OPEN_LOG_FILE_DIR_ASF_REQUEST_CODE) {
                file = FileUtils.getFileUriFromIntent(this, data, true);
            } else {
                file = FileUtils.getDirPathFromIntent(this, data);
            }
            if (file != null) {
                FileUtils.saveLogToFile(LimboActivity.this, file);
            }
        } else if (requestCode == Config.OPEN_IMPORT_BIOS_FILE_REQUEST_CODE || requestCode == Config.OPEN_IMPORT_BIOS_FILE_ASF_REQUEST_CODE) {
            String file;
            if (requestCode == Config.OPEN_IMPORT_BIOS_FILE_ASF_REQUEST_CODE) {
                file = FileUtils.getFileUriFromIntent(this, data, false);
            } else {
                file = FileUtils.getFilePathFromIntent(this, data);
            }
            if (file != null)
                BIOSImporter.importBIOSFile(this, file);
        }
    }

    private void updateDrive(FileType fileType, String diskValue) {

        // 下面是你原来的代码，完全不动
        if (fileType == null || diskValue == null) {
            return;
        }
        Spinner spinner = getSpinner(fileType);
        if (!diskValue.trim().isEmpty()) {
            if (SpinnerAdapter.getPositionFromSpinner(spinner, diskValue) < 0) {
                android.widget.SpinnerAdapter adapter = spinner.getAdapter();
                if (adapter instanceof ArrayAdapter) {
                    SpinnerAdapter.addItem(spinner, diskValue);
                }
            }
            notifyAction(MachineAction.INSERT_FAV, new Object[]{diskValue, fileType});
            seMachineDriveValue(fileType, diskValue);
        }
        int res = spinner.getSelectedItemPosition();
        if (res == 1) {
            spinner.setSelection(0);
        }
    }

    private ArrayAdapter getAdapter(FileType fileType) {
        Spinner spinner = getSpinner(fileType);
        return (ArrayAdapter) spinner.getAdapter();
    }

    private Spinner getSpinner(FileType fileType) {
        if (diskMapping.containsKey(fileType))
            return diskMapping.get(fileType).spinner;
        return null;
    }

    private MachineProperty getProperty(FileType fileType) {
        if (diskMapping.containsKey(fileType))
            return diskMapping.get(fileType).colName;
        return null;
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        savePendingEditText();
        MachineController.getInstance().removeOnStatusChangeListener(this);
        if (getMachine() != null) {
            getMachine().deleteObserver(LimboActivity.this);
        }
        setViewListener(null);
        super.onDestroy();
    }

    private void populateRAM() {
        String[] arraySpinner = new String[4 * 256];
        arraySpinner[0] = 4 + "";
        for (int i = 1; i < arraySpinner.length; i++) {
            arraySpinner[i] = i * 8 + "";
        }
        ArrayAdapter<String> ramAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arraySpinner);
        ramAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mRamSize.setAdapter(ramAdapter);
        mRamSize.invalidate();
    }

    private void populateCPUNum() {
        String[] arraySpinner = new String[Config.MAX_CPU_NUM];
        for (int i = 0; i < arraySpinner.length; i++) {
            arraySpinner[i] = (i + 1) + "";
        }
        ArrayAdapter<String> cpuNumAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arraySpinner);
        cpuNumAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mCPUNum.setAdapter(cpuNumAdapter);
        mCPUNum.invalidate();
    }

    private void populateBootDevices() {
        ArrayList<String> bootDevicesList = new ArrayList<>();
        bootDevicesList.add(getString(R.string.defaulttext));
        bootDevicesList.add("光驱");
        bootDevicesList.add(getString(R.string.harddisk));
        if (Config.enableEmulatedFloppy)
            bootDevicesList.add(getString(R.string.floppy));

        String[] arraySpinner = bootDevicesList.toArray(new String[0]);

        ArrayAdapter<String> bootDevAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arraySpinner);
        bootDevAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mBootDevices.setAdapter(bootDevAdapter);
        mBootDevices.invalidate();
    }

    private void populateBios() {
        ArrayList<String> biosList = new ArrayList<>();
        biosList.add(getString(R.string.defaulttext));
        biosList.add("SeaBios(x86)");
        biosList.add("SeaBios(x64)");
        biosList.add("vmwareEFI(x64)");
        biosList.add("ovmfEFI(x86)");
        biosList.add("ovmfEFI(x64)");

        String[] arraySpinner = biosList.toArray(new String[0]);

        ArrayAdapter<String> biosDevAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arraySpinner);
        biosDevAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mBios.setAdapter(biosDevAdapter);
        mBios.invalidate();
    }

    private void populateNet() {
        String[] arraySpinner = {getString(R.string.none), "User", "TAP"};
        ArrayAdapter<String> netAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arraySpinner);
        netAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mNetConfig.setAdapter(netAdapter);
        mNetConfig.invalidate();
    }

    private void populateVGA() {
        ArrayList<String> arrList = ArchDefinitions.getVGAValues(this);
        ArrayAdapter<String> vgaAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arrList);
        vgaAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mVGAConfig.setAdapter(vgaAdapter);
        mVGAConfig.invalidate();
    }
    private void populateVideoRAM() {
        String vga = getMachine() != null ? getMachine().getVga() : null;
        boolean isGl = vga != null && vga.contains("gl");

        ArrayList<String> arrList = new ArrayList<>();
        if (isGl) {
            // virtio-gpu-gl 系列：hostmem 值
            arrList.add("256M");
            arrList.add("512M");
            arrList.add("1G");
            arrList.add("2G");
            arrList.add("4G");
            arrList.add("8G");
        } else {
            // 传统显卡：原来的 4~4096 MB
            arrList.add("4");
            for (int i = 1; i < 4 * 256; i++) {
                arrList.add(String.valueOf(i * 16));
            }
        }

        ArrayAdapter<String> VideoramAdapter = new ArrayAdapter<>(
                this, R.layout.custom_spinner_item, arrList);
        VideoramAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mVideoRamSize.setAdapter(VideoramAdapter);
        mVideoRamSize.invalidate();

        // 恢复已保存的值
        if (getMachine() == null) {
            return;
        }
        String saved = getMachine().getVideoMemory();
        if (saved != null) {
            int idx = arrList.indexOf(saved);
            if (idx >= 0) {
                mVideoRamSize.setSelection(idx);
            }
        }
    }

    private void populateUSB() {
        ArrayList<String> arrList = ArchDefinitions.getUSBValues(this);
        ArrayAdapter<String> usbAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arrList);
        usbAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mUSBConfig.setAdapter(usbAdapter);
        mUSBConfig.invalidate();
    }
    private void populateKeyboardLayout() {
        ArrayList<String> values = ArchDefinitions.getKeyboardValues(this);
        ArrayList<String> labels = ArchDefinitions.getKeyboardLabels(this);

        ArrayAdapter<String> keyboardAdapter = new ArrayAdapter<>(
                this, R.layout.custom_spinner_item, labels);
        keyboardAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mKeyboard.setAdapter(keyboardAdapter);
        mKeyboard.invalidate();

        // populateKeyboardLayout 可能在机器加载之前被调用，getMachine() 可能为 null
        if (getMachine() == null) {
            return;
        }
        String saved = getMachine().getKeyboard();
        if (saved != null) {
            int idx = values.indexOf(saved);
            if (idx >= 0) {
                mKeyboard.setSelection(idx);
            }
        }
    }

    private void populateMouse() {
        ArrayList<String> values = ArchDefinitions.getMouseValues(this);
        ArrayList<String> labels = ArchDefinitions.getMouseLabels(this);

        ArrayAdapter<String> mouseAdapter = new ArrayAdapter<>(
                this, R.layout.custom_spinner_item, labels);
        mouseAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mMouse.setAdapter(mouseAdapter);
        mMouse.invalidate();

        // 如果之前已经保存过 mouse 的值，反查它在 values 里的位置，设置选中项
        // 注意：populateMouse 可能在机器加载之前被调用，此时 getMachine() 可能为 null
        if (getMachine() == null) {
            return;
        }
        String saved = getMachine().getMouse();
        if (saved != null) {
            int idx = values.indexOf(saved);
            if (idx >= 0) {
                mMouse.setSelection(idx);
            }
        }
    }

    private void populateSoundcardConfig() {
        ArrayList<String> soundCards = new ArrayList<>();
        soundCards.add(getString(R.string.none));
        soundCards.addAll(ArchDefinitions.getSoundcards(this));
        ArrayAdapter<String> sndAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, soundCards);
        sndAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mSoundCard.setAdapter(sndAdapter);
        mSoundCard.invalidate();
    }

    private void populateNetDevices(String nic) {
        ArrayList<String> networkCards = ArchDefinitions.getNetworkDevices(this);
        ArrayAdapter<String> nicCfgAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, networkCards);
        nicCfgAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mNetworkCard.setAdapter(nicCfgAdapter);
        mNetworkCard.invalidate();

        int pos = nicCfgAdapter.getPosition(nic);
        if (pos >= 0) {
            mNetworkCard.setSelection(pos);
        }
    }

    private void populateMachines(final String machineValue) {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                final ArrayList<String> machinesList = ArchDefinitions.getMachineValues(LimboActivity.this);
                ArrayList<String> machinesDB = MachineController.getInstance().getStoredMachines();
                machinesList.addAll(machinesDB);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        ArrayAdapter<String> machineAdapter = new ArrayAdapter<>(LimboActivity.this, R.layout.custom_spinner_item, machinesList);
                        machineAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
                        mMachine.setAdapter(machineAdapter);
                        mMachine.invalidate();
                        if (machineValue != null)
                            SpinnerAdapter.setDiskAdapterValue(mMachine, machineValue);
                    }
                });
            }
        });
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private void seMachineDriveValue(FileType fileType, final String diskValue) {
        Spinner spinner = getSpinner(fileType);
        if (spinner != null)
            SpinnerAdapter.setDiskAdapterValue(spinner, diskValue);
    }

    private void populateCPUs(String cpu) {
        ArrayList<String> arrList = ArchDefinitions.getCpuValues(this);
        ArrayAdapter<String> cpuAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arrList);
        cpuAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mCPU.setAdapter(cpuAdapter);
        mCPU.invalidate();
        int pos = cpuAdapter.getPosition(cpu);
        if (pos >= 0) {
            mCPU.setSelection(pos);
        }
    }

    private void populateMachineType(String machineType) {
        ArrayList<String> arrList = ArchDefinitions.getMachineTypeValues(this);

        ArrayAdapter<String> machineTypeAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arrList);
        machineTypeAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mMachineType.setAdapter(machineTypeAdapter);

        mMachineType.invalidate();
        int pos = machineTypeAdapter.getPosition(machineType);
        mMachineType.setSelection(Math.max(pos, 0));

    }

    private void populateUI() {
        ArrayList<String> arrList = ArchDefinitions.getUIValues();
        ArrayAdapter<String> uiAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, arrList);
        uiAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mUI.setAdapter(uiAdapter);
        mUI.invalidate();
    }

    public void populateDiskAdapter(final Spinner spinner, final FileType fileType, final boolean createOption) {
        Thread t = new Thread(new Runnable() {
            public void run() {
                ArrayList<String> oldHDs = MachineFilePaths.getRecentFilePaths(fileType);
                final ArrayList<String> arraySpinner = new ArrayList<>();
                arraySpinner.add(getString(R.string.none));
                if (createOption)
                    arraySpinner.add(getString(R.string.newtext));
                arraySpinner.add(getString(R.string.open));
                final int index = arraySpinner.size();
                if (oldHDs != null) {
                    for (String file : oldHDs) {
                        if (file != null) {
                            arraySpinner.add(file);
                        }
                    }
                }
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        SpinnerAdapter adapter = new SpinnerAdapter(LimboActivity.this, R.layout.custom_spinner_item, arraySpinner, index);
                        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
                        spinner.setAdapter(adapter);
                        spinner.invalidate();
                    }
                });
            }
        });
        t.start();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        invalidateOptionsMenu();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(0, HELP, 0, R.string.help).setIcon(R.drawable.help);
        menu.add(0, INSTALL, 0, R.string.InstallRoms).setIcon(R.drawable.install);
        if(!MachineController.getInstance().isRunning()) {
            menu.add(0, CREATE, 0, R.string.CreateMachine).setIcon(R.drawable.machinetype);
            menu.add(0, DELETE, 0, R.string.DeleteMachine).setIcon(R.drawable.delete);
            if (getMachine() != null && getMachine().getPaused() == 1)
                menu.add(0, DISCARD_VM_STATE, 0, R.string.DiscardSavedState).setIcon(R.drawable.close);
            menu.add(0, EXPORT, 0, R.string.ExportMachines).setIcon(R.drawable.exportvms);
            menu.add(0, IMPORT, 0, R.string.ImportMachines).setIcon(R.drawable.importvms);
        }
        menu.add(0, IMPORT_BIOS_FILE, 0, R.string.ImportBIOSFile).setIcon(R.drawable.importvms);
        menu.add(0, SETTINGS, 0, R.string.Settings).setIcon(R.drawable.settings);
        menu.add(0, TOOLS, 0, R.string.advancedTools).setIcon(R.drawable.advanced);
        menu.add(0, VIEWLOG, 0, R.string.ViewLog).setIcon(android.R.drawable.ic_menu_view);
        menu.add(0, HELP, 0, R.string.help).setIcon(R.drawable.help);
        menu.add(0, CHANGELOG, 0, R.string.Changelog).setIcon(android.R.drawable.ic_menu_help);
        menu.add(0, OSLIST, 0, R.string.OSLIST).setIcon(R.drawable.exportvms);
        menu.add(0, DOWNLOAD, 0, R.string.Downloads).setIcon(android.R.drawable.ic_menu_help);
        menu.add(0, LICENSE, 0, R.string.License).setIcon(android.R.drawable.ic_menu_help);
        menu.add(0, QUIT, 0, R.string.Exit).setIcon(android.R.drawable.ic_lock_power_off);
        if (mIsListMode) {

            for (int i = 0; i < 2; i++) {
                menu.getItem(i).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            }
            return true;
        }

        if (mIsListMode) {
            return true;
        }

        // 创建模式：打勾 + 打叉
        if (mIsCreating) {
            MenuItem saveItem = menu.add(0, MENU_SAVE, 0, "保存");
            saveItem.setIcon(R.drawable.ic_check_white);
            saveItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

            MenuItem cancelItem = menu.add(0, MENU_CANCEL, 0, "取消");
            cancelItem.setIcon(R.drawable.ic_close_white);
            cancelItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            return true;
        }

        // 编辑模式：打勾 + 垃圾桶(红) + 打叉
        // 编辑模式：打勾 + 打叉 + 垃圾桶(红)
        MenuItem saveItem = menu.add(0, MENU_SAVE, 0, "保存");
        saveItem.setIcon(R.drawable.ic_check_white);
        saveItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        MenuItem cancelItem = menu.add(0, MENU_CANCEL, 0, "取消");
        cancelItem.setIcon(R.drawable.ic_close_white);
        cancelItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        MenuItem deleteItem = menu.add(0, MENU_DELETE, 0, "删除");
        deleteItem.setIcon(R.drawable.delete_red);
        deleteItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {

        super.onOptionsItemSelected(item);
        if (item.getItemId() == INSTALL) {
            Installer.installFiles(this, true);
        } else if (item.getItemId() == DELETE) {
            promptDeleteMachine();
        } else if (item.getItemId() == DISCARD_VM_STATE) {
            promptDiscardVMState();
        } else if (item.getItemId() == CREATE) {
            promptMachineName(this);
        } else if (item.getItemId() == SETTINGS) {
            showSettings();
        } else if (item.getItemId() == TOOLS) {
            LimboActivityCommon.goToURL(this, Config.toolsLink);
        } else if (item.getItemId() == EXPORT) {
            MachineExporter.promptExport(this);
        } else if (item.getItemId() == IMPORT) {
            MachineImporter.promptImportMachines(this);
        } else if (item.getItemId() == IMPORT_BIOS_FILE) {
            BIOSImporter.promptImportBIOSFile(this);
        } else if (item.getItemId() == HELP) {
            Help.showHelp(this);
        } else if (item.getItemId() == DOWNLOAD) {
            showDownloads();
        } else if (item.getItemId() == OSLIST) {
            showOperatingSystems();
        } else if (item.getItemId() == VIEWLOG) {
            Logger.viewLimboLog(LimboActivity.this);
        } else if (item.getItemId() == CHANGELOG) {
            LimboActivityCommon.showChangelog(LimboActivity.this);
        } else if (item.getItemId() == LICENSE) {
            promptLicense();
        } else if (item.getItemId() == QUIT) {
            exit();
        } else if (item.getItemId() == MENU_SAVE) {
            saveMachineConfig();
        } else if (item.getItemId() == MENU_CANCEL) {
            cancelMachineConfig();
        } else if (item.getItemId() == MENU_DELETE) {
            promptDeleteMachine();
        }
        return true;
    }

    private void showSettings() {
        Intent i = new Intent(this, LimboSettingsManager.class);
        startActivity(i);
    }


    private void promptDeleteMachine() {
        if (mCreatingMachineName == null) {
            android.util.Log.e("DeleteDebug", "mCreatingMachineName is null!");
            ToastUtils.toastShort(this, "没有选中虚拟机");
            return;
        }

        android.util.Log.d("DeleteDebug", "deleting: " + mCreatingMachineName);

        AlertDialog dlgGlass3 = new AlertDialog.Builder(this)
                .setTitle("删除虚拟机")
                .setMessage("确定要删除虚拟机 " + mCreatingMachineName + " 吗？\n\n只会删除配置文件和状态，不会删除磁盘镜像。")
                .setPositiveButton("是", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        MachineController controller = MachineController.getInstance();
                        Machine m = controller.getMachine();
                        if (m == null || !mCreatingMachineName.equals(m.getName())) {
                            controller.setStoredMachine(mCreatingMachineName);
                            m = controller.getMachine();
                        }
                        if (m != null) {
                            boolean ok = controller.deleteMachine(m);
                            android.util.Log.d("DeleteDebug", "delete result: " + ok);
                        }
                        showMachineList();
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        GlassDialogs.applyOnShow(dlgGlass3);
        dlgGlass3.show();
    }
    public void promptDiscardVMState() {
        AlertDialog dlgGlass2 = new AlertDialog.Builder(this).setTitle(R.string.discardVMState)
                .setMessage(R.string.discardVMInstructions)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        notifyFieldChange(MachineProperty.PAUSED, 0);
                        changeStatus(MachineStatus.Ready);
                        enableNonRemovableDeviceOptions(true);
                        enableRemovableDeviceOptions(true);

                    }
                }).setNegativeButton(getString(android.R.string.no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
        }).create();
        GlassDialogs.applyOnShow(dlgGlass2);
        dlgGlass2.show();
    }

    public void onPause() {
        View currentView = getCurrentFocus();
        if (currentView instanceof EditText) {
            currentView.setFocusable(false);
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        int savedTheme = LimboSettingsManager.getTheme(this);
        int currentMode = AppCompatDelegate.getDefaultNightMode();
        int expectedMode;
        switch (savedTheme) {
            case 1:
                expectedMode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case 2:
                expectedMode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            default:
                expectedMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        if (currentMode != expectedMode) {
            AppCompatDelegate.setDefaultNightMode(expectedMode);
            recreate();
            return;
        }

        // 关键：每次 onResume 都重新给所有 Spinner 设背景
        applyRoundedPopupToAllSpinners(findViewById(android.R.id.content));

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                updateValues();
                if (libLoaded)
                    notifyAction(MachineAction.IGNORE_BREAKPOINT_INVALIDATION,
                            LimboSettingsManager.getIgnoreBreakpointInvalidation(LimboActivity.this));
            }
        }, 1000);
    }

    private void updateValues() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        changeStatus(MachineController.getInstance().getCurrStatus());
                        updateRemovableDiskValues();
                        updateSummary();
                    }
                });
            }
        });
        t.start();
    }

    private void updateRemovableDiskValues() {
        if (getMachine() != null) {
            disableRemovableDiskListeners();
            updateDrive(FileType.CDROM, getMachine().getCdImagePath());
            updateDrive(FileType.FDA, getMachine().getFdaImagePath());
            updateDrive(FileType.FDB, getMachine().getFdbImagePath());
            updateDrive(FileType.SD, getMachine().getSdImagePath());
            enableRemovableDiskListeners();
        }
    }

    public boolean isLandscapeOrientation(Activity activity) {
        Display display = activity.getWindowManager().getDefaultDisplay();
        Point screenSize = new Point();
        display.getSize(screenSize);
        return screenSize.x >= screenSize.y;
    }

    @Override
    public void onMachineStatusChanged(Machine machine, MachineStatus status, Object o) {
        switch (status) {
            case SaveFailed:
                LimboActivityCommon.promptPausedErrorVM(this, (String) o, viewListener);
                break;
            case SaveCompleted:
                LimboActivityCommon.promptPausedVM(this, viewListener);
                break;
            default:
                changeStatus(status);
        }
    }

    @Override
    public void onEvent(Machine machine, MachineController.Event event, Object o) {
        switch (event) {
            case MachineLoaded:
                if (o instanceof Integer) {
                    ToastUtils.toastShort(LimboActivity.this, getString((int) o));
                } else if (o instanceof String) {
                    ToastUtils.toastShort(LimboActivity.this, (String) o);
                } else {
                    loadMachine();
                }
                break;
            case MachineCreated:
                machineCreated();
                break;
            case MachineContinued:
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        invalidateOptionsMenu();
                        changeStatus(MachineController.getInstance().getCurrStatus());
                    }
                }, 1000);
                break;
            case MachinesImported:
                onMachinesImported((ArrayList<Machine>) o);
                break;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateSummary();
            }
        });
    }

    private void onMachinesImported(ArrayList<Machine> machines) {
        populateAttributesUI();
        setupMiscOptions();
        setupNonRemovableDiskListeners();
        enableRemovableDiskListeners();
        updateFavAdapters();
        LimboActivityCommon.promptMachinesImported(this, machines);
    }

    private void updateFavAdapters() {
        mHDA.getAdapter().getCount();
        mHDB.getAdapter().getCount();
        mHDC.getAdapter().getCount();
        mHDD.getAdapter().getCount();
        mCD.getAdapter().getCount();
        mFDA.getAdapter().getCount();
        mFDB.getAdapter().getCount();
        mSD.getAdapter().getCount();
        mKernel.getAdapter().getCount();
        mInitrd.getAdapter().getCount();
    }

    private void addGenericOperatingSystems() {
      /*  Config.osImages.put(getString(R.string.other), new LinksManager.LinkInfo(getString(R.string.other),
                getString(R.string.otherOperatingSystem),
                Config.otherOSLink,
                LinksManager.LinkType.OTHER)); */
                Config.osImages.put(getString(R.string.custom), new LinksManager.LinkInfo(getString(R.string.custom),
                getString(R.string.customOperatingSystem),
                null,
                LinksManager.LinkType.OTHER));
    }

    @Override
    public void update(Observable observable, final Object o) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Object [] params = (Object[] ) o;
                if (params[0] instanceof MachineProperty) {
                    MachineProperty property = (MachineProperty) params[0];
                    if (property == MachineProperty.UI) {
                        if (getMachine().getEnableVNC() != 1)
                            mSoundCard.setEnabled(true);
                        else
                            mSoundCard.setEnabled(true);
                    }
                }
                updateSummary();
            }
        });

    }

    public void notifyFieldChange(MachineProperty property, Object value) {
        if (viewListener != null)
            viewListener.onFieldChange(property, value);
    }

    public void notifyAction(MachineAction action, Object value) {
        if (viewListener != null)
            viewListener.onAction(action, value);
    }

    protected void onCreate() {
    }

    static class DiskInfo {
        public CheckBox enableCheckBox;
        public Spinner spinner;
        public MachineProperty colName;

        public DiskInfo(Spinner spinner, CheckBox enableCheckbox, MachineProperty dbColName) {
            this.spinner = spinner;
            this.enableCheckBox = enableCheckbox;
            this.colName = dbColName;
        }
    }
    private void startGuide() {
        android.content.SharedPreferences prefs =
                android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("guide_done", false)) {
            return;
        }

        mGuideOverlay = findViewById(R.id.guide_overlay);
        if (mGuideOverlay == null) return;

        mGuideCheckPage = findViewById(R.id.guide_check_page);
        mGuideLicensePage = findViewById(R.id.guide_license_page);
        mGuideBiosPage = findViewById(R.id.guide_bios_page);

        mGuideCheckSpinner = findViewById(R.id.guide_check_spinner);
        mGuideCheckResult = findViewById(R.id.guide_check_result);
        mGuideCheckContinue = findViewById(R.id.guide_check_continue);

        mGuideLicenseText = findViewById(R.id.guide_license_text);
        mGuideLicenseContinue = findViewById(R.id.guide_license_continue);

        mGuideBiosSpinner = findViewById(R.id.guide_bios_spinner);
        mGuideBiosCheck = findViewById(R.id.guide_bios_check);
        mGuideBiosText = findViewById(R.id.guide_bios_text);
        mGuideBiosContinue = findViewById(R.id.guide_bios_continue);
        mGuidePermissionPage = findViewById(R.id.guide_permission_page);
        mGuidePermissionSkip = findViewById(R.id.guide_permission_skip);
        mGuidePermissionGrant = findViewById(R.id.guide_permission_grant);
        mCurrentGuidePage = 0;
        mGuideOverlay.setVisibility(View.VISIBLE);
        showGuidePage(0);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                performVirtualizationCheck();
            }
        }, 1500);
    }

    private void showGuidePage(int page) {
        View[] pages = {mGuideCheckPage, mGuideLicensePage, mGuidePermissionPage, mGuideBiosPage};

        if (mCurrentGuidePage == page) return;

        boolean slideFromRight = page > mCurrentGuidePage;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        final View currentView = pages[mCurrentGuidePage];
        final View targetView = pages[page];

        // 当前页滑出
        currentView.animate()
                .translationX(slideFromRight ? -screenWidth : screenWidth)
                .setDuration(300)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        currentView.setVisibility(View.GONE);
                        currentView.setTranslationX(0);
                    }
                })
                .start();

        // 新页滑入
        targetView.setVisibility(View.VISIBLE);
        targetView.setTranslationX(slideFromRight ? screenWidth : -screenWidth);
        targetView.animate()
                .translationX(0)
                .setDuration(300)
                .start();

        mCurrentGuidePage = page;
    }

    private void performVirtualizationCheck() {
        boolean kvm = canAccess("/dev/kvm");
        boolean gunyah = canAccess("/dev/gunyah");
        boolean gzvm = canAccess("/dev/gzvm");
        boolean anySupport = kvm || gunyah || gzvm;

        if (mGuideCheckSpinner != null) {
            mGuideCheckSpinner.setVisibility(View.GONE);
        }

        if (mGuideCheckResult != null) {
            mGuideCheckResult.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            sb.append("你的设备");
            sb.append(anySupport ? "支持" : "不支持");
            sb.append("虚拟化\n\n");
            sb.append("KVM: ").append(kvm ? "支持" : "不支持").append("\n");
            sb.append("Gunyah: ").append(gunyah ? "支持" : "不支持").append("\n");
            sb.append("Gzvm: ").append(gzvm ? "支持" : "不支持");
            mGuideCheckResult.setText(sb.toString());
        }

        if (mGuideCheckContinue != null) {
            mGuideCheckContinue.setVisibility(View.VISIBLE);
            mGuideCheckContinue.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mGuideLicenseText.setText(getLicenseText());
                    showGuidePage(1);
                    setupLicensePage();
                }
            });
        }
    }

    private void setupLicensePage() {
        if (mGuideLicenseContinue != null) {
            mGuideLicenseContinue.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 切到权限页
                    showGuidePage(2);
                    setupPermissionPage();
                }
            });
        }
    }

    private void setupPermissionPage() {
        if (mGuidePermissionGrant != null) {
            mGuidePermissionGrant.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestStoragePermission();
                }
            });
        }

        if (mGuidePermissionSkip != null) {
            mGuidePermissionSkip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 跳过，直接进入 BIOS 安装页
                    showGuidePage(3);
                    startBiosInstall();
                }
            });
        }
    }

    private void requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // 已有权限，直接进 BIOS
                showGuidePage(3);
                startBiosInstall();
            } else {
                requestPermissions(new String[]{
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, 1001);
            }
        } else {
            // 低于 6.0 不需要运行时权限
            showGuidePage(3);
            startBiosInstall();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            // 不管授权成功还是失败，都进入 BIOS 安装
            showGuidePage(3);
            startBiosInstall();
        }
    }

    private void startBiosInstall() {
        if (mGuideBiosSpinner != null) mGuideBiosSpinner.setVisibility(View.VISIBLE);
        if (mGuideBiosCheck != null) mGuideBiosCheck.setVisibility(View.GONE);
        if (mGuideBiosText != null) {
            mGuideBiosText.setText("正在安装 BIOS 和映射文件...");
            mGuideBiosText.setVisibility(View.VISIBLE);
        }
        if (mGuideBiosContinue != null) mGuideBiosContinue.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Installer.installFiles(LimboActivity.this, true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        onBiosInstallDone();
                    }
                });
            }
        }).start();
    }

    private void onBiosInstallDone() {
        if (mGuideBiosSpinner != null) mGuideBiosSpinner.setVisibility(View.GONE);
        if (mGuideBiosCheck != null) mGuideBiosCheck.setVisibility(View.VISIBLE);
        if (mGuideBiosText != null) mGuideBiosText.setVisibility(View.GONE);
        if (mGuideBiosContinue != null) {
            mGuideBiosContinue.setText("开始吧");
            mGuideBiosContinue.setVisibility(View.VISIBLE);
            mGuideBiosContinue.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    android.content.SharedPreferences prefs =
                            android.preference.PreferenceManager.getDefaultSharedPreferences(LimboActivity.this);
                    prefs.edit().putBoolean("guide_done", true).apply();

                    mGuideOverlay.animate()
                            .alpha(0f)
                            .setDuration(300)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    mGuideOverlay.setVisibility(View.GONE);
                                }
                            })
                            .start();
                }
            });
        }
    }

    private String getLicenseText() {
        return "Limbo Emulator Plus\n\n" +
                "Copyright (C) Max Kastanas 2012\n\n" +
                "本程序为自由软件；您可以依据自由软件基金会发布的 GNU 通用公共许可证（GPL）的条款重新发布和/或修改它。本程序发布时希望它有用，但不提供任何担保；甚至不提供适销性或特定用途适用性的暗示担保。详见 GNU 通用公共许可证。\n\n" +
                "您应该已经随本程序收到一份 GNU 通用公共许可证的副本；如果没有，请写信给自由软件基金会，地址：59 Temple Place, Suite 330, Boston, MA 02111-1307 USA。\n\n" +
                "本程序使用了 QEMU、SDL2、标准virglrenderer 等开源项目，遵循各自的开源许可协议。\n\n" +
                "如需完整 GPL 2.0 协议文本，请访问：\nhttps://www.gnu.org/licenses/old-licenses/gpl-2.0.html";
    }

    private boolean canAccess(String path) {
        try {
            java.io.File f = new java.io.File(path);
            return f.exists() && f.canRead();
        } catch (Exception e) {
            return false;
        }
    }

    private void showMachineList() {
        mIsListMode = true;

        // 淡出配置界面
        mMachineConfigView.animate().alpha(0f).setDuration(200)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mMachineConfigView.setVisibility(View.GONE);
                        mMachineConfigView.setAlpha(1f);

                        // 淡入列表
                        mMachineListView.setAlpha(0f);
                        mMachineListView.setVisibility(View.VISIBLE);
                        mMachineListView.animate().alpha(1f).setDuration(200).start();
                    }
                }).start();

        refreshMachineList();
        invalidateOptionsMenu();
    }

    private void showMachineConfig(boolean isCreating, String machineName) {
        mIsListMode = false;
        mIsCreating = isCreating;
        mCreatingMachineName = machineName;

        // 淡出列表
        mMachineListView.animate().alpha(0f).setDuration(200)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mMachineListView.setVisibility(View.GONE);
                        mMachineListView.setAlpha(1f);

                        // 淡入配置界面
                        mMachineConfigView.setAlpha(0f);
                        mMachineConfigView.setVisibility(View.VISIBLE);
                        mMachineConfigView.animate().alpha(1f).setDuration(200).start();
                    }
                }).start();

        if (!isCreating && mMachine != null && machineName != null) {
            // 编辑：切换到目标机器
            android.widget.SpinnerAdapter adapter = mMachine.getAdapter();
            if (adapter instanceof android.widget.ArrayAdapter) {
                android.widget.ArrayAdapter<?> arrAdapter =
                        (android.widget.ArrayAdapter<?>) adapter;
                for (int i = 0; i < arrAdapter.getCount(); i++) {
                    if (machineName.equals(arrAdapter.getItem(i))) {
                        mMachine.setSelection(i);
                        break;
                    }
                }
            }
        }

        invalidateOptionsMenu();
    }


    private void refreshMachineList() {
        ArrayList<Machine> machines = MachineController.getInstance().getAllMachines();
        if (machines == null) {
            machines = new ArrayList<>();
        }

        if (machines.isEmpty()) {
            mMachineEmpty.setVisibility(View.VISIBLE);
            mMachineList.setVisibility(View.GONE);
        } else {
            mMachineEmpty.setVisibility(View.GONE);
            mMachineList.setVisibility(View.VISIBLE);
        }

        mMachineListAdapter = new MachineListAdapter(this, machines,
                new MachineListAdapter.OnMachineActionListener() {
                    @Override
                    public void onStart(String machineName) {
                        Machine m = getMachineByArch(machineName);
                        if (m != null) {
                            LimboApplication.updateArchFromMachine(m);
                        }
                        notifyAction(MachineAction.LOAD_VM, machineName);
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Intent i = new Intent(LimboActivity.this, LimboSDLActivity.class);
                                startActivity(i);
                                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        notifyAction(MachineAction.START_VM, null);
                                    }
                                }, 1000);
                            }
                        }, 300);
                    }

                    @Override
                    public void onConfig(String machineName) {
                        Machine m = getMachineByArch(machineName);
                        android.util.Log.d("ArchDebug", "onConfig: " + machineName + ", machine=" + (m != null ? m.getArch() : "null"));
                        if (m != null) {
                            LimboApplication.updateArchFromMachine(m);
                        }
                        android.util.Log.d("ArchDebug", "onConfig after update: arch=" + LimboApplication.arch);
                        sPendingOpenMachine = machineName;
                        recreate();
                    }
                });

        mMachineList.setAdapter(mMachineListAdapter);
    }

    private void saveMachineConfig() {
        // 新建已通过 promptMachineName 处理
        // 编辑：配置实时保存，直接回列表
        showMachineList();
    }
    private void cancelMachineConfig() {
        if (mIsCreating) {
            // 新建时取消：不创建
        } else {
            // 编辑时取消：重载原配置
            if (mCreatingMachineName != null) {
                notifyAction(MachineAction.LOAD_VM, mCreatingMachineName);
            }
        }
        showMachineList();
    }

    @Override
    public void onBackPressed() {
        if (!mIsListMode) {
            if (mIsCreating) {
                cancelMachineConfig();
            } else {
                showMachineList();
            }
            return;
        }

        // 如果当前不在虚拟机 tab，先切回去
        if (mCurrentTab != 0) {
            showTab(0);
            return;
        }

        // 在虚拟机 tab 的列表模式，退出
        super.onBackPressed();
    }

    private Machine getMachineByArch(String machineName) {
        try {
            ArrayList<Machine> list = MachineController.getInstance().getAllMachines();
            if (list != null) {
                for (Machine m : list) {
                    if (machineName.equals(m.getName())) {
                        return m;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void promptNewMachineArch() {
        final String[] archNames = {"x86_64", "ppc", "aarch64", "inferno_aarch64", "sparc"};
        final String[] archLabels = {
                "X86_64（PC/Windows/Linux）",
                "PPC（PowerPC）",
                "AArch64（ARM64 Linux）",
                "Inferno AArch64（iOS）",
                "SPARC"
        };

        AlertDialog dlgGlass1 = new AlertDialog.Builder(this)
                .setTitle("选择新建的模拟架构")
                .setItems(archLabels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mPendingCreateArch = archNames[which];
                        switch (mPendingCreateArch) {
                            case "x86_64": LimboApplication.arch = Config.Arch.x86_64; break;
                            case "ppc": LimboApplication.arch = Config.Arch.ppc; break;
                            case "aarch64": LimboApplication.arch = Config.Arch.arm64; break;
                            case "inferno_aarch64": LimboApplication.arch = Config.Arch.inferno_aarch64; break;
                            case "sparc": LimboApplication.arch = Config.Arch.sparc; break;
                        }
                        promptMachineName(LimboActivity.this);
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        GlassDialogs.applyOnShow(dlgGlass1);
        dlgGlass1.show();
    }

    private int getMainLayoutResId() {
        switch (LimboApplication.arch) {
            case x86:
            case x86_64:
                return R.layout.limbo_main_x86;
            case ppc:
            case ppc64:
                return R.layout.limbo_main_ppc;
            case arm:
            case arm64:
                return R.layout.limbo_main_aarch64;
            case inferno_aarch64:
                return R.layout.limbo_main_inferno;
            case sparc:
            case sparc64:
                return R.layout.limbo_main_sparc;
            default:
                return R.layout.limbo_main;
        }
    }
}


