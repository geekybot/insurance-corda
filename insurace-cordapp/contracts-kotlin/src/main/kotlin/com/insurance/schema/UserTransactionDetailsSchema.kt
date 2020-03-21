package com.insurance.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for [com.insurance.state.UserTransactionState].
 */
object UserTransactionDetailsSchema

/**
 * An UserTransactionState schema.
 */
object UserTransactionDetailsSchema1 : MappedSchema(
        schemaFamily = UserTransactionDetailsSchema.javaClass,
        version = 1,
        mappedTypes = listOf(UserTransactionDetailsTable::class.java)) {
    @Entity
    @Table(name = "UserTransactionDetailsTable")
    class UserTransactionDetailsTable(
            @Column(name = "userId")
            var userId: String,

            @Column(name = "Date")
            var date: String,

            @Column(name = "totalAmountToBePaid")
            var totalAmountToBePaid: Double,

            @Column(name = "AmountPaidInNativeCurrency")
            var amountPaidInNativeCurrency: Double,

            @Column(name = "NativeCurrencyName")
            var nativeCurrencyName : String,

            @Column(name = "AmountPaidInForeignCurrency")
            var amountPaidInForeignCurrency: Double,

            @Column(name = "ForeignCurrencyName")
            var foreignCurrencyName : String,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this("","",0.0, 0.0,"", 0.0,"", UUID.randomUUID())
    }
}