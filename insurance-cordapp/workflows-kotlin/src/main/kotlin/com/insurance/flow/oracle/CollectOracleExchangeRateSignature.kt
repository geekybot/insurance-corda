package com.insurance.flow.oracle

import co.paralleluniverse.fibers.Suspendable
import com.insurance.contract.ExchangeRateVerificationContract
import com.insurance.flow.oracle.service.ExchangeRateValidatorService
import net.corda.core.contracts.Command
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.security.InvalidParameterException
import java.security.PublicKey
import kotlin.Exception

@InitiatingFlow
class CollectOracleExchangeRateSignature(
        private val oracleParty : Party,
        private val transaction : FilteredTransaction
): FlowLogic<TransactionSignature>(){
    @Suspendable
    override fun call(): TransactionSignature {
        val session = initiateFlow(oracleParty)
        return session.sendAndReceive<TransactionSignature>(transaction).unwrap {it}
    }
}

@InitiatedBy(CollectOracleExchangeRateSignature::class)
class CollectOracleExchangeRateSignatureResponder(private val otherPartySession : FlowSession):FlowLogic<Unit>(){
    @Suspendable
    override fun call(){
        val filteredTransaction = otherPartySession.receive<FilteredTransaction>().unwrap{it->it}
        val key = key()
        val isValid = filteredTransaction.checkWithFun {element :Any ->
            when{
                (element is Command<*> && element.value is ExchangeRateVerificationContract.Commands.VerifyExchangeRate) ->{
                    val command = element.value as ExchangeRateVerificationContract.Commands.VerifyExchangeRate
                    (key in element.signers).also {
                        require(command.nativeCurrencyName=="INR" && command.foreignCurrencyName == "USD") {"************ exhchange com are swapped ************* $command.nativeCurrencyName as INR and $command.foreignCurrencyName as USD"}
                        validateExchangeRates(command.nativeCurrencyName,command.foreignCurrencyName,command.exchangeRate)
                    }
                }
                else -> {
                    false
                }
            }
            }
        if (isValid) {
            log.info("Transaction: ${filteredTransaction.id} is valid, signing with oracle key")
            otherPartySession.send(serviceHub.createSignature(filteredTransaction, key))
        } else {
            throw InvalidParameterException("Transaction: ${filteredTransaction.id} is invalid")
        }
    }
    private fun key(): PublicKey = serviceHub.myInfo.legalIdentities.first().owningKey

    private fun validateExchangeRates(nativeCurrencyName : String, foreignCurrencyName : String, exchangeRate : Double) =try{
        require(nativeCurrencyName=="INR" && foreignCurrencyName == "USD") {"************ exhchange com are swapped ************* $nativeCurrencyName as INR and $foreignCurrencyName as USD"}
        serviceHub.cordaService(ExchangeRateValidatorService::class.java).validateExchangeRates(nativeCurrencyName,foreignCurrencyName,exchangeRate)
    }catch (e : Exception){
        throw (IllegalArgumentException(e.message.toString()))
    }

    private companion object {
        val log = loggerFor<CollectOracleExchangeRateSignature>()
    }
}