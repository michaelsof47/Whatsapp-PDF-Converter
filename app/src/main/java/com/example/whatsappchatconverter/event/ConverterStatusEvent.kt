package com.example.whatsappchatconverter.event

import android.net.Uri

data class ConverterStatusEvent(
    val isCompleted: Boolean,
    val uri: Uri?,
)