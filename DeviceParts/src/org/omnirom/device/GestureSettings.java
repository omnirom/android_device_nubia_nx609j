/*
* Copyright (C) 2017 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package org.omnirom.device;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v14.preference.PreferenceFragment;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.TwoStatePreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.util.Log;
import android.os.UserHandle;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class GestureSettings extends PreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String KEY_PROXI_SWITCH = "proxi";
    public static final String KEY_OFF_SCREEN_GESTURE_FEEDBACK_SWITCH = "off_screen_gesture_feedback";
    public static final String KEY_CIRCLE_APP = "circle_gesture_app";
    public static final String KEY_ARROW_APP = "arrow_gesture_app";
    public static final String KEY_SWIPE_APP = "swipe_gesture_app";
    public static final String KEY_DOUBLE_TAP_TO_WAKE_SWITCH = "double_tap_to_wake";
    public static final String KEY_ENABLE_GESTURES = "enable_gestures";

    public static final String DEVICE_GESTURE_MAPPING_0 = "device_gesture_mapping_0_0";
    public static final String DEVICE_GESTURE_MAPPING_1 = "device_gesture_mapping_1_0";
    public static final String DEVICE_GESTURE_MAPPING_2 = "device_gesture_mapping_2_0";
    public static final String DEVICE_GESTURE_MAPPING_3 = "device_gesture_mapping_3_0";

    private TwoStatePreference mProxiSwitch;
    private TwoStatePreference mOffscreenGestureFeedbackSwitch;
    private AppSelectListPreference mCircleApp;
    private AppSelectListPreference mArrowApp;
    private AppSelectListPreference mSwipeApp;
    private TwoStatePreference mDoubleTapToWake;
    private TwoStatePreference mEnableGestures;
    private List<AppSelectListPreference.PackageItem> mInstalledPackages = new LinkedList<AppSelectListPreference.PackageItem>();
    private PackageManager mPm;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.gesture_settings, rootKey);
        mPm = getContext().getPackageManager();

        mEnableGestures = (TwoStatePreference) findPreference(KEY_ENABLE_GESTURES);
        mEnableGestures.setEnabled(OffscreenGesturesSwitch.isSupported());
        mEnableGestures.setChecked(OffscreenGesturesSwitch.isCurrentlyEnabled(this.getContext()));
        mEnableGestures.setOnPreferenceChangeListener(new OffscreenGesturesSwitch());

        mDoubleTapToWake = (TwoStatePreference) findPreference(KEY_DOUBLE_TAP_TO_WAKE_SWITCH);
        mDoubleTapToWake.setChecked(Settings.System.getInt(getContext().getContentResolver(),
                DEVICE_GESTURE_MAPPING_3, 0) != 0);

        mProxiSwitch = (TwoStatePreference) findPreference(KEY_PROXI_SWITCH);
        mProxiSwitch.setChecked(Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.OMNI_DEVICE_PROXI_CHECK_ENABLED, 1) != 0);

        mOffscreenGestureFeedbackSwitch = (TwoStatePreference) findPreference(KEY_OFF_SCREEN_GESTURE_FEEDBACK_SWITCH);
        mOffscreenGestureFeedbackSwitch.setChecked(Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.OMNI_DEVICE_GESTURE_FEEDBACK_ENABLED, 0) != 0);

        mCircleApp = (AppSelectListPreference) findPreference(KEY_CIRCLE_APP);
        String value = Settings.System.getString(getContext().getContentResolver(), DEVICE_GESTURE_MAPPING_0);
        mCircleApp.setValue(value);
        mCircleApp.setOnPreferenceChangeListener(this);

        mArrowApp = (AppSelectListPreference) findPreference(KEY_ARROW_APP);
        value = Settings.System.getString(getContext().getContentResolver(), DEVICE_GESTURE_MAPPING_1);
        mArrowApp.setValue(value);
        mArrowApp.setOnPreferenceChangeListener(this);

        mSwipeApp = (AppSelectListPreference) findPreference(KEY_SWIPE_APP);
        value = Settings.System.getString(getContext().getContentResolver(), DEVICE_GESTURE_MAPPING_2);
        mSwipeApp.setValue(value);
        mSwipeApp.setOnPreferenceChangeListener(this);

        new FetchPackageInformationTask().execute();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mDoubleTapToWake) {
            Settings.System.putInt(getContext().getContentResolver(),
                    DEVICE_GESTURE_MAPPING_3, mDoubleTapToWake.isChecked() ? 1 : 0);
            return true;
        }
        if (preference == mProxiSwitch) {
            Settings.System.putInt(getContext().getContentResolver(),
                    Settings.System.OMNI_DEVICE_PROXI_CHECK_ENABLED, mProxiSwitch.isChecked() ? 1 : 0);
            return true;
        }
        if (preference == mOffscreenGestureFeedbackSwitch) {
            Settings.System.putInt(getContext().getContentResolver(),
                    Settings.System.OMNI_DEVICE_GESTURE_FEEDBACK_ENABLED, mOffscreenGestureFeedbackSwitch.isChecked() ? 1 : 0);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mCircleApp) {
            String value = (String) newValue;
            Settings.System.putString(getContext().getContentResolver(), DEVICE_GESTURE_MAPPING_0, value);
        } else if (preference == mArrowApp) {
            String value = (String) newValue;
            Settings.System.putString(getContext().getContentResolver(), DEVICE_GESTURE_MAPPING_1, value);
        } else if (preference == mSwipeApp) {
            String value = (String) newValue;
            Settings.System.putString(getContext().getContentResolver(), DEVICE_GESTURE_MAPPING_2, value);
        }
        return true;
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (!(preference instanceof AppSelectListPreference)) {
            super.onDisplayPreferenceDialog(preference);
            return;
        }
        DialogFragment fragment =
                AppSelectListPreference.AppSelectListPreferenceDialogFragment
                        .newInstance(preference.getKey());
        fragment.setTargetFragment(this, 0);
        fragment.show(getFragmentManager(), "dialog_preference");
    }

    private void loadInstalledPackages() {
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> installedAppsInfo = mPm.queryIntentActivities(mainIntent, 0);

        for (ResolveInfo info : installedAppsInfo) {
            ActivityInfo activity = info.activityInfo;
            ApplicationInfo appInfo = activity.applicationInfo;
            ComponentName componentName = new ComponentName(appInfo.packageName, activity.name);
            CharSequence label = null;
            try {
                label = activity.loadLabel(mPm);
            } catch (Exception e) {
            }
            if (label != null) {
                final AppSelectListPreference.PackageItem item = new AppSelectListPreference.PackageItem(activity.loadLabel(mPm), 0, componentName);
                mInstalledPackages.add(item);
            }
        }
        Collections.sort(mInstalledPackages);
    }

    private class FetchPackageInformationTask extends AsyncTask<Void, Void, Void> {
        public FetchPackageInformationTask() {
        }

        @Override
        protected Void doInBackground(Void... params) {
            loadInstalledPackages();
            return null;
        }

        @Override
        protected void onPostExecute(Void feed) {
            mSwipeApp.setPackageList(mInstalledPackages);
            mCircleApp.setPackageList(mInstalledPackages);
            mArrowApp.setPackageList(mInstalledPackages);
        }
    }
}
