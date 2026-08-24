package com.lamphaus.app

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.FirebaseFirestore
import com.lamphaus.core.data.cloud.AccountGateway
import com.lamphaus.core.data.cloud.FirebaseAccountGateway
import com.lamphaus.core.data.cloud.FirebasePairingGateway
import com.lamphaus.core.data.cloud.LocalAccountGateway
import com.lamphaus.core.data.cloud.LocalPairingGateway
import com.lamphaus.core.data.cloud.CloudSyncGateway
import com.lamphaus.core.data.cloud.FirebaseCloudSyncGateway
import com.lamphaus.core.data.cloud.LocalCloudSyncGateway
import com.lamphaus.core.data.cloud.PairingGateway
import com.lamphaus.core.data.local.LamphausDatabase
import com.lamphaus.core.data.preferences.UserPreferences
import com.lamphaus.core.data.repository.LibraryRepository
import com.lamphaus.core.data.repository.RoomLibraryRepository
import com.lamphaus.core.data.security.AndroidKeystoreStringCipher
import com.lamphaus.core.provider.HttpProviderClient
import com.lamphaus.core.provider.ProviderAggregator
import com.lamphaus.core.provider.ProviderClient
import com.lamphaus.core.provider.ProviderUrlPolicy

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(
        context,
        LamphausDatabase::class.java,
        "lamphaus.db",
    ).build()

    val preferences = UserPreferences(context)
    val libraryRepository: LibraryRepository = RoomLibraryRepository(
        database.dao(),
        AndroidKeystoreStringCipher(),
    )
    val providerClient: ProviderClient = HttpProviderClient(
        ProviderUrlPolicy(allowDebugLocalhost = BuildConfig.DEBUG),
    )
    val providerAggregator = ProviderAggregator(providerClient)

    private val localAccount = LocalAccountGateway()
    val accountGateway: AccountGateway = if (BuildConfig.CLOUD_CONFIGURED) {
        FirebaseAccountGateway(
            FirebaseAuth.getInstance(),
            BuildConfig.EMAIL_LINK_DOMAIN,
        )
    } else {
        localAccount
    }
    val pairingGateway: PairingGateway = if (BuildConfig.CLOUD_CONFIGURED) {
        FirebasePairingGateway(FirebaseFunctions.getInstance())
    } else {
        LocalPairingGateway()
    }
    val cloudSyncGateway: CloudSyncGateway = if (BuildConfig.CLOUD_CONFIGURED) {
        FirebaseCloudSyncGateway(FirebaseFirestore.getInstance(), FirebaseFunctions.getInstance())
    } else {
        LocalCloudSyncGateway()
    }

    fun openDevelopmentSession() {
        check(BuildConfig.DEBUG) { "Development sessions are disabled in this build." }
        localAccount.openDevelopmentSession()
    }
}
