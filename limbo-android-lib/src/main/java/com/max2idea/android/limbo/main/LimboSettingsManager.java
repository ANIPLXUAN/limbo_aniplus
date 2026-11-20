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
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.network.NetworkUtils;
import com.max2idea.android.limbo.toast.ToastUtils;

import java.util.HashSet;
import java.util.Set;

public class LimboSettingsManager extends PreferenceActivity {
    private static final String TAG = "Limbo设置管理";

    // DNS server
    static String getDNSServer(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("dnsServer", Config.defaultDNSServer);
    }

    public static void setDNSServer(Context context, String dnsServer) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString("dnsServer", dnsServer);
        edit.apply();
    }

    // Screen
    public static int getOrientationSetting(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int orientation = Integer.parseInt(prefs.getString("orientationPref", "0"));
        return orientation;
    }

    public static boolean getAlwaysShowMenuToolbar(Context activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        return prefs.getBoolean("AlwaysShowMenuToolbar", false);
    }

    public static boolean getFullscreen(Context activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        return prefs.getBoolean("ShowFullscreen", true);
    }

    // updates
    public static boolean getPromptUpdateVersion(Context context) {
        if(!Config.enableSoftwareUpdates) {
            return false;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("updateVersionPrompt", Config.defaultCheckNewVersion);
    }

    public static void setPromptUpdateVersion(Context context, boolean value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean("updateVersionPrompt", value);
        edit.apply();
    }

    // advanced
    public static boolean getPrio(Context activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        return prefs.getBoolean("HighPrio", false);
    }

    // files
    public static boolean getEnableLegacyFileManager(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("EnableLegacyFileManager", true);
    }

    public static String getLastDir(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("lastDir", null);
    }

    public static void setLastDir(Context context, String imagesPath) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString("lastDir", imagesPath);
        edit.apply();
    }

    public static String getImagesDir(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("imagesDir", null);
    }

    public static void setImagesDir(Context context, String imagesPath) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString("imagesDir", imagesPath);
        edit.apply();
    }

    public static String getExportDir(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("exportDir", null);
    }

    public static void setExportDir(Context context, String imagesPath) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString("exportDir", imagesPath);
        edit.apply();
    }

    public static String getSharedDir(Context context) {
        String lastDir = Environment.getExternalStorageDirectory().getPath();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("sharedDir", lastDir);
    }

    public static void setSharedDir(Context context, String lastDir) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString("sharedDir", lastDir);
        edit.apply();
    }

    // exit code
    public static int getExitCode(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt("exitCode", Config.EXIT_SUCCESS);
    }

    //XXX: we need to make sure this gets written to preferences right before we call System.exit()
    // so we need to use commit instead of apply
    @SuppressLint("ApplySharedPref")
    public static void setExitCode(Context context, int exitCode) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putInt("exitCode", exitCode);
        edit.commit();
    }

    public static boolean isFirstLaunch(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("firstTime" + LimboApplication.getLimboVersionString(), true);
    }

    public static void setFirstLaunch(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean("firstTime" + LimboApplication.getLimboVersionString(), false);
        edit.apply();
    }

    // Key Mapper
    public static Set<String> getKeyMappers(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> set = new HashSet<>();
        return prefs.getStringSet("keyMappers", set);
    }

    public static void setKeyMappers(Context context, Set<String> keyMappers) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putStringSet("keyMappers", keyMappers);
        edit.apply();
    }

    public static int getKeyMapperSize(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String sizeStr = prefs.getString("keyMapperSize", "3");
        return Integer.parseInt(sizeStr);
    }

    // VNC
    public static boolean getVNCEnablePassword(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("enableVNCPassword", false);
    }

    public static String getVNCPass(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("vncPass", "");
    }

    public static void setVNCPass(Context context, String value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString("vncPass", value);
        edit.apply();
    }

    public static boolean getEnableExternalVNC(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("enableExternalVNC", false);
    }

    // QMP
    public static boolean getEnableQmp(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("enableQMP", true);
    }

    public static boolean getEnableExternalQMP(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("enableExternalQMP", false);
    }

    public static boolean getImmersiveMode(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("immersiveMode", false);
    }

    public static int getKeyPressDelay(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String sizeStr = prefs.getString("keyPressDelay", "100");
        return Integer.parseInt(sizeStr);
    }

    public static int getMouseButtonDelay(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String sizeStr = prefs.getString("mouseButtonDelay", "100");
        return Integer.parseInt(sizeStr);
    }
    
    public static boolean getEnableVirtioBalloon(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("enableVirtioBalloon", false);
    } 

    // virtio-serial
    public static boolean getEnableVirtioSerial(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("enableVirtioSerial", false);
    }

    // virtio-rng
    public static boolean getEnableVirtioRng(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("enableVirtioRng", false);
    }

    public static int getTheme(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.parseInt(prefs.getString("themePref", "0"));
    }

    public static int getSdlScaleMode(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.parseInt(prefs.getString("sdlScaleMode", "0"));
    }

    public static void setSdlScaleMode(Context context, int mode) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString("sdlScaleMode", String.valueOf(mode)).apply();
    }

    public static boolean getEnableAaudio(Context activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        return prefs.getBoolean("enableAaudio", true);
    }

    public static String getDiskCache(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("diskCachePref", context.getString(R.string.Default));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 自定义布局（含底部导航）
        setContentView(R.layout.settings_layout);

        // 读取进入方向
        enteredFromRight = getIntent().getBooleanExtra("from_right", true);
        enterTab = getIntent().getIntExtra("enter_tab", 0);

        Intent data = new Intent();
        setResult(Config.SETTINGS_RETURN_CODE, data);

        addPrefs();

        getPreferenceManager().findPreference("enableVNCPassword").setOnPreferenceChangeListener(
                new android.preference.Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(android.preference.Preference preference, Object newValue) {
                        if ((boolean) newValue)
                            promptVNCPass(LimboSettingsManager.this);
                        return true;
                    }
                });
        getPreferenceManager().findPreference("vncPass").setOnPreferenceClickListener(
                new android.preference.Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(android.preference.Preference preference) {
                        promptVNCPass(LimboSettingsManager.this);
                        return true;
                    }
                });

        // 底部导航监听
        setupBottomNav();
    }

    private void setupBottomNav() {
        View navVm = findViewById(R.id.nav_vm);
        View navOnline = findViewById(R.id.nav_online);
        View navSettings = findViewById(R.id.nav_settings);
        View navAbout = findViewById(R.id.nav_about);

        if (navVm != null) {
            navVm.setOnClickListener(v -> startMainActivity(0));
        }

        if (navOnline != null) {
            navOnline.setOnClickListener(v -> startMainActivity(1));
        }

        if (navSettings != null) {
            navSettings.setEnabled(false);
        }

        if (navAbout != null) {
            navAbout.setOnClickListener(v -> startMainActivity(2));
        }

        TextView navSettingsText = findViewById(R.id.nav_settings_text);
        if (navSettingsText != null) {
            navSettingsText.setTextColor(getResources().getColor(R.color.colorPrimary));
        }
    }

    private boolean enteredFromRight = true;
    private int enterTab = 0;

    @Override
    public void finish() {
        super.finish();
        // 不在这里加动画，由调用方决定
    }

    @Override
    public void onBackPressed() {
        // 按返回键，目标是进入前的 tab
        super.onBackPressed();
        if (enteredFromRight) {
            overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right);
        } else {
            overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left);
        }
    }

    private void startMainActivity(int targetTab) {
        Intent i = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (i != null) {
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.putExtra("showTab", targetTab);
            startActivity(i);

            // 根据目标 tab 相对进入前 tab 的位置决定退出方向
            if (targetTab > enterTab) {
                // 目标在右边 → 设置往左侧滑出
                overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left);
            } else if (targetTab < enterTab) {
                // 目标在左边 → 设置往右侧滑出
                overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right);
            } else {
                // 回到进入前的 tab → 按进入方向反向滑出
                if (enteredFromRight) {
                    overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right);
                } else {
                    overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left);
                }
            }
            finish();
        } else {
            Toast.makeText(this, "无法启动主界面", Toast.LENGTH_SHORT).show();
        }
    }


    public void addPrefs() {
        addPreferencesFromResource(R.xml.settings);
        if(Config.enableSoftwareUpdates)
            addPreferencesFromResource(R.xml.software_updates);
        if(Config.enableImmersiveMode)
            addPreferencesFromResource(R.xml.immersive);
        if (Build.VERSION.SDK_INT >= 26)
            addPreferencesFromResource(R.xml.aaudio);
    }

    public void promptVNCPass(final Activity activity) {
        final AlertDialog alertDialog;
        alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setTitle(getString(R.string.VNCPassword));

        TextView textView = new TextView(activity);
        textView.setVisibility(View.VISIBLE);
        textView.setPadding(20, 20, 20, 20);
        textView.setText(getString(R.string.vncServer) + ": " + NetworkUtils.getVNCAddress(activity)
                + ":" + Config.defaultVNCPort + "\n" + getString(R.string.externalVNCWarning));

        final EditText passwdView = new EditText(activity);
        passwdView.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        passwdView.setSelection(passwdView.getText().length());
        passwdView.setHint(R.string.Password);
        passwdView.setEnabled(true);
        passwdView.setVisibility(View.VISIBLE);
        passwdView.setSingleLine();

        LinearLayout mLayout = new LinearLayout(this);
        mLayout.setPadding(20, 20, 20, 20);
        mLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams textViewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mLayout.addView(textView, textViewParams);

        LinearLayout.LayoutParams passwordViewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mLayout.addView(passwdView, passwordViewParams);

        alertDialog.setView(mLayout);
        passwdView.setTag(false);
        passwdView.setTransformationMethod(new PasswordTransformationMethod());
        passwdView.setSelection(passwdView.getText().length());

        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (passwdView.getText().toString().trim().equals("")) {
                    ToastUtils.toastShort(getApplicationContext(), getString(R.string.passwordCannotBeEmpty));
                } else {
                    LimboSettingsManager.setVNCPass(activity, passwdView.getText().toString());
                }
            }
        });
        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.Cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                LimboSettingsManager.setVNCPass(activity, null);
            }
        });


        alertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                LimboSettingsManager.setVNCPass(activity, null);
            }
        });
        GlassDialogs.applyOnShow(alertDialog);
        alertDialog.show();

        try {
            Button passwordButton = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            passwordButton.setVisibility(View.VISIBLE);
            passwordButton.setBackgroundResource(android.R.drawable.ic_menu_view);
            passwordButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if ((boolean) passwdView.getTag()) {
                        passwdView.setTransformationMethod(new PasswordTransformationMethod());
                    } else
                        passwdView.setTransformationMethod(new HideReturnsTransformationMethod());
                    passwdView.setTag(!(boolean) passwdView.getTag());
                    passwdView.setSelection(passwdView.getText().length());
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    public static boolean getPreventMouseOutOfBounds(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("preventMouseOutOfBounds", false);
    }

    public static boolean getIgnoreBreakpointInvalidation(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("ignoreBreakpointInvalidation", false);
    }

    public static boolean getForceSimplifiedChinese(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("ForceSimplifiedChinese", false);
    }

    public static String getGlesMode(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString("glesModePref", "system");
    }

}
