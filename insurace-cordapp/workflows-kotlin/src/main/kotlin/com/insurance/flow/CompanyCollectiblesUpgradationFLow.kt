package com.insurance.flow

import co.paralleluniverse.fibers.Suspendable
import com.insurance.contract.CollectiblesContract
import com.insurance.contract.UserRegistrationContract
import com.insurance.flow.UserRegistrationFLow.Acceptor
import com.insurance.flow.UserRegistrationFLow.Initiator
import com.insurance.state.CollectiblesState
import com.insurance.state.IOUState
import com.insurance.state.UserState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import uitlities.getMonthFromString
import java.util.*

/**
 * This flow allows two parties (the [Initiator] and the [Acceptor]) to come to an agreement about the IOU encapsulated
 * within an [IOUState].
 *
 * In our simple example, the [Acceptor] always accepts a valid IOU.
 *
 * These flows have deliberately been implemented by using only the call() method for ease of understanding. In
 * practice we would recommend splitting up the various stages of the flow into sub-routines.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */
    class CompanyCollectiblesUpgradationFLow(val totalDue: Double,
                    val collectedDue : Double,
                    val pendingDue : Double,
                    val owner: Party,
                    val dateOfTransaction : String) : FlowLogic<SignedTransaction>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction for updating the Company Collectibles.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.

            val queryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
            val filteredValue = serviceHub.vaultService.queryBy(CollectiblesState::class.java, queryCriteria).states.single {
                getMonthFromString(it.state.data.date) == getMonthFromString(dateOfTransaction)
            }
            val previousCollectiblesState = filteredValue.state.data
            var totalDue=0.0
            var pendingDue =0.0
            var collectedDue = 0.0

            if(previousCollectiblesState!=null){
                totalDue+=previousCollectiblesState.totalDue
                pendingDue+=previousCollectiblesState.pendingDue
                collectedDue+=previousCollectiblesState.collectedDue
            }

            val collectiblesState = CollectiblesState(totalDue,collectedDue,pendingDue,0.0,owner,null,dateOfTransaction)
            val txCommand = Command(CollectiblesContract.Commands.UpdateCollectibles(), listOf(owner.owningKey))
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(collectiblesState, CollectiblesContract.ID)
                    .addCommand(txCommand)


            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(FinalityFlow(partSignedTx, emptyList(), FINALISING_TRANSACTION.childProgressTracker()))
        }

    }

    @InitiatedBy(CompanyCollectiblesUpgradationFLow::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
