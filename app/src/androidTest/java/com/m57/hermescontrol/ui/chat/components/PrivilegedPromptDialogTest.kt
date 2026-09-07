package com.m57.hermescontrol.ui.chat.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.m57.hermescontrol.data.ws.PrivilegedRequestBinding
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PrivilegedPromptDialogTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sudoReplacementClearsPasswordAndCannotSubmitOldValue() {
        var binding by mutableStateOf(binding("sudo-1", "session-1", "profile-1", 1))
        val submitted = mutableListOf<String>()
        composeTestRule.setContent {
            SudoPromptDialog(
                binding = binding,
                onConfirm = submitted::add,
                onCancel = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithTag("sudo_password_input").performTextInput("old-password")
        composeTestRule.runOnIdle {
            binding = binding("sudo-2", "session-2", "profile-2", 2)
        }

        composeTestRule.onNodeWithTag("sudo_password_input").assertTextEquals("")
        composeTestRule.onNodeWithTag("sudo_send_button").assertIsNotEnabled()
        composeTestRule.runOnIdle { assertTrue(submitted.isEmpty()) }
    }

    @Test
    fun secretReplacementClearsValueAndCannotSubmitOldValue() {
        var binding by mutableStateOf(binding("secret-1", "session-1", "profile-1", 1))
        val submitted = mutableListOf<String>()
        composeTestRule.setContent {
            SecretPromptDialog(
                binding = binding,
                onConfirm = submitted::add,
                onCancel = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithTag("secret_value_input").performTextInput("old-secret")
        composeTestRule.runOnIdle {
            binding = binding("secret-2", "session-2", "profile-2", 2)
        }

        composeTestRule.onNodeWithTag("secret_value_input").assertTextEquals("")
        composeTestRule.onNodeWithTag("secret_send_button").assertIsNotEnabled()
        composeTestRule.runOnIdle { assertTrue(submitted.isEmpty()) }
    }

    private fun binding(
        requestId: String,
        runtimeSessionId: String,
        profileId: String,
        connectionGeneration: Int,
    ) = PrivilegedRequestBinding(
        requestId = requestId,
        runtimeSessionId = runtimeSessionId,
        profileId = profileId,
        connectionGeneration = connectionGeneration,
    )
}
