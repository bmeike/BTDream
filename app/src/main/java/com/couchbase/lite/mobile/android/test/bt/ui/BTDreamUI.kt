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
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.couchbase.lite.mobile.android.test.bt.provider.Peer
import com.couchbase.lite.mobile.android.test.bt.vm.BTViewModel
import com.couchbase.lite.mobile.android.test.bt.vm.ProviderViewModel
import com.couchbase.lite.mobile.android.test.bt.vm.WifiViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.serialization.Serializable


private const val TAG = "BT_DREAM_UI"

@SuppressLint("InlinedApi")
private val PERMISSION_RATIONALS = mapOf(
    // Android bluetooth permissions for all API versions
    Manifest.permission.ACCESS_COARSE_LOCATION to "Coarse Location: required for the app to get BLE scan results. Used only to get scan results; never for location.",
    Manifest.permission.ACCESS_FINE_LOCATION to "Fine Location required for the app to get BLE scan results. Used only to get scan results; never for location.",

    // Android bluetooth 11 and below permissions
    Manifest.permission.BLUETOOTH to "Bluetooth: allows the app to connect to Bluetooth devices",
    Manifest.permission.BLUETOOTH_ADMIN to "Bluetooth Admin: allows the app to scan for Bluetooth devices",

    // Android 12 permissions
    Manifest.permission.BLUETOOTH_SCAN to "Bluetooth Scan: allows the app to scan for Bluetooth devices",
    Manifest.permission.BLUETOOTH_ADVERTISE to "Bluetooth Advertise: allows the app to advertise itself to other Bluetooth devices",
    Manifest.permission.BLUETOOTH_CONNECT to "Bluetooth Connect: allows the app to connect to other Bluetooth devices",

    Manifest.permission.ACCESS_WIFI_STATE to "Wifi Access State",
    Manifest.permission.CHANGE_WIFI_STATE to "Wifi Change State",
    Manifest.permission.ACCESS_NETWORK_STATE to "Net Access State",
    Manifest.permission.CHANGE_NETWORK_STATE to "Net Change State",

    // Android 13 permissions
    Manifest.permission.NEARBY_WIFI_DEVICES to "Nearby Wifi Devices",
)

private data class TopLevelRoute<T : Any>(val route: T, val icon: Int)

private val TOP_LEVEL_ROUTES = listOf(
    TopLevelRoute(Bluetooth, R.mipmap.ic_bt1_foreground),
    TopLevelRoute(Wifi, R.mipmap.ic_wifi1_foreground)
)

private val TEXT_COLOR = mapOf(
    Peer.State.DISCOVERED to darkVisibleText,
    Peer.State.CONNECTED to darkConnectedText
)

@Serializable
data object Bluetooth

@Serializable
data object Wifi

@Composable
fun BottomNav(nav: NavController) {
    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    NavigationBar {
        TOP_LEVEL_ROUTES.forEach { topLevelRoute ->
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
fun ItemList(label: String, model: ProviderViewModel) {
    val peers by remember { model.peers }
    val text = remember { mutableStateMapOf<String, String>() }

    val showRational = remember { mutableStateOf(false) }
    val permissionState = rememberMultiplePermissionsState(
        model.getRequiredPermissions(LocalContext.current.findActivity()).toList()
    )

    if (showRational.value) {
        ShowRational(permissionState.revokedPermissions.map { it.permission }, showRational)
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        Title(label)

        Log.d(TAG, "Needed permissions: ${permissionState.revokedPermissions.map { it.permission }}")
        if (!permissionState.allPermissionsGranted) {
            RequestPermissions(permissionState, showRational)
        } else {
            LaunchedEffect(Unit) { discoverPeers(model) }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(items = peers.keys.toList()) { peer ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(top = 12.dp)
                    ) {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .padding(top = 8.dp)
                                .clickable { model.connectPeer(peer) },
                            color = TEXT_COLOR[peer.state] ?: Color.Black,
                            style = MaterialTheme.typography.bodyMedium,
                            text = peer.toString()
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .padding(top = 8.dp)
                        ) {
                            val id = peer.id
                            TextField(
                                modifier = Modifier
                                    .weight(1.0f)
                                    .wrapContentHeight()
                                    .padding(top = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = TextFieldDefaults.colors().copy(unfocusedIndicatorColor = darkSurface),
                                textStyle = MaterialTheme.typography.bodyLarge,
                                singleLine = true,
                                value = text[id] ?: "",
                                onValueChange = { text[id] = it })
                            Button(
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .align(Alignment.CenterVertically)
                                    .wrapContentHeight()
                                    .wrapContentWidth(),
                                onClick = {
                                    model.sendToPeer(peer, text[id] ?: "")
                                    text[id] = ""
                                }
                            ) {
                                Text(text = "Send")
                            }
                        }

                        HorizontalDivider(color = liteTertiary)
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestPermissions(state: MultiplePermissionsState, showRational: MutableState<Boolean>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Button(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            onClick = { requestPermissions(state, showRational) }
        ) {
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

@OptIn(ExperimentalPermissionsApi::class)
private fun requestPermissions(state: MultiplePermissionsState, showRational: MutableState<Boolean>) {
    if (!showRational.value) {
        state.launchMultiplePermissionRequest()
    }
    showRational.value = state.shouldShowRationale
}

private fun discoverPeers(model: ProviderViewModel) {
    model.startPublishing()
    model.startBrowsing()
}

private fun assembleRational(revoked: List<String>) = revoked.map { PERMISSION_RATIONALS[it] }
    .joinToString("\n")

fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    throw IllegalStateException("findActivity must be called in the context of an Activity")
}
