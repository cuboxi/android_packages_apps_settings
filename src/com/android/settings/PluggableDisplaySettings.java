/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright (C) 2013 Freescale Semiconductor, Inc.
 */

package com.android.settings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ActivityManagerNative;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.DialogInterface;
import android.database.ContentObserver;
import android.os.Message;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.SeekBarPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.PreferenceCategory;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.view.IWindowManager;
import android.view.Surface;
import android.view.View;

import android.os.FileUtils;
import android.os.Process;

import java.util.ArrayList;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileNotFoundException;

public class PluggableDisplaySettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "PluggableDisplaySettings";
    private static final boolean DBG = true;

    private static final int MAX_DISPLAY_DEVICE = 6;    

    private static final String[] KEY_DISPLAY_ENABLE     = {"display_enable_0","display_enable_1","display_enable_2",
                                                            "display_enable_3","display_enable_4","display_enable_5"};
    private static final String[] KEY_DISPLAY_XOVERSCAN   = {"display_xoverscan_0","display_xoverscan_1","display_xoverscan_2",
                                                            "display_xoverscan_3","display_xoverscan_4","display_xoverscan_5"};
    private static final String[] KEY_DISPLAY_YOVERSCAN   = {"display_yoverscan_0","display_yoverscan_1","display_yoverscan_2",
                                                            "display_yoverscan_3","display_yoverscan_4","display_yoverscan_5"};
    private static final String[] KEY_DISPLAY_MODE       = {"display_mode_0","display_mode_1","display_mode_2",
                                                            "display_mode_3","display_mode_4","display_mode_5"};
    private static final String[] KEY_DISPLAY_KEEPRATE   = {"display_keeprate_0","display_keeprate_1","display_keeprate_2",
                                                            "display_keeprate_3","display_keeprate_4","display_keeprate_5"};
    private static final String[] KEY_DISPLAY_CATEGORY   = {"display_category_0","display_category_1","display_category_2",
                                                            "display_category_3","display_category_4","display_category_5"};

    private String[] mCurrentMode = new String[MAX_DISPLAY_DEVICE];
    private static final String DISPLAY_HIGH_MODE = "keepHighestMode";

//    private DisplayManager mDisplayManager;
    private static int[] mXOverScanVal = new int[MAX_DISPLAY_DEVICE];
    private static int[] mYOverScanVal = new int[MAX_DISPLAY_DEVICE];

    
    private final Configuration mCurConfig = new Configuration();

    private SeekBarPreference[]  mXOverScanPref      = new SeekBarPreference[MAX_DISPLAY_DEVICE];
    private SeekBarPreference[]  mYOverScanPref      = new SeekBarPreference[MAX_DISPLAY_DEVICE];
    private ListPreference[]     mDisplayModePref   = new ListPreference[MAX_DISPLAY_DEVICE];
    private ListPreference[]     mKeepRatePref    = new ListPreference[MAX_DISPLAY_DEVICE];

    private PreferenceCategory[] mCategoryPref      = new PreferenceCategory[MAX_DISPLAY_DEVICE];
    
    private Dialog mConfirmDialog;

    private IntentFilter mIntentFilter;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (true) {// LocalDisplay.DISPLAY_CONNECT_STATE.equals(action)) {
                int dispid = 1;// intent.getIntExtra(LocalDisplay.EXTRA_DISPLAY_ID, -1);
                boolean connect = true;//intent.getBooleanExtra(LocalDisplay.EXTRA_DISPLAY_CONNECT, false);
                Log.w(TAG, "Display connect state change " + dispid + ", " + connect);
                handleDisplayConnected(dispid, connect);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ContentResolver resolver = getActivity().getContentResolver();

        addPreferencesFromResource(R.xml.pluggable_display_settings);

//        mDisplayManager = (DisplayManager)getActivity().getSystemService(Context.DISPLAY_SERVICE);


        for(int i=0; i<MAX_DISPLAY_DEVICE; i++){
            mDisplayModePref[i] = null;
            mXOverScanPref[i] = null;
            mYOverScanPref[i] = null;
            mKeepRatePref[i] = null;
            mCategoryPref[i] = null;
        }
        mIntentFilter = new IntentFilter();//LocalDisplay.DISPLAY_CONNECT_STATE);

//        if (mDisplayManager.isDisplayConnected(mDisplayManager.getHDMIDisplayId())) {
            handleDisplayConnected(1/*mDisplayManager.getHDMIDisplayId()*/, true);
//            Log.d(TAG, "HDMI connected.");
//        } else {
//            handleDisplayConnected(mDisplayManager.getHDMIDisplayId(), false);
//            Log.d(TAG, "HDMI is NOT connected.");
//        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mReceiver);
    }

    private void updateDisplayModePreferenceDescription(int dispid, String CurrentDisplayMode) {
        ListPreference preference = mDisplayModePref[dispid];
        preference.setSummary(CurrentDisplayMode);
    }

    private void updateActionModePreferenceDescription(int dispid, CharSequence CurrentActionMode) {
        ListPreference preference = mKeepRatePref[dispid];
        preference.setSummary(CurrentActionMode);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if(DBG) Log.w(TAG, "onPreferenceTreeClick ");

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if(DBG) Log.w(TAG, "onPreferenceChange ");
        final String key = preference.getKey();
        int dispid = 0;

        for(int i=0; i<MAX_DISPLAY_DEVICE; i++ ) {
            dispid = i;
            if (KEY_DISPLAY_MODE[i].equals(key)) {
                String value = (String) objValue;
                boolean modeChanged = true;
                String lastMode = mCurrentMode[i];
                mCurrentMode[i] = value;
                updateDisplayModePreferenceDescription(dispid, value);

                break;
            }
        
            if (KEY_DISPLAY_XOVERSCAN[i].equals(key)) {
                int value = Integer.parseInt(objValue.toString());            
                    //mDisplayManager.setDisplayXOverScan(dispid, value);
                    mXOverScanVal[dispid] = value;
                    View rootView = getActivity().getWindow().peekDecorView();
                    if(rootView != null) {
                        rootView.postInvalidateDelayed(200);
                    }
                    String dispStr = getActivity().getString(R.string.pluggable_settings_width_overscan);
                    mXOverScanPref[dispid].setTitle(dispStr + " (" + mXOverScanVal[dispid] + " Pixels)");

                    try {
                        // Executes the command.
                    Runtime.getRuntime().exec("/system/bin/wm overscan "+
                        mXOverScanVal[dispid]+","+
                        mYOverScanPref[dispid].getProgress()+","+
                        mXOverScanVal[dispid]+","+
                        mYOverScanPref[dispid].getProgress());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                break;
            }

            if (KEY_DISPLAY_YOVERSCAN[i].equals(key)) {
                int value = Integer.parseInt(objValue.toString());            
                    mYOverScanVal[dispid] = value;
                    View rootView = getActivity().getWindow().peekDecorView();
                    if(rootView != null) {
                        rootView.postInvalidateDelayed(200);
                    }
                    String dispStr = getActivity().getString(R.string.pluggable_settings_height_overscan);
                    mYOverScanPref[dispid].setTitle(dispStr + " (" + mYOverScanVal[dispid] + " Pixels)");
                    try {
                        // Executes the command.
                        Runtime.getRuntime().exec("/system/bin/wm overscan "+
                            mXOverScanPref[dispid].getProgress()+","+
                            mYOverScanVal[dispid]+","+
                            mXOverScanPref[dispid].getProgress()+","+
                            mYOverScanVal[dispid]);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                break;
            }

            if (KEY_DISPLAY_KEEPRATE[i].equals(key)) {
                int value = Integer.parseInt((String) objValue, 16);
                Log.w(TAG, "onPreferenceChange keeprate value=" + value);;
//                mDisplayManager.setDisplayKeepRate(dispid, value);         
                mKeepRatePref[dispid].setValue((String)objValue);
                updateActionModePreferenceDescription(dispid, mKeepRatePref[dispid].getEntry());
                break;
            }

        }
        
        return true;
    }

    private void addDisplayPreference(int fbid) {
        String dispKey = null;
        String dispTitle = null;
        String dispSummary = null;
        String dispDialogTitle = null;
        mCategoryPref[fbid] = new PreferenceCategory(getActivity());

        dispKey = "display_category_" + fbid;
        mCategoryPref[fbid].setKey(dispKey);
        mCategoryPref[fbid].setOnPreferenceChangeListener(this);
        getPreferenceScreen().addPreference(mCategoryPref[fbid]);

        mDisplayModePref[fbid] = new ListPreference(getActivity());

        dispKey = "display_mode_" + fbid;
        mDisplayModePref[fbid].setKey(dispKey);
        dispTitle = getActivity().getString(R.string.pluggable_settings_display_mode);//"Display Mode";
        mDisplayModePref[fbid].setTitle(dispTitle);
        dispSummary = "No display plugged";
        mDisplayModePref[fbid].setSummary(dispSummary);
        dispDialogTitle = getActivity().getString(R.string.pluggable_settings_display_mode);//"Display Mode";
        mDisplayModePref[fbid].setDialogTitle(dispDialogTitle);

        mDisplayModePref[fbid].setOnPreferenceChangeListener(this);
//        mCategoryPref[fbid].addPreference(mDisplayModePref[fbid]);

    }


    private void handleDisplayConnected(int dispid, boolean connection){
        String[] display_modes = null;
        if(DBG) Log.w(TAG, "handleDisplayConnected " + connection + "dispid "+ dispid);
        if (dispid < 0) {
            Log.w(TAG, "dispid is no valid");
            return;
        }

        if(connection) {
            String displayName = null;
            String displayType = null;
            boolean isHDMI = false;

            displayType = "hdmi";//mDisplayManager.getDisplayName(dispid);
            Log.w(TAG, "Display Name is " + displayType);

            if(displayType != null) {
                isHDMI = displayType.contains("hdmi");
            }

            if(dispid == 0) {
                displayType = getActivity().getString(R.string.pluggable_settings_primary_display);
            } else {
                displayType = getActivity().getString(R.string.pluggable_settings_added_dislay);
            }

            if(isHDMI) {
                displayName = displayType + ": " + getActivity().getString(R.string.pluggable_settings_display_hdmi);
            }
            else {
                displayName = displayType + ": " + getActivity().getString(R.string.pluggable_settings_display_ldb);
            }


            if((mCategoryPref[dispid] == null)) {
                addDisplayPreference(dispid);
            }

            if((mCategoryPref[dispid] == null) || (mDisplayModePref[dispid] == null)) {
                Log.w(TAG, "addDisplayPreference init failed");
                return;
            }

            getPreferenceScreen().addPreference(mCategoryPref[dispid]);
            mCategoryPref[dispid].setTitle(displayName);

            String currentDisplayMode = "Display Mode";//mDisplayManager.getDisplayMode(dispid);
            mCurrentMode[dispid] = currentDisplayMode;
            
            ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
            ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();

            revisedEntries.add(mCurrentMode[dispid]);
            revisedValues.add(mCurrentMode[dispid]);

            mDisplayModePref[dispid].setEntries(
                revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            mDisplayModePref[dispid].setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));

            mDisplayModePref[dispid].setValue(String.valueOf(currentDisplayMode));
            mDisplayModePref[dispid].setOnPreferenceChangeListener(this);
            updateDisplayModePreferenceDescription(dispid, currentDisplayMode);

            int entry;
            int entryVal;

            if(isHDMI) {
                if(mXOverScanPref[dispid] == null) {
                    mXOverScanPref[dispid] = new SeekBarPreference(getActivity());
                    String dispStr = "display_xoverscan_" + dispid;
                    mXOverScanPref[dispid].setKey(dispStr);
                    dispStr = getActivity().getString(R.string.pluggable_settings_width_overscan);//"Width OverScan";
                    mXOverScanPref[dispid].setTitle(dispStr);// + " (" + mXOverScanPref[dispid].getProgress() + " Pixels)");
                    int value = 50;
                    mXOverScanPref[dispid].setMax(value);
                    value = mXOverScanVal[dispid];
                    mXOverScanPref[dispid].setProgress(value);

                    mXOverScanPref[dispid].setOnPreferenceChangeListener(this);
                    mCategoryPref[dispid].addPreference(mXOverScanPref[dispid]);
                }
                if(mYOverScanPref[dispid] == null) {
                    mYOverScanPref[dispid] = new SeekBarPreference(getActivity());
                    String dispStr = "display_yoverscan_" + dispid;
                    mYOverScanPref[dispid].setKey(dispStr);
                    dispStr = getActivity().getString(R.string.pluggable_settings_height_overscan);//"Height OverScan";
                    mYOverScanPref[dispid].setTitle(dispStr);// + " (" + mYOverScanPref[dispid].getProgress() + " Pixels)");
                    int value = 50;
                    mYOverScanPref[dispid].setMax(value);
                    value = mYOverScanVal[dispid];
                    mYOverScanPref[dispid].setProgress(value);

                    mYOverScanPref[dispid].setOnPreferenceChangeListener(this);
                    mCategoryPref[dispid].addPreference(mYOverScanPref[dispid]);
                }

		if(mKeepRatePref[dispid] == null) {
		    String dispKey = null;
		    String dispTitle = null;
		    String dispSummary = null;
		    String dispDialogTitle = null;

		    mKeepRatePref[dispid] = new ListPreference(getActivity());

		    dispKey = "display_keeprate_" + dispid;
		    mKeepRatePref[dispid].setKey(dispKey);
		    dispTitle = getActivity().getString(R.string.pluggable_settings_aspect_ratio);//"Aspect Ratio";
		    mKeepRatePref[dispid].setTitle(dispTitle);
		    dispSummary = "aspect ration";
		    mKeepRatePref[dispid].setSummary(dispSummary);
		    dispDialogTitle = getActivity().getString(R.string.pluggable_settings_aspect_ratio);//"Aspect Ratio";
		    mKeepRatePref[dispid].setDialogTitle(dispDialogTitle);

		    mKeepRatePref[dispid].setOnPreferenceChangeListener(this);
//		    mCategoryPref[dispid].addPreference(mKeepRatePref[dispid]);

		    if(dispid == 0) {
			entry = R.array.entries_primary_action_mode;
			entryVal = R.array.entryvalues_primary_action_mode;
		    } else {
			entry = R.array.entries_action_mode;
			entryVal = R.array.entryvalues_action_mode;
		    }
		    if(entry < 0 || entryVal < 0) {
			mKeepRatePref[dispid].setEnabled(false);
		    } else {
			mKeepRatePref[dispid].setEntries(entry);
			mKeepRatePref[dispid].setEntryValues(entryVal);
			int keepRate = 50;//mDisplayManager.getDisplayKeepRate(dispid);
			mKeepRatePref[dispid].setValue(Integer.toHexString(keepRate));
			updateActionModePreferenceDescription(dispid, mKeepRatePref[dispid].getEntry());
		    }
		}

            }
        } else {
            // delete the preferenc entry and value;
            if((mDisplayModePref[dispid] == null) || (mCategoryPref[dispid] == null)) {
                Log.w(TAG, "addDisplayPreference init 3 failed");
                return;
            }

            ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
            ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();              
            mDisplayModePref[dispid].setEntries(
                revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            mDisplayModePref[dispid].setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));     
            mDisplayModePref[dispid].setOnPreferenceChangeListener(this);

            getPreferenceScreen().removePreference(mCategoryPref[dispid]);
        }
    }
    
    private void handleDisplayStateChanged(int dispid, int state) {
        if(DBG) Log.w(TAG, "handleDisplayStateChanged");
        if (dispid < 0) {
            Log.w(TAG, "dispid is no valid");
            return;
        }
        
    }
        
}


