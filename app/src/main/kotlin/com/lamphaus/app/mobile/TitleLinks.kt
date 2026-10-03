package com.lamphaus.app.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * `lamphaus://title/<mediaKey>` links that open a followed title's details
 * on mobile: the target of new-episode notifications and the Continue
 * watching widget (MOB-NOT-05). The activity looks the key up in the
 * profile's own Library and progress; the link carries no metadata.
 */
internal object TitleLinks {
    const val SCHEME = "lamphaus"
    const val HOST = "title"

    fun uri(mediaKey: String): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(HOST)
        .appendPath(mediaKey)
        .build()

    /** An explicit intent to the mobile host, so no other app can claim the link. */
    fun intent(context: Context, mediaKey: String): Intent =
        Intent(Intent.ACTION_VIEW, uri(mediaKey), context, MobileActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun mediaKeyFrom(data: Uri): String? =
        data.pathSegments.firstOrNull()?.takeIf { it.contains(':') && it.length <= MAX_KEY_LENGTH }

    private const val MAX_KEY_LENGTH = 256
}
