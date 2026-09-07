package com.m57.hermescontrol.ui.mcp

import com.m57.hermescontrol.data.model.McpServer
import com.m57.hermescontrol.data.model.McpServerTestResponse
import com.m57.hermescontrol.data.model.McpServerToolInfo
import com.m57.hermescontrol.data.model.McpServersResponse
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.HermesApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class McpServersViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var api: HermesApiService

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(ApiClient)
        api = mockk()
        every { ApiClient.hermesApi } returns api
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `test all tests enabled servers and records each result`() {
        coEvery { api.getMcpServers() } returns
            Response.success(
                McpServersResponse(
                    listOf(
                        McpServer(name = "healthy", enabled = true),
                        McpServer(name = "broken", enabled = true),
                        McpServer(name = "disabled", enabled = false),
                    ),
                ),
            )
        coEvery { api.testMcpServer("healthy") } returns
            Response.success(
                McpServerTestResponse(
                    ok = true,
                    tools = listOf(McpServerToolInfo("read", schemaChars = 80)),
                ),
            )
        coEvery { api.testMcpServer("broken") } returns
            Response.error(503, "unavailable".toResponseBody())

        val viewModel = McpServersViewModel(ioDispatcher = dispatcher)
        viewModel.loadServers()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.testAllServers()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { api.testMcpServer("healthy") }
        coVerify(exactly = 3) { api.testMcpServer("broken") }
        coVerify(exactly = 0) { api.testMcpServer("disabled") }
        val state = viewModel.uiState.value
        assertFalse(state.isTestingAll)
        assertTrue(state.testingServers.isEmpty())
        assertEquals(true, state.serverTestResults["healthy"]?.ok)
        assertEquals(false, state.serverTestResults["broken"]?.ok)
        assertEquals("Tested 2 servers: 1 passed, 1 failed", state.toastMessage)
    }
}
