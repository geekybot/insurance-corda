package com.insurance.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for UserDetailsSchema.
 */
object UserDetailsSchema

/**
 * An UserDetailsSchema schema.
 */
object UserDetailsSchema1 : MappedSchema(
        schemaFamily = UserDetailsSchema.javaClass,
        version = 1,
        mappedTypes = listOf(UserDetailsTable::class.java)) {
    @Entity
    @Table(name = "UserDetails")
    class UserDetailsTable(
            @Column(name = "userId")
            var userId: String,

            @Column(name = "name")
            var name: String,

            @Column(name = "type")
            var type : String,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this("","","", UUID.randomUUID())
    }
}