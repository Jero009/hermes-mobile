package com.m57.hermescontrol.data.remote

import android.util.Log
import com.m57.hermescontrol.data.config.ServerStore
import com.m57.hermescontrol.data.config.ServerStoreState
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.local.AuthSessionState
import com.m57.hermescontrol.data.model.ConfigUpdateRequest
import com.m57.hermescontrol.data.ws.ConnectionStatus
import com.m57.hermescontrol.data.ws.HermesWsClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Disposable protocol peer, not a production dashboard. Only credential providers/storage are synthetic. */
class AuthenticatedDisposableFixtureTest {
    private lateinit var server: MockWebServer
    private lateinit var endpoint: ServerEndpoint
    private val jar = buildFakePersistentCookieJar()
    private val tickets = AtomicInteger()
    private val accepted = LinkedBlockingQueue<String>()
    private val frames = LinkedBlockingQueue<String>()
    private val requests = LinkedBlockingQueue<RecordedRequest>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.offer(request)
                val path = request.requestUrl!!.encodedPath
                if (path == "/dashboard/api/auth/login") {
                    return MockResponse().setBody("{}")
                        .addHeader("Set-Cookie", "session=fixture; Path=/dashboard/; HttpOnly")
                }
                if (request.getHeader("Cookie") != "session=fixture" || request.getHeader("Authorization") != null) {
                    return MockResponse().setResponseCode(401)
                }
                return when (path) {
                    "/dashboard/api/auth/ws-ticket" -> MockResponse().setBody("{\"ticket\":\"ticket-${tickets.incrementAndGet()}\"}")
                    "/dashboard/api/ws" -> {
                        val ticket = request.requestUrl!!.queryParameter("ticket")
                        if (ticket != "ticket-${tickets.get()}" || !accepted.offer(ticket)) {
                            MockResponse().setResponseCode(401)
                        } else {
                            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                                override fun onMessage(webSocket: WebSocket, text: String) {
                                    frames.offer(text)
                                }
                            })
                        }
                    }
                    "/dashboard/api/config" -> MockResponse().setResponseCode(403)
                        .setBody("{\"error\":\"explicit profile required\"}")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        endpoint = ServerEndpoint.parse(server.url("/dashboard/").toString(), CleartextPolicy.ALLOW_WITH_WARNING)
        jar.clearAll()
        CookieManager.setJarForTest(jar)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.isLoggable(any<String>(), any<Int>()) } returns false
        mockkObject(AuthManager)
        val store = mockk<ServerStore>()
        every { store.getLatestState() } returns ServerStoreState(wsAuthParam = "ticket")
        every { AuthManager.serverStore } returns store
        every { AuthManager.isGatedMode() } returns true
        every { AuthManager.endpointForBuild() } returns endpoint
        every { AuthManager.wsUrl() } returns endpoint.webSocketUrl("ticket", "unused")
        every { AuthManager.wsUrlWithCredential(any(), any()) } answers {
            endpoint.webSocketUrl(secondArg(), firstArg())
        }
        every { AuthManager.getSelectedProfileId() } returns "local-id"
        every { AuthManager.getToken() } returns "stale-bearer"
        every { AuthManager.isAutoReconnect() } returns false
        every { AuthManager.getSessionCookie() } returns null
        HermesWsClient.disconnect(clearPendingMessages = true)
        HermesWsClient.setAppForeground(true)
        AuthSessionState.markAuthenticated()
        ApiClient.rebuild()
    }

    @After
    fun tearDown() {
        HermesWsClient.disconnect(clearPendingMessages = true)
        ApiClient.rebuild()
        CookieManager.resetForTest()
        unmockkAll()
        server.shutdown()
    }

    @Test
    fun loginCookieAndFreshTicketOnEachConnection() = runBlocking {
        assertEquals(401, ApiClient.hermesApi.getConfig().code())
        assertEquals(200, OkHttpProvider.probe.newCall(
            okhttp3.Request.Builder().url(endpoint.resolve("api/auth/login"))
                .post("{}".toRequestBody()).build(),
        ).execute().use { it.code })
        assertTrue(ApiClient.hermesApi.getConfig().code() == 403)
        assertEquals(403, ApiClient.hermesApi.updateConfig(ConfigUpdateRequest(config = emptyMap())).code())
        val mutations = requests.filter { it.method == "PUT" }
        assertEquals(1, mutations.size)
        assertNull(mutations.single().requestUrl!!.queryParameter("profile"))
        assertEquals("session=fixture", requests.last { it.requestUrl!!.encodedPath == "/dashboard/api/config" }.getHeader("Cookie"))
        HermesWsClient.connect()
        withTimeout(5000) { HermesWsClient.connectionStatus.first { it == ConnectionStatus.CONNECTED } }
        assertEquals("ticket-1", accepted.poll(5, TimeUnit.SECONDS))
        HermesWsClient.sendMessage("runtime-session", "fixture prompt")
        assertTrue(frames.poll(5, TimeUnit.SECONDS).contains("fixture prompt"))
        HermesWsClient.disconnect(clearPendingMessages = true)
        HermesWsClient.connect()
        withTimeout(5000) { HermesWsClient.connectionStatus.first { it == ConnectionStatus.CONNECTED } }
        assertEquals("ticket-2", accepted.poll(5, TimeUnit.SECONDS))
        assertEquals(2, tickets.get())
        assertNull(requests.first { it.requestUrl!!.encodedPath == "/dashboard/api/auth/ws-ticket" }.getHeader("Authorization"))
    }
}
