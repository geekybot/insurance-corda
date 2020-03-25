package com.insurance.flow.oracle.service

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor

@CordaService
class ExchangeRateValidatorService(private val serviceHub: AppServiceHub) :
        SingletonSerializeAsToken() {

    fun validateExchangeRates(nativeCurrencyName : String, foreignCurrencyName : String, exchangeRate : Double) =
            serviceHub.cordaService(ExchangeRateFinder::class.java).getCurrent(foreignCurrencyName,nativeCurrencyName).let {
                log.info("Expected exchange rate is $it but the provided exchange rate is $it")
                require(exchangeRate == it) { "The  exchangeRate is ${it}, not $exchangeRate" }
            }

    private companion object {
        val log = loggerFor<ExchangeRateValidatorService>()
    }
}