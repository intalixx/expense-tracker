package com.intalix.expense.tracker

/**
 * Offline keyword classifier: payee name (and, for credits, the message text) -> category name.
 * Returns null when unsure, so the record stays in "Other" instead of being guessed wrong.
 *
 * UPI handles look like "9000000001@airtel": the part after "@" is just the *bank* the person uses,
 * so only the part before "@" is matched. A person's number or name never matches a merchant rule.
 */
object Classifier {

    private class Rule(val category: String, val words: List<String>, val tokens: List<String> = emptyList())

    // `words` match as substrings of the normalised payee; `tokens` must equal a whole word (for short/ambiguous names).
    private val expense = listOf(
        Rule("Telephone", listOf("jio", "vodafone", "vodafoneidea", "airtel", "bsnl", "mtnl", "recharge", "prepaid", "postpaid", "tataplay", "talktime"),
            listOf("vi", "idea")),
        Rule("Food", listOf("swiggy", "zomato", "eatsure", "dominos", "pizza", "mcdonald", "kfc", "burger", "starbucks", "cafe", "restaurant",
            "bakery", "caterer", "sweets", "icecream", "ice cream", "dhaba", "bhavan", "biryani", "blinkit", "zepto", "bigbasket", "instamart",
            "dmart", "grocery", "kirana", "supermarket", "dairy", "juice", "chaayos", "subway", "haldiram", "baskin", "faasos", "behrouz",
            "freshmenu", "foods", "kitchen", "tiffin", "canteen", "mess", "chai", "snacks", "vadapav", "bhel", "restro", "eatery"),
            listOf("tea", "hotel")),
        Rule("Transportation", listOf("irctc", "railway", "iruts", "uber", "rapido", "redbus", "abhibus", "makemytrip", "goibibo", "ixigo", "metro",
            "chalo", "bmtc", "msrtc", "ksrtc", "nammayatri", "indigo", "airindia", "spicejet", "akasa", "cab", "taxi", "parking", "ridesharing"),
            listOf("ola", "best", "bus")),
        Rule("Car", listOf("petrol", "fuel", "bpcl", "hpcl", "iocl", "indianoil", "petro", "fastag", "carservice", "garage"), listOf("shell")),
        Rule("Insurance", listOf("insurance", "hdfclife", "iciciprulife", "starhealth", "policybazaar", "acko", "licofindia", "lifeinsurance"), listOf("lic")),
        Rule("Bills", listOf("electricity", "adani", "bses", "tatapower", "msedcl", "mahadiscom", "torrentpower", "bescom", "tneb", "mahanagargas",
            "indane", "bharatgas", "gasbill", "waterbill", "tatasky", "dishtv", "d2h", "sundirect", "broadband", "fibernet", "hathway", "spectranet",
            "billdesk", "bbps", "bajajfinance", "bajajfinserv", "emi", "loanrepay", "creditcardbill", "cred", "municipal"), listOf("act")),
        Rule("Health", listOf("pharmacy", "pharma", "medical", "medplus", "apollo", "1mg", "netmeds", "hospital", "clinic", "diagnostic", "pathlab",
            "thyrocare", "lalpath", "practo", "dental", "chemist", "healthcare", "wellness"), listOf("dr")),
        Rule("Entertainment", listOf("netflix", "hotstar", "spotify", "primevideo", "youtube", "bookmyshow", "pvr", "inox", "sonyliv", "zee5",
            "jiocinema", "playstation", "googleplay", "steam", "gaming", "cinema", "movie", "multiplex", "gamezop", "dream11"), emptyList()),
        Rule("Education", listOf("school", "college", "university", "udemy", "coursera", "byjus", "unacademy", "tuition", "exam", "academy", "institute", "classes"), emptyList()),
        Rule("Clothing", listOf("myntra", "ajio", "zudio", "westside", "pantaloons", "maxfashion", "clothing", "garment", "tailor", "textile", "fashion",
            "boutique", "readymade", "apparel", "jeans"), listOf("hm")),
        Rule("Beauty", listOf("nykaa", "salon", "parlour", "parlor", "barber", "beauty", "cosmetic", "spa", "grooming"), emptyList()),
        Rule("Electronics", listOf("croma", "vijaysales", "reliancedigital", "oneplus", "samsung", "mobilestore", "mobilehub", "electronic", "gadget"), listOf("boat")),
        Rule("Sport", listOf("decathlon", "cultfit", "curefit", "gym", "fitness", "sports", "turf", "playo", "cricket"), emptyList()),
        Rule("Baby", listOf("firstcry", "hopscotch", "babyshop", "diaper"), emptyList()),
        Rule("Home", listOf("furniture", "ikea", "hardware", "urbancompany", "urbanclap", "housingsociety", "maintenance", "pepperfry", "plumber", "electrician", "paint"), listOf("rent")),
        Rule("Shopping", listOf("amazon", "flipkart", "meesho", "reliance retail", "relianceretail", "shoppersstop", "lifestyle", "snapdeal", "store", "mart", "mall", "bazaar", "enterprises", "traders"), emptyList()),
        Rule("Tax", listOf("incometax", "gstpayment", "gstin", "challan", "tdspayment", "traces"), emptyList()),
    )

    fun classifyExpense(payee: String?): String? {
        val key = normalise(payee) ?: return null
        val tokens = key.split(' ').filter { it.isNotEmpty() }
        val squashed = key.replace(" ", "")
        for (r in expense) {
            if (r.words.any { squashed.contains(it.replace(" ", "")) }) return r.category
            if (r.tokens.any { it in tokens }) return r.category
        }
        return null
    }

    /** Credits: only clear wording. Salary is never guessed from a payee name alone. */
    fun classifyIncome(payee: String?, messageText: String?): String? {
        val t = ((payee ?: "") + " " + (messageText ?: "")).lowercase()
        return when {
            Regex("""\b(salary|payroll)\b|\bsal\s*cr""").containsMatchIn(t) -> "Salary"
            Regex("""\brefund""").containsMatchIn(t) -> "Refunds"
            Regex("""\brent(al)?\b""").containsMatchIn(payee?.lowercase() ?: "") -> "Rental"
            else -> null
        }
    }

    fun classify(type: Int, payee: String?, messageText: String? = null): String? =
        if (type == 2) classifyExpense(payee) else classifyIncome(payee, messageText)

    private fun normalise(payee: String?): String? {
        if (payee.isNullOrBlank()) return null
        var p = payee.trim().lowercase()
        val at = p.indexOf('@')
        if (at >= 0) p = p.substring(0, at)          // drop the bank handle
        p = p.replace(Regex("[^a-z0-9]+"), " ").trim()
        // a plain phone number or a machine id carries no information
        if (p.isEmpty() || p.replace(" ", "").all { it.isDigit() }) return null
        return p
    }
}
