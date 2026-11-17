package com.example.domawear

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager

class AudioHelper(private val context: Context) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Retorna true se existir uma saída de áudio do tipo informado
    // (ex.: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ou TYPE_BLUETOOTH_A2DP)
    fun audioOutputAvailable(type: Int): Boolean {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
            return false
        }
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type == type }
    }
}
