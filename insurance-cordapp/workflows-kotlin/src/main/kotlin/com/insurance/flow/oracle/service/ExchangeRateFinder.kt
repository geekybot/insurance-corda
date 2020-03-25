package com.insurance.flow.oracle.service

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import okhttp3.*


// https://free.currconv.com/api/v7/convert?q=USD_PHP&compact=ultra&apiKey=4bdd51f55edb36a7456c

@CordaService
class ExchangeRateFinder(private  val serviceHub: AppServiceHub) :
        SingletonSerializeAsToken() {
    private val client = OkHttpClient()
    private val mapper = ObjectMapper()
    private var parameterName :String? =null
    fun getCurrent(foreignCurrencySymbol: String, nativeCurrencySymbol: String): kotlin.Double {
        parameterName = "$foreignCurrencySymbol" + "_" + "$nativeCurrencySymbol"
        val response = client.newCall(  Request.Builder().url("https://free.currconv.com/api/v7/convert?q=$parameterName&compact=ultra&apiKey=4bdd51f55edb36a7456c").build()).execute()
        return response.body()?.let {
            val json = it.string()
            val tree = mapper.readTree(json)
            tree[parameterName].toString().toDouble()
        } ?: throw IllegalArgumentException("No response")
    }
}
