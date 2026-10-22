package com.example.fintrack

import com.example.fintrack.data.local.KhataSnapshot
import com.example.fintrack.data.model.Customer
import com.example.fintrack.data.model.InterestAccrual
import com.example.fintrack.data.model.PaymentType
import com.example.fintrack.data.model.UdharType
import com.example.fintrack.data.repository.KhataRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KhataRepositoryTest {

    private val day = 86_400_000L
    private fun repo() = KhataRepository(seedDemoData = false)

    private fun KhataRepository.loan(principal: Double = 10_000.0): Customer =
        addCustomer("Rajesh", "999", principal, 5.0, 1_000 * day, "")

    private fun accrual(customerId: String, from: Long, to: Long, interest: Double) = InterestAccrual(
        customerId = customerId, startDate = from, endDate = to, interestRate = 5.0,
        principalBasis = 10_000.0, completedMonths = 1, partialDays = 0, daysInPartialMonth = 30,
        monthlyInterestAmount = interest, calculatedInterest = interest, calculationBreakdown = ""
    )

    @Test
    fun noDemoDataWhenSeedingDisabled() {
        val r = repo()
        assertTrue(r.customers.value.isEmpty())
        assertTrue(r.udharCustomers.value.isEmpty())
    }

    @Test
    fun accrualIncreasesOutstandingInterest() {
        val r = repo()
        val c = r.loan()
        assertTrue(r.recordInterestAccrual(accrual(c.id, 0, 30 * day, 500.0)).isSuccess)
        assertEquals(500.0, r.customers.value.single().outstandingInterest, 0.001)
    }

    @Test
    fun overlappingAccrualIsRejected() {
        val r = repo()
        val c = r.loan()
        r.recordInterestAccrual(accrual(c.id, 0, 30 * day, 500.0))
        assertTrue(r.recordInterestAccrual(accrual(c.id, 15 * day, 45 * day, 500.0)).isFailure)
        assertTrue(r.recordInterestAccrual(accrual(c.id, 30 * day, 60 * day, 500.0)).isSuccess)
    }

    @Test
    fun paymentCannotExceedOutstanding() {
        val r = repo()
        val c = r.loan()
        r.recordInterestAccrual(accrual(c.id, 0, 30 * day, 500.0))

        assertTrue(r.recordPayment(c.id, PaymentType.INTEREST_PAYMENT, 600.0, "CASH", "").isFailure)
        assertTrue(r.recordPayment(c.id, PaymentType.INTEREST_PAYMENT, 500.0, "CASH", "").isSuccess)
        assertTrue(r.recordPayment(c.id, PaymentType.PRINCIPAL_REPAYMENT, 10_000.01, "CASH", "").isFailure)
        assertTrue(r.recordPayment(c.id, PaymentType.PRINCIPAL_REPAYMENT, 4_000.0, "UPI", "").isSuccess)

        val updated = r.customers.value.single()
        assertEquals(6_000.0, updated.remainingPrincipal, 0.001)
        assertEquals(0.0, updated.outstandingInterest, 0.001)
    }

    @Test
    fun zeroOrNegativePaymentIsRejected() {
        val r = repo()
        val c = r.loan()
        assertTrue(r.recordPayment(c.id, PaymentType.PRINCIPAL_REPAYMENT, 0.0, "CASH", "").isFailure)
        assertTrue(r.recordPayment(c.id, PaymentType.PRINCIPAL_REPAYMENT, -5.0, "CASH", "").isFailure)
    }

    @Test
    fun udharEntryValidationAndBalance() {
        val r = repo()
        val c = r.addUdharCustomer("Ramesh", "", "", "")
        assertTrue(r.recordUdharEntry(c.id, 0.0, UdharType.GAVE, 0, "", "").isFailure)
        assertTrue(r.recordUdharEntry("missing", 10.0, UdharType.GAVE, 0, "", "").isFailure)
        r.recordUdharEntry(c.id, 500.0, UdharType.GAVE, 0, "", "")
        r.recordUdharEntry(c.id, 200.0, UdharType.GOT, 0, "", "")
        assertEquals(300.0, c.calculateBalance(r.udharEntries.value), 0.001)
    }

    @Test
    fun deletedRowsAreNotResurrectedByCloudMerge() {
        val r = repo()
        val c = r.loan()
        r.deleteCustomer(c.id)
        assertEquals(1, r.pendingDeletes().size)

        r.mergeRemote(KhataSnapshot(customers = listOf(c)))
        assertTrue(r.customers.value.isEmpty())

        r.clearPendingDeletes(r.pendingDeletes())
        r.mergeRemote(KhataSnapshot(customers = listOf(c)))
        assertEquals(1, r.customers.value.size)
    }

    @Test
    fun localRowsWinOnMergeAndAreNotDuplicated() {
        val r = repo()
        val c = r.loan()
        r.mergeRemote(KhataSnapshot(customers = listOf(c.copy(name = "Remote name"))))
        assertEquals(1, r.customers.value.size)
        assertEquals("Rajesh", r.customers.value.single().name)
    }

    @Test
    fun snapshotRoundTrip() {
        val r = repo()
        r.loan()
        val other = KhataRepository(seedDemoData = false)
        other.applySnapshot(r.snapshot())
        assertEquals(r.customers.value, other.customers.value)
        assertFalse(other.customers.value.isEmpty())
    }
}
