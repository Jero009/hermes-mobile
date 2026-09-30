package com.m57.hermescontrol.ui.sessions

import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.model.BulkDeleteResponse
import com.m57.hermescontrol.data.model.SessionSearchResponse
import com.m57.hermescontrol.data.model.SessionSearchResult
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.HermesApiService
import io.mockk.coEvery
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class SessionSearchCorrectnessTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = mockk<HermesApiService>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(AuthManager, ApiClient)
        every { AuthManager.getSelectedProfileId() } returns "profile-a"
        every { ApiClient.hermesApi } returns api
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun viewModel() = SessionsViewModel(pinStore = FakeSessionPinStore(emptyList()), ioDispatcher = dispatcher)

    private fun hits(vararg ids: String) = Response.success(SessionSearchResponse(ids.map { SessionSearchResult(it) }))

    @Test
    fun `dedupe keeps the first surfaced ID in server order`() {
        coEvery { api.searchSessions(any(), any(), any(), any()) } returns hits("a", "b", "a", "b", "c")
        val vm = viewModel()
        vm.setSearchQuery("deploy")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), vm.uiState.value.searchResults.map { it.session_id })
        vm.setSearchQuery("next")
        assertTrue(vm.uiState.value.searchResults.isEmpty())
        assertTrue(vm.uiState.value.isSearching)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `new query immediately clears previous failure before debounce`() {
        coEvery { api.searchSessions(any(), any(), any(), any()) } returns
            Response.error(400, "bad query".toResponseBody())
        val vm = viewModel()
        vm.setSearchQuery("bad")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.searchError != null)
        vm.toggleSessionSelection("old")
        vm.setSearchQuery("new")
        assertNull(vm.uiState.value.searchError)
        assertTrue(vm.uiState.value.isSearching)
        assertTrue(vm.uiState.value.selectedIds.isEmpty())
        vm.setSearchQuery("")
        assertFalse(vm.uiState.value.isSearching)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `cancel resistant A response cannot overwrite second A query`() {
        for (failure in listOf(false, true)) {
            lateinit var stale: Continuation<Response<SessionSearchResponse>>
            var calls = 0
            coEvery { api.searchSessions("a", null, null, "cron") } coAnswers {
                if (calls++ == 0) suspendCoroutine { stale = it } else hits("fresh")
            }
            val vm = viewModel()
            vm.setSearchQuery("a")
            dispatcher.scheduler.advanceUntilIdle()
            vm.setSearchQuery("b")
            vm.setSearchQuery("a")
            dispatcher.scheduler.advanceUntilIdle()
            stale.resume(if (failure) Response.error(400, "stale".toResponseBody()) else hits("stale"))
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf("fresh"), vm.uiState.value.searchResults.map { it.session_id })
            assertNull(vm.uiState.value.searchError)
            assertFalse(vm.uiState.value.isSearching)
        }
    }

    @Test
    fun `response from changed service or profile is discarded`() {
        for (changeService in listOf(false, true)) {
            every { ApiClient.hermesApi } returns api
            every { AuthManager.getSelectedProfileId() } returns "profile-a"
            lateinit var stale: Continuation<Response<SessionSearchResponse>>
            coEvery { api.searchSessions(any(), any(), any(), any()) } coAnswers {
                suspendCoroutine { stale = it }
            }
            val vm = viewModel()
            vm.setSearchQuery("a")
            dispatcher.scheduler.advanceUntilIdle()
            if (changeService) {
                every { ApiClient.hermesApi } returns mockk(relaxed = true)
            } else {
                every { AuthManager.getSelectedProfileId() } returns "profile-b"
            }
            stale.resume(hits("wrong-profile"))
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(vm.uiState.value.searchResults.isEmpty())
            assertNull(vm.uiState.value.searchError)
            assertFalse(vm.uiState.value.isSearching)
        }
    }

    @Test
    fun `section switch fences cancel resistant old section response`() {
        lateinit var stale: Continuation<Response<SessionSearchResponse>>
        coEvery { api.searchSessions("a", null, null, "cron") } coAnswers { suspendCoroutine { stale = it } }
        coEvery { api.searchSessions("a", null, "cron", null) } returns hits("automation")
        val vm = viewModel()
        vm.setSearchQuery("a")
        dispatcher.scheduler.advanceUntilIdle()
        vm.selectSection(HistorySection.AUTOMATIONS)
        dispatcher.scheduler.advanceUntilIdle()
        stale.resume(hits("conversation"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("automation"), vm.uiState.value.searchResults.map { it.session_id })
    }

    @Test
    fun `history refresh does not orphan a pending search`() {
        lateinit var pending: Continuation<Response<SessionSearchResponse>>
        coEvery { api.searchSessions(any(), any(), any(), any()) } coAnswers { suspendCoroutine { pending = it } }
        val vm = viewModel()
        vm.setSearchQuery("a")
        dispatcher.scheduler.advanceUntilIdle()
        vm.loadSessions()
        dispatcher.scheduler.advanceUntilIdle()
        pending.resume(hits("found"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("found"), vm.uiState.value.searchResults.map { it.session_id })
        assertFalse(vm.uiState.value.isSearching)
    }

    @Test
    fun `single and bulk delete remove already displayed hits`() {
        coEvery { api.searchSessions(any(), any(), any(), any()) } returns hits("a", "b", "c")
        coEvery { api.deleteSession("b") } returns Response.success(Unit)
        coEvery { api.bulkDeleteSessions(any()) } returns Response.success(BulkDeleteResponse(ok = true, deleted = 1))
        val vm = viewModel()
        vm.setSearchQuery("a")
        dispatcher.scheduler.advanceUntilIdle()
        vm.requestDeleteSession("b")
        vm.confirmDeleteSession()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("a", "c"), vm.uiState.value.searchResults.map { it.session_id })
        vm.toggleSessionSelection("c")
        vm.confirmBulkDelete()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("a"), vm.uiState.value.searchResults.map { it.session_id })
    }

    @Test
    fun `rename is preserved and pending search cannot resurrect single or bulk deletions`() {
        coEvery { api.searchSessions(any(), any(), any(), any()) } returns hits("a", "b", "c")
        val vm = viewModel()
        vm.setSearchQuery("a")
        dispatcher.scheduler.advanceUntilIdle()
        coEvery { api.renameSession(any(), any()) } returns Response.success(Unit)
        vm.renameSession("a", "Renamed")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Renamed", vm.uiState.value.searchTitles["a"])
        lateinit var pending: Continuation<Response<SessionSearchResponse>>
        coEvery { api.searchSessions(any(), any(), any(), any()) } coAnswers { suspendCoroutine { pending = it } }
        vm.setSearchQuery("refresh")
        dispatcher.scheduler.advanceUntilIdle()
        coEvery { api.deleteSession("b") } returns Response.success(Unit)
        coEvery { api.bulkDeleteSessions(any()) } returns Response.success(BulkDeleteResponse(ok = true, deleted = 1))
        vm.requestDeleteSession("b")
        vm.confirmDeleteSession()
        vm.toggleSessionSelection("c")
        vm.confirmBulkDelete()
        dispatcher.scheduler.advanceUntilIdle()
        pending.resume(hits("a", "b", "c"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("a"), vm.uiState.value.searchResults.map { it.session_id })
        assertEquals("Renamed", vm.uiState.value.searchTitles["a"])
        assertFalse(vm.uiState.value.isSearching)
    }
}
