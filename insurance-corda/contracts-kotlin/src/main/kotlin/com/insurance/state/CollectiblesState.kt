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
@BelongsToContract(CollectiblesContract::class)
data class CollectiblesState(   val totalDue: Double,
                                val collectedDue : Double,
                                val pendingDue : Double,
                                val remittances : Double,
                                val owner: Party,
                                val partner : Party?,
                                val date : String,
                                override val linearId: UniqueIdentifier = UniqueIdentifier()):
        LinearState, QueryableState {
    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() {
        return if (partner!=null) listOf(owner,partner) else listOf(owner)
    }

    //ToDo to remove the CompanyCOllectiblesTable as the details can be put into some offchain db for efficiency purpose
    override fun generateMappedObject(schema: MappedSchema): PersistentState {
         when (schema) {
            is Collectibles1 -> {
                //check if owner and partner details are available to select the required table. if both owner and partner is not null it means it's
                // a collectible details between both partner and company
                if(owner!= null && partner !=null) {
                   return Collectibles1.PartnerCollectiblesTable(
                            this.owner.name.toString(),
                            this.partner.name.toString(),
                            this.date,
                            this.totalDue,
                            this.collectedDue,
                            this.pendingDue,
                            this.remittances,
                            this.linearId.id
                    )
                }else{
                    return Collectibles1.CompanyCollectiblesTable(
                            this.owner.name.toString(),
                            this.date,
                            this.totalDue,
                            this.collectedDue,
                            this.pendingDue,
                            this.linearId.id
                    )
                }
            }
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(Collectibles1)
}
