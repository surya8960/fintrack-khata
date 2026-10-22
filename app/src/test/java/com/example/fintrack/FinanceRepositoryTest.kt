package com.example.fintrack

import com.example.fintrack.data.model.AccountType
import com.example.fintrack.data.model.TransactionType
import com.example.fintrack.data.repository.FinanceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceRepositoryTest {

    private fun repo() = FinanceRepository(seedDemoData = false)

    private fun FinanceRepository.balance(id: String) = accounts.value.first { it.id == id }.balance

    @Test
    fun incomeAndExpenseAdjustBalance() {
        val r = repo()
        val a = r.addAccount("Bank", AccountType.BANK, 1_000.0, "", "#000000")
        r.addTransaction(a.id, null, "Salary", 500.0, TransactionType.INCOME, "Pay", "")
        r.addTransaction(a.id, null, "Rent", 200.0, TransactionType.EXPENSE, "Rent", "")
        assertEquals(1_300.0, r.balance(a.id), 0.001)
    }

    @Test
    fun transferMovesMoneyBetweenAccountsAndIsReversible() {
        val r = repo()
        val a = r.addAccount("Bank", AccountType.BANK, 1_000.0, "", "#000000")
        val b = r.addAccount("Cash", AccountType.CASH, 0.0, "", "#000000")
        val tx = r.addTransaction(a.id, b.id, "ATM", 400.0, TransactionType.TRANSFER, "Transfer", "").getOrThrow()
        assertEquals(600.0, r.balance(a.id), 0.001)
        assertEquals(400.0, r.balance(b.id), 0.001)

        r.deleteTransaction(tx.id)
        assertEquals(1_000.0, r.balance(a.id), 0.001)
        assertEquals(0.0, r.balance(b.id), 0.001)
    }

    @Test
    fun invalidTransactionsAreRejectedWithoutChangingBalances() {
        val r = repo()
        val a = r.addAccount("Bank", AccountType.BANK, 1_000.0, "", "#000000")
        assertTrue(r.addTransaction(a.id, null, "", 10.0, TransactionType.EXPENSE, "x", "").isFailure)
        assertTrue(r.addTransaction(a.id, null, "x", -10.0, TransactionType.EXPENSE, "x", "").isFailure)
        assertTrue(r.addTransaction("nope", null, "x", 10.0, TransactionType.EXPENSE, "x", "").isFailure)
        assertTrue(r.addTransaction(a.id, null, "x", 10.0, TransactionType.TRANSFER, "x", "").isFailure)
        assertTrue(r.addTransaction(a.id, a.id, "x", 10.0, TransactionType.TRANSFER, "x", "").isFailure)
        assertEquals(1_000.0, r.balance(a.id), 0.001)
        assertTrue(r.transactions.value.isEmpty())
    }

    @Test
    fun deletingAccountClearsDanglingTransferTarget() {
        val r = repo()
        val a = r.addAccount("Bank", AccountType.BANK, 1_000.0, "", "#000000")
        val b = r.addAccount("Cash", AccountType.CASH, 0.0, "", "#000000")
        r.addTransaction(a.id, b.id, "ATM", 100.0, TransactionType.TRANSFER, "Transfer", "")
        r.deleteAccount(b.id)
        assertNull(r.transactions.value.single().targetAccountId)
        assertEquals(1, r.pendingDeletes().size)
    }

    @Test
    fun budgetKeepsItsMonth() {
        val r = repo()
        val budget = r.addBudget("Food", 5_000.0, "2026-10")
        assertEquals("2026-10", budget.monthYear)
    }

    @Test
    fun goalContributionValidation() {
        val r = repo()
        val g = r.addGoal("Laptop", 1_000.0, 100.0, 30, "#000000")
        assertFalse(r.contributeToGoal(g.id, -5.0))
        assertFalse(r.contributeToGoal("missing", 5.0))
        assertTrue(r.contributeToGoal(g.id, 50.0))
        assertEquals(150.0, r.goals.value.single().currentAmount, 0.001)
    }
}
