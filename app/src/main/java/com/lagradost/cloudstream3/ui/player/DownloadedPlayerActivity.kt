package com.lagradost.cloudstream3.ui.player

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.ui.player.OfflinePlaybackHelper.playLink
import com.lagradost.cloudstream3.ui.player.OfflinePlaybackHelper.playUri
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.UIHelper.enableEdgeToEdgeCompat

class DownloadedPlayerActivity : AppCompatActivity() {
    companion object {
        const val TAG = "DownloadedPlayerActivity"
    }

    private var exitDialog: AlertDialog? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        CommonActivity.dispatchKeyEvent(this, event) ?: super.dispatchKeyEvent(event)

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        CommonActivity.onKeyDown(this, keyCode, event) ?: super.onKeyDown(keyCode, event)

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        CommonActivity.onUserLeaveHint(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Ignore same intent so the player doesnt totally
        // reload if you are playing the same thing.
        if (isSameIntent(intent)) return
        setIntent(intent)
        Log.i(TAG, "onNewIntent")
        handleIntent(intent)
    }

    private fun isSameIntent(newIntent: Intent): Boolean {
        val old = intent ?: return false
        // Compare URIs first
        val oldUri = old.data ?: old.clipData?.getItemAt(0)?.uri
        val newUri = newIntent.data ?: newIntent.clipData?.getItemAt(0)?.uri
        if (oldUri != null && oldUri == newUri) return true
        // Fall back to comparing EXTRA_TEXT links
        val oldText = safe { old.getStringExtra(Intent.EXTRA_TEXT) }
        val newText = safe { newIntent.getStringExtra(Intent.EXTRA_TEXT) }
        return oldText != null && oldText == newText
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CommonActivity.loadThemes(this)
        CommonActivity.init(this)
        enableEdgeToEdgeCompat()
        setContentView(R.layout.empty_layout)
        Log.i(TAG, "onCreate")

        handleIntent(intent)
        attachBackPressedCallback("DownloadedPlayerActivity") {
            if (exitDialog?.isShowing == true) {
                exitDialog?.dismiss()
                return@attachBackPressedCallback
            }
            showExitConfirmDialog()
        }
    }

    private fun showExitConfirmDialog() {
        if (isFinishing || isDestroyed) return
        exitDialog = AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setTitle(R.string.exit_player_confirm_title)
            .setMessage(R.string.exit_player_confirm_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                finish()
            }
            .setNegativeButton(R.string.no, null)
            .setOnDismissListener { exitDialog = null }
            .show()
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data
        val dataString = intent.dataString
        val action = intent.action
        val scheme = data?.scheme

        // Comprehensive diagnostics for external intent troubleshooting.
        // Filter logcat with: adb logcat -s DownloadedPlayerActivity
        Log.d(
            TAG,
            "handleIntent: action=$action, scheme=$scheme, " +
                "data=$data, dataString=$dataString, " +
                "mimeType=${intent.type}, clipData=${intent.clipData}, " +
                "extras=${intent.extras}"
        )

        // Structured CloudStream-package playback (LINKS_EXTRA JSON array) — used when
        // CS3 launches itself for "Play in CloudStream" from inside the app.
        if (OfflinePlaybackHelper.playIntent(activity = this, intent = intent)) {
            Log.d(TAG, "handleIntent: routed via OfflinePlaybackHelper.playIntent (LINKS_EXTRA)")
            return
        }

        if (
            action == Intent.ACTION_SEND ||
            action == Intent.ACTION_OPEN_DOCUMENT ||
            action == Intent.ACTION_VIEW
        ) {
            val extraText = safe { intent.getStringExtra(Intent.EXTRA_TEXT) }
            val cd = intent.clipData
            val item = if (cd != null && cd.itemCount > 0) cd.getItemAt(0) else null
            val itemUri = item?.uri
            val itemText = item?.text?.toString()

            // Some senders (incl. internal helpers and certain share sheets) pass the URL
            // as a string extra instead of intent.data. Read all known locations so we
            // can interop with as many external players / ADB invocations as possible.
            val extraUrl = safe { intent.getStringExtra("url") }
            val extraMagnetLink = safe { intent.getStringExtra("magnet_link") }
            val extraMagnet = safe { intent.getStringExtra("magnet") }

            // Resolve a single URL string. For magnet URIs we prefer the raw dataString
            // because Uri.toString() can re-encode characters (e.g. '=' inside tracker
            // query strings) and corrupt the magnet for the torrent engine.
            val resolvedUrl: String? = when {
                // Direct magnet/http/https URI on intent.data
                scheme == "magnet" -> dataString ?: data?.toString()
                scheme == "http" || scheme == "https" -> dataString ?: data?.toString()
                // Magnet/http/https URI on clipData item
                itemUri?.scheme == "magnet" -> itemUri.toString()
                itemUri?.scheme == "http" || itemUri?.scheme == "https" ->
                    itemUri.toString()
                // String extras (used by some internal callers and external senders)
                extraMagnetLink?.startsWith("magnet:") == true -> extraMagnetLink
                extraMagnet?.startsWith("magnet:") == true -> extraMagnet
                extraUrl?.startsWith("magnet:") == true -> extraUrl
                extraUrl?.startsWith("http://") == true ||
                    extraUrl?.startsWith("https://") == true -> extraUrl
                // Plain text from ClipData item or EXTRA_TEXT (share-sheet flow)
                itemText?.startsWith("magnet:") == true -> itemText
                itemText?.startsWith("http://") == true ||
                    itemText?.startsWith("https://") == true -> itemText
                extraText?.startsWith("magnet:") == true -> extraText
                extraText?.startsWith("http://") == true ||
                    extraText?.startsWith("https://") == true -> extraText
                else -> null
            }

            Log.d(TAG, "handleIntent: resolvedUrl=$resolvedUrl")

            when {
                resolvedUrl != null && resolvedUrl.startsWith("magnet:") -> {
                    // The user explicitly picked CloudStream from Android's "Open with"
                    // menu (or sent the magnet via ADB), so they have already consented
                    // to torrent playback for this stream. Auto-accept here, otherwise
                    // the in-app torrent gate at CS3IPlayer.kt would block with
                    // R.string.torrent_not_accepted if the user had ever declined a
                    // torrent earlier in the same process session — which is the most
                    // common cause of the "broken link" symptom for external magnets.
                    Torrent.hasAcceptedTorrentForThisSession = true
                    playLink(this, resolvedUrl)
                }
                resolvedUrl != null && (
                    resolvedUrl.startsWith("http://") ||
                        resolvedUrl.startsWith("https://")
                ) -> playLink(this, resolvedUrl)
                // Non-URL URIs (content://, file://) — let playUri handle them. playUri
                // also has its own magnet branch as a safety net.
                itemUri != null -> playUri(this, itemUri)
                data != null -> playUri(this, data)
                else -> {
                    Log.w(TAG, "handleIntent: no valid URL/URI found in intent, finishing")
                    finish()
                    return
                }
            }
        } else if (data?.scheme == "content") {
            playUri(this, data)
        } else {
            Log.w(TAG, "handleIntent: unhandled action=$action scheme=$scheme, finishing")
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        CommonActivity.setActivityInstance(this)
    }
}
