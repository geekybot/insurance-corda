package com.insurance.flow

import co.paralleluniverse.fibers.Suspendable
import com.insurance.contract.CollectiblesContract
import com.insurance.flow.UserRegistrationFLow.Acceptor
import com.insurance.flow.UserRegistrationFLow.Initiator
//import com.insurance.schema.AnalyticsSchema1
import com.insurance.schema.Collectibles1
//import com.insurance.schema.AnalyticsSchema1
//import com.insurance.state.Analytics
import com.insurance.state.CollectiblesState
//import com.insurance.state.IOUState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import uitlities.parseGivenDateString
import uitlities.parseStringToDate
import java.util.*
import javax.swing.plaf.nimbus.State

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
class CompanyCollectiblesUpgradationFLow(val totalDue: Double,
                                         val collectedDue : Double,
                                         val pendingDue : Double,
                                         val exchangeRate : Double,
                                         val owner: Party,
                                         val date : String,
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
        println("Boots on the ground : CompanyCollectiblesUpgradationFLow")
        // Obtain a reference to the notary we want to use.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        // Stage 1.
        progressTracker.currentStep = GENERATING_TRANSACTION
        // Generate an unsigned transaction.

        val queryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val queriedValue = serviceHub.vaultService.queryBy(CollectiblesState::class.java, queryCriteria)

        var dateWiseData :MutableList<String> = mutableListOf()
        var weekWiseData :MutableList<String> = mutableListOf()
        var totalDue1=totalDue
        var pendingDue2 =pendingDue
        var collectedDue3 = collectedDue
        var previousCollectiblesState :StateAndRef<*>? =null
        val dateFromInput = parseStringToDate(this.date)
        if(queriedValue!=null){
            val filteredData = queriedValue.states.filter {
                (parseGivenDateString(this.date)!![1].toInt().compareTo(parseGivenDateString(it.state.data.date)!![1].toInt())==0)&&
                        (parseGivenDateString(this.date)!![2].toInt().compareTo(parseGivenDateString(it.state.data.date)!![2].toInt())==0)&&
                        (it.state.data.partner ==null)
            }
            var dayFlag =false;
            var weekFlag = false;
            if(filteredData.isNotEmpty()){

                println("Hello")
                 previousCollectiblesState = filteredData.single()

                dateWiseData= previousCollectiblesState.state.data.dailyData!!.toMutableList()
                weekWiseData = previousCollectiblesState.state.data.weeklyData!!.toMutableList()
                if (dateWiseData.isNotEmpty() && dateWiseData.size >= (dateFromInput!!.get(Calendar.DATE))) {
                    val date2 = dateWiseData[dateFromInput!!.get(Calendar.DATE).toInt()-1].split(',')
                    println("datawise not empty $date2")
                    println(date2.size==1)

                    val updatedDateWiseData = "${if(date2.size==1) totalDue else date2[0].toDouble() + totalDue}" +
                            ",${if(date2.size==1) collectedDue else date2[1].toDouble() + collectedDue}," +
                            "${if(date2.size==1) pendingDue else date2[2].toDouble() + pendingDue}"
                    dateWiseData[dateFromInput.get(Calendar.DATE).toInt() -1] = updatedDateWiseData
                    dayFlag=true
                }
                if (weekWiseData.isNotEmpty() && weekWiseData.size >= (dateFromInput.get(Calendar.WEEK_OF_MONTH).toInt() )) {
                    val week2 = weekWiseData[dateFromInput.get(Calendar.WEEK_OF_MONTH).toInt() - 1].split(",")
                    println("week2 data is $week2")
                    val updatedWeekWiseData = "${if(week2.size==1) totalDue else week2[0].toDouble() + totalDue}," +
                            "${if(week2.size==1) collectedDue else week2[1].toDouble() + collectedDue}," +
                            "${if(week2.size==1) pendingDue else week2[2].toDouble() + pendingDue}"
                    println("updates week wise data $updatedWeekWiseData")
                    println("weekWisedata from date input ${weekWiseData[dateFromInput.get(Calendar.WEEK_OF_MONTH).toInt()-1]}")
                    weekWiseData[dateFromInput.get(Calendar.WEEK_OF_MONTH).toInt()-1] = updatedWeekWiseData
                    weekFlag=true
                }
                if(dayFlag && weekFlag){
                    totalDue1+=previousCollectiblesState.state.data.totalDue
                    pendingDue2+=previousCollectiblesState.state.data.pendingDue
                    collectedDue3+=previousCollectiblesState.state.data.collectedDue
                }
            }

            if (!dayFlag) {
                println("data wise data is emptyd ${dateFromInput!!.get(Calendar.DATE).toInt()}")
                var i = dateWiseData.size
                while (i < dateFromInput!!.get(Calendar.DATE).toInt() - 1) {
                    dateWiseData.add("")
                    i++;
                }
                dateWiseData.add("${totalDue},${collectedDue},${pendingDue}")
            }
            if (!weekFlag) {
                println("week wise data is empty ${dateFromInput!!.get(Calendar.WEEK_OF_MONTH).toInt()}")
                var i = dateWiseData.size
                while (i < dateFromInput!!.get(Calendar.WEEK_OF_MONTH).toInt() - 1) {
                    weekWiseData.add("")
                    i++
                }
                weekWiseData.add("${totalDue},${collectedDue},${pendingDue}")
            }
        }
        println("artifacts collected,Engaging now")
        if (weeklyData != null) {
            for(i in weeklyData){
                println(i)
            }
        }
        if (dailyData != null) {
            for(i in dailyData){
               println(i)
            }
        }
        val collectiblesState = CollectiblesState(totalDue,collectedDue,pendingDue,0.0,owner,null,date, weeklyData,dailyData)
        val txCommand = Command(CollectiblesContract.Commands.UpdateCollectibles(exchangeRate,0.0,0.0,false,true), listOf(owner.owningKey))
        val txBuilder = TransactionBuilder(notary)
                .addOutputState(collectiblesState)
                .addCommand(txCommand)

        if(previousCollectiblesState!=null){
            txBuilder.addInputState(previousCollectiblesState)
        }

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
        println("returing back to base")
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