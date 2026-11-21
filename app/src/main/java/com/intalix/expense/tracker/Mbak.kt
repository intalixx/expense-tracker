package com.intalix.expense.tracker

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Reads and writes the MyMoney ".mbak" backup layout so data can move between the two apps.
 * Format (verified against a real MyMoney backup):
 *   base64(iv) ":" base64(hmac) ":" base64(ciphertext)  — AES-128-CBC + HMAC-SHA256 over iv+ciphertext,
 *   plaintext is a JSON object {accounts, budgets, categories, records}.
 */
object Mbak {
    private const val PASSWORD = "my_money_password"
    private const val SALT = "my_money_salt"

    private fun keys(): Pair<ByteArray, ByteArray> {
        val salt = lenientB64(SALT)
        val spec = PBEKeySpec(PASSWORD.toCharArray(), salt, 10000, 384)
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return k.copyOfRange(0, 16) to k.copyOfRange(16, 48)
    }

    /** Same as the app does: drop characters outside the base64 alphabet, pad to a multiple of 4, decode. */
    private fun lenientB64(s: String): ByteArray {
        var t = s.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' }
        while (t.length % 4 != 0) t += "="
        return java.util.Base64.getDecoder().decode(t)
    }

    fun decrypt(file: String): String {
        val parts = file.trim().split(":")
        require(parts.size == 3) { "Not a MyMoney backup file" }
        val iv = java.util.Base64.getDecoder().decode(parts[0])
        val mac = java.util.Base64.getDecoder().decode(parts[1])
        val ct = java.util.Base64.getDecoder().decode(parts[2])
        val (ek, mk) = keys()
        val m = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(mk, "HmacSHA256")) }
        val calc = m.doFinal(iv + ct)
        require(java.security.MessageDigest.isEqual(calc, mac)) { "Backup is corrupted or not from MyMoney" }
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(ek, "AES"), IvParameterSpec(iv))
        return String(c.doFinal(ct), Charsets.UTF_8)
    }

    fun encrypt(json: String): String {
        val (ek, mk) = keys()
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(ek, "AES"), IvParameterSpec(iv))
        val ct = c.doFinal(json.toByteArray(Charsets.UTF_8))
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(mk, "HmacSHA256")) }.doFinal(iv + ct)
        fun b(x: ByteArray) = java.util.Base64.getEncoder().encodeToString(x)
        return "${b(iv)}:${b(mac)}:${b(ct)}"
    }

    private fun paise(o: JSONObject, k: String): Long =
        BigDecimal(o.get(k).toString()).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()

    private fun rupees(paise: Long): Any =
        if (paise % 100 == 0L) paise / 100 else BigDecimal(paise).movePointLeft(2).toDouble()

    class Parsed(val accounts: List<Account>, val categories: List<Category>, val records: List<Rec>, val budgets: List<Budget>)

    /** Parse a decrypted backup. Ids are kept as in the file so references stay consistent. */
    fun parse(json: String): Parsed {
        val root = JSONObject(json)
        val accounts = root.getJSONArray("accounts").objs().map {
            Account(it.getLong("id"), it.getString("name"), "account", paise(it, "initial"))
        }
        val cats = root.getJSONArray("categories").objs().map {
            Category(it.getLong("id"), it.getString("name"), "cat", it.getInt("type"))
        }
        val recs = root.getJSONArray("records").objs().map {
            Rec(
                id = it.getLong("id"), accountId = it.getJSONObject("account").getLong("id"),
                categoryId = it.getJSONObject("category").getLong("id"), type = it.getInt("type"),
                amountPaise = paise(it, "amount"), note = it.optString("note", ""), time = it.getLong("time"), source = "import",
            )
        }
        val budgets = emptyList<Budget>() // schema of non-empty budgets not verified, so not imported
        return Parsed(accounts, cats, recs, budgets)
    }

    fun build(accounts: List<Account>, cats: List<Category>, recs: List<Rec>): String {
        val acctBal = accounts.associate { a ->
            a.id to a.initialPaise + recs.filter { it.accountId == a.id }.sumOf { if (it.type == 1) it.amountPaise else -it.amountPaise }
        }
        fun acc(a: Account) = JSONObject().put("amount", rupees(acctBal[a.id] ?: 0)).put("icon", 301).put("id", a.id)
            .put("initial", rupees(a.initialPaise)).put("name", a.name)
        fun cat(c: Category) = JSONObject().put("icon", c.id).put("id", c.id).put("name", c.name).put("type", c.type)
        val ja = JSONArray().also { arr -> accounts.forEach { arr.put(acc(it)) } }
        val jc = JSONArray().also { arr -> cats.forEach { arr.put(cat(it)) } }
        val jr = JSONArray()
        val am = accounts.associateBy { it.id }; val cm = cats.associateBy { it.id }
        for (r in recs) {
            val a = am[r.accountId] ?: continue; val c = cm[r.categoryId] ?: continue
            jr.put(JSONObject().put("account", acc(a)).put("amount", rupees(r.amountPaise)).put("category", cat(c))
                .put("id", r.time + r.id % 1000).put("note", r.note).put("time", r.time).put("type", r.type))
        }
        // org.json writes keys in insertion order; MyMoney sorts them, but it reads by name so order is irrelevant.
        return JSONObject().put("accounts", ja).put("budgets", JSONArray()).put("categories", jc).put("records", jr).toString()
    }

    private fun JSONArray.objs(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
