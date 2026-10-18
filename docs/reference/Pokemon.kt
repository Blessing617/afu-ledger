/*
 * Designed and developed by 2024 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.skydoves.pokedex.compose.core.model

import android.annotation.SuppressLint
import android.os.Parcel
import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@SuppressLint("BanParcelableUsage") // TODO(b/374318532): Migrate to VersionedParcelable
@Immutable
@Serializable
data class Pokemon(val name: String, val imageUrl: String) : Parcelable {

    constructor(parcel: Parcel) : this(parcel.readString()!!, parcel.readString()!!)

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(imageUrl)
    }

    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<Pokemon> {
        override fun createFromParcel(parcel: Parcel) = Pokemon(parcel)

        override fun newArray(size: Int): Array<Pokemon?> = arrayOfNulls(size)
    }
}
