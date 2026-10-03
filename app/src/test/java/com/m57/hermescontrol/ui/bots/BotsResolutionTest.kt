package com.m57.hermescontrol.ui.bots

import com.m57.hermescontrol.data.bots.BotResolutionSource
import com.m57.hermescontrol.data.bots.BotSessionResolution
import com.m57.hermescontrol.data.model.CanonicalSessionInfo
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.data.model.ProfilesResponse
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.HermesApiService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * The Bots roster resolves every bot that the server left without a canonical
 * session through the injected async resolver, fenced on the connection
 * profile that produced the roster.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BotsResolutionTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var api: HermesApiService

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(ApiClient)
        api = mockk(relaxed = true)
        every { ApiClient.hermesApi } returns api
        coEvery {
            api.getProfiles()
        } returns
            Response.success(
                ProfilesResponse(
                    listOf(
                        ProfileInfo("alpha", canonical_session = CanonicalSessionInfo("root")),
                        ProfileInfo("beta"),
                    ),
                ),
            )
    }

    @After
    fun tearDown() {
        unmockkObject(ApiClient)
        Dispatchers.resetMain()
    }

    private fun viewModel(
        selectedProfile: () -> String? = { "connection-a" },
        resolver: suspend (ProfileInfo) -> BotSessionResolution = { BotSessionResolution.CreateUnsupported(it.name) },
    ): BotsViewModel =
        BotsViewModel(
            ioDispatcher = dispatcher,
            autoLoad = false,
            clockSeconds = { 1_000L },
            selectedConnectionProfileId = selectedProfile,
            resolveBotSession = resolver,
        )

    @Test
    fun `bots without a canonical session resolve asynchronously after load`() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    resolver = {
                        BotSessionResolution.Resolved("beta-session", BotResolutionSource.MANAGED_TITLE)
                    },
                )

            viewModel.loadBots()
            advanceUntilIdle()

            assertEquals(listOf("alpha", "beta"), viewModel.uiState.value.profiles.map { it.name })
            assertEquals("beta-session", viewModel.uiState.value.resolutions["beta"]?.sessionId)
            assertNull(viewModel.uiState.value.resolutions["alpha"])
        }

    @Test
    fun `a resolved session makes the bot openable through its producing profile`() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    resolver = { BotSessionResolution.Resolved("beta-session", BotResolutionSource.CREATED) },
                )
            viewModel.loadBots()
            advanceUntilIdle()

            val beta = viewModel.uiState.value.profiles.first { it.name == "beta" }
            val resolutionState = viewModel.uiState.value.resolutions.getValue("beta")

            assertTrue(canOpenBot(beta, "connection-a", "connection-a", resolutionState.sessionId))
            assertFalse(canOpenBot(beta, "connection-a", "connection-b", resolutionState.sessionId))
        }

    @Test
    fun `canonical bots never invoke the resolver`() =
        runTest(dispatcher) {
            val requestedBots = mutableListOf<String>()
            val viewModel =
                viewModel(
                    resolver = { bot ->
                        requestedBots.add(bot.name)
                        BotSessionResolution.Resolved("x", BotResolutionSource.CREATED)
                    },
                )
            viewModel.loadBots()
            advanceUntilIdle()

            assertEquals(setOf("beta"), viewModel.uiState.value.resolutions.keys)
            assertEquals(listOf("beta"), requestedBots)
        }

    @Test
    fun `capability gated bots surface the unresolved reason and stay unopenable`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.loadBots()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val beta = state.profiles.first { it.name == "beta" }
            val resolution = state.resolutions.getValue("beta")

            assertEquals(BotSessionResolution.CreateUnsupported("beta"), resolution.unresolved)
            assertNull(resolution.sessionId)
            assertFalse(canOpenBot(beta, "connection-a", "connection-a", resolution.sessionId))
            assertTrue(state.hasUnresolvedBots)
        }

    @Test
    fun `resolution results from a superseded connection profile are fenced out`() =
        runTest(dispatcher) {
            var selected = "connection-a"
            val viewModel =
                viewModel(
                    selectedProfile = { selected },
                    resolver = { BotSessionResolution.Resolved("stale-session", BotResolutionSource.CREATED) },
                )
            viewModel.loadBots()
            selected = "connection-b"
            advanceUntilIdle()

            val resolution = viewModel.uiState.value.resolutions["beta"]
            assertNull(resolution?.sessionId)
        }

    @Test
    fun `failed resolution keeps the bot unopenable without inventing a session`() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    resolver = { BotSessionResolution.Failed("offline") },
                )
            viewModel.loadBots()
            advanceUntilIdle()

            val resolution = viewModel.uiState.value.resolutions.getValue("beta")
            assertNull(resolution.sessionId)
            assertEquals(BotSessionResolution.Failed("offline"), resolution.unresolved)
            assertFalse(viewModel.uiState.value.profiles.isEmpty())
        }
}
