package com.lamphaus.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDataOwnershipTest {
    @Test
    fun `SHR-PROD-06 the same account keeps its data`() {
        assertEquals(LocalDataClaim.KEEP, localDataClaim("account-a", "account-a"))
    }

    @Test
    fun `SHR-PROD-06 another account's data is cleared before the next account syncs`() {
        assertEquals(LocalDataClaim.WIPE, localDataClaim("account-a", "account-b"))
        assertEquals(LocalDataClaim.WIPE, localDataClaim("local-development", "account-b"))
    }

    @Test
    fun `SHR-ARC-05 updating keeps the signed-in account's data and records it`() {
        assertEquals(LocalDataClaim.RECORD, localDataClaim(null, "account-a"))
    }
}
