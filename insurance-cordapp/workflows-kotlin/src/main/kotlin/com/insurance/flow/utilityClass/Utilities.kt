package uitlities

import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*

const val AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
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
     return (arrayOf(SimpleDateFormat("dd").format(cal.time),SimpleDateFormat("MM").format(cal.time),SimpleDateFormat("YYYY").format(cal.time)))
}

 fun parseCurrentDateString():Array<String>{
    val cal2 = Calendar.getInstance()
    return (arrayOf(SimpleDateFormat("dd").format(cal2.time),SimpleDateFormat("MM").format(cal2.time),SimpleDateFormat("YYYY").format(cal2.time)))
}

// fun getInForeignCurrency(totalAmountToBePaid: Double,foreignCurrencyName: String): Double{
//    return 0.0
//}