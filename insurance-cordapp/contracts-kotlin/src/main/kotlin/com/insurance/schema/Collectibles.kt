package com.insurance.schema

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
        mappedTypes = listOf(CompanyCollectiblesTable::class.java,PartnerCollectiblesTable::class.java)) {
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

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this("","","",0.0, 0.0,0.0, 0.0,UUID.randomUUID())
    }
}