package com.lamphaus.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lamphaus.app.R
import com.lamphaus.app.mobile.TitleLinks
import com.lamphaus.core.model.NewEpisodeAlert

/**
 * Posts new-episode notifications (SHR-PROD-16): one per series in its own
 * user-configurable channel at Default importance (MOB-NOT-06), grouped
 * with a summary when several arrive (MOB-NOT-08). Titles lead with the
 * series, text names the episode (MOB-NOT-04), the lock screen shows only
 * "New episode available" (MOB-NOT-08, SHR-PROD-06), and a tap opens the
 * series' details (MOB-NOT-05). There are no actions: none would do more
 * than tapping.
 */
internal class NewEpisodeNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Whether posting would show anything: the app's notifications and this channel are on. */
    fun canNotify(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val channel = manager.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_new_episodes),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_new_episodes_description)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    /** Posts [alerts]; [posters] holds artwork that loaded, keyed by media key. */
    @SuppressLint("MissingPermission") // canNotify() checks POST_NOTIFICATIONS first.
    fun post(alerts: List<NewEpisodeAlert>, posters: Map<String, Bitmap>) {
        if (alerts.isEmpty() || !canNotify()) return
        ensureChannel()
        alerts.forEach { alert ->
            val key = alert.media.stableKey
            val builder = base()
                .setContentTitle(alert.media.name)
                .setContentText(episodeText(alert))
                .setContentIntent(openIntent(key))
                .setWhen(alert.episode.releasedAtEpochMillis ?: System.currentTimeMillis())
                .setShowWhen(true)
                .setPublicVersion(publicVersion().build())
                .addExtras(android.os.Bundle().apply { putString(EXTRA_VIDEO_ID, alert.episode.id) })
            posters[key]?.let(builder::setLargeIcon)
            manager.notify(key, NOTIFICATION_ID, builder.build())
        }
        if (activeCount() >= 2) {
            manager.notify(
                SUMMARY_TAG,
                NOTIFICATION_ID,
                base()
                    .setContentTitle(context.getString(R.string.notification_new_episodes_summary))
                    .setGroupSummary(true)
                    .setPublicVersion(publicVersion().build())
                    .build(),
            )
        }
    }

    /** Removes notifications for episodes the viewer has since watched, and a lone summary. */
    fun removeWatched(watchedVideoIds: Set<String>) {
        val active = activeNotifications()
        active.filter { it.notification.extras.getString(EXTRA_VIDEO_ID) in watchedVideoIds }
            .forEach { manager.cancel(it.tag, NOTIFICATION_ID) }
        if (activeCount() < 2) manager.cancel(SUMMARY_TAG, NOTIFICATION_ID)
    }

    fun removeAll() {
        activeNotifications().forEach { manager.cancel(it.tag, NOTIFICATION_ID) }
        manager.cancel(SUMMARY_TAG, NOTIFICATION_ID)
    }

    private fun base() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_lamphaus)
        .setColor(ContextCompat.getColor(context, R.color.brand_accent))
        .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setGroup(GROUP)
        .setAutoCancel(true)

    private fun publicVersion() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_lamphaus)
        .setColor(ContextCompat.getColor(context, R.color.brand_accent))
        .setContentTitle(context.getString(R.string.notification_new_episode_public))

    private fun episodeText(alert: NewEpisodeAlert): String {
        val episode = alert.episode
        val code = if (episode.season != null && episode.episode != null) {
            context.getString(R.string.episode_format, episode.season, episode.episode)
        } else {
            null
        }
        val label = listOfNotNull(code, episode.title.takeIf(String::isNotBlank)).joinToString(" · ")
        val more = alert.newCount - 1
        return if (more > 0) {
            context.resources.getQuantityString(R.plurals.notification_new_episodes_more, more, label, more)
        } else {
            label
        }
    }

    private fun openIntent(mediaKey: String): PendingIntent = PendingIntent.getActivity(
        context,
        mediaKey.hashCode(),
        TitleLinks.intent(context, mediaKey),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun activeNotifications() =
        context.getSystemService(NotificationManager::class.java)
            ?.activeNotifications
            .orEmpty()
            .filter { it.id == NOTIFICATION_ID && it.tag != SUMMARY_TAG && it.notification.group == GROUP }

    private fun activeCount() = activeNotifications().size

    companion object {
        const val CHANNEL_ID = "new_episodes"
        private const val GROUP = "com.lamphaus.app.NEW_EPISODES"
        private const val SUMMARY_TAG = "new-episodes-summary"
        private const val NOTIFICATION_ID = 4101
        private const val EXTRA_VIDEO_ID = "com.lamphaus.app.extra.VIDEO_ID"
    }
}
