package com.ablecredit.manualkotlinclient

import android.app.Application
import com.ablecredit.sdk.manager.AbleCredit

class ManualKotlinClientApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AbleCredit.initialize(this)
    }
}
