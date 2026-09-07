package com.m57.hermescontrol.ui.chat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

object SpeechInputHelper {
    /** Returns whether Android exposes either an activity or service for speech input. */
    fun isSpeechInputAvailable(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT,
        queryLegacy: (PackageManager, Intent) -> List<ResolveInfo?> = { packageManager, intent ->
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        },
        queryModern: (PackageManager, Intent) -> List<ResolveInfo?> = { packageManager, intent ->
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        },
        isRecognizerServiceAvailable: (Context) -> Boolean = { candidate ->
            runCatching { SpeechRecognizer.isRecognitionAvailable(candidate) }.getOrDefault(false)
        },
    ): Boolean {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val activities: List<ResolveInfo?> =
            runCatching {
                if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                    queryModern(context.packageManager, intent)
                } else {
                    queryLegacy(context.packageManager, intent)
                }
            }.getOrDefault(emptyList())

        return activities.any { it != null } || isRecognizerServiceAvailable(context)
    }
}
