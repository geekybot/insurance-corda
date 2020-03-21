package com.insurance.state

import com.insurance.contract.UserRegistrationContract
import com.insurance.contract.UserTransactionContract
import com.insurance.schema.UserTransactionDetailsSchema1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState

/**
 * The state object [UserTransactionState] recording user tx details of the users involved in the platform.
 *
 * @param date date on which the transaction took place
 * @param amountPaidInNativeCurrency total amount paid with native currency
 * @param nativeCurrencyName name of the native currency
 * @param amountPaidInForeignCurrency total amount pain with foreign currency
 * @param foreignCurrencyName name of the foreign currency
 * @param owner party who owns & stores the state
 * @param partner partner party who owns and stores the state
 */
@BelongsToContract(UserTransactionContract::class)
data class UserTransactionState(val date: String,
                                val totalAmountTobePaid :Double ,
                                val amountPaidInNativeCurrency: Double,
                                val nativeCurrencyName : String,
                                val amountPaidInForeignCurrency : Double,
                                val foreignCurrencyName : String,
                                val owner: Party,
                                val partner : Party,
                                override val linearId: UniqueIdentifier = UniqueIdentifier()):
        LinearState, QueryableState {
    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(owner,partner)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is UserTransactionDetailsSchema1 -> UserTransactionDetailsSchema1.UserTransactionDetailsTable(
                    this.date,
                    this.totalAmountTobePaid,
                    this.amountPaidInNativeCurrency,
                    this.nativeCurrencyName,
                    this.amountPaidInForeignCurrency,
                    this.foreignCurrencyName,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(UserTransactionDetailsSchema1)
}
