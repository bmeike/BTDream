//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.mobile.android.test.bt.app

import android.app.Application
import com.couchbase.lite.mobile.android.test.bt.provider.ble.BLEService
import com.couchbase.lite.mobile.android.test.bt.vm.BTViewModel
import com.couchbase.lite.mobile.android.test.bt.vm.WifiViewModel
import com.couchbase.lite.mobile.android.test.bt.provider.wifi.WifiService
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.GlobalContext
import org.koin.dsl.module


class BTDreamApp : Application() {
    @Suppress("USELESS_CAST")
    override fun onCreate() {
        super.onCreate()

        // Enable Koin dependency injection framework
        GlobalContext.startKoin {
            // inject Android context
            androidContext(this@BTDreamApp)

            // dependency register modules
            modules(
                module {
                    single { BLEService(get()) as BLEService }
                    single { WifiService(get()) as WifiService }

                    viewModel { BTViewModel(get()) }
                    viewModel { WifiViewModel(get()) }
                })
        }
    }
}
