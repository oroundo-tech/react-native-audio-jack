package com.robinpowered.AudioJack;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableNativeMap;

import java.util.Map;
import java.util.HashMap;
import javax.annotation.Nullable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.bluetooth.BluetoothA2dp;

public class AudioJackModule extends ReactContextBaseJavaModule implements LifecycleEventListener {
    private static final String MODULE_NAME = "AudioJack";
    private static final String AUDIO_CHANGED_NOTIFICATION = "AUDIO_CHANGED_NOTIFICATION";
    private static final String IS_PLUGGED_IN = "isPluggedIn";

    private BroadcastReceiver headsetReceiver;

    public AudioJackModule(final ReactApplicationContext reactContext) {
        super(reactContext);
    }

    private void maybeRegisterReceiver() {
        final ReactApplicationContext reactContext = getReactApplicationContext();

        if (headsetReceiver != null) {
            return;
        }

        headsetReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean pluggedIn = false;
                String intentAction = intent.getAction();

                // Set pluggedIn to false when ACTION_AUDIO_BECOMING_NOISY is encountered.
                if (intentAction == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    pluggedIn = false;
                }

                // Check whether connection status of audio jack has been changed.
                else if (intentAction == AudioManager.ACTION_HEADSET_PLUG) {
                    if (intent.getIntExtra("state", -1) == 1) {
                        pluggedIn = true;
                    }
                }

                // Check whether connection status of bluetooth audio device has been changed.
                else if (intentAction == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
                    if (intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1) == BluetoothA2dp.STATE_CONNECTED) {
                        pluggedIn = true;
                    }
                }

                WritableNativeMap data = new WritableNativeMap();
                data.putBoolean(IS_PLUGGED_IN, pluggedIn);

                if (reactContext.hasActiveCatalystInstance()) {
                    reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(AUDIO_CHANGED_NOTIFICATION,
                            data);
                }
            }
        };

        IntentFilter headsetFilter = new IntentFilter();
        headsetFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        headsetFilter.addAction(AudioManager.ACTION_HEADSET_PLUG);
        headsetFilter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        reactContext.registerReceiver(headsetReceiver, headsetFilter);
    }

    private void maybeUnregisterReceiver() {
        if (headsetReceiver == null) {
            return;
        }
        getReactApplicationContext().unregisterReceiver(headsetReceiver);
        headsetReceiver = null;
    }

    private boolean isHeadsetPluggedIn() {
        AudioManager audioManager = (AudioManager) getReactApplicationContext().getSystemService(Context.AUDIO_SERVICE);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return audioManager.isWiredHeadsetOn() || audioManager.isBluetoothA2dpOn();
        } else {
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (int i = 0; i < devices.length; i++) {
                AudioDeviceInfo device = devices[i];
                switch(device.getType()) {
                    case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                    case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                    case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                    case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                        return true;
                }
            }
        }

        return false;
    }

    @Override
    public @Nullable Map<String, Object> getConstants() {
        HashMap<String, Object> constants = new HashMap<>();
        constants.put(AUDIO_CHANGED_NOTIFICATION, AUDIO_CHANGED_NOTIFICATION);
        return constants;
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @ReactMethod
    public void isPluggedIn(final Promise promise) {
        promise.resolve(isHeadsetPluggedIn());
    }

    @Override
    public void initialize() {
        getReactApplicationContext().addLifecycleEventListener(this);
        maybeRegisterReceiver();
    }

    @Override
    public void onHostResume() {
    }

    @Override
    public void onHostPause() {
    }

    @Override
    public void onHostDestroy() {
        maybeUnregisterReceiver();
    }
}
