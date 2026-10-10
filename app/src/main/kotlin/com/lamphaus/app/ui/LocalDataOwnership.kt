package com.lamphaus.app.ui

/** What a sign-in does with the profiles, library, and history already on the device. */
internal enum class LocalDataClaim {
    /** The data already belongs to this account. */
    KEEP,

    /** Data from before ownership was recorded: it belongs to whoever is signed in. */
    RECORD,

    /** Another account's data: cleared before this account syncs (SHR-PROD-06). */
    WIPE,
}

/**
 * Leaving an account clears the device only when the app sees it happen.
 * A session that ends while the app is closed (a TV disconnected from the
 * phone, an expired session, an account deleted elsewhere) left the previous
 * account's rows behind, and the next account's first sign-in mixed them in
 * or even uploaded them into an empty account. The device now remembers
 * whose data it holds.
 */
internal fun localDataClaim(recordedOwner: String?, signedInUser: String): LocalDataClaim = when (recordedOwner) {
    signedInUser -> LocalDataClaim.KEEP
    null -> LocalDataClaim.RECORD
    else -> LocalDataClaim.WIPE
}
