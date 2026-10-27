package com.floralens.domain.model

/**
 * ALWAYS derived from lastWateredAt + intervalDays (data-flow rule),
 * never stored. See ComputeCareStatus.
 */
enum class CareStatus { OVERDUE, DUE_TODAY, THIS_WEEK, HEALTHY }
