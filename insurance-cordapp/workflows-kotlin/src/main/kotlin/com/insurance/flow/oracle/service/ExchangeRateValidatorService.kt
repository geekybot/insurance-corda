package com.insurance.flow.oracle.service

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor

@CordaService
class ExchangeRateValidatorService(private val serviceHub: AppServiceHub) :
        SingletonSerializeAsToken() {

    fun validateExchangeRates(nativeCurrencyName : String, foreignCurrencyName : String, exchangeRate : Double) =
            serviceHub.cordaService(ExchangeRateFinder::class.java).getCurrent(nativeCurrencyName,foreignCurrencyName).let {
                log.info("Expected exchange rate is $it and the provided exchange rate is $it")
//                require(nativeCurrencyName=="USD" && foreignCurrencyName == "INR") {"************ exhchange com are swapped ************* $nativeCurrencyName as INR and $foreignCurrencyName as USD"}
                require(it == exchangeRate) { "The  exchangeRate is ${it}, not $exchangeRate" }
            }

    private companion object {
        val log = loggerFor<ExchangeRateValidatorService>()
    }
}