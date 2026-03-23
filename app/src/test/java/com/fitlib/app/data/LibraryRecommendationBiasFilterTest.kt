package com.fitlib.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryRecommendationBiasFilterTest {
    @Test
    fun recommendationBiasFromScoreDelta_usesSharedThreshold() {
        assertEquals(RecommendationBias.Neutral, RecommendationBias.fromScoreDelta(0.49))
        assertEquals(RecommendationBias.MoreOften, RecommendationBias.fromScoreDelta(0.5))
        assertEquals(RecommendationBias.LessOften, RecommendationBias.fromScoreDelta(-0.5))
    }

    @Test
    fun recommendationBiasFilterClause_returnsNullWhenNoFilterIsSelected() {
        assertNull(recommendationBiasFilterClause(emptySet()))
    }

    @Test
    fun recommendationBiasFilterClause_supportsMoreOftenOnly() {
        assertEquals(
            "(COALESCE(p.preference_score_delta, 0) >= 0.5)",
            recommendationBiasFilterClause(setOf(RecommendationBias.MoreOften)),
        )
    }

    @Test
    fun recommendationBiasFilterClause_supportsLessOftenOnly() {
        assertEquals(
            "(COALESCE(p.preference_score_delta, 0) <= -0.5)",
            recommendationBiasFilterClause(setOf(RecommendationBias.LessOften)),
        )
    }

    @Test
    fun recommendationBiasFilterClause_supportsCombinedRecommendationFilters() {
        assertEquals(
            "(COALESCE(p.preference_score_delta, 0) >= 0.5 OR COALESCE(p.preference_score_delta, 0) <= -0.5)",
            recommendationBiasFilterClause(
                setOf(
                    RecommendationBias.MoreOften,
                    RecommendationBias.LessOften,
                ),
            ),
        )
    }

    @Test
    fun recommendationBiasFacetOptions_keepStableOrderingAndZeroCounts() {
        assertEquals(
            listOf(
                RecommendationBiasFilterOptionCount(RecommendationBias.MoreOften, 3),
                RecommendationBiasFilterOptionCount(RecommendationBias.LessOften, 0),
            ),
            recommendationBiasFacetOptions(
                mapOf(RecommendationBias.MoreOften to 3),
            ),
        )
    }

    @Test
    fun libraryFiltersActiveCount_includesRecommendationBiasSelections() {
        assertEquals(
            3,
            LibraryFilters(
                equipment = setOf("Cable"),
                recommendationBiases = setOf(RecommendationBias.MoreOften, RecommendationBias.LessOften),
            ).activeCount(),
        )
    }
}
