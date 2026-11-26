package com.example.fintrack.domain.calculator

import com.example.fintrack.data.model.InterestAccrual
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.math.BigDecimal
import java.math.RoundingMode

data class CalculationResult(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val principalBasis: Double,
    val monthlyRate: Double,
    val monthlyInterestAmount: Double,
    val completedMonths: Int,
    val partialDays: Int,
    val daysInPartialMonth: Int,
    val fullMonthsInterest: Double,
    val partialMonthInterest: Double,
    val totalAccruedInterest: Double,
    val formulaBreakdown: String,
    val isOverlapping: Boolean = false,
    val overlapErrorMessage: String? = null
)

object InterestCalculator {

    /** Rounds a rupee amount to 2 decimals using commercial (HALF_UP) rounding. */
    fun roundMoney(value: Double): Double =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    /**
     * Converts epoch milliseconds to LocalDate in system default zone
     */
    fun millisToLocalDate(millis: Long): LocalDate {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    /**
     * Converts LocalDate to start-of-day epoch milliseconds
     */
    fun localDateToMillis(date: LocalDate): Long {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /**
     * Computes the Simple Interest based on fixed principal amount and custom date range.
     * Rule:
     * - Fixed principal amount is used (NO compound interest).
     * - Each completed monthly period earns: Principal * (Rate / 100).
     * - Any remaining partial period is calculated proportionally:
     *   (Principal * (Rate / 100)) * (partialDays / daysInPartialMonth).
     */
    fun calculate(
        principal: Double,
        monthlyRate: Double,
        startEpochMs: Long,
        endEpochMs: Long,
        existingAccruals: List<InterestAccrual> = emptyList()
    ): CalculationResult {
        val startDate = millisToLocalDate(startEpochMs)
        val endDate = millisToLocalDate(endEpochMs)

        // 1. Validation: positive principal, non-negative rate, End date strictly after start date
        require(principal >= 0.0 && principal.isFinite()) { "Principal must be a non-negative number" }
        require(monthlyRate >= 0.0 && monthlyRate.isFinite()) { "Rate must be a non-negative number" }
        if (!endDate.isAfter(startDate)) {
            val monthlyInterest = (principal * monthlyRate) / 100.0
            return CalculationResult(
                startDate = startDate,
                endDate = endDate,
                principalBasis = principal,
                monthlyRate = monthlyRate,
                monthlyInterestAmount = monthlyInterest,
                completedMonths = 0,
                partialDays = 0,
                daysInPartialMonth = 30,
                fullMonthsInterest = 0.0,
                partialMonthInterest = 0.0,
                totalAccruedInterest = 0.0,
                formulaBreakdown = "End date must be after Start date.",
                isOverlapping = false,
                overlapErrorMessage = "End date must be after Start date"
            )
        }

        // 2. Overlap & Duplicate check
        val overlappingEntry = existingAccruals.firstOrNull { existing ->
            val exStart = millisToLocalDate(existing.startDate)
            val exEnd = millisToLocalDate(existing.endDate)
            // Intervals [startDate, endDate] and [exStart, exEnd] overlap if:
            // max(startDate, exStart) < min(endDate, exEnd)
            val overlapStart = if (startDate.isAfter(exStart)) startDate else exStart
            val overlapEnd = if (endDate.isBefore(exEnd)) endDate else exEnd
            overlapStart.isBefore(overlapEnd)
        }

        val isOverlapping = overlappingEntry != null
        val overlapError = if (overlappingEntry != null) {
            val exStart = millisToLocalDate(overlappingEntry.startDate)
            val exEnd = millisToLocalDate(overlappingEntry.endDate)
            "Overlapping period! Interest is already recorded for $exStart to $exEnd."
        } else null

        // 3. Count complete monthly periods. Every boundary is derived from the ORIGINAL
        //    start date (startDate + n months) instead of chaining month-by-month, so a loan
        //    that starts on the 31st does not drift to the 28th after passing through February.
        var completedMonths = 0
        while (!startDate.plusMonths((completedMonths + 1).toLong()).isAfter(endDate)) {
            completedMonths++
        }
        val currentAnchor = startDate.plusMonths(completedMonths.toLong())

        // 4. Calculate partial month (always between 28 and 31 days long)
        val partialDays = ChronoUnit.DAYS.between(currentAnchor, endDate).toInt()
        val nextFullCycle = startDate.plusMonths((completedMonths + 1).toLong())
        val daysInPartialMonth = ChronoUnit.DAYS.between(currentAnchor, nextFullCycle).toInt()

        val monthlyInterest = (principal * monthlyRate) / 100.0
        val fullMonthsInterest = completedMonths * monthlyInterest

        val partialMonthInterest = if (partialDays > 0 && daysInPartialMonth > 0) {
            monthlyInterest * (partialDays.toDouble() / daysInPartialMonth.toDouble())
        } else {
            0.0
        }

        val totalAccrued = roundMoney(fullMonthsInterest + partialMonthInterest)

        // 5. Build clean, auditable formula breakdown
        val breakdown = buildString {
            append("• Principal basis: ₹%,.2f\n".format(Locale.ENGLISH, principal))
            append("• Monthly rate: %.2f%% (₹%,.2f / month)\n".format(Locale.ENGLISH, monthlyRate, monthlyInterest))
            append("• Completed monthly periods: %d month(s) = ₹%,.2f\n".format(Locale.ENGLISH, completedMonths, fullMonthsInterest))
            if (partialDays > 0) {
                append("• Partial period: %d / %d days = ₹%,.2f (proportional)\n".format(
                    Locale.ENGLISH, partialDays, daysInPartialMonth, partialMonthInterest
                ))
            } else {
                append("• Partial period: None (exact full period)\n")
            }
            append("• Total Accrued Simple Interest: ₹%,.2f".format(Locale.ENGLISH, totalAccrued))
        }

        return CalculationResult(
            startDate = startDate,
            endDate = endDate,
            principalBasis = principal,
            monthlyRate = monthlyRate,
            monthlyInterestAmount = monthlyInterest,
            completedMonths = completedMonths,
            partialDays = partialDays,
            daysInPartialMonth = daysInPartialMonth,
            fullMonthsInterest = fullMonthsInterest,
            partialMonthInterest = partialMonthInterest,
            totalAccruedInterest = totalAccrued,
            formulaBreakdown = breakdown,
            isOverlapping = isOverlapping,
            overlapErrorMessage = overlapError
        )
    }
}
