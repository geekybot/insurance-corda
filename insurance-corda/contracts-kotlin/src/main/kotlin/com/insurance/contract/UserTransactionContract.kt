package com.insurance.contract

import com.insurance.state.UserTransactionState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

/**
 * This contract enforces rules regarding the creation of a valid [UserTransactionState], which in turn encapsulates an [UserTransactionState].
 *
 * For a new [UserTransactionState] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [UserTransactionState].
 * - An CreateUser() command with the public keys of both the parties that needs to store the state.
 */
class UserTransactionContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.example.contract.UserTransactionContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands.CreateUser>()
        requireThat {
            // Generic constraints around the create user transaction.
            "No inputs should be consumed when creating a user." using (tx.inputs.isEmpty())
            "Only one output state should be created." using (tx.outputs.size == 1)
            val out = tx.outputsOfType<UserTransactionState>().single()
            "The owner and the partner cannot be the same entity." using (out.owner != out.partner)
            "All of the participants must be signers." using (command.signers.containsAll(out.participants.map { it.owningKey }))

            //User transaction state specific constraint
            "The amounts paid should be greater than zero in any one of the payment way." using (out.amountPaidInForeignCurrency>0 || out.amountPaidInNativeCurrency>0)
        }
    }

    /**
     * This contract only implements one command, CreateUser.
     */
    interface Commands : CommandData {
        class CreateUser : Commands
    }
}
