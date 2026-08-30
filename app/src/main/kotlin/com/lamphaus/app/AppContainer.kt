package com.lamphaus.app

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.room.Room
import com.lamphaus.core.data.cloud.ArtworkStorageModeGateway
import com.lamphaus.core.data.cloud.LocalArtworkClient
import com.lamphaus.core.data.cloud.AccountGateway
import com.lamphaus.core.data.cloud.LocalAccountGateway
import com.lamphaus.core.data.cloud.LocalPairingGateway
import com.lamphaus.core.data.cloud.SupabaseAccountGateway
import com.lamphaus.core.data.cloud.SupabasePairingGateway
import com.lamphaus.core.data.cloud.SupabaseCloudSyncGateway
import com.lamphaus.core.data.cloud.SupabaseSessionRecovery
import com.lamphaus.core.data.cloud.LocalCloudSyncGateway
import com.lamphaus.core.data.cloud.CloudSyncGateway
import com.lamphaus.core.data.cloud.PairingGateway
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import com.lamphaus.core.data.local.LamphausDatabase
import com.lamphaus.core.data.preferences.UserPreferences
import com.lamphaus.core.data.repository.LibraryRepository
import com.lamphaus.core.data.repository.RoomLibraryRepository
import com.lamphaus.core.data.security.AndroidKeystoreStringCipher
import com.lamphaus.core.provider.HttpProviderClient
import com.lamphaus.core.data.security.LocalArtworkKeyStore
import com.lamphaus.core.provider.ProviderAggregator
import com.lamphaus.core.provider.ProviderClient
import com.lamphaus.core.provider.ProviderUrlPolicy

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(
        context,
        LamphausDatabase::class.java,
        "lamphaus.db",
    )
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()

    /**
     * Hardware-bound pairing identity (plan D3 refinement): ANDROID_ID is
     * stable per device + signing key and needs no permissions. Lets the
     * claim endpoint reuse ONE devices row per physical TV instead of
     * cloning a new row on every re-pair.
     */
    // ANDROID_ID is the deliberate choice here: it is stable per app-signing
    // key without permissions or Play services and is never used for ads or
    // analytics — exactly the non-identifier use case Google's data-ids
    // guidance permits for device binding.
    @SuppressLint("HardwareIds")
    val pairingDeviceKey: String? =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        )?.takeIf { it.length >= 8 }

    val preferences = UserPreferences(context)
    val libraryRepository: LibraryRepository = RoomLibraryRepository(
        database.dao(),
        AndroidKeystoreStringCipher(),
    )
    val providerClient: ProviderClient = HttpProviderClient(
        ProviderUrlPolicy(allowDebugLocalhost = BuildConfig.DEBUG),
    )
    val providerAggregator = ProviderAggregator(providerClient)

    /**
     * Shared Supabase client, present only when cloud credentials are provided via
     * Gradle properties (lamphaus.supabaseUrl / lamphaus.supabasePublishableKey).
     * Gateways migrate onto this client milestone by milestone (M2 auth, M3 sync,
     * M5 functions); until then Local gateways keep every surface functional.
     */
    val supabase: SupabaseClient? = if (BuildConfig.CLOUD_CONFIGURED) {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Functions)
        }
    } else {
        null
    }
    private val sessionRecovery = supabase?.let(::SupabaseSessionRecovery)

    private val localAccount = LocalAccountGateway()
    val accountGateway: AccountGateway = if (supabase != null) {
        SupabaseAccountGateway(supabase, checkNotNull(sessionRecovery))
    } else {
        localAccount
    }
    val pairingGateway: PairingGateway = if (supabase != null) {
        SupabasePairingGateway(supabase, checkNotNull(sessionRecovery))
    } else {
        LocalPairingGateway()
    }
    private val baseCloudSyncGateway: CloudSyncGateway = if (supabase != null) {
        SupabaseCloudSyncGateway(supabase, checkNotNull(sessionRecovery))
    } else {
        LocalCloudSyncGateway()
    }
    val artworkStorageModeGateway = ArtworkStorageModeGateway(
        delegate = baseCloudSyncGateway,
        preferences = preferences,
        localKeys = LocalArtworkKeyStore(context),
        localArtwork = LocalArtworkClient(),
    )
    val cloudSyncGateway: CloudSyncGateway = artworkStorageModeGateway

    fun openDevelopmentSession() {
        check(BuildConfig.DEBUG) { "Development sessions are disabled in this build." }
        localAccount.openDevelopmentSession()
    }
}
