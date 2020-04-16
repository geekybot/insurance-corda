package com.insurance.contract

import com.insurance.state.*
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException

/**
 * This contract enforces rules regarding the creation of a valid [CollectiblesState], which in turn encapsulates an [CollectiblesState].
 *
 * For a new [CollectiblesState] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [CollectiblesState].
 * - An CreateUser() command with the public keys of both the parties that needs to store the state.
 */
class CollectiblesContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.insurance.contract.CollectiblesContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        when (command.value){
           is Commands.CreateCollectibles -> {
                requireThat {
                   // Generic constraints around the create user transaction.
//                   "No inputs should be consumed when creating a user." using (tx.inputs.isEmpty())
//                   "Only one output state should be created." using (tx.outputsOfType<CollectiblesState>().size == 1)
                   val out = tx.outputsOfType<CollectiblesState>().single()
                   "The owner and the partner cannot be the same entity." using (out.owner != out.partner)
                   "All of the participants must be signers." using (command.signers.containsAll(out.participants.map { it.owningKey }))

                   //Collectibles state specific constraint
                   //ToDo more constraints to be added based on requirement
                   "The totalDue should be greater than zero." using (out.totalDue>0)
               }
           }

           is Commands.UpdateCollectibles -> {
               requireThat {
                   // Generic constraints around the create user transaction.
//                   "No inputs should be consumed when creating a user." using (tx.inputs.isEmpty())
//                   "Only one output state should be created." using (tx.outputsOfType<CollectiblesState>().size == 1)
                   val out = tx.outputsOfType<CollectiblesState>().single()

                   "The owner and the partner cannot be the same entity." using (out.owner != out.partner)
                   "All of the participants must be signers." using (command.signers.containsAll(out.participants.map { it.owningKey }))

                   //Collectibles state specific constraint
                   //ToDo more constraints to be added based on requirement
                   "The total due should be greater than zero." using (out.totalDue >= 0)
                   if ((command.value as Commands.UpdateCollectibles).inputStatus && (command.value as Commands.UpdateCollectibles).isOwner) {
                       val ip = tx.inputsOfType<CollectiblesState>().single()
                       "Conversion rate for the native currency amount is mismatching" using (out.collectedDue == (ip.collectedDue + (((command.value as Commands.UpdateCollectibles).exchangeRate) * (command.value as Commands.UpdateCollectibles).amountPaidInNativeCurrency)))
                       "Conversion rate for the total amount is mismatching" using (out.totalDue == (ip.totalDue + (((command.value as Commands.UpdateCollectibles).exchangeRate) * (command.value as Commands.UpdateCollectibles).totalAmountToBePaid)))
                   }
               }
           }



            //ToDo needs to be changed based on requirements in the future.
            else -> throw  IllegalArgumentException("Not a valid command")
        }
    }

    /**
     * This contract only implements one command, CreateUser.
     */
    interface Commands : CommandData {
        class CreateCollectibles : Commands
        class UpdateCollectibles(val exchangeRate : Double,val totalAmountToBePaid:Double,val amountPaidInNativeCurrency: Double,val inputStatus: Boolean,val isOwner : Boolean) : Commands
        class UpdateCollectiblesOfOwner() :Commands
    }
}
