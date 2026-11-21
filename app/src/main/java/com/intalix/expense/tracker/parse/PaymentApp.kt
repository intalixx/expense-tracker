package com.intalix.expense.tracker.parse

/**
 * A UPI / payment app whose notifications are read (Google Pay, PhonePe, BHIM, supermoney ...). Unlike a [Bank] it has no
 * SMS sender and no account of its own: the money moves through whichever bank account the user linked to the app.
 * To add an app, create `parse/apps/<Name>.kt` with one of these and add it to [PaymentApps.all]. See CONTRIBUTING.md.
 *
 * Most apps need nothing but [packages]: the generic rules in `NotificationParser` already understand "Paid ₹100 to X" and
 * "X paid you ₹100", and pick up the note the sender typed (the line under the amount). Add [notif] templates only for wording
 * the generic rules get wrong, and [reject] for notifications that look like payments but are not (cashback, reminders ...).
 *
 * @param name      Shown in the app. Not stored anywhere, so it can be changed.
 * @param packages  Android package names of the app (find one with `adb shell pm list packages | grep <name>`).
 * @param notif     Optional templates for this app's notification text (lines joined with " | "), tried before the generic rules.
 * @param reject    Extra phrases (lower case) meaning "not a completed payment". Added to the global list.
 */
class PaymentApp(
    val name: String,
    val packages: List<String>,
    val notif: List<Template> = emptyList(),
    val reject: List<String> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "PaymentApp needs a name" }
        require(packages.isNotEmpty() && packages.all { it.contains('.') && !it.contains(' ') }) { "$name: give real package names (like com.example.app)" }
        require(reject.all { it == it.lowercase() }) { "$name: reject phrases must be lower case" }
    }

    fun matchNotif(text: String): Hit? {
        for (t in notif) t.match(text)?.let { return it }
        return null
    }
}
