package com.ablecredit.manualkotlinclient.ui.hostdemo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ablecredit.manualkotlinclient.R

/**
 * Host-owned screen shown inside the SDK's HOST step. One Activity reused for Video (position 0),
 * cl1 (1) and cl2 (2): each screen opens the next on top of itself, the last one hands control back
 * to the SDK. System back needs no code, the screens sit on top of each other.
 */
class HostScreenActivity : AppCompatActivity() {

    private val screens = listOf("Video", "cl1", "cl2")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host_screen)

        val applicationId = intent.getStringExtra(EXTRA_APPLICATION_ID).orEmpty()
        val position = intent.getIntExtra(EXTRA_POSITION, 0)
        val isLast = position == screens.lastIndex

        findViewById<TextView>(R.id.tv_host_title).text = "Host screen: ${screens[position]}"
        findViewById<TextView>(R.id.tv_host_application_id).text = applicationId

        findViewById<Button>(R.id.btn_host_next).apply {
            text = if (isLast) "Resume SDK" else "Next host screen"
            setOnClickListener {
                if (isLast) {
                    // SDK call: continue the flow, keep these screens on the back stack so back
                    // from the next SDK step walks cl2 -> cl1 -> Video -> Photos.
                    HostFlowHolder.controller?.proceed(keepHostScreens = true)
                } else {
                    startActivity(intentFor(this@HostScreenActivity, applicationId, position + 1))
                }
            }
        }
    }

    companion object {
        private const val EXTRA_APPLICATION_ID = "applicationId"
        private const val EXTRA_POSITION = "position"

        fun intentFor(context: android.content.Context, applicationId: String, position: Int) =
            Intent(context, HostScreenActivity::class.java)
                .putExtra(EXTRA_APPLICATION_ID, applicationId)
                .putExtra(EXTRA_POSITION, position)
    }
}
