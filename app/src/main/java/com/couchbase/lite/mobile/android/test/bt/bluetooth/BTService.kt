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
package com.couchbase.lite.mobile.android.test.bt.bluetooth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds

class BTService {
    companion object {
        private const val TAG = "BT_SVC"
    }

    private var i = -1

    fun startDiscovery(): Flow<List<String>> {
        i = 0
        android.util.Log.i(TAG, "Starting")
        return flow {
            while (i >= 0) {
                i++
                android.util.Log.i(TAG, "Emitting BT#$i")
                emit(listOf("BT#$i"))
                delay(10.seconds)
            }
            android.util.Log.i(TAG, "Stopped")
        }
    }

    fun stopDiscovery() {
        android.util.Log.i(TAG, "Stopping")
        i = -1
    }
}
