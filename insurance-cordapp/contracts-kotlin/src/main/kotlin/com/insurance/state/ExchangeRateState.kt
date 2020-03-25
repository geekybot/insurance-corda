package com.insurance.state

import com.insurance.contract.CollectiblesContract
import com.insurance.contract.UserRegistrationContract
import com.insurance.schema.Collectibles
import com.insurance.schema.Collectibles1
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
 * The state object [CollectiblesState] records the collectibles of a company or a partner involved in the platform.
 *
 * @param totalDue total due left to be submitted or dealt with ( for a month )
 * @param collectedDue collected due for a month
 * @param pendingDue due to be collected for a month
 * @param remittances * to be clarified *
 * @param owner party who needs to own and store the state
 * @param partner party who needs to own and store the state
 * @param date date(month) for which this collectibles state corresponds to.
 */
//ToDo to remove ExchangeRateState
@BelongsToContract(CollectiblesContract::class)
data class ExchangeRateState(   val timeStamp : String,
                                val exchangeRate : Double,
                                val foreignCurrencySymbol : String,
                                val nativeCurrencySymbol : String,
                                val totalAmount : Double,
                                val amountPaidInNativeCurrency : Double, //converted to the foreignCurrency based on exchange rate
                                val owner : Party,
                                val partner : Party,
                                val oracle : Party,
                                override val linearId: UniqueIdentifier = UniqueIdentifier()):
        LinearState, QueryableState {
    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() =listOf(owner,partner,oracle)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        when (schema) {
            is Collectibles1 -> {
                    return Collectibles1.ExchangeRateTable(
                            this.timeStamp,
                            this.exchangeRate,
                            this.foreignCurrencySymbol,
                            this.nativeCurrencySymbol,
                            this.owner.name.toString(),
                            this.partner.name.toString(),
                            this.oracle.name.toString(),
                            this.linearId.id
                    )
            }
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(Collectibles1)
}
