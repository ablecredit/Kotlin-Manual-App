package com.ablecredit.manualkotlinclient.ui.scenarios

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.ablecredit.sdk.model.AbleCreditFlowController

/**
 * Host-owned screens shown INSIDE an SDK flow's HOST step (the client's own "Video" screen, or the
 * chain Video → cl1 → cl2). They are launched from the SDK Activity handed to onHostStep, so they
 * sit on top of the SDK's own Activities in the same task.
 *
 * Each screen starts the next one on top of itself, so system back pops them natively. The last
 * screen hands control back with `controller.proceed(keepHostScreens = true)`, which leaves the
 * chain on the stack: back from the SDK's next step walks cl2 → cl1 → Video → Photos with no
 * rebuilding. The single-screen variant uses plain `proceed()`.
 */
class ClientChainActivity : Activity() {

    companion object {
        private const val EXTRA_APPLICATION_ID = "extra_application_id"
        private const val EXTRA_POSITION = "extra_position"
        private const val EXTRA_CHAIN = "extra_chain"

        const val AC_LOG_TAG = "AC_LOG_FLOW"

        fun acLog(event: String, detail: String = "") {
            Log.d(AC_LOG_TAG, "AC_LOG_$event" + if (detail.isNotEmpty()) " | $detail" else "")
        }

        private val CHAIN_SCREENS = listOf("Video", "cl1", "cl2")
        private val SINGLE_SCREENS = listOf("Video")

        // The controller can't ride an Intent; every screen of the chain shares the one handed to
        // the current host step.
        private var controller: AbleCreditFlowController? = null

        fun start(activity: Activity, applicationId: String, chain: Boolean, flowController: AbleCreditFlowController) {
            controller = flowController
            // Started from the SDK Activity passed to onHostStep so the screens land in the same task.
            activity.startActivity(intentFor(activity, applicationId, 0, chain))
        }

        private fun intentFor(context: Context, applicationId: String, position: Int, chain: Boolean) =
            Intent(context, ClientChainActivity::class.java)
                .putExtra(EXTRA_APPLICATION_ID, applicationId)
                .putExtra(EXTRA_POSITION, position)
                .putExtra(EXTRA_CHAIN, chain)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val applicationId = intent.getStringExtra(EXTRA_APPLICATION_ID).orEmpty()
        val position = intent.getIntExtra(EXTRA_POSITION, 0)
        val chain = intent.getBooleanExtra(EXTRA_CHAIN, false)
        val screens = if (chain) CHAIN_SCREENS else SINGLE_SCREENS
        val name = screens[position]
        val isLast = position == screens.lastIndex

        acLog("hostScreenShown", "screen=$name position=$position chain=$chain")

        setContentView(buildContent(applicationId, name, position, screens.size, isLast, chain))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun buildContent(
        applicationId: String,
        name: String,
        position: Int,
        total: Int,
        isLast: Boolean,
        chain: Boolean,
    ): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            // Edge-to-edge is enforced on recent target SDKs: pad for the system bars so the
            // header (and its back arrow) isn't drawn under the status bar.
            fitsSystemWindows = true
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(8), 0, dp(16), 0)
        }
        header.addView(
            TextView(this).apply {
                text = "←"
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
                setOnClickListener { onBackPressed() }
            },
            LinearLayout.LayoutParams(dp(48), dp(56)),
        )
        header.addView(
            TextView(this).apply {
                text = "Client screen: $name"
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            },
        )
        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))

        val banner = TextView(this).apply {
            text = "SIMULATED CLIENT SCREEN (host step)"
            setBackgroundColor(Color.parseColor("#FFF3CD"))
            setTextColor(Color.parseColor("#856404"))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        root.addView(banner, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        body.addView(
            TextView(this).apply {
                text = "Application ID\n$applicationId"
                setTextColor(Color.BLACK)
                textSize = 13f
                gravity = Gravity.CENTER
            },
        )
        body.addView(
            TextView(this).apply {
                val which = "Host screen ${position + 1} of $total ($name) inside one SDK host step. "
                text = which + if (isLast) {
                    if (chain) {
                        "Resume SDK continues the flow on top of this chain; back from the SDK then walks " +
                            "cl2 → cl1 → Video → Photos."
                    } else {
                        "Resume SDK continues the flow with audio + photos round 2; back from Audio returns " +
                            "to the Video screen via onHostStep(BACKWARD)."
                    }
                } else {
                    "Next opens the following host screen; system back pops this chain natively."
                }
                setTextColor(Color.parseColor("#555555"))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, 0)
            },
        )
        root.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(
            Button(this).apply {
                text = if (isLast) "Resume SDK" else "Next client screen"
                isAllCaps = false
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.BLACK)
                setOnClickListener {
                    if (isLast) {
                        if (chain) {
                            acLog("controller.proceed", "screen=$name keepHostScreens=true")
                            controller?.proceed(keepHostScreens = true)
                        } else {
                            acLog("controller.proceed", "screen=$name keepHostScreens=false")
                            controller?.proceed()
                        }
                    } else {
                        acLog("hostScreenNext", "from=$name")
                        startActivity(intentFor(this@ClientChainActivity, applicationId, position + 1, chain))
                    }
                }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
                setMargins(dp(16), dp(8), dp(16), dp(16))
            },
        )
        return root
    }

    override fun onBackPressed() {
        acLog("hostScreenBack", "position=${intent.getIntExtra(EXTRA_POSITION, 0)}")
        super.onBackPressed()
    }
}
