package com.insurance.server



//import com.insurance.state.IOUState

import com.insurance.flow.UserPaymentFLow
import com.insurance.flow.UserRegistrationFLow
import com.insurance.state.CollectiblesState
import com.insurance.state.ExchangeRateState
import com.insurance.state.UserState
import com.insurance.state.UserTransactionState
import net.corda.core.identity.CordaX500Name
//import net.corda.server.NodeRPCConnection
import org.json.simple.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.SecureRandom
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import javax.servlet.http.HttpServletRequest


val SERVICE_NAMES = listOf("Notary", "Network Map Service")

/**
 *  A Spring Boot Server API controller for interacting with the node via RPC.
 */

@RestController
@RequestMapping("/api/example/") // The paths for GET and POST requests are relative to this base path.
class MainController(rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val myLegalName = rpc.proxy.nodeInfo().legalIdentities.first().name
    private val proxy = rpc.proxy

    /**
     * Returns the node's name.
     */
    @GetMapping(value = [ "me" ], produces = [ APPLICATION_JSON_VALUE ])
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the network map service. These names can be used to look up identities using
     * the identity service.
     */
    @GetMapping(value = [ "peers" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = proxy.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }

    /**
     * Displays all IOU states that exist in the node's vault.
    //     */
//    @GetMapping(value = [ "ious" ], produces = [ APPLICATION_JSON_VALUE ])
//    fun getIOUs() : ResponseEntity<List<StateAndRef<IOUState>>> {
//        return ResponseEntity.ok(proxy.vaultQueryBy<IOUState>().states)
//    }

    /**
     * Initiates a flow to agree an IOU between two parties.
     *
     * Once the flow finishes it will have written the IOU to ledger. Both the lender and the borrower will be able to
     * see it when calling springapiious on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */

    @PostMapping(value = [ "register-user" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/x-www-form-urlencoded" ])
    fun createUser(request: HttpServletRequest): ResponseEntity<String> {
        println("hello ******** im in")
        val userName = request.getParameter("userName").toString()
        val userAddress  = request.getParameter("userAddress").toString()
        val ownerParty = request.getParameter("ownerParty").toString()
        val partnerParty = request.getParameter("partnerParty").toString()
        if(userName != null && userAddress !=null && ownerParty !=null && partnerParty !=null){
            val ownerPartyX500Name = CordaX500Name.parse(ownerParty)
            val partnerPartyX500Name = CordaX500Name.parse(partnerParty)

            println("ownerPartyX500Name = ${ownerPartyX500Name} *********")

            val ownerPartyObj = proxy.wellKnownPartyFromX500Name(ownerPartyX500Name) ?: return ResponseEntity.badRequest().body("Party named $ownerParty cannot be found.\n")
            val partnerPartyObj = proxy.wellKnownPartyFromX500Name(partnerPartyX500Name)?: return ResponseEntity.badRequest().body("Party named $partnerParty cannot be found.\n")
            return try {
                val signedTx = proxy.startFlowDynamic(UserRegistrationFLow.Initiator::class.java,userName,userAddress,ownerPartyObj,partnerPartyObj)
                ResponseEntity.status(HttpStatus.CREATED).body("Transaction id ${signedTx.id} committed to ledger.\n")

            } catch (ex: Throwable) {
                logger.error(ex.message, ex)
                ResponseEntity.badRequest().body(ex.message!!)
            }
        }else {
            return ResponseEntity.badRequest().body("Some parameter is missing or not properly given userName=${userName} userAddress=!${userAddress} owner=${ownerParty} partnerParty=${partnerParty}")
        }

    }

    /**
     * Displays all IOU states that only this node has been involved in.
     */
//    @GetMapping(value = [ "fetch-user-details-of-partner" ], produces = [ APPLICATION_JSON_VALUE ])
//    fun getMyIOUs(): ResponseEntity<List<StateAndRef<IOUState>>>  {
//        val myious = proxy.vaultQueryBy<IOUState>().states.filter { it.state.data.lender.equals(proxy.nodeInfo().legalIdentities.first()) }
//        return ResponseEntity.ok(myious)
//    }

    // Design assumption that the partners hold the node
    @PostMapping(value = [ "fetch-user-details-of-partner" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/x-www-form-urlencoded" ])
    fun fetchDetailsOfPartner(request: HttpServletRequest): ResponseEntity<List<JSONObject>>  {
        println("i am in fetch details")
        val partnerName = request.getParameter("partnerName")
//        val queryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
//        val queriedValue = proxy.vaultQueryByCriteria(queryCriteria,UserState::class.java)
        val userList = proxy.vaultQuery(UserState::class.java).states
        var result = mutableListOf<JSONObject>()
        var resultStatus= JSONObject()
        resultStatus["resultStatus"]=false
        if(userList.isEmpty()){
            print("User list is empty baby")
            result.add(resultStatus)
            return ResponseEntity.badRequest().body(result)
        }
        for(value in userList){
            var userDetail =JSONObject()
            userDetail["userName"] = value.state.data.userName
            userDetail["userId"] = value.state.data.userName
            userDetail["userAddress"] = value.state.data.userAddress
            userDetail["owner"] = value.state.data.owner
            userDetail["partner"] = value.state.data.partner
            resultStatus["resultStatus"]=true
            result.add(userDetail)
            result.add(resultStatus)
        }
        return ResponseEntity.ok().body(result)
    }

    //  ToDo  pagination if required

    @PostMapping(value = [ "fetch-collectibles-details-of-all-partners-for-a-month" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/x-www-form-urlencoded" ])
    fun getCollectibleDetailsOfAllPartnersForAMonth(request: HttpServletRequest): ResponseEntity<List<JSONObject>>  {

        val month= request.getParameter("month").toInt()
        val year= request.getParameter("year").toInt()

        var result = mutableListOf<JSONObject>()
        var resultStatus= JSONObject()
        var description = JSONObject()

        val ownerParty = proxy.nodeInfo().legalIdentities.single()
        val partnerList = proxy.vaultQuery(CollectiblesState::class.java).states

        resultStatus["resultStatus"]=false

        if(month > 12 || month < 1 ){
            description["description"] ="Not a valid month"
            result.add(resultStatus)
            result.add(description)
            return ResponseEntity.badRequest().body(result)
        }else if(partnerList.isNotEmpty()){
            val filteredData = partnerList.filter {  if(month.compareTo(parseGivenDateString(it.state.data.date)!![1].toInt())==0 &&
                    year.compareTo(parseGivenDateString(it.state.data.date)!![2].toInt())==0) true else false }
            if(filteredData.isNotEmpty()){
                description["description"] ="Data listed successfully"
                resultStatus["resultStatus"] =true
                for( data in filteredData){
                    var partnerCollectibleData = JSONObject()
//                    if(data.state.data.partner!=null){
                    partnerCollectibleData["totalDue"] = roundOffValue(data.state.data.totalDue)
                    partnerCollectibleData["collectedDue"] = roundOffValue(data.state.data.collectedDue)
                    partnerCollectibleData["pendingDue"] = roundOffValue(data.state.data.pendingDue)
                    partnerCollectibleData["count"] = data.state.data.count
                    partnerCollectibleData["date"] = data.state.data.date
                    partnerCollectibleData["owner"] = data.state.data.owner
                    partnerCollectibleData["partner"] = data.state.data.partner
                    partnerCollectibleData["remittances"] = data.state.data.remittances
                    result.add(partnerCollectibleData)
//                    }
                }
                result.add(description)
                result.add(resultStatus)
                return  ResponseEntity.ok().body(result)
            }
            description["description"] = "Partners data not found for $month $year"
            result.add(description)
            result.add(resultStatus)
            return ResponseEntity.badRequest().body(result)

        }else{
            description["description"] ="Partner data not found"
            result.add(resultStatus)
            result.add(description)
            return ResponseEntity.badRequest().body(result)
        }
    }
////

    @PostMapping(value = [ "fetch-collectibles-details-of-a-partner-for-a-month" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/x-www-form-urlencoded" ])
    fun getCollectibleDetailsOfAPartnerForAMonth(request: HttpServletRequest): ResponseEntity<List<JSONObject>>  {
        val month= request.getParameter("month").toInt()
        val year= request.getParameter("year").toInt()
        val partnerName = request.getParameter("partnerName").toString()
        val partnerPartyX500Name = CordaX500Name.parse(partnerName)
        val partnerPartyObj = proxy.wellKnownPartyFromX500Name(partnerPartyX500Name)
        var result = mutableListOf<JSONObject>()
        var resultStatus= JSONObject()
        var description = JSONObject()
        var percentageOfCollectibles = JSONObject()
        val partnerList = proxy.vaultQuery(CollectiblesState::class.java).states
        println(    )
        println("**************************")

        for (partner in partnerList){
            println(partner)
        }

        println("****************")
        resultStatus["resultStatus"]=false

        if(partnerPartyObj == null){
            description["description"] ="Not a valid partnerName : $partnerName"
            result.add(resultStatus)
            result.add(description)
            return ResponseEntity.badRequest().body(result)
        }else if(partnerList.isNotEmpty()){
            val filteredDataForGivenMonth = partnerList.filter {
                ((it.state.data.partner).toString()!!.equals(partnerPartyObj.toString())
                        && month.compareTo(parseGivenDateString(it.state.data.date)!![1].toInt())==0 &&
                        year.compareTo(parseGivenDateString(it.state.data.date)!![2].toInt())==0
                        )
            }

            if(filteredDataForGivenMonth.isNotEmpty()){
                description["description"] ="Data listed successfully"
                resultStatus["resultStatus"] =true
                for( data in filteredDataForGivenMonth){
                    var partnerCollectibleData = JSONObject()
                    partnerCollectibleData["totalDue"] = data.state.data.totalDue
                    partnerCollectibleData["collectedDue"] = data.state.data.collectedDue
                    partnerCollectibleData["pendingDue"] = data.state.data.pendingDue
                    partnerCollectibleData["count"] = data.state.data.count
                    partnerCollectibleData["date"] = data.state.data.date
                    partnerCollectibleData["owner"] = data.state.data.owner
                    partnerCollectibleData["partner"] = data.state.data.partner
                    partnerCollectibleData["remittances"] = data.state.data.remittances
                    result.add(partnerCollectibleData)

                }
                result.add(description)
                result.add(resultStatus)
                return  ResponseEntity.ok().body(result)
            }
            description["description"] = "Partner data not found for partnerName:$partnerName month:$month year:$year"
            result.add(description)
            result.add(resultStatus)
            return ResponseEntity.badRequest().body(result)

        }else{
            description["description"] ="Partner data not found"
            result.add(resultStatus)
            result.add(description)
            return ResponseEntity.badRequest().body(result)
        }
    }


    @PostMapping(value = [ "fetch-users-transaction-details-of-a-partner-for-a-month" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/x-www-form-urlencoded" ])
    fun getUsersTransactionDetailsOfAPartnerForAMonth(request: HttpServletRequest): ResponseEntity<List<JSONObject>> {
        val month = request.getParameter("month").toInt()
        val year = request.getParameter("year").toInt()
        val partnerName = request.getParameter("partnerName").toString()
        val partnerPartyX500Name = CordaX500Name.parse(partnerName)
        val partnerPartyObj = proxy.wellKnownPartyFromX500Name(partnerPartyX500Name)

        var result = mutableListOf<JSONObject>()
        var resultStatus= JSONObject()
        var description = JSONObject()

        val userTransactionList = proxy.vaultQuery(UserTransactionState::class.java).states
        println("User transaction list"+ userTransactionList)
        val userState = proxy.vaultQuery(UserState::class.java).states
        println("UserState"+userState)
        resultStatus["resultStatus"]=false

        if(partnerPartyObj == null){
            description["description"] ="Not a valid partnerName : $partnerName"
            result.add(resultStatus)
            result.add(description)
            return ResponseEntity.badRequest().body(result)
        }else if(userTransactionList.isNotEmpty()){
            println("User Transaction list is not empty")
            println("month $month")
            println("year $year")
            for(i in userState){
                println(i.state.data.userId)
            }
            println("***************")
            val filteredData = userTransactionList.filter {
                ((it.state.data.partner).toString()!!.equals(partnerPartyObj.toString())
                        && if(month.compareTo(parseGivenDateString(it.state.data.date)!![1].toInt())==0 && year.compareTo(parseGivenDateString(it.state.data.date)!![2].toInt())==0) true else false
                        )
            }
            println("filtered data is $filteredData")
            if(filteredData.isNotEmpty() && userState!=null){
                println("filtered data is not empty")
                description["description"] ="Data listed successfully"
                resultStatus["resultStatus"] =true
                for( data in filteredData){
                    println("in for")
                    var userTransactionData = JSONObject()
                    for(xi in userState){
                        println("${xi.state.data.userId} ${data.state.data.userId} ${xi.state.data.userId.equals(data.state.data.userId)}")
                    }
                    println(userState)
                    var userDetails = userState.single { it.state.data.userId.equals(data.state.data.userId) }
                    println("user Details $userDetails")
                    userTransactionData["totalAmountTobePaid"] = data.state.data.totalAmountTobePaid
                    userTransactionData["amountPaidInNativeCurrency"] = data.state.data.amountPaidInNativeCurrency
                    userTransactionData["foreignCurrencyName"] = data.state.data.foreignCurrencyName
                    userTransactionData["date"] = data.state.data.date
                    userTransactionData["nativeCurrencyName"] = data.state.data.nativeCurrencyName
                    userTransactionData["userId"] = data.state.data.userId
                    userTransactionData["userName"] = userDetails.state.data.userName
                    val parsedDate = parseStringToDate(data.state.data.date)
                    val exchangeRateState = proxy.vaultQuery(ExchangeRateState::class.java).states
                    val filteredExchangeRate = exchangeRateState.filter { parseStringToDate(it.state.data.timeStamp) == parsedDate}.single()
                    userTransactionData["amountPaidInForeignCurrency"] = roundOffValue(data.state.data.amountPaidInForeignCurrency*(filteredExchangeRate.state.data.exchangeRate))
                    result.add(userTransactionData)
                }
                result.add(description)
                result.add(resultStatus)
                println("result is ** $result")
                return  ResponseEntity.ok().body(result)
            }
            description["description"] = "Partner data not found for partnerName:$partnerName month:$month year:$year"
            result.add(description)
            result.add(resultStatus)
            return ResponseEntity.badRequest().body(result)

        }else{
            description["description"] ="Partner data not found"
            result.add(resultStatus)
            result.add(description)
            return ResponseEntity.badRequest().body(result)
        }
    }
    //
//    //ToDo add percentage details as required
    @PostMapping(value = [ "fetch-total-collectibles-of-owner" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/x-www-form-urlencoded" ])
    fun getTotalCollectiblesOfOwner(request: HttpServletRequest): ResponseEntity<List<JSONObject>>  {
        println("tjalkfj")
        val partnerName = proxy.nodeInfo().legalIdentities.single()
        val ownerColletibleList = proxy.vaultQuery(CollectiblesState::class.java).states
        for(list in ownerColletibleList){
            println("lsit is $list")
        }
        var result = mutableListOf<JSONObject>()
        var resultStatus= JSONObject()
        var description = JSONObject()
        resultStatus["resultStatus"]=false
        if(ownerColletibleList.isEmpty()){
            description["description"] = "No results found"
            result.add(resultStatus)
            result.add(description)
            return ResponseEntity.badRequest().body(result)
        }else{
            println("List ain't empty ")
            println(proxy.nodeInfo().legalIdentities.single().name.organisation)
            val filteredData = ownerColletibleList.filter { it.state.data.owner.name.organisation==proxy.nodeInfo().legalIdentities.single().name.organisation }
            if(filteredData.isNotEmpty()){
//                for(value in ownerColletibleList){
                description["description"] ="Data listed successfully"
                resultStatus["resultStatus"] =true
//                    var collectiblesDetail =JSONObject()
//
//                    collectiblesDetail["totalDue"] = value.state.data.totalDue
//                    collectiblesDetail["collectedDue"] = value.state.data.collectedDue
//                    collectiblesDetail["pendingDue"] = value.state.data.pendingDue

                resultStatus["resultStatus"]=true
//                    result.add(collectiblesDetail)
                val date = parseStringToDate(parseCurrentDateString())
                val analytics = fetchAnalyticsData(date.get(Calendar.MONTH).toString(),date.get(Calendar.YEAR).toString())
                for(a in analytics){
                    result.add(a)
                }
                result.add(description)
                result.add(resultStatus)
                return  ResponseEntity.ok().body(result)
//                }
            }
            description["description"] ="No results found"
            result.add(resultStatus)
            result.add(description)
            return ResponseEntity.badRequest().body(result)
        }
    }
    //
    @PostMapping(value = [ "make-user-payment" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/x-www-form-urlencoded" ])
    fun makeUserPayment(request: HttpServletRequest): ResponseEntity<List<JSONObject>> {
        val userName = request.getParameter("userName").toString()
        var description = JSONObject()
        var resultStatus = JSONObject()
        resultStatus["resultStatus"]= false
//        val date :String = parseCurrentDateString()
        val date = request.getParameter("date").toString()
        println("**************** date is $date")

        val totalAmountToBePaid : Double = request.getParameter("totalAmountToBePaid").toString().toDouble()
        val amountPaidInNativeCurrency: Double = request.getParameter("amountPaidInNativeCurrency").toString().toDouble()
        val nativeCurrencyName : String = request.getParameter("nativeCurrencyName").toString()
        val amountPaidInForeignCurrency : Double = request.getParameter("amountPaidInForeignCurrency").toString().toDouble()
        val foreignCurrencyName : String = request.getParameter("foreignCurrencyName").toString()
        val ownerParty: String = request.getParameter("owner").toString()
        val partnerParty : String = request.getParameter("partner").toString()
        var result : MutableList<JSONObject> = mutableListOf()
        if(userName != "" && totalAmountToBePaid !=null && amountPaidInNativeCurrency !=null && nativeCurrencyName !=""
                &&amountPaidInForeignCurrency!=null  && foreignCurrencyName!="" && ownerParty!="" && partnerParty!=""){
            val ownerPartyX500Name = CordaX500Name.parse(ownerParty)
            val partnerPartyX500Name = CordaX500Name.parse(partnerParty)
            val userDetailsQueried = proxy.vaultQuery(UserState::class.java).states
            for(user in userDetailsQueried){
                println("user name is :${user.state.data.userName}")
                println(user.state.data.userName == userName)
            }
            val userDetails = userDetailsQueried.single { it.state.data.userName == userName }
            val ownerPartyObj  = proxy.wellKnownPartyFromX500Name(ownerPartyX500Name)
            val partnerPartyObj = proxy.wellKnownPartyFromX500Name(partnerPartyX500Name)
            if(ownerPartyObj !=null && partnerParty!=null) {
                return try {
                    val signedTx = proxy.startFlowDynamic(UserPaymentFLow.Initiator::class.java, userDetails.state.data.userId, date, totalAmountToBePaid, amountPaidInNativeCurrency, nativeCurrencyName,
                            amountPaidInForeignCurrency, foreignCurrencyName, ownerPartyObj, partnerPartyObj)
                    resultStatus["resultStatus"] = true
                    description["description"] = "Transaction id ${signedTx.id} committed to ledger.\n"
                    result.add(resultStatus)
                    result.add(description)
                    ResponseEntity.status(HttpStatus.CREATED).body(result)
                } catch (ex: Throwable) {
                    logger.error(ex.message, ex)
                    description["description"] = ex.message!!
                    result.add(resultStatus)
                    result.add(description)
                    ResponseEntity.badRequest().body(result)
                }
            }else{
                description["description"] ="Party named $partnerParty or $ownerParty cannot be found."
                result.add(description)
                result.add(resultStatus)
                return ResponseEntity.badRequest().body(result)
            }
        }else {
            description["description"]= "Some parameter is missing or not properly given userName: $userName date=${date} totalAmountToBePaid =${totalAmountToBePaid} \n" +
                    "                     amountPaidInNativeCurrency=${amountPaidInNativeCurrency}\n" +
                    "                     nativeCurrencyName =${nativeCurrencyName} \n" +
                    "                     amountPaidInForeignCurrency =${amountPaidInForeignCurrency} \n" +
                    "                     foreignCurrencyName =${foreignCurrencyName} \n" +
                    "                     owner=${ownerParty}\n" +
                    "                     partner =${partnerParty}"
            result.add(description)
            result.add(resultStatus)
            return ResponseEntity.badRequest().body(result)
        }

    }

    fun fetchAnalyticsData(month:String,year :String):List<JSONObject>{
        var result : MutableList<JSONObject> = mutableListOf()
        val party = proxy.nodeInfo().legalIdentities.last()
        val queriedMonthlyData = proxy.vaultQuery(CollectiblesState::class.java).states
        println("queriedMonthlyData $queriedMonthlyData")
        var monthlyData= JSONObject()
        var analytics = JSONObject()
        var currentDate = 1
        var currentWeek =1
        if(month != parseCurrentDateToCalendar().get(Calendar.MONTH).toString() && year != parseCurrentDateToCalendar().get(Calendar.YEAR).toString()){
            currentDate = if(month.toInt()%2==0) { if(month.toInt()==2) 28 else 30} else 31
            currentWeek = parseStringToDate("$currentDate'/'${month.toInt()}'/'${year.toInt()}").get(Calendar.WEEK_OF_MONTH)
        }else{
            println("Yes current")
            currentDate = parseCurrentDateToCalendar().get(Calendar.DATE)
            currentWeek = parseCurrentDateToCalendar().get(Calendar.WEEK_OF_MONTH)
        }
        if(queriedMonthlyData.isNotEmpty()){
            val filteredMonthlyData = queriedMonthlyData.filter { parseStringToDate(it.state.data.date)!!.get(Calendar.MONTH).toString() ==month && parseStringToDate(it.state.data.date)!!.get(Calendar.YEAR).toString()== year && it.state.data.partner==null }
            if(filteredMonthlyData.isNotEmpty()){
                val value = filteredMonthlyData.single()
                monthlyData["totalDue"] = value.state.data.totalDue
                monthlyData["collectedDue"] = value.state.data.collectedDue
                monthlyData["pendingDue"] = value.state.data.pendingDue

                println("$currentWeek week data is ${value.state.data.weeklyData}")

                val currentWeekData = value.state.data.weeklyData!!.toMutableList().get(currentWeek-1).split(",")
                println("currentWeek data is $currentWeekData")

                val previousWeekData : List<String> = if(currentWeek!=1)value.state.data.weeklyData!!.toMutableList().get(currentWeek-2).split(",") else emptyList()
                println("previous week data is $previousWeekData ${previousWeekData.size}")

                if(previousWeekData.size==3) analytics["collectedDuePercentage"] = Math.round(100 - (((previousWeekData[1].toDouble())/(currentWeekData[1].toDouble())*100))) else  analytics["collectedDuePercentage"]=100

                println("current day data ${value.state.data.dailyData!!.size}")
                val currentDayData = value.state.data.dailyData!!.toMutableList().get(currentDate-1).split(",")

                val previousDayData :List<String> = if(currentDate!=1) value.state.data.dailyData!!.toMutableList().get(currentDate-2).split(",") else emptyList()
                println("previousDayData $previousDayData")

                println("current day data $currentDayData")
                if(previousDayData.size==3) analytics["pendingDuePercentage"] = Math.round(100 - (((previousDayData[1].toDouble())/(currentDayData[1].toDouble())*100))) else analytics["pendingDuePercentage"]=100

                result.add(monthlyData)
            }
            println(queriedMonthlyData.filter { parseStringToDate(it.state.data.date).get(Calendar.MONTH) == month.toInt() && it.state.data.partner==null &&
                    parseStringToDate(it.state.data.date).get(Calendar.YEAR) == year.toInt()}.single().state.data.totalDue)
            println(queriedMonthlyData.filter { parseStringToDate(it.state.data.date).get(Calendar.MONTH) == month.toInt()-1 && it.state.data.partner==null &&
                    parseStringToDate(it.state.data.date).get(Calendar.YEAR) == year.toInt()}.single().state.data.totalDue)
            val filteredPreviousMonthlyData = if(month.toInt()-1<=0){
                queriedMonthlyData.filter { parseStringToDate(it.state.data.date).get(Calendar.MONTH) == 13-month.toInt() && it.state.data.partner==null &&
                        parseStringToDate(it.state.data.date).get(Calendar.YEAR) == year.toInt()-1}.single()
            }else{
                queriedMonthlyData.filter { parseStringToDate(it.state.data.date).get(Calendar.MONTH) == month.toInt()-1 && it.state.data.partner==null &&
                        parseStringToDate(it.state.data.date).get(Calendar.YEAR) == year.toInt()}.single()
            }
            if(filteredPreviousMonthlyData.state.data.date!=null) analytics["totalDuePercentage"] = Math.round(100- (((filteredPreviousMonthlyData.state.data.totalDue)/(monthlyData["totalDue"] as Double))*100) ) else analytics["totalDuePercentage"]=100
            result.add(analytics)
        }
        return result
    }


    val AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    var rnd: SecureRandom = SecureRandom()

    fun getSaltString(): String? {
        val salt = StringBuilder(3)
        for (i in 0 until 3) salt.append(AB[rnd.nextInt(AB.length)])
        return salt.toString()
    }

    fun parseGivenDateString(date: String): Array<String>? {
        println()
        val d = SimpleDateFormat("dd/MM/yyyy HH:mm:SS", Locale.ENGLISH).parse(date)
        val cal = Calendar.getInstance()
        cal.time = d
        return (arrayOf(SimpleDateFormat("dd").format(cal.time), SimpleDateFormat("MM").format(cal.time), SimpleDateFormat("yyyy").format(cal.time),SimpleDateFormat("HH").format(cal.time),
                SimpleDateFormat("mm").format(cal.time),SimpleDateFormat("SS").format(cal.time)))
    }

    fun parseCurrentDateString():String{
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:SS", Locale.ENGLISH)
        val cal = Calendar.getInstance()
        val current = formatter.format(cal.time)
        return (current)
    }

    fun parseCurrentDateToCalendar() : Calendar{
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:SS", Locale.ENGLISH)
        val calObj = Calendar.getInstance()
        return calObj
    }

    fun parseStringToDate(data : String):Calendar{
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:SS", Locale.ENGLISH).parse(data)
        val calObj = Calendar.getInstance()
        calObj.time = formatter
        return calObj
    }

    fun roundOffValue(value : Double) : Double{
        val df = DecimalFormat("#.##")
        return df.format(value).toDouble()
    }
//    fun getAnalyticsData() : List<JSONObject>?{
//        val result : MutableList<JSONObject> = emptyList<JSONObject>() as MutableList<JSONObject>
//        val user = proxy.nodeInfo().legalIdentities.last().name.organisation
//        val collectiblesState = proxy.vaultQuery(CollectiblesState::class.java).states
//        val filteredData = collectiblesState.filter { if(user =="Alliance") it.state.data.partner==null else it.state.data.partner!=null }
//        val dateWise : MutableList<JSONObject> = emptyList<JSONObject>() as MutableList<JSONObject>
//        var totalDue =0.0;
//        var collectedDue = 0.0
//        var pendingDue =0.0
//        var monthWise: MutableList<JSONObject> = emptyList<JSONObject>() as MutableList<JSONObject>
//         var weekWise :MutableList<JSONObject> = emptyList<JSONObject>() as MutableList<JSONObject>
//        val weekCount =1;
//        for(data in filteredData){
//            totalDue+=data.state.data.totalDue
//            collectedDue+=data.state.data.collectedDue
//            pendingDue+=data.state.data.pendingDue
//        }
//        result.add(dataWise)
//        result.add(weekWise)
//        result.add(monthWise)
//        return result;
//    }
}
