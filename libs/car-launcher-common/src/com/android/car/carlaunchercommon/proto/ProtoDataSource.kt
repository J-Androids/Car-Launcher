/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.carlaunchercommon.proto

import android.util.Log
import com.google.protobuf.MessageLite
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Class level abstraction representing a proto file holding app data.
 *
 * Only a single controller should hold reference to this class. All methods that perform read or
 * write operations must be thread safe and idempotent.
 *
 * @param <T> the proto object type that this data file is holding
</T> */
// TODO: b/301482942 This class is copied from AppGrid. We should reuse it in AppGrid
abstract class ProtoDataSource<T : MessageLite>(private val dataFile: File) {
    private var mInputStream: FileInputStream? = null
    private var mOutputStream: FileOutputStream? = null

    /**
     * @return true if the file exists on disk, and false otherwise.
     */
    fun exists(): Boolean {
        return try {
            dataFile.exists() && dataFile.canRead()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Writes the [MessageLite] subclass T to the file represented by this object.
     * This method will write data as bytes to the declared file using protobuf library.
     */
    fun writeToFile(data: T) {
        try {
            if (mOutputStream == null) {
                mOutputStream = FileOutputStream(dataFile, false)
            }
            writeDelimitedTo(data, mOutputStream)
        } catch (e: IOException) {
            Log.e(TAG, "Dock item list not written to file successfully.", e)
        } finally {
            try {
                mOutputStream?.apply {
                    flush()
                    fd.sync()
                    close()
                }
                mOutputStream = null
            } catch (e: IOException) {
                Log.e(TAG, "Unable to close output stream. ")
            }
        }
    }

    /**
     * Reads the [MessageLite] subclass T from the file represented by this object.
     * This method will parse the bytes using protobuf library.
     */
    fun readFromFile(): T? {
        if (!exists()) {
            Log.e(TAG, "File does not exist. Cannot read from file.")
            return null
        }
        var result: T? = null
        try {
            if (mInputStream == null) {
                mInputStream = FileInputStream(dataFile)
            }
            result = parseDelimitedFrom(mInputStream)
        } catch (e: IOException) {
            Log.e(TAG, "Read from input stream not successfully")
        } finally {
            try {
                mInputStream?.close()
                mInputStream = null
            } catch (e: IOException) {
                Log.e(TAG, "Unable to close input stream")
            }
        }
        return result
    }

    /**
     * This method will be called by [ProtoDataSource.readFromFile].
     *
     * Implementation is left to subclass since [MessageLite.parseDelimitedFrom]
     * requires a defined class at compile time. Subclasses should implement this method by directly
     * calling YourMessageType.parseDelimitedFrom(inputStream) here.
     *
     * @param inputStream the input stream to be which the data source should read from.
     * @return the object T written to this file.
     * @throws IOException an IOException for when reading from proto fails.
     */
    @Throws(IOException::class)
    protected abstract fun parseDelimitedFrom(inputStream: InputStream?): T?

    /**
     * This method will be called by [ProtoDataSource.writeToFile].
     *
     * Implementation is left to subclass since [MessageLite.writeDelimitedTo]
     * requires a defined class at compile time. Subclasses should implement this method by directly
     * calling T.writeDelimitedTo(outputStream) here.
     *
     * @param outputData the output data T to be written to the file.
     * @param outputStream the output stream which the data should be written to.
     * @throws IOException an IO Exception for when writing to proto fails.
     */
    @Throws(IOException::class)
    protected abstract fun writeDelimitedTo(outputData: T, outputStream: OutputStream?)

    companion object {
        private const val TAG = "ProtoDataSource"
    }

    override fun toString(): String {
        return dataFile.absolutePath
    }
}
