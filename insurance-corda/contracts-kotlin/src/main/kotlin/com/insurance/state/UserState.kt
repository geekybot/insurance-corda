package com.insurance.state

import com.insurance.contract.UserRegistrationContract
import com.insurance.schema.IOUSchemaV1
import com.insurance.schema.UserDetailsSchema1
import com.insurance.schema.UserTransactionDetailsSchema
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState

/**
 * The state object recording user details of the users involved. It could be either a insurance customer
 * or a partner/company.
 *
 * @param userId the unique id of the user.
 * @param userName the name of the user.
 * @param userAddress the address of the user
 * @param owner the party who owns & stores the state
 * @param partner the partner party who owns & stores the state
 */
@BelongsToContract(UserRegistrationContract::class)
data class UserState(val userId: String,
                     val userName: String,
                     val userAddress : String,
                     val owner : Party,
                     val partner : Party,
                     override val linearId: UniqueIdentifier = UniqueIdentifier()):
        LinearState, QueryableState {
    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(owner,partner)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is UserDetailsSchema1 -> UserDetailsSchema1.UserDetailsTable(
                    this.userId,
                    this.userAddress,
                    this.owner.name.toString(),
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(UserDetailsSchema1)
}
