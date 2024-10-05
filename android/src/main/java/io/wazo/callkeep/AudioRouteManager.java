package io.wazo.callkeep;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.telecom.Connection;
import android.telecom.CallAudioState;
import android.util.Log;

import java.util.List;

public class AudioRouteManager {
    private static final String TAG = "AudioRouteManager";
    private final AudioManager audioManager;
    private final Context context;

    public AudioRouteManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public boolean setDefaultAudioRoute(String uuid) {
        return setAudioRoute(uuid, "speaker");
    }

    public boolean setAudioRoute(String uuid, String audioRoute) {
        Log.d(TAG, "Setting audio route to: " + audioRoute + " for call: " + uuid);
        try {
            Connection connection = VoiceConnectionService.getConnection(uuid);
            if (connection == null) {
                Log.e(TAG, "No active connection found for UUID: " + uuid);
                return false;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return setAudioRouteNew(connection, audioRoute);
            } else {
                return setAudioRouteLegacy(connection, audioRoute);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting audio route", e);
            return false;
        }
    }

    private boolean setAudioRouteNew(Connection connection, String audioRoute) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List<AudioDeviceInfo> availableDevices = audioManager.getAvailableCommunicationDevices();
            AudioDeviceInfo selectedDevice = findAudioDevice(availableDevices, audioRoute);

            if (selectedDevice != null) {
                connection.setAudioRoute(getAudioRouteFromDevice(selectedDevice));
                return true;
            } else {
                Log.w(TAG, "No suitable device found for " + audioRoute + ". Defaulting to speaker.");
                connection.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
                return true;
            }
        }
        return false;
    }

    private boolean setAudioRouteLegacy(Connection connection, String audioRoute) {
        int route;
        switch (audioRoute.toLowerCase()) {
            case "bluetooth":
                route = CallAudioState.ROUTE_BLUETOOTH;
                audioManager.startBluetoothSco();
                audioManager.setBluetoothScoOn(true);
                break;
            case "headset":
                route = CallAudioState.ROUTE_WIRED_HEADSET;
                audioManager.stopBluetoothSco();
                audioManager.setBluetoothScoOn(false);
                audioManager.setSpeakerphoneOn(false);
                break;
            case "earpiece":
            case "phone":
                route = CallAudioState.ROUTE_EARPIECE;
                audioManager.stopBluetoothSco();
                audioManager.setBluetoothScoOn(false);
                audioManager.setSpeakerphoneOn(false);
                break;
            case "speaker":
            default:
                route = CallAudioState.ROUTE_SPEAKER;
                audioManager.stopBluetoothSco();
                audioManager.setBluetoothScoOn(false);
                audioManager.setSpeakerphoneOn(true);
                break;
        }
        connection.setAudioRoute(route);
        return true;
    }

    private AudioDeviceInfo findAudioDevice(List<AudioDeviceInfo> devices, String audioRoute) {
        for (AudioDeviceInfo device : devices) {
            if (isMatchingAudioDevice(device, audioRoute)) {
                return device;
            }
        }
        // If requested device not found, default to speaker
        return findSpeakerDevice(devices);
    }

    private boolean isMatchingAudioDevice(AudioDeviceInfo device, String audioRoute) {
        switch (audioRoute.toLowerCase()) {
            case "bluetooth":
                return device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                       device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
            case "headset":
                return device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                       device.getType() == AudioDeviceInfo.TYPE_USB_HEADSET;
            case "earpiece":
            case "phone":
                return device.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE;
            case "speaker":
            default:
                return device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
        }
    }

    private AudioDeviceInfo findSpeakerDevice(List<AudioDeviceInfo> devices) {
         for (AudioDeviceInfo device : devices) {
             if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                 return device;
             }
         }
         return null;
    }

    private int getAudioRouteFromDevice(AudioDeviceInfo device) {
         switch (device.getType()) {
             case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
             case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                 return CallAudioState.ROUTE_BLUETOOTH;
             case AudioDeviceInfo.TYPE_WIRED_HEADSET:
             case AudioDeviceInfo.TYPE_USB_HEADSET:
                 return CallAudioState.ROUTE_WIRED_HEADSET;
             case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                 return CallAudioState.ROUTE_EARPIECE;
             case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
             default:
                 return CallAudioState.ROUTE_SPEAKER;
         }
    }

    public boolean handleDisconnection(String uuid) {
        return setAudioRoute(uuid, "speaker");
    }

    public boolean clearAudioRoute(String uuid) {
        return setAudioRoute(uuid, "speaker");
    }
}
