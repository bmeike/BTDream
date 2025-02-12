//
// Copyright (c) 2025 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.mobile.android.test.bt.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.couchbase.lite.mobile.android.test.bt.R
import com.couchbase.lite.mobile.android.test.bt.vm.BTViewModel
import com.couchbase.lite.mobile.android.test.bt.vm.WifiViewModel
import kotlinx.serialization.Serializable

@Serializable
object Bluetooth

@Serializable
object Wifi

data class TopLevelRoute<T : Any>(val route: T, val icon: Int, val label: Int)

val topLevelRoutes = listOf(
    TopLevelRoute(Bluetooth, R.mipmap.ic_bt1_foreground, R.string.bluetooth),
    TopLevelRoute(Wifi, R.mipmap.ic_wifi1_foreground, R.string.wifi)
)

@Composable
fun BTDreamUI() {
}

@Composable
fun BTScreen(btModel: BTViewModel, wifiModel: WifiViewModel) {
    wifiModel.stop()
    ItemList("Bluetooth", btModel.start())
}

@Composable
fun WifiScreen(btModel: BTViewModel, wifiModel: WifiViewModel) {
    btModel.stop()
    ItemList("Wifi", wifiModel.start())
}

@Composable
fun Title(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(text, Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun BottomNav(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    NavigationBar {
        topLevelRoutes.forEach { topLevelRoute ->
            val label = stringResource(topLevelRoute.label)
            NavigationBarItem(
                label = { Text(label) },
                icon = {
                    Icon(
                        painter = painterResource(topLevelRoute.icon),
                        contentDescription = label
                    )
                },
                selected = currentDestination?.hierarchy?.any { it.hasRoute(topLevelRoute.route::class) } == true,
                onClick = {
                    navController.navigate(topLevelRoute.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun ItemList(label: String, items: SnapshotStateList<String>) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        Title(label)
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(items.toList()) { item ->
                Text(text = item, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge)
                HorizontalDivider(color = liteTertiary)
            }
        }
    }
}
