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

 fun getMonthFromString(date: String): String? {
    val d = SimpleDateFormat("dd/mm/yyyy", Locale.ENGLISH).parse(date)
    val cal = Calendar.getInstance()
    cal.time = d
    return (SimpleDateFormat("MM").format(cal.time))
}

 fun getCurrentMonth():String{
    val cal2 = Calendar.getInstance()
    return (SimpleDateFormat("MM").format(cal2.time))
}

 fun getInForeignCurrency(totalAmountToBePaid: Double,foreignCurrencyName: String): Double{
    return 0.0
}