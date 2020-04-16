package com.insurance.flow

import co.paralleluniverse.fibers.Suspendable
import com.insurance.flow.UserRegistrationFLow.Acceptor
import com.insurance.flow.UserRegistrationFLow.Initiator
//import com.insurance.schema.AnalyticsSchema1
//import com.insurance.schema.AnalyticsSchema1
import com.insurance.schema.Collectibles1
//import com.insurance.state.Analytics
//import com.insurance.state.IOUState
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.unwrap
import org.checkerframework.checker.units.qual.A

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
@InitiatingFlow
class CompanyCollectiblesUpgradationInitiator(val totalDue: Double,
                                              val collectedDue : Double,
                                              val pendingDue : Double,
                                              val exchangeRate : Double,
                                              val owner: Party,
                                              val dateOfTransaction : String,
                                              val weeklyData : List<String>?,
                                              val dailyData : List<String>?) : FlowLogic<SignedTransaction>() {
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
        val otherPartySession = initiateFlow(owner);
        val stx = otherPartySession.sendAndReceive<SignedTransaction>(listOf(collectedDue.toString(),pendingDue.toString(),totalDue.toString(),exchangeRate.toString(),dateOfTransaction,owner.name,weeklyData,dailyData)).unwrap { it }
        return stx
    }

}

@InitiatedBy(CompanyCollectiblesUpgradationInitiator::class)
class Responder(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val list = otherPartySession.receive<List<Any>>().unwrap { it }
        val ownerParty = serviceHub.identityService.wellKnownPartyFromX500Name(list[5] as CordaX500Name)
        val stx = subFlow(CompanyCollectiblesUpgradationFLow((list[2] as String).toDouble(),(list[0]  as String).toDouble(),(list[1] as String).toDouble(),(list[3] as String).toDouble(),ownerParty!!,
                (list[4]  as String),(list[6] as List<String>),list[7] as List<String>))
        otherPartySession.send(stx)
        return stx
    }
}
