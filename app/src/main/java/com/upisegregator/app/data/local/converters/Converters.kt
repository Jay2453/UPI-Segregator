package com.upisegregator.app.data.local.converters

import androidx.room.TypeConverter
import com.upisegregator.app.data.local.entity.PaymentMethod
import com.upisegregator.app.data.local.entity.TransactionDirection
import com.upisegregator.app.data.local.entity.TransactionSource
import com.upisegregator.app.data.local.entity.TransactionStatus

/**
 * Room type converters for persisting Enum types safely as Strings.
 */
class Converters {

    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod?): String? = value?.name

    @TypeConverter
    fun toPaymentMethod(value: String?): PaymentMethod =
        value?.let { runCatching { PaymentMethod.valueOf(it) }.getOrDefault(PaymentMethod.OTHER) }
            ?: PaymentMethod.OTHER

    @TypeConverter
    fun fromTransactionStatus(value: TransactionStatus?): String? = value?.name

    @TypeConverter
    fun toTransactionStatus(value: String?): TransactionStatus =
        value?.let { runCatching { TransactionStatus.valueOf(it) }.getOrDefault(TransactionStatus.UNKNOWN) }
            ?: TransactionStatus.UNKNOWN

    @TypeConverter
    fun fromTransactionSource(value: TransactionSource?): String? = value?.name

    @TypeConverter
    fun toTransactionSource(value: String?): TransactionSource =
        value?.let { runCatching { TransactionSource.valueOf(it) }.getOrDefault(TransactionSource.MANUAL) }
            ?: TransactionSource.MANUAL

    @TypeConverter
    fun fromTransactionDirection(value: TransactionDirection?): String? = value?.name

    @TypeConverter
    fun toTransactionDirection(value: String?): TransactionDirection =
        value?.let { runCatching { TransactionDirection.valueOf(it) }.getOrDefault(TransactionDirection.DEBIT) }
            ?: TransactionDirection.DEBIT
}
