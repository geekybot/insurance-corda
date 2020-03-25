package com.insurance.schema

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for CollectiblesState.
 */
object Collectibles

/**
 * An Collectibles schema.
 */
object Collectibles1 : MappedSchema(
        schemaFamily = Collectibles.javaClass,
        version = 1,
        mappedTypes = listOf(CompanyCollectiblesTable::class.java,PartnerCollectiblesTable::class.java,ExchangeRateTable::class.java)) {
    @Entity
    @Table(name = "CompanyCollectibles")
    class CompanyCollectiblesTable(
            @Column(name = "owner")
            var ownerName : String,

            @Column(name = "date")
            var date : String,

            @Column(name = "totalDue")
            var totalDue : Double,

            @Column(name = "collectedDue")
            var collectedDue : Double,

            @Column(name = "pendingDue")
            var pendingDue: Double,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this("","",0.0, 0.0,0.0, UUID.randomUUID())
    }

    @Entity
    @Table(name = "PartnerCollectibles")
    class PartnerCollectiblesTable(
            @Column(name = "owner")
            var ownerName : String,

            @Column(name = "partner")
            var partnerName : String,

            @Column(name = "date")
            var date : String,

            @Column(name = "totalDue")
            var totalDue : Double,

            @Column(name = "collectedDue")
            var collectedDue : Double,

            @Column(name = "pendingDue")
            var pendingDue: Double,

            @Column(name = "remittances")
            var remittances: Double,

            @Column(name = "totalInsured")
            var totalInsured: Int,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this("","","",0.0, 0.0,0.0, 0.0,0,UUID.randomUUID())
    }

    //Todo  remove this after the testing
    @Entity
    @Table(name = "ExchangeRateTable")
    class ExchangeRateTable(
            @Column(name = "timestamp")
            val timeStamp : String,

            @Column(name = "exchangeRate")
            val exchangeRate : Double,

            @Column(name = "foreignCurrencySymbol")
            val foreignCurrencySymbol : String,

            @Column(name = "nativeCurrencySymbol")
            val nativeCurrencySymbol : String,

            @Column(name="owner")
            val owner : String,

            @Column(name = "partner")
            val partner : String,

            @Column(name = "oracle")
            val oracle : String,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this("",0.0,"","","","","",UUID.randomUUID())
    }
}