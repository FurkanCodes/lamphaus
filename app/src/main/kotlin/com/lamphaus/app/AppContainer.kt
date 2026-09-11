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
import com.lamphaus.core.data.cloud.SupabaseIntegrationsGateway
import com.lamphaus.core.data.cloud.IntegrationsGateway
import com.lamphaus.core.data.cloud.LocalIntegrationsGateway
import com.lamphaus.core.data.cloud.SupabaseDetailEnrichmentRemoteDataSource
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
import com.lamphaus.core.data.playback.IntroDbSkipRepository
import com.lamphaus.core.data.repository.LibraryRepository
import com.lamphaus.core.data.repository.RoomLibraryRepository
import com.lamphaus.core.data.repository.DefaultDetailEnrichmentRepository
import com.lamphaus.core.data.repository.DefaultPlaybackPreferencesRepository
import com.lamphaus.core.data.repository.PlaybackPreferencesRepository
import com.lamphaus.core.player.Media3EngineFactory
import com.lamphaus.core.data.repository.DetailEnrichmentRepository
import com.lamphaus.core.data.security.AndroidKeystoreStringCipher
import com.lamphaus.core.provider.HttpProviderClient
import com.lamphaus.core.data.security.LocalArtworkKeyStore
import com.lamphaus.core.provider.ProviderAggregator
import com.lamphaus.core.provider.ProviderClient
import com.lamphaus.core.provider.ProviderUrlPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    /** Only test APKs carry this generated clip; no production session or network is involved. */
    suspend fun benchmarkMediaUri(): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        check(BuildConfig.BENCHMARK_FIXTURES)
        val clip = java.io.File(applicationContext.filesDir, "benchmark.mp4")
        if (!clip.exists()) {
            applicationContext.assets.open("benchmark.mp4").use { input ->
                clip.outputStream().use(input::copyTo)
            }
        }
        android.net.Uri.fromFile(clip).toString()
    }

    private val database = Room.databaseBuilder(
        context,
        LamphausDatabase::class.java,
        "lamphaus.db",
    )
        .addMigrations(*LamphausDatabase.ALL_MIGRATIONS)
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
    val skipRepository = IntroDbSkipRepository()

    /**
     * Application-lifetime scope for work that must outlive any single screen.
     * Watch-progress writes are launched from [com.lamphaus.app.player.PlayerActivity]
     * while it is stopping or already destroyed, where the activity's
     * lifecycleScope is cancelled before the write can land (mirrors NuvioTV's
     * singleton persistence scope).
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Publishes device-local player knobs to the playback service (plan §1). */
    init {
        applicationScope.launch {
            preferences.settings.map { it.devicePlayback }.collect { config ->
                Media3EngineFactory.deviceConfig = config
            }
        }
    }

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

    private val localAccount = LocalAccountGateway().apply {
        if (BuildConfig.BENCHMARK_FIXTURES) openDevelopmentSession()
    }
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

    /**
     * Shared JSON instance for enrichment payloads: lenient so provider-shaped
     * variation in edge responses never breaks decoding.
     */
    private val enrichmentJson: Json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Provider-neutral detail enrichment (TMDB + MDBList), cached in Room.
     * Null when this build has no cloud configured — the UI then simply never
     * shows enrichment sections instead of surfacing a permanent error.
     */
    val detailEnrichmentRepository: DetailEnrichmentRepository? = supabase?.let { client ->
        DefaultDetailEnrichmentRepository(
            dao = database.dao(),
            remote = SupabaseDetailEnrichmentRemoteDataSource(client, enrichmentJson),
            json = enrichmentJson,
        )
    }
    val playbackPreferencesRepository: PlaybackPreferencesRepository =
        DefaultPlaybackPreferencesRepository(database.dao())
    val integrationsGateway: IntegrationsGateway = if (supabase != null) {
        SupabaseIntegrationsGateway(supabase, checkNotNull(sessionRecovery))
    } else {
        LocalIntegrationsGateway()
    }
    /**
     * Dedicated update stack (plan §4, SHR-ARC-02/14). Independent of account
     * sync and the large application ViewModel; application-scoped so checks
     * and reconciliation outlive any single screen.
     */
    val updatePreferences = com.lamphaus.app.update.UpdatePreferences(context)
    val updateRepository = com.lamphaus.app.update.UpdateRepository(context, updatePreferences)
    val updateDownloader = com.lamphaus.app.update.UpdateDownloader(context, updatePreferences)
    val updateInstaller = com.lamphaus.app.update.UpdateInstaller(context)
    val updateCoordinator = com.lamphaus.app.update.UpdateCoordinator(
        context, updateRepository, updatePreferences, updateDownloader, updateInstaller,
    )
    fun openDevelopmentSession() {
        check(BuildConfig.DEBUG) { "Development sessions are disabled in this build." }
        localAccount.openDevelopmentSession()
    }
}
