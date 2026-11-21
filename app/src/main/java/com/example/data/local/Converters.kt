package com.example.data.local

import androidx.room.TypeConverter
import com.example.domain.NotificationType
import com.example.domain.RecurrenceFrequency
import com.example.domain.ThemePreference
import com.example.domain.TransactionType

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType =
        enumValueOf<TransactionType>(value)

    @TypeConverter
    fun fromRecurrenceFrequency(value: RecurrenceFrequency): String = value.name

    @TypeConverter
    fun toRecurrenceFrequency(value: String): RecurrenceFrequency =
        enumValueOf<RecurrenceFrequency>(value)

    @TypeConverter
    fun fromThemePreference(value: ThemePreference): String = value.name

    @TypeConverter
    fun toThemePreference(value: String): ThemePreference =
        enumValueOf<ThemePreference>(value)

    @TypeConverter
    fun fromNotificationType(value: NotificationType): String = value.name

    @TypeConverter
    fun toNotificationType(value: String): NotificationType =
        enumValueOf<NotificationType>(value)
}
