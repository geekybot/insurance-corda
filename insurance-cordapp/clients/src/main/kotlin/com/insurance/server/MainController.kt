package com.insurance.server



import com.insurance.flow.UserPaymentFLow
import com.insurance.flow.UserRegistrationFLow
import com.insurance.state.CollectiblesState
import com.insurance.state.IOUState
import com.insurance.state.UserState
import com.insurance.state.UserTransactionState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.vaultQueryBy
import org.json.simple.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
//import uitlities.parseGivenDateString
import java.security.SecureRandom
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
     */
    @GetMapping(value = [ "ious" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getIOUs() : ResponseEntity<List<StateAndRef<IOUState>>> {
        return ResponseEntity.ok(proxy.vaultQueryBy<IOUState>().states)
    }

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

//    /**
//     * Displays all IOU states that only this node has been involved in.
//     */
//    @GetMapping(value = [ "fetch-user-details-of-partner" ], produces = [ APPLICATION_JSON_VALUE ])
//    fun getMyIOUs(): ResponseEntity<List<StateAndRef<IOUState>>>  {
//        val myious = proxy.vaultQueryBy<IOUState>().states.filter { it.state.data.lender.equals(proxy.nodeInfo().legalIdentities.first()) }
//        return ResponseEntity.ok(myious)
//    }
//
    // Design assumption that the partners hold the node
    @PostMapping(value = [ "fetch-user-details-of-partner" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=application/x-www-form-urlencoded" ])
    fun fetchDetailsOfPartner(request: HttpServletRequest): ResponseEntity<List<JSONObject>>  {
    println("i am in fetch details")
        val partnerName = request.getParameter("partnerName")
        val userList = proxy.vaultQuery(UserState::class.java).states
        var result = mutableListOf<JSONObject>()
        var resultStatus= JSONObject()
        resultStatus["resultStatus"]=false
        if(userList.isEmpty()){
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
//
//  //  ToDo  pagination if required
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
            val filteredData = partnerList.filter { (parseGivenDateString(it.state.data.date)!![1].equals(month) && parseGivenDateString(it.state.data.date)!![2].equals(year)) }
            if(filteredData.isNotEmpty()){
                description["description"] ="Data listed successfully"
                resultStatus["resultStatus"] =true
                for( data in filteredData){
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
//
//    @PostMapping(value = [ "fetch-collectibles-details-of-a-partner-for-a-month" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=applicationx-www-form-urlencoded" ])
//    fun getCollectibleDetailsOfAPartnerForAMonth(request: HttpServletRequest): ResponseEntity<List<JSONObject>>  {
//
//        val month= request.getParameter("month").toInt()
//        val year= request.getParameter("year").toInt()
//        val partnerName = request.getParameter("partnerName").toString()
//        val partnerPartyX500Name = CordaX500Name.parse(partnerName)
//        val partnerPartyObj = proxy.wellKnownPartyFromX500Name(partnerPartyX500Name)
//
//        var result = mutableListOf<JSONObject>()
//        var resultStatus= JSONObject()
//        var description = JSONObject()
//
//        val partnerList = proxy.vaultQuery(CollectiblesState::class.java).states
//
//        resultStatus["resultStatus"]=false
//
//        if(partnerPartyObj == null){
//            description["description"] ="Not a valid partnerName : $partnerName"
//            result.add(resultStatus)
//            result.add(description)
//            return ResponseEntity.badRequest().body(result)
//        }else if(partnerList.isNotEmpty()){
//            val filteredData = partnerList.filter {
//                ((it.state.data.partner)!!.equals(partnerPartyObj.toString())
//                        && parseGivenDateString(it.state.data.date)!![1].equals(month)
//                        && parseGivenDateString(it.state.data.date)!![2].equals(year))
//            }
//            if(filteredData.isNotEmpty()){
//                description["description"] ="Data listed successfully"
//                resultStatus["resultStatus"] =true
//                for( data in filteredData){
//                    var partnerCollectibleData = JSONObject()
//                    partnerCollectibleData["totalDue"] = data.state.data.totalDue
//                    partnerCollectibleData["collectedDue"] = data.state.data.collectedDue
//                    partnerCollectibleData["pendingDue"] = data.state.data.pendingDue
//                    partnerCollectibleData["count"] = data.state.data.count
//                    partnerCollectibleData["date"] = data.state.data.date
//                    partnerCollectibleData["owner"] = data.state.data.owner
//                    partnerCollectibleData["partner"] = data.state.data.partner
//                    partnerCollectibleData["remittances"] = data.state.data.remittances
//                    result.add(partnerCollectibleData)
//                }
//                result.add(description)
//                result.add(resultStatus)
//                return  ResponseEntity.ok().body(result)
//            }
//            description["description"] = "Partner data not found for partnerName:$partnerName month:$month year:$year"
//            result.add(description)
//            result.add(resultStatus)
//            return ResponseEntity.badRequest().body(result)
//
//        }else{
//            description["description"] ="Partner data not found"
//            result.add(resultStatus)
//            result.add(description)
//            return ResponseEntity.badRequest().body(result)
//        }
//    }
//
//    @PostMapping(value = [ "fetch-users-transaction-details-of-a-partner-for-a-month" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=applicationx-www-form-urlencoded" ])
//    fun getUsersTransactionDetailsOfAPartnerForAMonth(request: HttpServletRequest): ResponseEntity<List<JSONObject>>  {
//        val month = request.getParameter("month").toInt()
//        val year = request.getParameter("year").toInt()
//        val partnerName = request.getParameter("partnerName").toString()
//        val partnerPartyX500Name = CordaX500Name.parse(partnerName)
//        val partnerPartyObj = proxy.wellKnownPartyFromX500Name(partnerPartyX500Name)
//
//        var result = mutableListOf<JSONObject>()
//        var resultStatus= JSONObject()
//        var description = JSONObject()
//
//        val userTransactionList = proxy.vaultQuery(UserTransactionState::class.java).states
//        val userState = proxy.vaultQuery(UserState::class.java).states
//        resultStatus["resultStatus"]=false
//
//        if(partnerPartyObj == null){
//            description["description"] ="Not a valid partnerName : $partnerName"
//            result.add(resultStatus)
//            result.add(description)
//            return ResponseEntity.badRequest().body(result)
//        }else if(userTransactionList.isNotEmpty()){
//            val filteredData = userTransactionList.filter {
//                ((it.state.data.partner)!!.equals(partnerPartyObj.toString())
//                        && parseGivenDateString(it.state.data.date)!![1].equals(month)
//                        && parseGivenDateString(it.state.data.date)!![2].equals(year))
//            }
//            if(filteredData.isNotEmpty() && userState!=null){
//                description["description"] ="Data listed successfully"
//                resultStatus["resultStatus"] =true
//                for( data in filteredData){
//                    var userTransactionData = JSONObject()
//                    var userDetails = userState.single { it.state.data.userId.equals(data.state.data.userId) }
//                    userTransactionData["totalAmountTobePaid"] = data.state.data.totalAmountTobePaid
//                    userTransactionData["amountPaidInForeignCurrency"] = data.state.data.amountPaidInForeignCurrency
//                    userTransactionData["amountPaidInNativeCurrency"] = data.state.data.amountPaidInNativeCurrency
//                    userTransactionData["foreignCurrencyName"] = data.state.data.foreignCurrencyName
//                    userTransactionData["date"] = data.state.data.date
//                    userTransactionData["nativeCurrencyName"] = data.state.data.nativeCurrencyName
//                    userTransactionData["userId"] = data.state.data.userId
//                    userTransactionData["userName"] = userDetails.state.data.userName
//                    result.add(userTransactionData)
//                }
//                result.add(description)
//                result.add(resultStatus)
//                return  ResponseEntity.ok().body(result)
//            }
//            description["description"] = "Partner data not found for partnerName:$partnerName month:$month year:$year"
//            result.add(description)
//            result.add(resultStatus)
//            return ResponseEntity.badRequest().body(result)
//
//        }else{
//            description["description"] ="Partner data not found"
//            result.add(resultStatus)
//            result.add(description)
//            return ResponseEntity.badRequest().body(result)
//        }
//    }
//
//    //ToDo add percentage details as required
//    @PostMapping(value = [ "fetch-total-collectibles-of-owner" ], produces = [ APPLICATION_JSON_VALUE ], headers = [ "Content-Type=applicationx-www-form-urlencoded" ])
//    fun getTotalCollectiblesOfOwner(request: HttpServletRequest): ResponseEntity<List<JSONObject>>  {
//        val partnerName = proxy.nodeInfo().legalIdentities.single()
//        val ownerColletibleList = proxy.vaultQuery(CollectiblesState::class.java).states
//        var result = mutableListOf<JSONObject>()
//        var resultStatus= JSONObject()
//        var description = JSONObject()
//        resultStatus["resultStatus"]=false
//        if(ownerColletibleList.isEmpty()){
//            description["description"] = "No results found"
//            result.add(resultStatus)
//            result.add(description)
//            return ResponseEntity.badRequest().body(result)
//        }else{
//            val filteredData = ownerColletibleList.filter { it.state.data.owner.toString()==partnerName.toString() }
//            if(filteredData.isNotEmpty()){
//                for(value in ownerColletibleList){
//                    description["description"] ="Data listed successfully"
//                    resultStatus["resultStatus"] =true
//                    var collectiblesDetail =JSONObject()
//
//                    collectiblesDetail["userName"] = value.state.data.totalDue
//                    collectiblesDetail["userId"] = value.state.data.collectedDue
//                    collectiblesDetail["userAddress"] = value.state.data.pendingDue
//
//                    resultStatus["resultStatus"]=true
//                    result.add(collectiblesDetail)
//
//                    result.add(description)
//                    result.add(resultStatus)
//                    return  ResponseEntity.ok().body(result)
//                }
//            }
//            description["description"] ="No results found"
//            result.add(resultStatus)
//            result.add(description)
//            return ResponseEntity.badRequest().body(result)
//        }
//
//    }
//
//    @PostMapping(value = [ "make-user-payment" ], produces = [ TEXT_PLAIN_VALUE ], headers = [ "Content-Type=applicationx-www-form-urlencoded" ])
//    fun makeUserPayment(request: HttpServletRequest): ResponseEntity<List<JSONObject>> {
//        val userName = request.getParameter("userName").toString()
//        var description = JSONObject()
//        var resultStatus = JSONObject()
//        resultStatus["resultStatus"]= false
//        val date = Calendar.getInstance().toString()
//        val totalAmountToBePaid : Double = request.getParameter("totalAmountToBePaid").toString().toDouble()
//        val amountPaidInNativeCurrency: Double = request.getParameter("amountPaidInNativeCurrency").toString().toDouble()
//        val nativeCurrencyName : String = request.getParameter("nativeCurrencyName").toString()
//        val amountPaidInForeignCurrency : Double = request.getParameter("amountPaidInForeignCurrency").toString().toDouble()
//        val foreignCurrencyName : String = request.getParameter("foreignCurrencyName").toString()
//        val ownerParty: String = request.getParameter("owner").toString()
//        val partnerParty : String = request.getParameter("partner").toString()
//        var result : MutableList<JSONObject> = mutableListOf()
//        if(userName != "" && totalAmountToBePaid !=null && amountPaidInNativeCurrency !=null && nativeCurrencyName !=""
//                &&amountPaidInForeignCurrency!=null  && foreignCurrencyName!="" && ownerParty!="" && partnerParty!=""){
//            val ownerPartyX500Name = CordaX500Name.parse(ownerParty)
//            val partnerPartyX500Name = CordaX500Name.parse(partnerParty)
//            val userDetails = proxy.vaultQuery(UserState::class.java).states.single { it.state.data.userName == userName}
//            val ownerPartyObj = proxy.wellKnownPartyFromX500Name(ownerPartyX500Name)
//            val partnerPartyObj = proxy.wellKnownPartyFromX500Name(partnerPartyX500Name)
//            if(ownerPartyObj !=null && partnerParty!=null) {
//                return try {
//                    val signedTx = proxy.startFlowDynamic(UserPaymentFLow.Initiator::class.java, userDetails.state.data.userId, date, totalAmountToBePaid, amountPaidInNativeCurrency, nativeCurrencyName,
//                            amountPaidInForeignCurrency, foreignCurrencyName, ownerParty, partnerParty)
//                    resultStatus["resultStatus"] = true
//                    description["description"] = "Transaction id ${signedTx.id} committed to ledger.\n"
//                    result.add(resultStatus)
//                    result.add(description)
//                    ResponseEntity.status(HttpStatus.CREATED).body(result)
//                } catch (ex: Throwable) {
//                    logger.error(ex.message, ex)
//                    description["description"] = ex.message!!
//                    result.add(resultStatus)
//                    result.add(description)
//                    ResponseEntity.badRequest().body(result)
//                }
//            }else{
//                description["description"] ="Party named $partnerParty or $ownerParty cannot be found."
//                result.add(description)
//                result.add(resultStatus)
//                return ResponseEntity.badRequest().body(result)
//            }
//        }else {
//            description["description"]= "Some parameter is missing or not properly given userName: $userName date=${date} totalAmountToBePaid =${totalAmountToBePaid} \n" +
//                    "                     amountPaidInNativeCurrency=${amountPaidInNativeCurrency}\n" +
//                    "                     nativeCurrencyName =${nativeCurrencyName} \n" +
//                    "                     amountPaidInForeignCurrency =${amountPaidInForeignCurrency} \n" +
//                    "                     foreignCurrencyName =${foreignCurrencyName} \n" +
//                    "                     owner=${ownerParty}\n" +
//                    "                     partner =${partnerParty}"
//            result.add(description)
//            result.add(resultStatus)
//            return ResponseEntity.badRequest().body(result)
//        }
//
//    }
 val AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    var rnd: SecureRandom = SecureRandom()

    fun getSaltString(): String? {
        val salt = StringBuilder(3)
        for (i in 0 until 3) salt.append(AB[rnd.nextInt(AB.length)])
        return salt.toString()
    }

    fun parseGivenDateString(date: String): Array<String>? {
        val d = SimpleDateFormat("dd/mm/yyyy", Locale.ENGLISH).parse(date)
        val cal = Calendar.getInstance()
        cal.time = d
        return (arrayOf(SimpleDateFormat("dd").format(cal.time), SimpleDateFormat("MM").format(cal.time), SimpleDateFormat("YYYY").format(cal.time)))
    }

    fun parseCurrentDateString():Array<String>{
        val cal2 = Calendar.getInstance()
        return (arrayOf(SimpleDateFormat("dd").format(cal2.time), SimpleDateFormat("MM").format(cal2.time), SimpleDateFormat("YYYY").format(cal2.time)))
    }
}
