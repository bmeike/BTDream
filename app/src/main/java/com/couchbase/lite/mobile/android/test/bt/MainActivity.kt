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
package com.couchbase.lite.mobile.android.test.bt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.couchbase.lite.mobile.android.test.bt.ui.BTDreamTheme
import com.couchbase.lite.mobile.android.test.bt.ui.BTDreamUI
import com.couchbase.lite.mobile.android.test.bt.ui.BTScreen
import com.couchbase.lite.mobile.android.test.bt.ui.Bluetooth
import com.couchbase.lite.mobile.android.test.bt.ui.BottomNav
import com.couchbase.lite.mobile.android.test.bt.ui.Wifi
import com.couchbase.lite.mobile.android.test.bt.ui.WifiScreen
import com.couchbase.lite.mobile.android.test.bt.vm.BTViewModel
import com.couchbase.lite.mobile.android.test.bt.vm.WifiViewModel
import org.koin.androidx.viewmodel.ext.android.getViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BTDreamTheme {
                val navController = rememberNavController()
                Scaffold(bottomBar = { BottomNav(navController) }) { innerPadding ->
                    NavHost(navController, startDestination = Wifi, Modifier.padding(innerPadding)) {
                        composable<Bluetooth> {
                            BTScreen(getViewModel<BTViewModel>(), getViewModel<WifiViewModel>())
                        }
                        composable<Wifi> {
                            WifiScreen(getViewModel<BTViewModel>(), getViewModel<WifiViewModel>())
                        }
                    }
                }
            }
        }
    }
}

@Preview(apiLevel = 33, showBackground = true)
@Composable
fun PagePreview() = BTDreamTheme { BTDreamUI() }