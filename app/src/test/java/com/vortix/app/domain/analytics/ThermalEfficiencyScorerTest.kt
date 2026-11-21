package com.vortix.app.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThermalEfficiencyScorerTest {
    @Test fun lowHeatPerWattGetsTopGrade() = assertEquals("A+", ThermalEfficiencyScorer.grade(40.0, 1.0))
    @Test fun moderateHeatPerWattGetsAGrade() = assertEquals("A", ThermalEfficiencyScorer.grade(20.0, 2.0))
    @Test fun elevatedHeatPerWattGetsBGrade() = assertEquals("B", ThermalEfficiencyScorer.grade(10.0, 2.0))
    @Test fun highHeatPerWattGetsCGrade() = assertEquals("C", ThermalEfficiencyScorer.grade(5.0, 2.0))
    @Test fun missingMeasurementsHaveNoGrade() = assertNull(ThermalEfficiencyScorer.grade(null, 1.0))
}
