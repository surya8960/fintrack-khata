package com.example.fintrack

import com.example.fintrack.data.model.Customer
import com.example.fintrack.data.model.InterestAccrual
import com.example.fintrack.domain.calculator.InterestCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InterestCalculatorTest {

    @Test
    fun testUserPromptExample_SingleMonth() {
        // User example from prompt:
        // Principal: ₹10,000
        // Monthly interest rate: 5%
        // Start date: 5 October
        // End date: 5 November
        // Expected Monthly interest: ₹10,000 × 5% = ₹500
        // Expected Principal remains: ₹10,000
        // Expected Accrued interest: ₹500
        val startDate = LocalDate.of(2026, 10, 5)
        val endDate = LocalDate.of(2026, 11, 5)

        val result = InterestCalculator.calculate(
            principal = 10000.0,
            monthlyRate = 5.0,
            startEpochMs = InterestCalculator.localDateToMillis(startDate),
            endEpochMs = InterestCalculator.localDateToMillis(endDate),
            existingAccruals = emptyList()
        )

        assertEquals(1, result.completedMonths)
        assertEquals(0, result.partialDays)
        assertEquals(500.0, result.monthlyInterestAmount, 0.01)
        assertEquals(500.0, result.totalAccruedInterest, 0.01)
        assertEquals(10000.0, result.principalBasis, 0.01)
        assertFalse(result.isOverlapping)
    }

    @Test
    fun testTwoFullMonths_NoCompoundInterest() {
        // Principal remains fixed; each month earns 5% = 500
        val startDate = LocalDate.of(2026, 10, 5)
        val endDate = LocalDate.of(2026, 12, 5)

        val result = InterestCalculator.calculate(
            principal = 10000.0,
            monthlyRate = 5.0,
            startEpochMs = InterestCalculator.localDateToMillis(startDate),
            endEpochMs = InterestCalculator.localDateToMillis(endDate),
            existingAccruals = emptyList()
        )

        assertEquals(2, result.completedMonths)
        assertEquals(0, result.partialDays)
        assertEquals(1000.0, result.totalAccruedInterest, 0.01)
        assertEquals(10000.0, result.principalBasis, 0.01)
    }

    @Test
    fun testPartialMonth_ProportionalCalculation() {
        // Start: Oct 5, End: Nov 20 (1 full month Oct 5 - Nov 5, plus 15 partial days)
        val startDate = LocalDate.of(2026, 10, 5)
        val endDate = LocalDate.of(2026, 11, 20)

        val result = InterestCalculator.calculate(
            principal = 10000.0,
            monthlyRate = 5.0,
            startEpochMs = InterestCalculator.localDateToMillis(startDate),
            endEpochMs = InterestCalculator.localDateToMillis(endDate)
        )

        assertEquals(1, result.completedMonths)
        assertEquals(15, result.partialDays)
        assertTrue(result.daysInPartialMonth >= 28)
        // 500 for full month + 500 * (15 / daysInPartialMonth)
        assertTrue(result.totalAccruedInterest > 500.0)
        assertTrue(result.totalAccruedInterest < 1000.0)
    }

    @Test
    fun testDuplicateAndOverlappingPeriodPrevention() {
        val startDate1 = LocalDate.of(2026, 10, 5)
        val endDate1 = LocalDate.of(2026, 11, 5)

        val existingAccrual = InterestAccrual(
            id = "acc_1",
            customerId = "cust_1",
            startDate = InterestCalculator.localDateToMillis(startDate1),
            endDate = InterestCalculator.localDateToMillis(endDate1),
            interestRate = 5.0,
            principalBasis = 10000.0,
            completedMonths = 1,
            partialDays = 0,
            daysInPartialMonth = 31,
            monthlyInterestAmount = 500.0,
            calculatedInterest = 500.0,
            calculationBreakdown = "1 month = ₹500"
        )

        // Try overlapping period: Oct 20 to Nov 20
        val overlappingStart = LocalDate.of(2026, 10, 20)
        val overlappingEnd = LocalDate.of(2026, 11, 20)

        val resultOverlap = InterestCalculator.calculate(
            principal = 10000.0,
            monthlyRate = 5.0,
            startEpochMs = InterestCalculator.localDateToMillis(overlappingStart),
            endEpochMs = InterestCalculator.localDateToMillis(overlappingEnd),
            existingAccruals = listOf(existingAccrual)
        )

        assertTrue(resultOverlap.isOverlapping)
        assertTrue(resultOverlap.overlapErrorMessage?.contains("Overlapping") == true)

        // Non-overlapping period: Nov 5 to Dec 5 should be accepted
        val nextPeriodStart = LocalDate.of(2026, 11, 5)
        val nextPeriodEnd = LocalDate.of(2026, 12, 5)

        val resultValid = InterestCalculator.calculate(
            principal = 10000.0,
            monthlyRate = 5.0,
            startEpochMs = InterestCalculator.localDateToMillis(nextPeriodStart),
            endEpochMs = InterestCalculator.localDateToMillis(nextPeriodEnd),
            existingAccruals = listOf(existingAccrual)
        )

        assertFalse(resultValid.isOverlapping)
        assertEquals(500.0, resultValid.totalAccruedInterest, 0.01)
    }

    @Test
    fun testCustomerPaymentSeparation() {
        // Customer with ₹10,000 principal, ₹500 accrued interest
        val customer = Customer(
            id = "cust_123",
            name = "Ramesh Kumar",
            phone = "9876543210",
            principalAmount = 10000.0,
            monthlyInterestRate = 5.0,
            loanStartDate = InterestCalculator.localDateToMillis(LocalDate.of(2026, 10, 5)),
            totalAccruedInterest = 500.0,
            totalInterestPaid = 0.0,
            totalPrincipalRepaid = 0.0
        )

        assertEquals(10000.0, customer.remainingPrincipal, 0.01)
        assertEquals(500.0, customer.outstandingInterest, 0.01)
        assertEquals(10500.0, customer.totalDue, 0.01)

        // 1. Interest Payment of ₹300 reduces interest ONLY, principal remains ₹10,000
        val afterInterestPayment = customer.copy(totalInterestPaid = 300.0)
        assertEquals(10000.0, afterInterestPayment.remainingPrincipal, 0.01)
        assertEquals(200.0, afterInterestPayment.outstandingInterest, 0.01)
        assertEquals(10200.0, afterInterestPayment.totalDue, 0.01)

        // 2. Principal Repayment of ₹2000 reduces principal ONLY
        val afterPrincipalRepay = afterInterestPayment.copy(totalPrincipalRepaid = 2000.0)
        assertEquals(8000.0, afterPrincipalRepay.remainingPrincipal, 0.01)
        assertEquals(200.0, afterPrincipalRepay.outstandingInterest, 0.01)
        assertEquals(8200.0, afterPrincipalRepay.totalDue, 0.01)
        // Original lent basis remains intact for audit history
        assertEquals(10000.0, afterPrincipalRepay.principalAmount, 0.01)
    }

    private fun ms(y: Int, m: Int, d: Int) = InterestCalculator.localDateToMillis(LocalDate.of(y, m, d))

    @Test
    fun testMonthEndStart_doesNotDriftThroughFebruary() {
        // 31 Jan -> 31 Mar is exactly two months. Chaining month-by-month would land on 28 Mar
        // after February and wrongly leave 3 partial days.
        val result = InterestCalculator.calculate(10000.0, 5.0, ms(2026, 1, 31), ms(2026, 3, 31))
        assertEquals(2, result.completedMonths)
        assertEquals(0, result.partialDays)
        assertEquals(1000.0, result.totalAccruedInterest, 0.001)
    }

    @Test
    fun testPartialMonthUsesActualLengthOfThatMonth() {
        // 1 Feb -> 15 Feb 2026: 14 days of a 28-day month = exactly half a month
        val result = InterestCalculator.calculate(10000.0, 5.0, ms(2026, 2, 1), ms(2026, 2, 15))
        assertEquals(0, result.completedMonths)
        assertEquals(14, result.partialDays)
        assertEquals(28, result.daysInPartialMonth)
        assertEquals(250.0, result.totalAccruedInterest, 0.001)
    }

    @Test
    fun testLeapYearFebruary() {
        val result = InterestCalculator.calculate(10000.0, 5.0, ms(2028, 2, 1), ms(2028, 2, 15))
        assertEquals(29, result.daysInPartialMonth)
    }

    @Test
    fun testEndBeforeStartIsRejected() {
        val result = InterestCalculator.calculate(10000.0, 5.0, ms(2026, 5, 10), ms(2026, 5, 10))
        assertEquals(0.0, result.totalAccruedInterest, 0.0)
        assertEquals("End date must be after Start date", result.overlapErrorMessage)
    }

    @Test
    fun testAdjacentPeriodsDoNotOverlap() {
        val existing = InterestAccrual(
            customerId = "c", startDate = ms(2026, 10, 5), endDate = ms(2026, 11, 5), interestRate = 5.0,
            principalBasis = 10000.0, completedMonths = 1, partialDays = 0, daysInPartialMonth = 31,
            monthlyInterestAmount = 500.0, calculatedInterest = 500.0, calculationBreakdown = ""
        )
        val next = InterestCalculator.calculate(10000.0, 5.0, ms(2026, 11, 5), ms(2026, 12, 5), listOf(existing))
        assertFalse(next.isOverlapping)
    }

    @Test
    fun testRoundMoneyIsHalfUp() {
        assertEquals(1.01, InterestCalculator.roundMoney(1.005), 0.0)
        assertEquals(2.68, InterestCalculator.roundMoney(2.675), 0.0)
        assertEquals(0.0, InterestCalculator.roundMoney(0.0), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeRateIsRejected() {
        InterestCalculator.calculate(10000.0, -1.0, ms(2026, 1, 1), ms(2026, 2, 1))
    }
}
