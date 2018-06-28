/*
* Copyright (C) 2016 The OmniROM Project
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
import android.content.res.Resources;
import android.content.Intent;
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

public class DeviceSettings extends PreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String KEY_VIBSTRENGTH = "vib_strength";
    public static final String KEY_DOUBLE_TAP_TO_WAKE_SWITCH = "double_tap_to_wake";
    private static final String KEY_PROXI_SWITCH = "proxi";
    private static final String KEY_SLIDER_MODE_TOP = "slider_mode_top";
    private static final String KEY_LED_EFFECT = "led_effect";
    public static final String SLIDER_DEFAULT_VALUE = "0";
    private static final String LED_EFFECT_FILE = "/sys/class/leds/aw22xxx_led/effect";

    private VibratorStrengthPreference mVibratorStrength;
    private TwoStatePreference mDoubleTapToWake;
    private TwoStatePreference mProxiSwitch;
    private ListPreference mSliderModeTop;
    private ListPreference mLedEffect;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.main, rootKey);

        mDoubleTapToWake = (TwoStatePreference) findPreference(KEY_DOUBLE_TAP_TO_WAKE_SWITCH);
        mDoubleTapToWake.setEnabled(DoubleTapToWake.isSupported());
        mDoubleTapToWake.setChecked(DoubleTapToWake.isCurrentlyEnabled(this.getContext()));
        mDoubleTapToWake.setOnPreferenceChangeListener(new DoubleTapToWake());

        mProxiSwitch = (TwoStatePreference) findPreference(KEY_PROXI_SWITCH);
        mProxiSwitch.setChecked(Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.DEVICE_PROXI_CHECK_ENABLED, 1) != 0);

        mVibratorStrength = (VibratorStrengthPreference) findPreference(KEY_VIBSTRENGTH);
        if (mVibratorStrength != null) {
            mVibratorStrength.setEnabled(VibratorStrengthPreference.isSupported());
        }
        mSliderModeTop = (ListPreference) findPreference(KEY_SLIDER_MODE_TOP);
        mSliderModeTop.setOnPreferenceChangeListener(this);
        int sliderModeTop = getSliderAction();
        int valueIndex = mSliderModeTop.findIndexOfValue(String.valueOf(sliderModeTop));
        mSliderModeTop.setValueIndex(valueIndex);
        mSliderModeTop.setSummary(mSliderModeTop.getEntries()[valueIndex]);

        mLedEffect = (ListPreference) findPreference(KEY_LED_EFFECT);
        mLedEffect.setOnPreferenceChangeListener(this);
        // effect = 0x0b
        String currentEffect = Utils.getFileValue(LED_EFFECT_FILE, "effect = 0x00");
        String effect = currentEffect.split(" = ")[1];
        int ledEffect = Integer.decode(effect);
        valueIndex = mLedEffect.findIndexOfValue(String.valueOf(ledEffect));
        mLedEffect.setValueIndex(valueIndex);
        mLedEffect.setSummary(mLedEffect.getEntries()[valueIndex]);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mProxiSwitch) {
            Settings.System.putInt(getContext().getContentResolver(),
                    Settings.System.DEVICE_PROXI_CHECK_ENABLED, mProxiSwitch.isChecked() ? 1 : 0);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mSliderModeTop) {
            String value = (String) newValue;
            int sliderMode = Integer.valueOf(value);
            setSliderAction(sliderMode);
            int valueIndex = mSliderModeTop.findIndexOfValue(value);
            mSliderModeTop.setSummary(mSliderModeTop.getEntries()[valueIndex]);
        } else if (preference == mLedEffect) {
            String value = (String) newValue;
            Utils.writeValue(LED_EFFECT_FILE, value);
            int valueIndex = mLedEffect.findIndexOfValue(value);
            mLedEffect.setSummary(mLedEffect.getEntries()[valueIndex]);
        }
        return true;
    }

    private int getSliderAction() {
        String value = Settings.System.getString(getContext().getContentResolver(),
                    Settings.System.BUTTON_EXTRA_KEY_MAPPING);
        final String defaultValue = DeviceSettings.SLIDER_DEFAULT_VALUE;

        if (value == null) {
            value = defaultValue;
        }
        return Integer.valueOf(value);
    }

    private void setSliderAction(int action) {
        Settings.System.putString(getContext().getContentResolver(),
                    Settings.System.BUTTON_EXTRA_KEY_MAPPING, String.valueOf(action));
    }
}
