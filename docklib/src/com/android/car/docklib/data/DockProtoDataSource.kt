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

package com.android.car.docklib.data

import com.android.car.carlaunchercommon.proto.ProtoDataSource
import com.android.car.docklib.DockItemProto.DockAppItemListMessage
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Proto file wrapper with helper methods
 * @param dataFile a file that the current user has read and write permission
 */
class DockProtoDataSource(dataFile: File) : ProtoDataSource<DockAppItemListMessage>(dataFile) {
    override fun parseDelimitedFrom(inputStream: InputStream?): DockAppItemListMessage {
        return DockAppItemListMessage.parseDelimitedFrom(inputStream)
    }

    override fun writeDelimitedTo(outputData: DockAppItemListMessage, outputStream: OutputStream?) {
        return outputData.writeDelimitedTo(outputStream)
    }
}
