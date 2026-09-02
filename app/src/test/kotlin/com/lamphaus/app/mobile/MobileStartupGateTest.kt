package com.lamphaus.app.mobile

import com.lamphaus.app.ui.AppUiState
import com.lamphaus.app.ui.CatalogSection
import com.lamphaus.app.ui.HomeCatalogBatchState
import com.lamphaus.core.data.cloud.AccountState
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cold-start policy (SHR-ARC-05/06/09): the gate holds the branded loading
 * surface until Home is meaningfully ready, completes exactly once, and never
 * regresses after completion.
 */
class MobileStartupGateTest {

    private fun signedIn() = AccountState.SignedIn(
        userId = "user-1",
        displayName = null,
        email = "user@example.com",
    )

    private fun media(id: String) = MediaPreview(
        id = id,
        type = MediaType.MOVIE,
        rawType = "movie",
        name = id,
    )

    private fun section(id: String, items: List<MediaPreview> = emptyList()) = CatalogSection(
        id = id,
        providerId = id,
        title = id,
        providerName = id,
        items = items,
    )

    @Test
    fun `authentication unresolved holds the startup surface`() {
        val gate = MobileStartupGate(initiallyResident = false)

        gate.onAccountChanged(AccountState.Loading)

        assertEquals(MobileStartupPhase.Startup, gate.phase)
        assertFalse(gate.awaitingContent)
    }

    @Test
    fun `signed-out launch leaves startup when authentication resolves`() {
        val gate = MobileStartupGate(initiallyResident = false)

        gate.onAccountChanged(AccountState.SignedOut)
        assertEquals(MobileStartupPhase.SignedOut, gate.phase)

        // Startup is once per process: signing in afterwards mounts the app
        // immediately instead of re-entering the content gate.
        gate.onAccountChanged(signedIn())
        assertEquals(MobileStartupPhase.SignedIn, gate.phase)
    }

    @Test
    fun `signed-in startup holds for zero and one rows and readies at two`() {
        val gate = MobileStartupGate(initiallyResident = false)
        gate.onAccountChanged(signedIn())

        gate.onHomeContent(readyRows = 0, settledWithoutContent = false)
        assertEquals(MobileStartupPhase.Startup, gate.phase)

        gate.onHomeContent(readyRows = 1, settledWithoutContent = false)
        assertEquals(MobileStartupPhase.Startup, gate.phase)

        gate.onHomeContent(readyRows = 2, settledWithoutContent = false)
        assertTrue(gate.contentReady)
        // Still holding: the artwork warm-up runs before the handoff.
        assertEquals(MobileStartupPhase.Startup, gate.phase)
    }

    @Test
    fun `warm-up completion after content readiness completes startup`() {
        val gate = MobileStartupGate(initiallyResident = false)
        gate.onAccountChanged(signedIn())
        gate.onHomeContent(readyRows = 2, settledWithoutContent = false)

        gate.onWarmUpElapsed()

        assertEquals(MobileStartupPhase.SignedIn, gate.phase)
    }

    @Test
    fun `resident content marks readiness for an immediate handoff`() {
        val gate = MobileStartupGate(initiallyResident = true)
        gate.onAccountChanged(signedIn())

        gate.onHomeContent(readyRows = 2, settledWithoutContent = false)

        assertTrue(gate.contentReady)
        assertTrue(gate.initiallyResident)
    }

    @Test
    fun `terminal empty home completes startup without rows`() {
        val gate = MobileStartupGate(initiallyResident = false)
        gate.onAccountChanged(signedIn())

        gate.onHomeContent(readyRows = 0, settledWithoutContent = true)

        assertEquals(MobileStartupPhase.SignedIn, gate.phase)
    }

    @Test
    fun `content timeout before sign-in resolves does not complete startup`() {
        val gate = MobileStartupGate(initiallyResident = false)

        // The timeout schedule starts only once the signed-in account resolved.
        gate.onContentTimeout()
        assertEquals(MobileStartupPhase.Startup, gate.phase)

        gate.onAccountChanged(signedIn())
        gate.onContentTimeout()
        assertEquals(MobileStartupPhase.SignedIn, gate.phase)
    }

    @Test
    fun `completed startup never regresses on transient loading emissions`() {
        val gate = MobileStartupGate(initiallyResident = false)
        gate.onAccountChanged(signedIn())
        gate.onHomeContent(readyRows = 2, settledWithoutContent = false)
        gate.onWarmUpElapsed()
        assertEquals(MobileStartupPhase.SignedIn, gate.phase)

        gate.onAccountChanged(AccountState.Loading)
        assertEquals(MobileStartupPhase.SignedIn, gate.phase)

        gate.onAccountChanged(signedIn())
        assertEquals(MobileStartupPhase.SignedIn, gate.phase)

        // A real signed-out transition still switches the surface.
        gate.onAccountChanged(AccountState.SignedOut)
        assertEquals(MobileStartupPhase.SignedOut, gate.phase)
    }

    @Test
    fun `empty provider set with an idle pipeline is terminal`() {
        val state = AppUiState(initialContentLoading = false)

        assertTrue(homeStartupSettledWithoutContent(state))
    }

    @Test
    fun `a loading pipeline is never terminal`() {
        assertFalse(homeStartupSettledWithoutContent(AppUiState(initialContentLoading = true)))
        assertFalse(
            homeStartupSettledWithoutContent(
                AppUiState(
                    initialContentLoading = false,
                    homeCatalogBatch = HomeCatalogBatchState(loadingMore = true),
                ),
            ),
        )
        assertFalse(
            homeStartupSettledWithoutContent(
                AppUiState(
                    initialContentLoading = false,
                    sections = listOf(section("row").copy(initialLoading = true)),
                ),
            ),
        )
    }

    @Test
    fun `rows with content are never settled without content`() {
        val state = AppUiState(
            initialContentLoading = false,
            sections = listOf(section("row", items = listOf(media("m1")))),
        )

        assertFalse(homeStartupSettledWithoutContent(state))
    }

    @Test
    fun `failed initial window is terminal so recovery stays local`() {
        val state = AppUiState(
            initialContentLoading = false,
            homeCatalogBatch = HomeCatalogBatchState(loadMoreFailed = true),
        )

        assertTrue(homeStartupSettledWithoutContent(state))
    }

    @Test
    fun `all sections failing initial load is terminal`() {
        val failed = section("row").copy(
            initialLoading = false,
            errorMessage = "provider unavailable",
        )
        val state = AppUiState(initialContentLoading = false, sections = listOf(failed))

        assertTrue(homeStartupSettledWithoutContent(state))
    }

    @Test
    fun `partially failed sections still loading are not terminal`() {
        val failed = section("a").copy(initialLoading = false, errorMessage = "down")
        val loading = section("b").copy(initialLoading = true)
        val state = AppUiState(initialContentLoading = false, sections = listOf(failed, loading))

        assertFalse(homeStartupSettledWithoutContent(state))
    }
}
