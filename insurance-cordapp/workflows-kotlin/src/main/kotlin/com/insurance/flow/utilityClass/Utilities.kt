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

fun parseStringToDate(data : String):Calendar{
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:SS", Locale.ENGLISH).parse(data)
    val calObj = Calendar.getInstance()
    calObj.time = formatter
    return calObj
}

// fun getInForeignCurrency(totalAmountToBePaid: Double,foreignCurrencyName: String): Double{
//    return 0.0
//}