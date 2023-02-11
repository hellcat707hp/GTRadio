package com.sc.gtradio.media

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import com.google.android.exoplayer2.DefaultControlDispatcher
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager


const val NOW_PLAYING_CHANNEL_ID = "com.sc.gtradio.media.NOW_PLAYING"
const val NOW_PLAYING_NOTIFICATION_ID = 0xb369 // Arbitrary number used to identify our notification

class GTRadioNotificationManager(
    private val context: Context,
    sessionToken: MediaSessionCompat.Token,
    notificationListener: PlayerNotificationManager.NotificationListener,
) {
    private val notificationManager: PlayerNotificationManager

    init {
        val mediaController = MediaControllerCompat(context, sessionToken)

        notificationManager = PlayerNotificationManager.createWithNotificationChannel(
            context,
            NOW_PLAYING_CHANNEL_ID,
            R.string.notification_channel,
            R.string.notification_channel_description,
            NOW_PLAYING_NOTIFICATION_ID,
            DescriptionAdapter(mediaController),
            notificationListener
        ).apply {

            setMediaSessionToken(sessionToken)

            // Don't display the rewind or fast-forward buttons, or the media time
            setUseChronometer(false)
            setControlDispatcher(CustomControlDispatcher(sessionToken))
        }
    }

    fun hideNotification() {
        notificationManager.setPlayer(null)
    }

    fun showNotificationForPlayer(player: Player){
        notificationManager.setPlayer(player)
        updatePlayerButtons(player)
    }

    fun updatePlayerButtons(player: Player?) {
        if (player == null ) {
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
                } else  {
                    controller.metadata.description.subtitle.toString()
                }
            }

            override fun getCurrentLargeIcon(player: Player, callback: PlayerNotificationManager.BitmapCallback): Bitmap? {
                return controller.metadata.description.iconBitmap
            }

            override fun getCurrentSubText(player: Player): CharSequence {
                return if (controller.metadata.description.subtitle == null) {
                    ""
                } else  {
                    controller.metadata.description.subtitle.toString()
                }
            }

        }

        private inner class CustomControlDispatcher(sessionToken: MediaSessionCompat.Token): DefaultControlDispatcher() {
            val mediaController = MediaControllerCompat(context, sessionToken)

            override fun dispatchNext(player: Player): Boolean {
                mediaController.transportControls.skipToNext()
                return true
            }

            override fun dispatchPrevious(player: Player): Boolean {
                mediaController.transportControls.skipToPrevious()
                return true
            }

            override fun dispatchStop(player: Player, reset: Boolean): Boolean {
                mediaController.transportControls.stop()
                return true
            }

            override fun isRewindEnabled(): Boolean {
                return false
            }

            override fun isFastForwardEnabled(): Boolean {
                return false
            }

        }
    }
