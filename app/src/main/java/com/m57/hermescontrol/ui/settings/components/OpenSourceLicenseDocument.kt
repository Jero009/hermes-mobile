package com.m57.hermescontrol.ui.settings.components

import androidx.annotation.RawRes
import com.m57.hermescontrol.R

internal data class OpenSourceLicenseDocument(
    val title: String,
    @param:RawRes val rawResourceId: Int,
)

internal fun openSourceLicenseDocuments(): List<OpenSourceLicenseDocument> =
    listOf(
        OpenSourceLicenseDocument("Apache License 2.0", R.raw.apache_license_2_0),
        OpenSourceLicenseDocument("Open-source notices", R.raw.open_source_notices),
    )
