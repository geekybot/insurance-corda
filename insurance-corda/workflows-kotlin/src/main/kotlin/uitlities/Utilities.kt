package uitlities

import java.text.SimpleDateFormat
import java.util.*

 fun getSaltString(): String? {
    val SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
    val salt = StringBuilder()
    val rnd = Random()
    while (salt.length < 3) {
        val index = (rnd.nextFloat() * SALTCHARS.length) as Int
        salt.append(SALTCHARS[index])
    }
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