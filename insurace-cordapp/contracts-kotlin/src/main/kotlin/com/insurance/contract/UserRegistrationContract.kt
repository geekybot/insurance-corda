package com.insurance.contract

import com.insurance.state.UserState
import com.insurance.state.UserTransactionState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

/**
 * This contract enforces rules regarding the creation of a valid [UserState], which in turn encapsulates an [UserState].
 *
 * For a new [UserState] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [UserState].
 * - An CreateUser() command with the public keys of both the parties that needs to store the state.
 */
class UserRegistrationContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.example.contract.UserRegistrationContract"
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
            val out = tx.outputsOfType<UserState>().single()
            "The owner and the partner cannot be the same entity." using (out.owner != out.partner)
            "All of the participants must be signers." using (command.signers.containsAll(out.participants.map { it.owningKey }))
        }
    }

    /**
     * This contract only implements one command, CreateUser.
     */
    interface Commands : CommandData {
        class CreateUser : Commands
    }
}
