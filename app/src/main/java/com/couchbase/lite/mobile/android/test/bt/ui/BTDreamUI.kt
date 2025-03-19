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

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.couchbase.lite.mobile.android.test.bt.vm.ServiceModel
import com.couchbase.lite.mobile.android.test.bt.vm.WifiViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.serialization.Serializable


@Serializable
data object Bluetooth

@Serializable
data object Wifi

data class TopLevelRoute<T : Any>(val route: T, val icon: Int)

val Permissions = mapOf(
    Manifest.permission.BLUETOOTH_SCAN to "Bluetooth Scan",
    Manifest.permission.BLUETOOTH_ADVERTISE to "Bluetooth Advertise",
    Manifest.permission.BLUETOOTH_CONNECT to "Bluetooth Connect",
    Manifest.permission.ACCESS_WIFI_STATE to "Wifi Access State",
    Manifest.permission.CHANGE_WIFI_STATE to "Wifi Change State",
    Manifest.permission.ACCESS_NETWORK_STATE to "Net Access State",
    Manifest.permission.CHANGE_NETWORK_STATE to "Net Change State",
    Manifest.permission.ACCESS_COARSE_LOCATION to "Coarse Location",
    Manifest.permission.ACCESS_FINE_LOCATION to "Fine Location",
    Manifest.permission.NEARBY_WIFI_DEVICES to "Nearby Wifi Devices",
)


val topLevelRoutes = listOf(
    TopLevelRoute(Bluetooth, R.mipmap.ic_bt1_foreground),
    TopLevelRoute(Wifi, R.mipmap.ic_wifi1_foreground)
)

@OptIn(ExperimentalPermissionsApi::class)
data class PermissionsState(val showRational: MutableState<Boolean>, val state: MultiplePermissionsState)

@Composable
fun BottomNav(nav: NavController) {
    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    NavigationBar {
        topLevelRoutes.forEach { topLevelRoute ->
            val label = topLevelRoute.route.toString()
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
                    nav.navigate(topLevelRoute.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun BTScreen(model: BTViewModel) {
    ItemList(Bluetooth.toString(), model)
}

@Composable
fun WifiScreen(model: WifiViewModel) {
    ItemList(Wifi.toString(), model)
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ItemList(label: String, model: ServiceModel) {
    val peers by remember { model.peers }

    val permState = PermissionsState(
        remember { mutableStateOf(false) },
        rememberMultiplePermissionsState(permissions = model.PERMISSIONS)
    )

    if (permState.showRational.value) {
        ShowRational(permState.state.revokedPermissions.map { it.permission }, permState.showRational)
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        Title(label)

        if (!permState.state.allPermissionsGranted) {
            RequestPermissions(permState)
        } else {
            LaunchedEffect(Unit) { model.start() }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(peers.toList()) { item ->
                    Text(
                        text = item,
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    HorizontalDivider(color = liteTertiary)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestPermissions(permState: PermissionsState) {
    android.util.Log.i("#####", "PERMISSIONS: ${permState.state.permissions.map { it.permission }}")
    android.util.Log.i("#####", "REVOKED: ${permState.state.revokedPermissions.map { it.permission }}")
    android.util.Log.i("#####", "GRANTED: ${permState.state.allPermissionsGranted}")
    android.util.Log.i("#####", "RATIONAL: ${permState.state.shouldShowRationale}")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Button(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            onClick = {
                if (!permState.state.shouldShowRationale) {
                    permState.state.launchMultiplePermissionRequest()
                }
                permState.showRational.value = permState.state.shouldShowRationale
            }) {
            Text(text = "Request Permissions")
        }
    }
}

@Composable
fun ShowRational(revoked: List<String>, showRationalDialog: MutableState<Boolean>) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { showRationalDialog.value = false },
        title = { Text(text = "Permission") },
        text = { Text(text = String.format(stringResource(R.string.permissions_required), assembleRational(revoked))) },
        confirmButton = {
            TextButton(onClick = {
                showRationalDialog.value = false
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent, null)
            }) {
                Text("Accept")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    showRationalDialog.value = false
                }) {
                Text("Refuse")
            }
        }
    )
}

private fun assembleRational(revoked: List<String>): String {
    var rational = ""
    for (perm in revoked) {
        if (rational.isNotEmpty()) {
            rational += ", "
        }
        rational += Permissions[perm]
    }
    return rational
}