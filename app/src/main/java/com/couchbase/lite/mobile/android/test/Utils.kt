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
package com.couchbase.lite.mobile.android.test

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

private const val TAG = "UTIL"


fun unzip(src: InputStream, dst: File?) {
    val buffer = ByteArray(1024)
    src.use { sis ->
        ZipInputStream(sis).use { zis ->
            while (true) {
                val ze = zis.nextEntry ?: break
                val newFile = File(dst, ze.name)
                if (ze.isDirectory) {
                    newFile.mkdirs()
                } else {
                    File(newFile.parent!!).mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        while (true) {
                            val l = zis.read(buffer)
                            if (l <= 0) break
                            fos.write(buffer, 0, l)
                        }
                    }
                }
            }
            zis.closeEntry()
        }
    }
}

fun currentDirectory(): File = File("").canonicalFile

fun verifyDir(dirPath: String) = File(dirPath, "directory path").verifyDir()

fun File.verifyDir(): File {
    var err: IOException? = null
    try {
        val d = this.canonicalFile
        if (d.exists() && d.isDirectory || d.mkdirs()) {
            return d
        }
    } catch (e: IOException) {
        err = e
    }
    throw IllegalStateException("Cannot create or access directory at $this", err)
}

fun copyFile(src: InputStream, out: OutputStream) {
    val buffer = ByteArray(1024)
    while (true) {
        val l = src.read()
        if (l <= 0) break
        out.write(buffer, 0, l)
    }
}

fun eraseFileOrDir(fileOrDirectory: String) = File(fileOrDirectory).eraseFileOrDir()

fun File.eraseFileOrDir() = this.deleteRecursive()

fun deleteContents(fileOrDirectory: String?) =
    if (fileOrDirectory == null) null else deleteContents(File(fileOrDirectory))

fun deleteContents(fileOrDirectory: File?): Boolean {
    if (fileOrDirectory == null || !fileOrDirectory.isDirectory) {
        return true
    }

    val contents = fileOrDirectory.listFiles() ?: return true
    var succeeded = true
    for (file in contents) {
        if (!file.deleteRecursive()) {
            Log.i(TAG, "Failed deleting file: $file")
            succeeded = false
        }
    }
    return succeeded
}

fun File.deleteRecursive() = !this.exists() || deleteContents(this) && this.delete()

fun setPermissionRecursive(fileOrDirectory: File, readable: Boolean, writable: Boolean): Boolean {
    if (fileOrDirectory.isDirectory) {
        val files = fileOrDirectory.listFiles()
        if (files != null) {
            for (child in files) {
                setPermissionRecursive(child, readable, writable)
            }
        }
    }
    return fileOrDirectory.setReadable(readable) && fileOrDirectory.setWritable(writable)
}


