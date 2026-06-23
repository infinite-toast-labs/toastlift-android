package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class BountyCardsTest {
    private val baseInstant: Instant = Instant.parse("2026-06-19T10:00:00Z")

    @Test
    fun drySpellForcesEligibleBountyReveal() {
        val session = session(
            sets = listOf(
                completedSet(id = 101L, setNumber = 1, completedAt = baseInstant),
                plannedSet(id = 102L, setNumber = 2),
            ),
        )

        val result = evaluateBountyAfterSetCompletion(
            afterSession = session,
            exerciseIndex = 0,
            completedSetId = 101L,
            activeBounty = null,
            eligibleMissCount = 4,
            earnedCardsThisSessionCount = 0,
            now = baseInstant.plusSeconds(1),
        )

        assertNotNull(result.activeBounty)
        val bounty = result.activeBounty!!
        assertNull(result.earnedCard)
        assertEquals(0, result.eligibleMissCount)
        assertEquals(102L, bounty.targetSetId)
        assertEquals("Bench Press", bounty.exerciseName)
    }

    @Test
    fun completingVisibleBountyCreatesCollectibleCard() {
        val completedAt = baseInstant.plusSeconds(90)
        val session = session(
            sets = listOf(
                completedSet(id = 101L, setNumber = 1, completedAt = baseInstant),
                completedSet(id = 102L, setNumber = 2, completedAt = completedAt),
            ),
        )
        val exercise = session.exercises.first()
        val bounty = activeBounty(
            session = session,
            exercise = exercise,
            targetSetId = 102L,
            targetSetNumber = 2,
            type = BountyType.NO_SKIP_LINE,
        )

        val result = evaluateBountyAfterSetCompletion(
            afterSession = session,
            exerciseIndex = 0,
            completedSetId = 102L,
            activeBounty = bounty,
            eligibleMissCount = 2,
            earnedCardsThisSessionCount = 0,
            now = completedAt,
        )

        assertNotNull(result.earnedCard)
        val card = result.earnedCard!!
        assertNull(result.activeBounty)
        assertEquals(0, result.eligibleMissCount)
        assertEquals("No Skip Line", card.title)
        assertEquals(BountyCardFamily.CONTINUITY, card.family)
        assertEquals("Logged set 2 for Bench Press.", card.proofLine)
        assertEquals(2, card.sourceSetNumber)
    }

    @Test
    fun restWindowBountyCapturesElapsedRestProof() {
        val completedAt = baseInstant.plusSeconds(90)
        val session = session(
            sets = listOf(
                completedSet(id = 101L, setNumber = 1, completedAt = baseInstant),
                completedSet(id = 102L, setNumber = 2, completedAt = completedAt),
            ),
        )
        val exercise = session.exercises.first()
        val bounty = activeBounty(
            session = session,
            exercise = exercise,
            targetSetId = 102L,
            targetSetNumber = 2,
            type = BountyType.REST_SNIPER,
            sourceCompletedAtUtc = baseInstant.toString(),
            lowerRestSeconds = 60,
            upperRestSeconds = 110,
        )

        val result = evaluateBountyAfterSetCompletion(
            afterSession = session,
            exerciseIndex = 0,
            completedSetId = 102L,
            activeBounty = bounty,
            eligibleMissCount = 0,
            earnedCardsThisSessionCount = 0,
            now = completedAt,
        )

        assertNotNull(result.earnedCard)
        val card = result.earnedCard!!
        assertNull(result.activeBounty)
        assertEquals(BountyResolutionScope.REST_WINDOW, card.resolutionScope)
        assertEquals("Started at 1:30 rest.", card.proofLine)
    }

    @Test
    fun visibleBountySurvivesSetLoggedOnDifferentExercise() {
        val session = activeSession(
            exercises = listOf(
                exercise(
                    id = 2001L,
                    name = "Bench Press",
                    sets = listOf(
                        completedSet(id = 101L, setNumber = 1, completedAt = baseInstant),
                        plannedSet(id = 102L, setNumber = 2),
                    ),
                ),
                exercise(
                    id = 3001L,
                    name = "Cable Row",
                    sets = listOf(
                        completedSet(id = 201L, setNumber = 1, completedAt = baseInstant.plusSeconds(120)),
                        plannedSet(id = 202L, setNumber = 2),
                    ),
                ),
            ),
        )
        val bounty = activeBounty(
            session = session,
            exercise = session.exercises.first(),
            targetSetId = 102L,
            targetSetNumber = 2,
            type = BountyType.NO_SKIP_LINE,
        )

        val result = evaluateBountyAfterSetCompletion(
            afterSession = session,
            exerciseIndex = 1,
            completedSetId = 201L,
            activeBounty = bounty,
            eligibleMissCount = 3,
            earnedCardsThisSessionCount = 0,
            now = baseInstant.plusSeconds(120),
        )

        assertEquals(bounty, result.activeBounty)
        assertNull(result.earnedCard)
        assertEquals(3, result.eligibleMissCount)
    }

    @Test
    fun sessionCardCapSuppressesNewBountyReveal() {
        val session = session(
            sets = listOf(
                completedSet(id = 101L, setNumber = 1, completedAt = baseInstant),
                plannedSet(id = 102L, setNumber = 2),
                plannedSet(id = 103L, setNumber = 3),
            ),
        )

        val result = evaluateBountyAfterSetCompletion(
            afterSession = session,
            exerciseIndex = 0,
            completedSetId = 101L,
            activeBounty = null,
            eligibleMissCount = 4,
            earnedCardsThisSessionCount = 4,
            now = baseInstant.plusSeconds(1),
        )

        assertNull(result.activeBounty)
        assertNull(result.earnedCard)
        assertEquals(4, result.eligibleMissCount)
    }

    private fun session(sets: List<SessionSet>): ActiveSession {
        return activeSession(
            exercises = listOf(
                exercise(
                    id = 2001L,
                    name = "Bench Press",
                    sets = sets,
                ),
            ),
        )
    }

    private fun activeSession(exercises: List<SessionExercise>): ActiveSession {
        return ActiveSession(
            title = "Upper Day",
            origin = "generated",
            locationModeId = 2L,
            startedAtUtc = baseInstant.toString(),
            focusKey = "upper_body",
            exercises = exercises,
        )
    }

    private fun exercise(id: Long, name: String, sets: List<SessionSet>): SessionExercise {
        return SessionExercise(
            exerciseId = id,
            name = name,
            bodyRegion = "Upper Body",
            targetMuscleGroup = "Chest",
            equipment = "Barbell",
            restSeconds = 90,
            sets = sets,
        )
    }

    private fun plannedSet(id: Long, setNumber: Int): SessionSet {
        return SessionSet(
            id = id,
            setNumber = setNumber,
            targetReps = "8",
            reps = "8",
            weight = "185",
        )
    }

    private fun completedSet(id: Long, setNumber: Int, completedAt: Instant): SessionSet {
        return plannedSet(id = id, setNumber = setNumber).copy(
            completed = true,
            completedAtUtc = completedAt.toString(),
        )
    }

    private fun activeBounty(
        session: ActiveSession,
        exercise: SessionExercise,
        targetSetId: Long,
        targetSetNumber: Int,
        type: BountyType,
        sourceCompletedAtUtc: String? = null,
        lowerRestSeconds: Int? = null,
        upperRestSeconds: Int? = null,
    ): ActiveWorkoutBounty {
        return ActiveWorkoutBounty(
            bountyId = "bounty-test-${type.storageKey}",
            type = type,
            title = type.title,
            family = type.family,
            rarity = BountyCardRarity.STEEL,
            resolutionScope = type.resolutionScope,
            sessionStartedAtUtc = session.startedAtUtc,
            exerciseId = exercise.exerciseId,
            exerciseName = exercise.name,
            targetSetId = targetSetId,
            targetSetNumber = targetSetNumber,
            createdAtUtc = baseInstant.toString(),
            guidance = "Test guidance.",
            proofPrompt = "Test proof.",
            flavorText = type.flavorText,
            sourceCompletedAtUtc = sourceCompletedAtUtc,
            lowerRestSeconds = lowerRestSeconds,
            upperRestSeconds = upperRestSeconds,
            artSeed = "card-test-${type.storageKey}",
        )
    }
}
