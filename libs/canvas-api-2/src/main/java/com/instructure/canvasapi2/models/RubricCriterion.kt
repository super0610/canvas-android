/*
 * Copyright (C) 2017 - present Instructure, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.instructure.canvasapi2.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize
import java.util.*

@Parcelize
data class RubricCriterion(
        val id: String? = null,
        val description: String? = null,
        @SerializedName("long_description")
        val longDescription: String? = null,
        val points: Double = 0.0,
        val ratings: MutableList<RubricCriterionRating> = ArrayList(),
        @SerializedName("criterion_use_range")
        val criterionUseRange: Boolean = false,
        @SerializedName("ignore_for_scoring")
        val ignoreForScoring: Boolean = false
) : Parcelable