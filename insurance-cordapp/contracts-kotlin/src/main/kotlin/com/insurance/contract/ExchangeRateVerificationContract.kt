package com.insurance.contract

import com.insurance.state.ExchangeRateState
//import com.insurance.state.IOUState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

/**
 * A implementation of a basic smart contract in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [IOUState], which in turn encapsulates an [IOUState].
 *
 * For a new [IOUState] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [IOUState].
 * - An Create() command with the public keys of both the lender and the borrower.
 *
 * All contracts must sub-class the [Contract] interface.
 */
class ExchangeRateVerificationContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.insurance.contract.ExchangeRateVerificationContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>().value
        when(command){
            is Commands.VerifyExchangeRate -> requireThat {
                //ToDo check some other constraints too
                //Exchange rate specific constraints
                val outputState = tx.outputsOfType<ExchangeRateState>().single()
                "Output of totalAmount doesn't correspond with proper exchange rate" using (command.exchangeRate == outputState.exchangeRate)
            }
        }
    }

    /**
     * This contract only implements one command, Create.
     */
    interface Commands : CommandData {
        class VerifyExchangeRate(val foreignCurrencyName :String ,val nativeCurrencyName :String,val exchangeRate : Double) : Commands
    }
}
