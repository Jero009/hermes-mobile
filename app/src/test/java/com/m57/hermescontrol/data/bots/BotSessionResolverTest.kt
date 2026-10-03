package com.m57.hermescontrol.data.bots

import com.m57.hermescontrol.data.model.ActiveProfileResponse
import com.m57.hermescontrol.data.model.CanonicalSessionInfo
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.data.model.SessionInfo
import com.m57.hermescontrol.data.model.SessionListResponse
import com.m57.hermescontrol.data.model.SessionRenameRequest
import com.m57.hermescontrol.data.remote.HermesApiService
import com.m57.hermescontrol.data.ws.WsMethods
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * Resolution contract for the gateway session backing a roster bot.
 *
 * Order is fixed: server canonical id → exact unique managed-title match →
 * create+rename. Creation is only allowed where the gateway's active profile
 * IS the bot's profile, so `session.create` provably creates the correct
 * profile session. Recency is never consulted.
 */
class BotSessionResolverTest {
    private lateinit var api: HermesApiService
    private val capturedRenames = mutableListOf<Pair<String, String>>()

    private fun gatewayOk(): suspend (String, Map<String, Any>) -> Result<Any?> =
        { _, _ ->
            Result.success(mapOf("session_id" to "new-1", "stored_session_id" to "new-1"))
        }

    private fun gatewayFailing(): suspend (String, Map<String, Any>) -> Result<Any?> =
        { _, _ ->
            Result.failure(IllegalStateException("socket down"))
        }

    private fun resolver(gateway: suspend (String, Map<String, Any>) -> Result<Any?> = gatewayOk()) =
        BotSessionResolver(api = api, gatewayRequest = gateway)

    @Before
    fun setUp() {
        api = mockk(relaxed = true)
        capturedRenames.clear()
    }

    private fun sessionsPage(
        vararg sessions: SessionInfo,
        total: Int = sessions.size,
        offset: Int = 0,
        limit: Int = sessions.size,
    ): Response<SessionListResponse> =
        Response.success(
            SessionListResponse(
                sessions = sessions.toList(),
                total = total,
                limit = limit,
                offset = offset,
            ),
        )

    private fun activeProfile(name: String): Response<ActiveProfileResponse> =
        Response.success(ActiveProfileResponse(active = name))

    @Test
    fun `canonical session id wins without any other call`() =
        runTest {
            val bot =
                ProfileInfo("researcher", canonical_session = CanonicalSessionInfo("root", resolved_id = "tip"))

            val resolution = resolver().resolve(bot)

            assertEquals(BotSessionResolution.Resolved("tip", BotResolutionSource.CANONICAL), resolution)
            coVerify(exactly = 0) { api.getSessions(any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { api.getActiveProfile() }
        }

    @Test
    fun `canonical id falls back to id when resolved id is blank`() =
        runTest {
            val bot = ProfileInfo("researcher", canonical_session = CanonicalSessionInfo("root", " "))

            val resolution = resolver().resolve(bot)

            assertEquals(BotSessionResolution.Resolved("root", BotResolutionSource.CANONICAL), resolution)
        }

    @Test
    fun `unique exact managed-title match resolves without creating`() =
        runTest {
            coEvery { api.getSessions(any(), any(), any(), any(), any()) } returns
                sessionsPage(
                    SessionInfo(id = "plain", title = "researcher"),
                    SessionInfo(id = "owned", title = ManagedBotSession.managedTitle("researcher")),
                )

            val resolution = resolver().resolve(ProfileInfo("researcher"))

            assertEquals(BotSessionResolution.Resolved("owned", BotResolutionSource.MANAGED_TITLE), resolution)
            coVerify(exactly = 0) { api.getActiveProfile() }
            coVerify(exactly = 0) { api.renameSession(any(), any<SessionRenameRequest>()) }
        }

    @Test
    fun `title search pages until total is reached to find a later match`() =
        runTest {
            coEvery { api.getSessions(limit = 100, offset = 0, any(), any(), any()) } returns
                sessionsPage(SessionInfo(id = "a", title = "old"), total = 150, limit = 100, offset = 0)
            coEvery { api.getSessions(limit = 100, offset = 100, any(), any(), any()) } returns
                sessionsPage(
                    SessionInfo(id = "owned", title = ManagedBotSession.managedTitle("researcher")),
                    total = 150,
                    limit = 100,
                    offset = 100,
                )

            val resolution = resolver().resolve(ProfileInfo("researcher"))

            assertEquals(BotSessionResolution.Resolved("owned", BotResolutionSource.MANAGED_TITLE), resolution)
        }

    @Test
    fun `ambiguous managed titles fail closed instead of guessing`() =
        runTest {
            coEvery { api.getSessions(any(), any(), any(), any(), any()) } returns
                sessionsPage(
                    SessionInfo(id = "one", title = ManagedBotSession.managedTitle("researcher")),
                    SessionInfo(id = "two", title = ManagedBotSession.managedTitle("researcher")),
                )

            val resolution = resolver().resolve(ProfileInfo("researcher"))

            assertEquals(BotSessionResolution.Ambiguous(2), resolution)
            coVerify(exactly = 0) { api.getActiveProfile() }
        }

    @Test
    fun `recency is never used - a recent unmanaged session creates a managed bot session`() =
        runTest {
            coEvery { api.getSessions(any(), any(), any(), any(), any()) } returns
                sessionsPage(SessionInfo(id = "recent", title = "Random chat"))

            val resolution = resolver().resolve(ProfileInfo("researcher"))

            assertEquals(BotSessionResolution.Resolved("new-1", BotResolutionSource.CREATED), resolution)
        }

    @Test
    fun `creation targets the bot profile without changing the active profile`() =
        runTest {
            coEvery { api.getSessions(any(), any(), any(), any(), any()) } returns sessionsPage()
            val requests = mutableListOf<Map<String, Any>>()
            val gateway: suspend (String, Map<String, Any>) -> Result<Any?> = { _, params ->
                requests += params
                Result.success(mapOf("session_id" to "new-1"))
            }

            val resolution = resolver(gateway).resolve(ProfileInfo("researcher"))

            assertEquals(BotSessionResolution.Resolved("new-1", BotResolutionSource.CREATED), resolution)
            assertEquals(
                listOf(
                    mapOf(
                        "source" to "desktop",
                        "profile" to "researcher",
                        "title" to ManagedBotSession.managedTitle("researcher"),
                        "hidden" to true,
                    ),
                ),
                requests,
            )
            coVerify(exactly = 0) { api.getActiveProfile() }
            coVerify(exactly = 0) { api.renameSession(any(), any<SessionRenameRequest>()) }
        }

    @Test
    fun `creation assigns the managed title atomically`() =
        runTest {
            coEvery { api.getSessions(any(), any(), any(), any(), any()) } returns sessionsPage()

            val resolution = resolver(gatewayOk()).resolve(ProfileInfo("researcher"))

            assertEquals(BotSessionResolution.Resolved("new-1", BotResolutionSource.CREATED), resolution)
            coVerify(exactly = 0) { api.renameSession(any(), any<SessionRenameRequest>()) }
        }

    @Test
    fun `a gateway create failure surfaces as Failed and never invents a session`() =
        runTest {
            coEvery { api.getSessions(any(), any(), any(), any(), any()) } returns sessionsPage()
            val resolution = resolver(gatewayFailing()).resolve(ProfileInfo("researcher"))

            assertTrue(resolution is BotSessionResolution.Failed)
            coVerify(exactly = 0) { api.renameSession(any(), any<SessionRenameRequest>()) }
        }

    @Test
    fun `create request targets the session create gateway method`() =
        runTest {
            coEvery { api.getSessions(any(), any(), any(), any(), any()) } returns sessionsPage()

            val methods = mutableListOf<String>()
            val gateway: suspend (String, Map<String, Any>) -> Result<Any?> = { method, _ ->
                methods.add(method)
                Result.success(mapOf("session_id" to "new-1"))
            }

            resolver(gateway).resolve(ProfileInfo("researcher"))

            assertEquals(listOf(WsMethods.SESSION_CREATE), methods)
        }

    @Test
    fun `search transport failure fails closed instead of creating`() =
        runTest {
            coEvery { api.getSessions(any(), any(), any(), any(), any()) } throws
                java.io.IOException("offline")

            val resolution = resolver().resolve(ProfileInfo("researcher"))

            assertTrue(resolution is BotSessionResolution.Failed)
            coVerify(exactly = 0) { api.getActiveProfile() }
        }
}
