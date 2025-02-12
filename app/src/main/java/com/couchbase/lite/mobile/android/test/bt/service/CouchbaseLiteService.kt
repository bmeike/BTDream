//
// Copyright (c) 2024 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.mobile.android.test.bt.service

import android.content.Context
import android.text.TextUtils
import com.couchbase.lite.Collection
import com.couchbase.lite.CouchbaseLite
import com.couchbase.lite.Database
import com.couchbase.lite.DatabaseConfiguration
import com.couchbase.lite.Document
import com.couchbase.lite.LogLevel
import com.couchbase.lite.internal.CouchbaseLiteInternal
import com.couchbase.lite.internal.core.C4Database
import com.couchbase.lite.logging.ConsoleLogSink
import com.couchbase.lite.logging.LogSinks
import com.couchbase.lite.mobile.android.test.eraseFileOrDir
import com.couchbase.lite.mobile.android.test.unzip
import java.io.File
import java.io.IOException


private const val ZIP_EXTENSION = ".zip"
private const val DB_EXTENSION = C4Database.DB_EXTENSION

fun Collection.fqn() = this.database.name + "." + this.fullName
fun Document.fullName() = (this.collection?.fullName ?: "???") + "." + this.id
fun Document.fqn() = (this.collection?.fqn() ?: "???") + "." + this.id


class CouchbaseLiteService(private val ctxt: Context) {
    companion object {
        private const val TAG = "DB_SVC"
    }

    init {
        CouchbaseLite.init(ctxt, true)
        LogSinks.get().console = ConsoleLogSink(LogLevel.DEBUG)
    }

    // ****************************************************************************************************
    // ************************************ U T I L I T Y   M E T H O D S *********************************
    // ****************************************************************************************************

    fun parseCollectionFullName(collName: String): Array<String> {
        val collScopeAndName = collName
            .split("\\.".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()
        if (collScopeAndName.size != 2
            || TextUtils.isEmpty(collScopeAndName[0])
            || TextUtils.isEmpty(collScopeAndName[1])
        ) {
            throw IllegalArgumentException("Cannot parse collection name: $collName")
        }
        return collScopeAndName
    }

    fun parseCollectionFQN(collFQN: String): Array<String> {
        val collDbScopeAndName = collFQN
            .split("\\.".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()
        if ((collDbScopeAndName.size != 3)
            || TextUtils.isEmpty(collDbScopeAndName[0])
            || TextUtils.isEmpty(collDbScopeAndName[1])
            || TextUtils.isEmpty(collDbScopeAndName[2])
        ) {
            throw IllegalArgumentException("Cannot parse collection fqn: $collFQN")
        }
        return collDbScopeAndName
    }

    fun unzipDb(assetName: String, dbName: String): Database {
        val zipDir = File(CouchbaseLiteInternal.getDefaultDbDir(), "zipDir").canonicalFile
        zipDir.eraseFileOrDir()

        if (!zipDir.mkdirs()) {
            throw IOException("Failed creating directory ${zipDir.canonicalPath}")
        }
        unzip(ctxt.assets.open("${assetName}$DB_EXTENSION$ZIP_EXTENSION"), zipDir)

        return copyDb(zipDir.absolutePath, assetName, dbName)
    }

    fun copyDb(srcPath: String, srcName: String, dstDbName: String): Database {
        val srcDbFile = File(srcPath, "${srcName}$DB_EXTENSION")
        val config = DatabaseConfiguration()
        File(config.directory, "${dstDbName}$DB_EXTENSION").eraseFileOrDir()
        Database.copy(srcDbFile, dstDbName, config)
        return Database(dstDbName, config)
    }
}


