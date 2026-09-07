package com.m57.hermescontrol.ui.chat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpeechInputHelperTest {
    @Before
    fun setUp() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setAction(any()) } answers { self as Intent }
    }

    @After
    fun tearDown() {
        unmockkConstructor(Intent::class)
    }

    @Test
    fun `provider activity makes speech input available`() {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()
        val provider = mockk<ResolveInfo>()
        every { context.packageManager } returns packageManager
        every { packageManager.queryIntentActivities(any<Intent>(), any<Int>()) } returns listOf(provider)

        assertTrue(
            SpeechInputHelper.isSpeechInputAvailable(
                context = context,
                sdkInt = Build.VERSION_CODES.S,
                isRecognizerServiceAvailable = { false },
            ),
        )
    }

    @Test
    fun `speech input is unavailable when provider and recognizer service are absent`() {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()
        every { context.packageManager } returns packageManager
        every { packageManager.queryIntentActivities(any<Intent>(), any<Int>()) } returns emptyList()

        assertFalse(
            SpeechInputHelper.isSpeechInputAvailable(
                context = context,
                sdkInt = Build.VERSION_CODES.S,
                isRecognizerServiceAvailable = { false },
            ),
        )
    }

    @Test
    fun `malformed and failed resolver responses fall back to recognizer service`() {
        val malformedContext = mockk<Context>()
        val malformedPackageManager = mockk<PackageManager>()
        every { malformedContext.packageManager } returns malformedPackageManager
        @Suppress("UNCHECKED_CAST")
        val malformedResponse = listOf(null) as List<ResolveInfo>
        every {
            malformedPackageManager.queryIntentActivities(any<Intent>(), any<Int>())
        } returns malformedResponse

        assertFalse(
            SpeechInputHelper.isSpeechInputAvailable(
                context = malformedContext,
                sdkInt = Build.VERSION_CODES.S,
                isRecognizerServiceAvailable = { false },
            ),
        )

        val failedContext = mockk<Context>()
        every { failedContext.packageManager } throws IllegalStateException("broken resolver")
        assertTrue(
            SpeechInputHelper.isSpeechInputAvailable(
                context = failedContext,
                sdkInt = Build.VERSION_CODES.S,
                isRecognizerServiceAvailable = { true },
            ),
        )
    }

    @Test
    fun `Android 13 and newer use typed resolver flags`() {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()
        val provider = mockk<ResolveInfo>()
        every { context.packageManager } returns packageManager

        assertTrue(
            SpeechInputHelper.isSpeechInputAvailable(
                context = context,
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                queryLegacy = { _, _ -> error("legacy resolver called") },
                queryModern = { actualPackageManager, _ ->
                    assertTrue(actualPackageManager === packageManager)
                    listOf(provider)
                },
                isRecognizerServiceAvailable = { false },
            ),
        )
    }
}
