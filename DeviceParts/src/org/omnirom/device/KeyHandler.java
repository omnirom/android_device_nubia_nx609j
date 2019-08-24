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

import android.app.ActivityManagerNative;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.IAudioService;
import android.media.AudioManager;
import android.media.session.MediaSessionLegacyHelper;
import android.net.Uri;
import android.text.TextUtils;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.HapticFeedbackConstants;
import android.view.WindowManagerGlobal;

import com.android.internal.util.omni.DeviceKeyHandler;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.omni.OmniUtils;
import com.android.internal.statusbar.IStatusBarService;

import org.omnirom.omnilib.utils.OmniVibe;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = "KeyHandler";
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_SENSOR = false;

    private static final int BATCH_LATENCY_IN_MS = 100;
    private static final int GESTURE_WAKELOCK_DURATION = 2000;
    private static final int MIN_PULSE_INTERVAL_MS = 2500;
    private static final String DOZE_INTENT = "com.android.systemui.doze.pulse";
    private static final int HANDWAVE_MAX_DELTA_MS = 1000;
    private static final int POCKET_MIN_DELTA_MS = 5000;
    private static final String GAME_SWITCH_STATUS = "/sys/devices/soc/soc:gpio_keys/GamekeyStatus";

    private static final int KEY_GAME_SWITCH = 249;     /*nubia add for game switch key*/
    private static final int KEY_DOUBLE_TAP = 68; // F10
    private static final int GESTURE_SWIPE_SCANCODE = 251;
    private static final int GESTURE_CIRCLE_SCANCODE = 250;
    private static final int GESTURE_ARROW_SCANCODE = 252;

    private static final int[] sSupportedGestures = new int[]{
        KEY_GAME_SWITCH,
        KEY_DOUBLE_TAP,
        GESTURE_SWIPE_SCANCODE,
        GESTURE_CIRCLE_SCANCODE,
        GESTURE_ARROW_SCANCODE
    };

    private static final int[] sHandledGestures = new int[]{
        KEY_GAME_SWITCH
    };

    private static final int[] sProxiCheckedGestures = new int[]{
        KEY_DOUBLE_TAP,
        GESTURE_SWIPE_SCANCODE,
        GESTURE_CIRCLE_SCANCODE,
        GESTURE_ARROW_SCANCODE
    };

    protected final Context mContext;
    private final PowerManager mPowerManager;
    private WakeLock mGestureWakeLock;
    private Handler mHandler = new Handler();
    private SettingsObserver mSettingsObserver;
    private final NotificationManager mNoMan;
    private final AudioManager mAudioManager;
    private SensorManager mSensorManager;
    private boolean mProxyIsNear;
    private boolean mUseProxiCheck;
    private Sensor mTiltSensor;
    private boolean mUseTiltCheck;
    private boolean mProxyWasNear;
    private long mProxySensorTimestamp;
    private boolean mUseWaveCheck;
    private Sensor mPocketSensor;
    private boolean mUsePocketCheck;
    private boolean mDoubleTapEnabled;

    private SensorEventListener mProximitySensor = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            mProxyIsNear = event.values[0] < mPocketSensor.getMaximumRange();
            if (DEBUG_SENSOR) Log.i(TAG, "mProxyIsNear = " + mProxyIsNear + " mProxyWasNear = " + mProxyWasNear);

            if (mUseWaveCheck || mUsePocketCheck) {
                if (mProxyWasNear && !mProxyIsNear) {
                    long delta = SystemClock.elapsedRealtime() - mProxySensorTimestamp;
                    if (DEBUG_SENSOR) Log.i(TAG, "delta = " + delta);
                    if (mUseWaveCheck && delta < HANDWAVE_MAX_DELTA_MS) {
                        launchDozePulse();
                    }
                    if (mUsePocketCheck && delta > POCKET_MIN_DELTA_MS) {
                        launchDozePulse();
                    }
                }
                mProxySensorTimestamp = SystemClock.elapsedRealtime();
                mProxyWasNear = mProxyIsNear;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private SensorEventListener mTiltSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values[0] == 1) {
                launchDozePulse();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.OMNI_DEVICE_PROXI_CHECK_ENABLED),
                    false, this);
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.OMNI_DEVICE_FEATURE_SETTINGS),
                    false, this);
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    GestureSettings.DEVICE_GESTURE_MAPPING_3),
                    false, this);
            update();
            updateDozeSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(Settings.System.getUriFor(
                    Settings.System.OMNI_DEVICE_FEATURE_SETTINGS))){
                updateDozeSettings();
                return;
            }
            update();
        }

        public void update() {
            mUseProxiCheck = Settings.System.getIntForUser(
                    mContext.getContentResolver(), Settings.System.OMNI_DEVICE_PROXI_CHECK_ENABLED, 1,
                    UserHandle.USER_CURRENT) == 1;
            mDoubleTapEnabled = Settings.System.getIntForUser(
                    mContext.getContentResolver(), GestureSettings.DEVICE_GESTURE_MAPPING_3, 0,
                    UserHandle.USER_CURRENT) != 0;
        }
    }

    private BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
         @Override
         public void onReceive(Context context, Intent intent) {
             if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                 onDisplayOn();
             } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                 onDisplayOff();
             }
         }
    };

    public KeyHandler(Context context) {
        mContext = context;
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mGestureWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "GestureWakeLock");
        mSettingsObserver = new SettingsObserver(mHandler);
        mSettingsObserver.observe();
        mNoMan = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mPocketSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mTiltSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_TILT_DETECTOR);
        IntentFilter screenStateFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mScreenStateReceiver, screenStateFilter);
    }

    @Override
    public boolean handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }
        if (DEBUG) Log.i(TAG, "scanCode=" + event.getScanCode());

        boolean isKeySupported = ArrayUtils.contains(sHandledGestures, event.getScanCode());
        if (isKeySupported) {
            if (DEBUG) Log.i(TAG, "scanCode=" + event.getScanCode());
            switch(event.getScanCode()) {
                case KEY_GAME_SWITCH:
                    if (DEBUG) Log.i(TAG, "KEY_GAME_SWITCH");
                    if (getGameModeSwitchStatus()) {
                        doHandleSliderAction(true);
                    } else {
                        doHandleSliderAction(false);
                    }
                    return true;
            }
        }
        return isKeySupported;
    }

    @Override
    public boolean canHandleKeyEvent(KeyEvent event) {
       return ArrayUtils.contains(sSupportedGestures, event.getScanCode());
    }

    @Override
    public boolean isDisabledKeyEvent(KeyEvent event) {
        boolean isProxyCheckRequired = mUseProxiCheck &&
                ArrayUtils.contains(sProxiCheckedGestures, event.getScanCode());
        if (mProxyIsNear && isProxyCheckRequired) {
            if (DEBUG) Log.i(TAG, "isDisabledKeyEvent: blocked by proxi sensor - scanCode=" + event.getScanCode());
            return true;
        }
        return false;
    }

    @Override
    public boolean isCameraLaunchEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }
        String value = getGestureValueForScanCode(event.getScanCode());
        return !TextUtils.isEmpty(value) && value.equals(AppSelectListPreference.CAMERA_ENTRY);
    }

    @Override
    public boolean isWakeEvent(KeyEvent event){
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }
        String value = getGestureValueForScanCode(event.getScanCode());
        if (!TextUtils.isEmpty(value) && value.equals(AppSelectListPreference.WAKE_ENTRY)) {
            if (DEBUG) Log.i(TAG, "isWakeEvent " + event.getScanCode() + value);
            return true;
        }
        return event.getScanCode() == KEY_DOUBLE_TAP && mDoubleTapEnabled;
    }

    @Override
    public Intent isActivityLaunchEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return null;
        }
        String value = getGestureValueForScanCode(event.getScanCode());
        if (!TextUtils.isEmpty(value) && !value.equals(AppSelectListPreference.DISABLED_ENTRY)) {
            if (DEBUG) Log.i(TAG, "isActivityLaunchEvent " + event.getScanCode() + value);
            if (!launchSpecialActions(value)) {
                vibe();
                Intent intent = createIntent(value);
                return intent;
            }
        }
        return null;
    }

    private void onDisplayOn() {
        if (DEBUG) Log.i(TAG, "Display on");
        if (enableProxiSensor()) {
            mSensorManager.unregisterListener(mProximitySensor, mPocketSensor);
        }
        if (mUseTiltCheck) {
            mSensorManager.unregisterListener(mTiltSensorListener, mTiltSensor);
        }
    }

    private void onDisplayOff() {
        if (DEBUG) Log.i(TAG, "Display off");
        if (enableProxiSensor()) {
            mProxyWasNear = false;
            mSensorManager.registerListener(mProximitySensor, mPocketSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            mProxySensorTimestamp = SystemClock.elapsedRealtime();
        }
        if (mUseTiltCheck) {
            mSensorManager.registerListener(mTiltSensorListener, mTiltSensor,
                    SensorManager.SENSOR_DELAY_NORMAL, BATCH_LATENCY_IN_MS * 1000);
        }
    }

    private void launchDozePulse() {
        if (DEBUG) Log.i(TAG, "Doze pulse");
        mContext.sendBroadcastAsUser(new Intent(DOZE_INTENT),
                new UserHandle(UserHandle.USER_CURRENT));
    }

    private boolean enableProxiSensor() {
        return mUsePocketCheck || mUseWaveCheck || mUseProxiCheck;
    }

    private void updateDozeSettings() {
        String value = Settings.System.getStringForUser(mContext.getContentResolver(),
                    Settings.System.OMNI_DEVICE_FEATURE_SETTINGS,
                    UserHandle.USER_CURRENT);
        if (DEBUG) Log.i(TAG, "Doze settings = " + value);
        if (!TextUtils.isEmpty(value)) {
            String[] parts = value.split(":");
            if (parts.length == 3) {
                mUseWaveCheck = Boolean.valueOf(parts[0]);
                mUsePocketCheck = Boolean.valueOf(parts[1]);
                mUseTiltCheck = Boolean.valueOf(parts[2]);
            }
        }
    }

    private void vibe(){
        boolean doVibrate = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.OMNI_DEVICE_GESTURE_FEEDBACK_ENABLED, 0,
                UserHandle.USER_CURRENT) == 1;
        if (doVibrate) {
            OmniVibe.performHapticFeedbackLw(HapticFeedbackConstants.LONG_PRESS, false, mContext);
        }
    }

    private int getSliderAction() {
        String value = Settings.System.getStringForUser(mContext.getContentResolver(),
                    Settings.System.OMNI_BUTTON_EXTRA_KEY_MAPPING,
                    UserHandle.USER_CURRENT);
        final String defaultValue = DeviceSettings.SLIDER_DEFAULT_VALUE;

        if (value == null) {
            value = defaultValue;
        }
        return Integer.valueOf(value);
    }

    private void doHandleSliderAction(boolean on) {
        if (!on) {
            mNoMan.setZenMode(Global.ZEN_MODE_OFF, null, TAG);
            mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
        } else {
            int action = getSliderAction();
            if (action == 0) {
                mNoMan.setZenMode(Global.ZEN_MODE_OFF, null, TAG);
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
            } else if (action == 1) {
                mNoMan.setZenMode(Global.ZEN_MODE_OFF, null, TAG);
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_VIBRATE);
            } else if (action == 2) {
                mNoMan.setZenMode(Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS, null, TAG);
            } else if (action == 3) {
                mNoMan.setZenMode(Global.ZEN_MODE_ALARMS, null, TAG);
            } else if (action == 4) {
                mNoMan.setZenMode(Global.ZEN_MODE_NO_INTERRUPTIONS, null, TAG);
            }
        }
    }

    private boolean getGameModeSwitchStatus() {
        return Utils.getFileValueAsBoolean(GAME_SWITCH_STATUS, false);
    }

    private Intent createIntent(String value) {
        ComponentName componentName = ComponentName.unflattenFromString(value);
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        intent.setComponent(componentName);
        return intent;
    }

    private boolean launchSpecialActions(String value) {
        if (value.equals(AppSelectListPreference.TORCH_ENTRY)) {
            mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
            IStatusBarService service = getStatusBarService();
            if (service != null) {
                try {
                    vibe();
                    service.toggleCameraFlash();
                } catch (RemoteException e) {
                    // do nothing.
                }
            }
            return true;
        }
        return false;
    }

    private String getGestureValueForScanCode(int scanCode) {
        switch(scanCode) {
            case GESTURE_CIRCLE_SCANCODE:
                return Settings.System.getStringForUser(mContext.getContentResolver(),
                    GestureSettings.DEVICE_GESTURE_MAPPING_0, UserHandle.USER_CURRENT);
            case GESTURE_ARROW_SCANCODE:
                return Settings.System.getStringForUser(mContext.getContentResolver(),
                    GestureSettings.DEVICE_GESTURE_MAPPING_1, UserHandle.USER_CURRENT);
            case GESTURE_SWIPE_SCANCODE:
                return Settings.System.getStringForUser(mContext.getContentResolver(),
                    GestureSettings.DEVICE_GESTURE_MAPPING_2, UserHandle.USER_CURRENT);
        }
        return null;
    }

    IStatusBarService getStatusBarService() {
        return IStatusBarService.Stub.asInterface(ServiceManager.getService("statusbar"));
    }
}
