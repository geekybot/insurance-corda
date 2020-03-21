package com.insurance.flow

import co.paralleluniverse.fibers.Suspendable
import com.insurance.contract.CollectiblesContract
import com.insurance.contract.UserRegistrationContract
import com.insurance.contract.UserTransactionContract
import com.insurance.flow.UserPaymentFLow.Acceptor
import com.insurance.flow.UserPaymentFLow.Initiator
import com.insurance.state.CollectiblesState
import com.insurance.state.IOUState
import com.insurance.state.UserTransactionState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.unwrap
import uitlities.getInForeignCurrency


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
object UserPaymentFLow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val userId : String,
                    val date: String,
                    val totalAmountToBePaid : Double,
                    val amountPaidInNativeCurrency: Double,
                    val nativeCurrencyName : String,
                    val amountPaidInForeignCurrency : Double, // currency of the insurance company owner
                    val foreignCurrencyName : String,
                    val owner: Party,
                    val partner : Party) : FlowLogic<SignedTransaction?>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on the input given.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            object UPDATING_OWNER_DETAILS : Step("Updating the owner's collectible details") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction? {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val userTransactionState = UserTransactionState(userId,date,totalAmountToBePaid,amountPaidInNativeCurrency,nativeCurrencyName,amountPaidInForeignCurrency,foreignCurrencyName,owner,partner)

            val queryCriteria2 = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED,contractStateTypes = setOf(CollectiblesState::class.java)).withParticipants(listOf(partner))
            val queriedData = (serviceHub.vaultService.queryBy(CollectiblesState::class.java,queryCriteria2))
            println("Queried data is ${queriedData.toString()}")
            var totalDue= getInForeignCurrency(totalAmountToBePaid,foreignCurrencyName)
            var collectedDue= amountPaidInForeignCurrency
            var pendingDue= getInForeignCurrency(amountPaidInNativeCurrency,foreignCurrencyName)


            if(queriedData!=null){
                println("Queried date is not null ************** I repeat queried data is not null")
                val filteredValue = queriedData.states.filter { date == this.date }
                if (filteredValue.isNotEmpty()) {
                    val collectiblesStateOfPartner = filteredValue.single().state.data
                    totalDue+= collectiblesStateOfPartner.totalDue
                    collectedDue+= collectiblesStateOfPartner.collectedDue
                    pendingDue+= collectiblesStateOfPartner.pendingDue
                }
            }

            val newCollectiblesStateOfPartner = CollectiblesState(totalDue,
                    collectedDue = collectedDue,
                    pendingDue = pendingDue,
                    remittances = 0.0,
                    owner = owner,
                    partner = partner,
                    date = date
            )

            val txCommand = Command(UserTransactionContract.Commands.CreateTransaction(), listOf(owner.owningKey,partner.owningKey))
            val txCommand1 = Command(CollectiblesContract.Commands.UpdateCollectibles(), listOf(owner.owningKey,partner.owningKey))
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(userTransactionState, UserTransactionContract.ID)
                    .addOutputState(newCollectiblesStateOfPartner,CollectiblesContract.ID)
                    .addCommand(txCommand)
                    .addCommand(txCommand1)


            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS
            // Send the state to the counterparty, and receive it back with their signature.
            val otherPartySession = initiateFlow(owner)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartySession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            val finalSignedTx =  subFlow(FinalityFlow(fullySignedTx, setOf(otherPartySession), FINALISING_TRANSACTION.childProgressTracker()))
            var flag=false
            if(finalSignedTx!=null){
                flag = otherPartySession.sendAndReceive<Boolean>(newCollectiblesStateOfPartner).unwrap { it -> it }
            }
            return if(flag) finalSignedTx else null
        }


    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                }
            }
            val txId = subFlow(signTransactionFlow).id

            val collectiblesReceivedFromPartner :CollectiblesState? = otherPartySession.receive<CollectiblesState>().unwrap { it -> it }
            if(collectiblesReceivedFromPartner!=null){
                val txId = subFlow(CompanyCollectiblesUpgradationFLow(
                        collectiblesReceivedFromPartner.totalDue,
                        collectiblesReceivedFromPartner.collectedDue,
                        collectiblesReceivedFromPartner.pendingDue,
                        ourIdentity,
                        collectiblesReceivedFromPartner.date
                ))
                if(txId != null) otherPartySession.send(true)
            }

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}
