package com.intalix.expense.tracker

import com.intalix.expense.tracker.parse.Banks
import com.intalix.expense.tracker.parse.PaymentApps
import com.intalix.expense.tracker.parse.Watched
import com.intalix.expense.tracker.parse.Template
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Data-driven tests: every message in the .txt files of src/test/resources/samples is parsed and compared with its `expect:` line.
 * Adding a bank or a new wording = add the template + paste a sample here. No Kotlin test code needed. See CONTRIBUTING.md.
 *
 * Sample block (blocks are separated by a line with ---, lines starting with # are comments):
 *   kind: sms | notif            (default sms)
 *   sender: VM-KOTAKB-S          (sms)        package: com.kotak.bank.mobile   (notif)
 *   text: the message            (write a line break inside a notification as \n)
 *   expect: DEBIT|CREDIT <amount> [acct=1234] [payee="Name"] [ref=123456] [bank=Name] [review] [template]
 *       or: IGNORED
 */
class BankSamplesTest {
    private class Sample(val file: String, val index: Int, val kind: String, val sender: String?, val pkg: String?, val text: String, val expect: String) {
        override fun toString() = "$file #$index: ${text.take(70)}"
    }

    private fun load(): List<Sample> {
        val dir = File(javaClass.classLoader.getResource("samples")!!.toURI())
        val out = ArrayList<Sample>()
        for (f in dir.listFiles { x -> x.name.endsWith(".txt") }!!.sortedBy { it.name }) {
            var i = 0
            for (block in f.readText().replace("\r\n", "\n").split(Regex("(?m)^---\\s*$"))) {
                val kv = HashMap<String, String>()
                for (line in block.lines()) {
                    if (line.isBlank() || line.startsWith("#")) continue
                    val c = line.indexOf(':'); require(c > 0) { "${f.name}: bad line \"$line\"" }
                    kv[line.substring(0, c).trim()] = line.substring(c + 1).trim()
                }
                if (kv.isEmpty()) continue
                i++
                val kind = kv["kind"] ?: "sms"
                require(kind == "sms" || kind == "notif") { "${f.name} #$i: kind must be sms or notif" }
                out.add(Sample(f.name, i, kind, kv["sender"], kv["package"], (kv["text"] ?: error("${f.name} #$i: no text:")).replace("\\n", "\n"), kv["expect"] ?: error("${f.name} #$i: no expect:")))
            }
        }
        return out
    }

    private fun parse(s: Sample): ParseResult =
        if (s.kind == "sms") SmsParser.parse(s.text, s.sender) else NotificationParser.parse(s.text, s.pkg)

    private fun tokens(expect: String): List<String> =
        Regex("""[^\s"=]+=\"[^\"]*\"|[^\s]+""").findAll(expect).map { it.value }.toList()

    private fun kvOf(tokens: List<String>) = tokens.filter { it.contains('=') }.associate {
        it.substringBefore('=') to it.substringAfter('=').trim('"')
    }

    @Test fun samplesParseAsExpected() {
        val samples = load()
        assertTrue("no samples found", samples.isNotEmpty())
        for (s in samples) {
            val toks = tokens(s.expect)
            val result = parse(s)
            if (toks[0] == "IGNORED") { assertTrue("$s should be IGNORED but was $result", result is ParseResult.Ignored); continue }
            val t = (result as? ParseResult.Txn)?.txn ?: fail("$s was ignored: $result").let { return }
            assertEquals("$s direction", Direction.valueOf(toks[0]), t.direction)
            assertEquals("$s amount", Money.parse(toks[1]), t.amountPaise)
            val kv = kvOf(toks)
            kv["acct"]?.let { assertEquals("$s acct", it, t.accountLast4) }
            kv["payee"]?.let { assertEquals("$s payee", it, t.counterparty) }
            kv["ref"]?.let { assertEquals("$s ref", it, t.reference) }
            kv["bank"]?.let { assertEquals("$s bank", it, t.bank) }
            assertEquals("$s certain (add \"review\" if it should need review)", "review" !in toks, t.certain)
        }
    }

    private fun hit(s: Sample): Template? {
        val bank = if (s.kind == "sms") Banks.forSender(s.sender) else Banks.forPackage(s.pkg)
        val text = if (s.kind == "sms") s.text.replace(Regex("\\s+"), " ").trim() else s.text.replace(Regex("""\s*\n+\s*"""), " | ").replace(Regex("[ \t]+"), " ").trim()
        return (if (s.kind == "sms") bank?.matchSms(text) else bank?.matchNotif(text))?.template
    }

    @Test fun samplesMarkedTemplateReallyUseATemplate() {
        for (s in load()) {
            val wants = "template" in tokens(s.expect)
            if (wants) assertNotNull("$s is marked `template` but no bank template matched it (is the sender/package registered?)", hit(s))
            else if (s.expect != "IGNORED") assertNull("$s matched a template; add `template` to its expect line", hit(s))
        }
    }

    @Test fun everyTemplateHasASample() {
        val used = load().mapNotNull { hit(it) }
        for (b in Banks.all) for (t in b.sms + b.notif)
            assertTrue("${b.name}: no sample covers template \"${t.source}\" (add one to samples/)", used.any { it === t })
    }

    @Test fun banksDoNotLeakIntoEachOther() {
        for (s in load()) {
            val own = if (s.kind == "sms") Banks.forSender(s.sender) else Banks.forPackage(s.pkg)
            val text = s.text.replace(Regex("\\s+"), " ")
            for (b in Banks.all) if (b !== own) {
                val h = if (s.kind == "sms") b.matchSms(text) else b.matchNotif(text.replace("\n", " | "))
                assertNull("$s also matches ${b.name}'s template \"${h?.template?.source}\"; make the patterns more specific", h)
            }
        }
    }

    @Test fun samplesContainNoRealLookingData() {
        val okAccounts = setOf("1234", "4321", "9876")
        for (s in load()) {
            kvOf(tokens(s.expect))["acct"]?.let { assertTrue("$s: use a dummy account (1234, 4321 or 9876)", it in okAccounts) }
            assertFalse("$s: looks like a real phone/account number (10 digits); use dummy digits", Regex("""(?<!\d)\d{10}(?!\d)""").containsMatchIn(s.text))
            for (m in Regex("""(?i)(?:a/c|ac|acct|account|card|xx|x|\*+)\s*(?:no\.?)?\s*[x*]*\s*(\d{3,4})(?!\d)""").findAll(s.text))
                assertTrue("$s: account digits ${m.groupValues[1]} look real; use 1234, 4321 or 9876", m.groupValues[1] in okAccounts)
        }
    }

    @Test fun senderMatching() {
        assertEquals("Kotak", Banks.forSender("VM-KOTAKB-S")?.name)
        assertEquals("Kotak", Banks.forSender("kotakb")?.name)
        assertNull(Banks.forSender("+919999999999"))
        assertNull(Banks.forSender(null))
        assertEquals("Kotak", Banks.forPackage("com.kotak.bank.mobile")?.name)
        assertTrue("com.google.android.apps.nbu.paisa.user" in Watched.packages)
    }

    @Test fun templateBuilderRejectsMistakes() {
        assertThrows(IllegalArgumentException::class.java) { com.intalix.expense.tracker.parse.debit("Rs.{amnt} debited") }
        assertThrows(IllegalArgumentException::class.java) { com.intalix.expense.tracker.parse.debit("debited to {payee}") }
        assertThrows(IllegalArgumentException::class.java) { com.intalix.expense.tracker.parse.debit("Rs.{amount} to {payee} or {payee}") }
    }
}
