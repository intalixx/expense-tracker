package com.intalix.expense.tracker

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** All money is stored as Long paise so there is never any floating-point rounding. */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "wallet",
    val initialPaise: Long = 0,
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "category",
    /** 1 = income, 2 = expense (same convention as MyMoney backups). */
    val type: Int,
)

@Entity(
    tableName = "records",
    indices = [Index("time"), Index("accountId"), Index("categoryId"), Index("reference")],
)
data class Rec(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val categoryId: Long,
    val type: Int,
    val amountPaise: Long,
    val note: String = "",
    val time: Long,
    /** manual | sms | notification | import */
    val source: String = "manual",
    val reference: String? = null,
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 0 = overall monthly budget, otherwise a category id */
    val categoryId: Long = 0,
    val limitPaise: Long,
)

/** A payment message that was recognised but has not (yet) become a record. */
@Entity(tableName = "pending", indices = [Index("status")])
data class Pending(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val direction: Int,
    val amountPaise: Long,
    val counterparty: String?,
    val bank: String?,
    val last4: String?,
    val reference: String?,
    val raw: String,
    val sender: String,
    val source: String,
    /** why it needs a human: e.g. "ambiguous", "unknown merchant" */
    val reason: String,
    /** 0 = waiting, 1 = accepted, 2 = dismissed */
    val status: Int = 0,
    val suggestedCategoryId: Long? = null,
    val suggestedAccountId: Long? = null,
)

/** Raw message ids already handled — guarantees the same SMS is never processed twice. */
@Entity(tableName = "seen", primaryKeys = ["key"])
data class Seen(val key: String, val at: Long)

/**
 * Every person / merchant money was paid to or received from, one row per (name key, direction). [categoryId] is the
 * user's lookup: "payments to this payee always go to this category" (null = no rule yet). Filled automatically from
 * incoming messages and from existing records; edited in Settings -> People & payees.
 */
@Entity(tableName = "payees", primaryKeys = ["key", "type"])
data class Payee(
    /** [merchantKey] of the name: lower-case letters/digits/@ only, so "SUDIP  Santra" and "sudip santra" are one payee. */
    val key: String,
    /** 1 = received from, 2 = paid to (same numbers as Rec.type / Category.type) */
    val type: Int,
    /** display name, as the latest message wrote it */
    val name: String,
    val categoryId: Long? = null,
    /** if true (and a rule exists), matching confident messages are added without review */
    val auto: Boolean = false,
    val lastTime: Long = 0,
)

/** bank / last-4 digits → account, e.g. "kotak:1234" → Savings */
@Entity(tableName = "account_links")
data class AccountLink(@PrimaryKey val key: String, val accountId: Long)

@Dao
interface AppDao {
    @Query("SELECT * FROM accounts ORDER BY id") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM accounts ORDER BY id") suspend fun accountsNow(): List<Account>
    @Query("SELECT * FROM categories ORDER BY type DESC, name") fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM categories ORDER BY type DESC, name") suspend fun categoriesNow(): List<Category>
    @Query("SELECT * FROM records ORDER BY time DESC") fun records(): Flow<List<Rec>>
    @Query("SELECT * FROM records ORDER BY time DESC") suspend fun recordsNow(): List<Rec>
    @Query("SELECT * FROM budgets") fun budgets(): Flow<List<Budget>>
    @Query("SELECT * FROM budgets") suspend fun budgetsNow(): List<Budget>
    @Query("SELECT * FROM pending WHERE status = 0 ORDER BY time DESC") fun pending(): Flow<List<Pending>>
    @Query("SELECT COUNT(*) FROM pending WHERE status = 0") fun pendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(a: Account): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(c: Category): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(r: Rec): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(b: Budget): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(p: Pending): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(p: Payee)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPayee(p: Payee): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(l: AccountLink)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun markSeen(s: Seen): Long

    @Delete suspend fun delete(r: Rec)
    @Delete suspend fun delete(b: Budget)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategory(id: Long)
    @Query("DELETE FROM accounts WHERE id = :id") suspend fun deleteAccount(id: Long)
    @Query("DELETE FROM records WHERE accountId = :id") suspend fun deleteRecordsOfAccount(id: Long)
    @Query("UPDATE records SET categoryId = :to WHERE categoryId = :from") suspend fun moveCategory(from: Long, to: Long)
    @Query("UPDATE pending SET status = :status WHERE id = :id") suspend fun setPendingStatus(id: Long, status: Int)

    @Query("SELECT * FROM records WHERE reference = :ref AND amountPaise = :amt AND type = :type LIMIT 1")
    suspend fun findByRef(ref: String, amt: Long, type: Int): Rec?
    @Query("SELECT * FROM records WHERE amountPaise = :amt AND type = :type AND time BETWEEN :from AND :to LIMIT 1")
    suspend fun findNear(amt: Long, type: Int, from: Long, to: Long): Rec?
    @Query("SELECT * FROM records WHERE amountPaise = :amt AND type = :type AND time BETWEEN :from AND :to")
    suspend fun findAllNear(amt: Long, type: Int, from: Long, to: Long): List<Rec>
    /** A notification-sourced record that has not yet been matched to a bank SMS (no reference). */
    @Query("SELECT * FROM records WHERE amountPaise = :amt AND type = :type AND source = 'notification' AND reference IS NULL AND time BETWEEN :from AND :to ORDER BY ABS(time - :near) LIMIT 1")
    suspend fun findUnmatchedNotifRec(amt: Long, type: Int, from: Long, to: Long, near: Long): Rec?
    @Query("SELECT * FROM pending WHERE amountPaise = :amt AND direction = :dir AND time BETWEEN :from AND :to AND status = 0 LIMIT 1")
    suspend fun findPendingNear(amt: Long, dir: Int, from: Long, to: Long): Pending?
    @Query("SELECT * FROM pending WHERE reference = :ref AND amountPaise = :amt AND direction = :dir LIMIT 1")
    suspend fun findPendingByRef(ref: String, amt: Long, dir: Int): Pending?

    @Query("SELECT * FROM payees WHERE `key` = :k AND type = :type") suspend fun payee(k: String, type: Int): Payee?
    @Query("SELECT * FROM payees ORDER BY lastTime DESC, name") fun payees(): Flow<List<Payee>>
    @Query("SELECT * FROM payees") suspend fun payeesNow(): List<Payee>
    /** keeps the newest spelling of the name and the newest time; never touches the category rule */
    @Query("UPDATE payees SET name = CASE WHEN :t >= lastTime THEN :name ELSE name END, lastTime = MAX(lastTime, :t) WHERE `key` = :k AND type = :type")
    suspend fun touchPayee(k: String, type: Int, name: String, t: Long)
    @Query("UPDATE payees SET categoryId = :cat, auto = :auto WHERE `key` = :k AND type = :type")
    suspend fun setPayeeRule(k: String, type: Int, cat: Long?, auto: Boolean)
    /** after a backup import the category ids are different, so every rule would point at the wrong category */
    @Query("UPDATE payees SET categoryId = NULL, auto = 0") suspend fun clearPayeeRules()
    @Query("SELECT * FROM account_links WHERE key = :k") suspend fun link(k: String): AccountLink?
    @Query("SELECT * FROM account_links") suspend fun linksNow(): List<AccountLink>
    @Query("SELECT * FROM account_links ORDER BY `key`") fun links(): Flow<List<AccountLink>>
    @Query("DELETE FROM account_links WHERE key = :k") suspend fun deleteLink(k: String)

    @Query("DELETE FROM records") suspend fun wipeRecords()
    @Query("DELETE FROM budgets") suspend fun wipeBudgets()
    @Query("DELETE FROM categories") suspend fun wipeCategories()
    @Query("DELETE FROM accounts") suspend fun wipeAccounts()
}

@Database(
    entities = [Account::class, Category::class, Rec::class, Budget::class, Pending::class, Seen::class, Payee::class, AccountLink::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "expenses.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // Seed defaults with plain SQL so the very first SMS can never hit an empty DB.
                        val inc = listOf("Awards", "Coupons", "Grants", "Lottery", "Refunds", "Rental", "Salary", "Sale")
                        val exp = listOf("Bills", "Car", "Clothing", "Education", "Electronics", "Entertainment", "Food", "Health",
                            "Home", "Insurance", "Shopping", "Social", "Sport", "Tax", "Telephone", "Transportation", "Baby", "Beauty")
                        inc.forEach { db.execSQL("INSERT INTO categories(name,icon,type) VALUES(?,?,1)", arrayOf(it, it.lowercase())) }
                        exp.forEach { db.execSQL("INSERT INTO categories(name,icon,type) VALUES(?,?,2)", arrayOf(it, it.lowercase())) }
                        db.execSQL("INSERT INTO categories(name,icon,type) VALUES('Other','other',2)")
                        db.execSQL("INSERT INTO categories(name,icon,type) VALUES('Other income','other',1)")
                        listOf("Cash" to "cash", "Card" to "card", "Savings" to "savings").forEach {
                            db.execSQL("INSERT INTO accounts(name,icon,initialPaise) VALUES(?,?,0)", arrayOf(it.first, it.second))
                        }
                    }
                }).build().also { inst = it }
        }
    }
}

/** v1 -> v2: `merchant_rules` (key -> category, learned from the review list) becomes `payees`, which also lists every payee seen. */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `payees` (`key` TEXT NOT NULL, `type` INTEGER NOT NULL, `name` TEXT NOT NULL, `categoryId` INTEGER, `auto` INTEGER NOT NULL, `lastTime` INTEGER NOT NULL, PRIMARY KEY(`key`, `type`))")
        // the old rule did not store the direction; the category's own type is the same thing
        db.execSQL("INSERT OR IGNORE INTO payees(`key`, type, name, categoryId, auto, lastTime) SELECT r.merchantKey, c.type, r.merchantKey, r.categoryId, r.auto, 0 FROM merchant_rules r JOIN categories c ON c.id = r.categoryId")
        db.execSQL("DROP TABLE merchant_rules")
    }
}

fun merchantKey(name: String?): String =
    (name ?: "").lowercase().replace(Regex("[^a-z0-9@]"), "")
