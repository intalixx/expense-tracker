package com.intalix.expense.tracker

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Calendar

data class MonthKey(val year: Int, val month: Int) {
    fun start(): Long = Calendar.getInstance().apply { clear(); set(year, month, 1) }.timeInMillis
    fun end(): Long = Calendar.getInstance().apply { clear(); set(year, month, 1); add(Calendar.MONTH, 1) }.timeInMillis
    fun plus(n: Int): MonthKey = Calendar.getInstance().apply { clear(); set(year, month, 1); add(Calendar.MONTH, n) }
        .let { MonthKey(it.get(Calendar.YEAR), it.get(Calendar.MONTH)) }
    companion object {
        fun now() = Calendar.getInstance().let { MonthKey(it.get(Calendar.YEAR), it.get(Calendar.MONTH)) }
    }
}

class Vm(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    val dao = db.dao()

    val accounts = dao.accounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories = dao.categories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val records = dao.records().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val budgets = dao.budgets().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val pending = dao.pending().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val payees = dao.payees().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val month = MutableStateFlow(MonthKey.now())
    val message = MutableStateFlow<String?>(null)

    fun monthRecords(all: List<Rec>, m: MonthKey) = all.filter { it.time >= m.start() && it.time < m.end() }

    fun balance(a: Account, all: List<Rec>): Long =
        a.initialPaise + all.filter { it.accountId == a.id }.sumOf { if (it.type == 1) it.amountPaise else -it.amountPaise }

    fun save(r: Rec) = viewModelScope.launch { dao.put(r) }
    fun delete(r: Rec) = viewModelScope.launch { dao.delete(r) }
    /** "kotak:1234" -> account. Messages from that bank for that last-4 go to the linked account (Accounts -> edit). */
    val links = dao.links().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun addLink(bankName: String, last4: String, accountId: Long) =
        viewModelScope.launch { dao.put(AccountLink("${bankName.lowercase()}:$last4", accountId)) }
    fun removeLink(key: String) = viewModelScope.launch { dao.deleteLink(key) }
    fun saveAccount(a: Account) = viewModelScope.launch { dao.put(a) }
    fun deleteAccount(a: Account) = viewModelScope.launch { dao.deleteRecordsOfAccount(a.id); dao.deleteAccount(a.id) }
    fun saveCategory(c: Category) = viewModelScope.launch { dao.put(c) }
    fun deleteCategory(c: Category, moveTo: Long) = viewModelScope.launch { dao.moveCategory(c.id, moveTo); dao.deleteCategory(c.id) }
    fun saveBudget(b: Budget) = viewModelScope.launch { dao.put(b) }
    fun deleteBudget(b: Budget) = viewModelScope.launch { dao.delete(b) }

    fun accept(p: Pending, accountId: Long, categoryId: Long, remember: Boolean, auto: Boolean) =
        viewModelScope.launch { Ingest.accept(getApplication(), p, accountId, categoryId, remember, auto) }
    fun dismiss(p: Pending) = viewModelScope.launch { dao.setPendingStatus(p.id, 2) }

    /** Fill the payee list from records that already exist. Safe to repeat. */
    fun backfillPayees() = viewModelScope.launch { runCatching { Payees.backfill(dao) } }

    fun setPayeeRule(p: Payee, categoryId: Long?, auto: Boolean, includeOthers: Boolean) = viewModelScope.launch {
        val r = runCatching { Payees.setRule(dao, p, categoryId, auto, includeOthers) }
        message.value = r.fold({ n -> if (categoryId == null) "Rule removed" else if (n > 0) "Saved · moved $n past entr${if (n == 1) "y" else "ies"}" else "Saved" }, { "Failed: ${it.message}" })
    }

    fun recategorize() = viewModelScope.launch {
        val n = runCatching { Ingest.recategorize(getApplication()) }
        message.value = n.fold({ "Re-categorised $it record(s)" }, { "Failed: ${it.message}" })
    }

    /** days <= 0 means all time. */
    fun scanInbox(days: Int) = viewModelScope.launch {
        message.value = "Scanning messages…"
        val since = if (days <= 0) 0L else System.currentTimeMillis() - days * 86_400_000L
        val n = runCatching { InboxScanner.scan(getApplication(), since) }
        message.value = n.fold({ "Scan finished: $it new item(s)" }, { "Scan failed: ${it.message}" })
    }

    val skipped = MutableStateFlow<List<SkippedMsg>?>(null)
    /** days <= 0 means all time. null = still loading. */
    fun loadSkipped(days: Int) = viewModelScope.launch {
        skipped.value = null
        val since = if (days <= 0) 0L else System.currentTimeMillis() - days * 86_400_000L
        skipped.value = runCatching { SkippedScanner.scan(getApplication(), since) }
            .getOrElse { message.value = "Could not read messages: ${it.message}"; emptyList() }
    }

    fun exportTo(uri: Uri) = viewModelScope.launch {
        runCatching {
            val enc = Mbak.encrypt(Mbak.build(dao.accountsNow(), dao.categoriesNow(), dao.recordsNow()))
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(enc.toByteArray()) }
        }.onSuccess { message.value = "Backup saved" }.onFailure { message.value = "Export failed: ${it.message}" }
    }

    /** Replaces everything with the contents of a MyMoney-format backup. Atomic: nothing changes if the file is bad. */
    fun importFrom(uri: Uri) = viewModelScope.launch {
        runCatching {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
            val p = Mbak.parse(Mbak.decrypt(text))
            db.withTransaction {
                dao.wipeRecords(); dao.wipeBudgets(); dao.wipeCategories(); dao.wipeAccounts(); dao.clearPayeeRules()
                p.accounts.forEach { dao.put(it) }
                p.categories.forEach { dao.put(it) }
                if (p.categories.none { it.type == 2 && it.name == "Other" }) dao.put(Category(name = "Other", icon = "other", type = 2))
                if (p.categories.none { it.type == 1 && it.name == "Other income" }) dao.put(Category(name = "Other income", icon = "other", type = 1))
                p.records.forEach { dao.put(it) }
            }
            p.records.size
        }.onSuccess { message.value = "Imported $it records" }.onFailure { message.value = "Import failed: ${it.message}" }
    }

    fun setLock(pin: String?) {
        Prefs.setLock(getApplication(), pin?.let { hash(it) })
    }
    fun checkLock(pin: String) = Prefs.lock(getApplication()) == hash(pin)
    private fun hash(s: String) = MessageDigest.getInstance("SHA-256").digest(("expense-lock:$s").toByteArray()).joinToString("") { "%02x".format(it) }
}
