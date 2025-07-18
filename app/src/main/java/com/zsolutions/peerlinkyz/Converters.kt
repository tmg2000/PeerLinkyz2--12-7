package com.zsolutions.peerlinkyz

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromByteArray(value: ByteArray): String {
        return String(value, Charsets.ISO_8859_1)
    }

    @TypeConverter
    fun toByteArray(value: String): ByteArray {
        return value.toByteArray(Charsets.ISO_8859_1)
    }
}