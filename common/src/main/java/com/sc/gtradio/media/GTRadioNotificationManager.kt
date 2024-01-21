import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.registerReceiver
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerNotificationManager
import com.sc.gtradio.media.R

const val NOW_PLAYING_CHANNEL_ID = "com.sc.gtradio.media.NOW_PLAYING"
const val NOW_PLAYING_NOTIFICATION_ID = 0xb369 // Arbitrary number used to identify our notification

@UnstableApi class GTRadioNotificationManager(
    private val context: Context,
    sessionToken: MediaSessionCompat.Token,
    notificationListener: PlayerNotificationManager.NotificationListener,
) {
    private val notificationManager: PlayerNotificationManager

    init {
        val mediaController = MediaControllerCompat(context, sessionToken)
        notificationManager = PlayerNotificationManager.Builder(
            context,
            NOW_PLAYING_NOTIFICATION_ID, NOW_PLAYING_CHANNEL_ID
        )
            .setChannelNameResourceId(R.string.notification_channel)
            .setChannelDescriptionResourceId(R.string.notification_channel_description)
            .setMediaDescriptionAdapter(DescriptionAdapter(mediaController))
            .setNotificationListener(notificationListener)
            .build().apply {
                setMediaSessionToken(sessionToken)
                // Don't display the rewind or fast-forward buttons, or the media time
                setUseChronometer(false)
                registerReceiver(
                    context,
                    NotificationReceiver(context, sessionToken),
                    NotificationReceiver.intentFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                setUseRewindAction(false)
                setUseFastForwardAction(false)
            }
    }

    fun hideNotification() {
        notificationManager.setPlayer(null)
    }

    fun showNotificationForPlayer(player: Player) {
        notificationManager.setPlayer(player)
        updatePlayerButtons(player)
    }

    fun updatePlayerButtons(player: Player?) {
        if (player == null) {
            notificationManager.setUseNextAction(false)
            notificationManager.setUsePreviousAction(false)
            notificationManager.setUsePlayPauseActions(false)
            notificationManager.setUseStopAction(false)
        } else {
            notificationManager.setUseNextActionInCompactView(player.hasNext())
            notificationManager.setUseNextAction(player.hasNext())
            notificationManager.setUsePlayPauseActions(true)
            notificationManager.setUseStopAction(false)
            notificationManager.setUsePreviousActionInCompactView(player.hasPrevious())
            notificationManager.setUsePreviousAction(player.hasPrevious())
        }
    }

    private inner class DescriptionAdapter(private val controller: MediaControllerCompat) :
        PlayerNotificationManager.MediaDescriptionAdapter {
        override fun getCurrentContentTitle(player: Player): CharSequence {
            return controller.metadata.description.title.toString()
        }

        override fun createCurrentContentIntent(player: Player): PendingIntent? {
            return controller.sessionActivity
        }

        override fun getCurrentContentText(player: Player): CharSequence {
            return if (controller.metadata.description.subtitle == null) {
                ""
            } else {
                controller.metadata.description.subtitle.toString()
            }
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): Bitmap? {
            return controller.metadata.description.iconBitmap
        }

        override fun getCurrentSubText(player: Player): CharSequence {
            return if (controller.metadata.description.subtitle == null) {
                ""
            } else {
                controller.metadata.description.subtitle.toString()
            }
        }

    }
}

@UnstableApi class NotificationReceiver(private val context: Context, sessionToken: MediaSessionCompat.Token) : BroadcastReceiver() {
    companion object {
        val intentFilter = IntentFilter().apply {
            addAction(PlayerNotificationManager.ACTION_NEXT)
            addAction(PlayerNotificationManager.ACTION_PREVIOUS)
            addAction(PlayerNotificationManager.ACTION_PAUSE)
            addAction(PlayerNotificationManager.ACTION_PLAY)
            addAction(PlayerNotificationManager.ACTION_STOP)
        }
    }

    val mediaController = MediaControllerCompat(context, sessionToken)

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            PlayerNotificationManager.ACTION_NEXT -> {
                mediaController.transportControls.skipToNext()
            }
            PlayerNotificationManager.ACTION_PREVIOUS -> {
                mediaController.transportControls.skipToPrevious()
            }
            PlayerNotificationManager.ACTION_PLAY -> {
                mediaController.transportControls.play()
            }
            PlayerNotificationManager.ACTION_PAUSE -> {
                mediaController.transportControls.stop()
            }
            PlayerNotificationManager.ACTION_STOP -> {
                mediaController.transportControls.stop()
            }
        }
    }
}