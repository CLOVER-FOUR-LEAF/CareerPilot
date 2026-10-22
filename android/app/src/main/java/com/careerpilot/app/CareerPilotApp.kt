package com.careerpilot.app

import android.app.Application
import com.careerpilot.app.data.repo.ServiceLocator

class CareerPilotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
