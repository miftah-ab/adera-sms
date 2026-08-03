package com.adera.sms.data

import androidx.room.TypeConverter
import com.adera.sms.data.entity.CallStatus

/**
 * Room type converters for non-primitive types.
 * Registered on [AppDatabase] via @TypeConverters annotation.
 */
class Converters {
    @TypeConverter
    fun fromCallStatus(value: String?): CallStatus? =
        value?.let { CallStatus.valueOf(it) }

    @TypeConverter
    fun callStatusToString(status: CallStatus?): String? =
        status?.name
}
