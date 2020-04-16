package com.insurance.flow

import co.paralleluniverse.fibers.Suspendable
import com.insurance.contract.CollectiblesContract
import com.insurance.contract.ExchangeRateVerificationContract
import com.insurance.contract.UserTransactionContract
import com.insurance.flow.UserPaymentFLow.Acceptor
import com.insurance.flow.UserPaymentFLow.Initiator
import com.insurance.flow.oracle.CollectOracleExchangeRateSignature
import com.insurance.flow.oracle.service.ExchangeRateFinder
//import com.insurance.schema.AnalyticsSchema1
//import com.insurance.schema.AnalyticsSchema1
import com.insurance.schema.Collectibles1
//import com.insurance.state.Analytics
import com.insurance.state.CollectiblesState
import com.insurance.state.ExchangeRateState
//import com.insurance.state.IOUState
import com.insurance.state.UserTransactionState
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
import net.corda.core.utilities.loggerFor
import uitlities.parseGivenDateString
import uitlities.parseStringToDate
import java.util.*
import java.util.function.Predicate


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
    class Initiator(val userId: String,
                    val date: String,
                    val totalAmountToBePaid: Double,
                    val amountPaidInNativeCurrency: Double,
                    val nativeCurrencyName: String,
                    val amountPaidInForeignCurrency: Double, // currency of the insurance company owner
                    val foreignCurrencyName: String,
                    val owner: Party,
                    val partner: Party) : FlowLogic<SignedTransaction?>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {

            val log = loggerFor<UserPaymentFLow>()

            //            object FETCHING_DETAILS_FROM_VAULT : Step("Fetching details from the vault for the performing the tx.")
//            object FETCHING_EXCHANGE_RATES : Step("Fetching exchange rate from oracle")
            object GENERATING_TRANSACTION : Step("Generating transaction based on the input given.")

            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object GATHERING_ORACLE_SIGS : Step("Gathering the oracle's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

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
//                    FETCHING_DETAILS_FROM_VAULT,
//                    FETCHING_EXCHANGE_RATES,
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    GATHERING_ORACLE_SIGS,
                    FINALISING_TRANSACTION
//                    UPDATING_OWNER_DETAILS
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction? {
            /**            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            val oracleParty = serviceHub.identityService.partiesFromName("Oracle",true).single()

            //            progressTracker.currentStep = FETCHING_DETAILS_FROM_VAULT
            val queryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(CollectiblesState::class.java)).withParticipants(listOf(partner))
            val queriedData = (serviceHub.vaultService.queryBy(CollectiblesState::class.java, queryCriteria))

            //            progressTracker.currentStep = FETCHING_EXCHANGE_RATES
            val rateInForeignCurrency = getInForeignCurrency(foreignCurrencyName,nativeCurrencyName,arrayOf(totalAmountToBePaid,amountPaidInNativeCurrency))

            var totalDue = rateInForeignCurrency[0]
            var collectedDue = rateInForeignCurrency[1]
            var pendingDue = totalDue-collectedDue
            var inputState : StateAndRef<*>?=null
            if (queriedData != null) {
            println("Queried data is not null ************** I repeat queried data is not null")
            val filteredValue = queriedData.states.filter { date == this.date }
            if(filteredValue.isNotEmpty()){
            inputState = filteredValue.single()
            println("filtered values are *******************888")
            println(filteredValue.toString())
            if (filteredValue.isNotEmpty()) {
            val collectiblesStateOfPartner = filteredValue.single().state.data
            println("collectible state of partner $collectiblesStateOfPartner")
            totalDue += collectiblesStateOfPartner.totalDue
            collectedDue += collectiblesStateOfPartner.collectedDue
            pendingDue += collectiblesStateOfPartner.pendingDue
            }
            }

            }


            val userTransactionState = UserTransactionState(userId, date, totalAmountToBePaid, amountPaidInNativeCurrency, nativeCurrencyName, amountPaidInForeignCurrency, foreignCurrencyName, owner, partner)

            val newCollectiblesStateOfPartner = CollectiblesState(totalDue,
            collectedDue = collectedDue,
            pendingDue = pendingDue,
            remittances = 0.0,
            owner = owner,
            partner = partner,
            date = date
            )

            val exchangeRate = ExchangeRateState(date,rateInForeignCurrency[2],foreignCurrencyName,nativeCurrencyName,owner,partner,oracleParty)

            val txCommand = Command(UserTransactionContract.Commands.CreateTransaction(), listOf(owner.owningKey, partner.owningKey))
            val txCommand1 = Command(CollectiblesContract.Commands.UpdateCollectibles(), listOf(owner.owningKey, partner.owningKey))
            val txCommand2 = Command(ExchangeRateVerificationContract.Commands.VerifyExchangeRate(foreignCurrencyName,nativeCurrencyName,rateInForeignCurrency[2]), listOf(owner.owningKey,partner.owningKey,oracleParty.owningKey))

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            val txBuilder = TransactionBuilder(notary)
            .addOutputState(userTransactionState,UserTransactionContract.ID)
            .addOutputState(newCollectiblesStateOfPartner, CollectiblesContract.ID)
            //                    .addOutputState(exchangeRate,ExchangeRateVerificationContract.ID)
            .addCommand(txCommand)
            .addCommand(txCommand1)
            //                    .addCommand(txCommand2)
            if(inputState!=null){
            txBuilder.addInputState(inputState)
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
            progressTracker.currentStep = GATHERING_SIGS
            // Send the state to the counterparty, and receive it back with their signature.
            val partSignedTx2 = collectRecipientSignature(partSignedTx,owner)


            //Stage
            //            progressTracker.currentStep = GATHERING_ORACLE_SIGS
            //            val fullySignedTx = collectOracleSignature(partSignedTx2,oracleParty)

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            val otherPartySession = initiateFlow(owner)
            val finalSignedTx = subFlow(FinalityFlow(partSignedTx2, setOf(otherPartySession), FINALISING_TRANSACTION.childProgressTracker()))


            progressTracker.currentStep = UPDATING_OWNER_DETAILS
            println("Good to go,Ordering the owner of the company to engage")

            var flag = false

            flag = otherPartySession.sendAndReceive<Boolean>("newCollectiblesStateOfPartner").unwrap{it}
            //            flag = otherPartySession.receive<Boolean>().unwrap{it}
            //            return finalSignedTx
            return if (flag) finalSignedTx else null
            //

            }

            @Suspendable
            private fun collectRecipientSignature(
            transaction: SignedTransaction,
            party: Party
            ): SignedTransaction {
            val signature = subFlow(
            CollectSignatureFlow(
            transaction,
            initiateFlow(party),
            party.owningKey
            )
            ).single()
            return transaction.withAdditionalSignature(signature)

             */

            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val oracleParty = serviceHub.identityService.partiesFromName("Oracle", true).single()
            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val userTransactionState = UserTransactionState(userId, date, totalAmountToBePaid, amountPaidInNativeCurrency, nativeCurrencyName, amountPaidInForeignCurrency, foreignCurrencyName, owner, partner)

            val queryCriteria2 = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(CollectiblesState::class.java)).withParticipants(listOf(partner))
            val queriedData = (serviceHub.vaultService.queryBy(CollectiblesState::class.java, queryCriteria2))

            val rateInForeignCurrency = getInForeignCurrency(foreignCurrencyName, nativeCurrencyName, arrayOf(totalAmountToBePaid, amountPaidInNativeCurrency, amountPaidInForeignCurrency))

            var totalDue = rateInForeignCurrency[0]
            var collectedDue = rateInForeignCurrency[2]
            var pendingDue = totalDue - collectedDue
            var dateWiseData: MutableList<String> = mutableListOf()
            var weekWiseData: MutableList<String> = mutableListOf()
            println("amount in foreign currency is ${rateInForeignCurrency[2]} and total amount ${rateInForeignCurrency[0]} and native is ${rateInForeignCurrency[1]} " +
                    "exchange rate is ${rateInForeignCurrency[3]}" +
                    "pending due is ${pendingDue}")
            var inputState: StateAndRef<*>? = null
            if (queriedData != null) {
                println("Queried data is not null ************** I repeat queried data is not null")
                println("Given date is ${parseGivenDateString(this.date)!![1].toInt()} and year ${parseGivenDateString(this.date)!![2].toInt()}")
                for (it in queriedData.states) {
                    println()
                }
                val dateFromInput = parseStringToDate(this.date)
                println("date is ${dateFromInput.toString()}")
                var dayFlag =false;
                var weekFlag = false;
                val filteredValue = queriedData.states.filter {
                    (parseGivenDateString(this.date)!![1].toInt().compareTo(parseGivenDateString(it.state.data.date)!![1].toInt()) == 0) &&
                            (parseGivenDateString(this.date)!![2].toInt().compareTo(parseGivenDateString(it.state.data.date)!![2].toInt()) == 0)
                }
                if (filteredValue.isNotEmpty()) {
                    inputState = filteredValue.single()
                    println("filtered values are *******************888")
                    println(filteredValue.toString())
                    if (filteredValue.isNotEmpty()) {
                        val collectiblesStateOfPartner = filteredValue.single().state.data
                        println("collectible state of partner ${collectiblesStateOfPartner}")
                        dateWiseData = collectiblesStateOfPartner.dailyData!!.toMutableList()
                        weekWiseData = collectiblesStateOfPartner.weeklyData!!.toMutableList()
                        println("dateWiseData $dateWiseData")
                        println("weekWiseData $weekWiseData")
                        println("date wise data size ${dateWiseData.size}")
                        println("week wise data size ${weekWiseData.size}")
                        println(weekWiseData)
                        totalDue += collectiblesStateOfPartner.totalDue
                        collectedDue += collectiblesStateOfPartner.collectedDue
                        pendingDue += collectiblesStateOfPartner.pendingDue
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
                            }
                     /*   if(dateWiseData.isNotEmpty()&& !dayFlag){
                            println("datawise data is not empty ")
                            var i=dateWiseData.size+1
                            while(i< dateFromInput!![Calendar.WEEK_OF_MONTH].toInt()){
                                dateWiseData.add("")
                                i++;
                            }
                            dateWiseData.add("${totalDue},${collectedDue},${pendingDue}")
                        }

                        if(weekWiseData.isNotEmpty()&& !dayFlag){
                            println("weekswise data is not empty")
                            var i=weekWiseData.size+1
                            while(i< dateFromInput!![Calendar.WEEK_OF_MONTH].toInt()){
                                weekWiseData.add("")
                                i++;
                            }
                            weekWiseData.add("${totalDue},${collectedDue},${pendingDue}")
                        }
*/
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
                    var i = weekWiseData.size
                    while (i < dateFromInput!!.get(Calendar.WEEK_OF_MONTH).toInt() - 1) {
                        weekWiseData.add("")
                        i++
                    }
                    weekWiseData.add("${totalDue},${collectedDue},${pendingDue}")
                }
                val newCollectiblesStateOfPartner = CollectiblesState(totalDue,
                        collectedDue = collectedDue,
                        pendingDue = pendingDue,
                        remittances = 0.0,
                        owner = owner,
                        partner = partner,
                        date = date,
                        weeklyData = weekWiseData,
                        dailyData = dateWiseData
                )

                println("New collectibles state of partner is $newCollectiblesStateOfPartner")
                val exchangeRate = ExchangeRateState(date, rateInForeignCurrency[3], foreignCurrencyName, nativeCurrencyName, owner, partner, oracleParty)
                val txCommand2 = Command(ExchangeRateVerificationContract.Commands.VerifyExchangeRate(foreignCurrencyName, nativeCurrencyName, rateInForeignCurrency[3]), listOf(owner.owningKey, partner.owningKey, oracleParty.owningKey))
                val txCommand = Command(UserTransactionContract.Commands.CreateTransaction(), listOf(owner.owningKey, partner.owningKey))
                val txBuilder = TransactionBuilder(notary)
                        .addOutputState(userTransactionState, UserTransactionContract.ID)
                        .addOutputState(newCollectiblesStateOfPartner, CollectiblesContract.ID)
                        .addOutputState(exchangeRate, ExchangeRateVerificationContract.ID)
                        .addCommand(txCommand)
                        .addCommand(txCommand2)

                if (inputState != null) {
                    txBuilder.addInputState(inputState)
                    println("InputState is $inputState")
                    val txCommand1 = Command(CollectiblesContract.Commands.UpdateCollectibles(rateInForeignCurrency[3], totalAmountToBePaid, amountPaidInNativeCurrency, true, false), listOf(owner.owningKey, partner.owningKey))
                    txBuilder.addCommand(txCommand1)
                } else {
                    val txCommand1 = Command(CollectiblesContract.Commands.UpdateCollectibles(rateInForeignCurrency[3], totalAmountToBePaid, amountPaidInNativeCurrency, false, false), listOf(owner.owningKey, partner.owningKey))
                    txBuilder.addCommand(txCommand1)
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
                progressTracker.currentStep = GATHERING_SIGS
                // Send the state to the counterparty, and receive it back with their signature.

                val partSignedTx2 = collectOracleSignature(partSignedTx, oracleParty)
                val otherPartySession = initiateFlow(owner)

                val fullySignedTx = partSignedTx2.withAdditionalSignature(subFlow(CollectSignatureFlow(partSignedTx, otherPartySession, owner.owningKey)).single())
                progressTracker.currentStep = FINALISING_TRANSACTION
                // Notarise and record the transaction in both parties' vaults.
                val finalSignedTx = subFlow(FinalityFlow(fullySignedTx, setOf(otherPartySession, initiateFlow(oracleParty)), FINALISING_TRANSACTION.childProgressTracker()))

                /**            var flag = false
                println("Good to go,Ordering the owner of the company to engage")
                flag = otherPartySession.sendAndReceive<Boolean>(newCollectiblesStateOfPartner).unwrap { it }
                return if (flag) finalSignedTx else null
                 */
                subFlow(CompanyCollectiblesUpgradationInitiator(totalDue, collectedDue, pendingDue, rateInForeignCurrency[2], owner, date, weekWiseData.toList(), dateWiseData.toList()))
                return finalSignedTx
            }

            return null

        }

        @Suspendable
        private fun collectOracleSignature(
                transaction: SignedTransaction,
                oracle: Party
        ): SignedTransaction {
            val filtering1 = Predicate<Any> {
                when (it) {
                    is Command<*> -> oracle.owningKey in it.signers && it.value is ExchangeRateVerificationContract.Commands.VerifyExchangeRate
                    else -> false
                }
            }
            var filteredTransaction = transaction.buildFilteredTransaction(filtering1)
            val signature = subFlow(CollectOracleExchangeRateSignature(oracle, filteredTransaction))
            return transaction.withAdditionalSignature(signature)
        }

        private fun getInForeignCurrency(foreignCurrencyName: String, nativeCurrencyName: String, amounts: Array<Double>): List<Double> {
            println("get in foreign currency")
            val exchangeRate = exchangeRateFinder(foreignCurrencyName, nativeCurrencyName)
            val list: MutableList<Double> = amounts.map { (it * exchangeRate) }.toMutableList()
            list.add(exchangeRate);
            println(list.toString())
            return list;
        }

        @Suspendable
        private fun collectRecipientSignature(
                transaction: SignedTransaction,
                party: Party
        ): SignedTransaction {
            val signature = subFlow(
                    CollectSignatureFlow(
                            transaction,
                            initiateFlow(party),
                            party.owningKey
                    )
            ).single()
            return transaction.withAdditionalSignature(signature)
        }

        private fun exchangeRateFinder(foreignCurrencyName: String, nativeCurrencyName: String): Double =
                serviceHub.cordaService(ExchangeRateFinder::class.java).getCurrent(nativeCurrencyName, foreignCurrencyName)
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
            val receivedFinalityFlow = subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
            /**            val collectiblesReceivedFromPartner: CollectiblesState? = otherPartySession.receive<CollectiblesState>().unwrap { it -> it }
            if (collectiblesReceivedFromPartner != null) {
            println("Roger that, Got the message of CollectibleState")
            val txId1 = subFlow(CompanyCollectiblesUpgradationFLow(
            collectiblesReceivedFromPartner.totalDue,
            collectiblesReceivedFromPartner.collectedDue,
            collectiblesReceivedFromPartner.pendingDue,
            ourIdentity,
            collectiblesReceivedFromPartner.date
            ))
            println("back to base *******")
            val queryCriteria2 = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED, contractStateTypes = setOf(CollectiblesState::class.java)).withParticipants((serviceHub.myInfo.legalIdentities))
            val queriedData = (serviceHub.vaultService.queryBy(CollectiblesState::class.java, queryCriteria2))
            val filteredData = queriedData.states.filter { it.state.data.date == collectiblesReceivedFromPartner.date && it.state.data.partner!=null }
            println("***********8  filtered data is $filteredData")
            val value = filteredData.single()
            println("***********8 value is $value")
            if(value.state.data.date == collectiblesReceivedFromPartner.date){
            otherPartySession.send(true)
            }else{
            otherPartySession.send(false)
            }
            }
             */
            return receivedFinalityFlow
        }
    }
}

