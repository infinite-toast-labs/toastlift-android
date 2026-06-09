package dev.toastlabs.toastlift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ExerciseFamilyTest {
    @Test
    fun buildExerciseFamily_groupsProgressionsRegressionsAndSetupBranchesWithoutDuplicates() {
        val anchor = detail(
            id = 1L,
            name = "Dumbbell Bench Press",
            difficulty = "Intermediate",
            target = "Chest",
            equipment = "Dumbbell",
            mechanics = "Compound",
            primeMover = "Pectoralis Major",
            movementPatterns = listOf("Horizontal Push"),
        )
        val candidates = listOf(
            profile(
                id = 2L,
                name = "Barbell Bench Press",
                difficulty = "Advanced",
                target = "Chest",
                equipment = "Barbell",
                mechanics = "Compound",
                primeMover = "Pectoralis Major",
                movementPatterns = listOf("Horizontal Push"),
            ),
            profile(
                id = 3L,
                name = "Push-Up",
                difficulty = "Beginner",
                target = "Chest",
                equipment = "Bodyweight",
                mechanics = "Compound",
                primeMover = "Pectoralis Major",
                movementPatterns = listOf("Horizontal Push"),
            ),
            profile(
                id = 4L,
                name = "Cable Chest Press",
                difficulty = "Intermediate",
                target = "Chest",
                equipment = "Cable",
                mechanics = "Compound",
                primeMover = "Pectoralis Major",
                movementPatterns = listOf("Horizontal Push"),
            ),
            profile(
                id = 5L,
                name = "Dumbbell Shoulder Press",
                difficulty = "Intermediate",
                target = "Shoulders",
                equipment = "Dumbbell",
                mechanics = "Compound",
                primeMover = "Anterior Deltoid",
                movementPatterns = listOf("Vertical Push"),
            ),
            profile(
                id = 6L,
                name = "Machine Chest Press",
                difficulty = "Intermediate",
                target = "Chest",
                equipment = "Machine",
                mechanics = "Compound",
                primeMover = "Pectoralis Major",
                movementPatterns = listOf("Horizontal Push"),
            ),
        )

        val family = buildExerciseFamily(
            anchor = anchor,
            candidates = candidates,
            generatedAtUtc = Instant.parse("2026-06-09T00:00:00Z"),
        )

        assertEquals(5, family.relatedExerciseCount)
        assertEquals("Barbell Bench Press", family.section(ExerciseFamilySectionKind.Progressions).candidates.first().exercise.name)
        assertEquals("Push-Up", family.section(ExerciseFamilySectionKind.Regressions).candidates.first().exercise.name)
        assertTrue(
            family.section(ExerciseFamilySectionKind.SameMuscleDifferentSetup)
                .candidates
                .any { it.exercise.name == "Cable Chest Press" },
        )
        assertEquals(
            "Dumbbell Shoulder Press",
            family.section(ExerciseFamilySectionKind.SameSetupDifferentTarget).candidates.first().exercise.name,
        )
        val allIds = family.sections.flatMap { section -> section.candidates.map { it.exercise.id } }
        assertEquals(allIds.distinct(), allIds)
    }

    @Test
    fun buildExerciseFamily_returnsEmptySectionsWhenNoUsefulRelationshipExists() {
        val family = buildExerciseFamily(
            anchor = detail(
                id = 1L,
                name = "Cable Curl",
                target = "Biceps",
                equipment = "Cable",
                primeMover = "Biceps Brachii",
                movementPatterns = listOf("Elbow Flexion"),
            ),
            candidates = listOf(
                profile(
                    id = 2L,
                    name = "Bodyweight Calf Raise",
                    target = "Calves",
                    equipment = "Bodyweight",
                    primeMover = "Gastrocnemius",
                    movementPatterns = listOf("Plantar Flexion"),
                ),
            ),
            generatedAtUtc = Instant.parse("2026-06-09T00:00:00Z"),
        )

        assertEquals(0, family.relatedExerciseCount)
        assertTrue(family.sections.isEmpty())
    }

    @Test
    fun buildExerciseFamily_penalizesHiddenAndBannedCandidates() {
        val family = buildExerciseFamily(
            anchor = detail(
                id = 1L,
                name = "Lat Pulldown",
                target = "Back",
                equipment = "Cable",
                primeMover = "Latissimus Dorsi",
                movementPatterns = listOf("Vertical Pull"),
            ),
            candidates = listOf(
                profile(
                    id = 2L,
                    name = "Hidden Pulldown",
                    target = "Back",
                    equipment = "Cable",
                    primeMover = "Latissimus Dorsi",
                    movementPatterns = listOf("Vertical Pull"),
                    hidden = true,
                ),
                profile(
                    id = 3L,
                    name = "Banned Pulldown",
                    target = "Back",
                    equipment = "Cable",
                    primeMover = "Latissimus Dorsi",
                    movementPatterns = listOf("Vertical Pull"),
                    banned = true,
                ),
            ),
            generatedAtUtc = Instant.parse("2026-06-09T00:00:00Z"),
        )

        assertFalse(family.sections.flatMap { it.candidates }.any { it.exercise.hidden || it.exercise.banned })
        assertEquals(0, family.relatedExerciseCount)
    }

    private fun ExerciseFamily.section(kind: ExerciseFamilySectionKind): ExerciseFamilySection =
        sections.first { it.kind == kind }

    private fun detail(
        id: Long,
        name: String,
        difficulty: String = "Intermediate",
        target: String,
        equipment: String,
        mechanics: String = "Compound",
        primeMover: String?,
        movementPatterns: List<String>,
    ): ExerciseDetail {
        return ExerciseDetail(
            summary = summary(
                id = id,
                name = name,
                difficulty = difficulty,
                target = target,
                equipment = equipment,
                mechanics = mechanics,
            ),
            notes = null,
            primeMover = primeMover,
            secondaryMuscle = null,
            tertiaryMuscle = null,
            posture = "Supine",
            laterality = "Bilateral",
            classification = "Bodybuilding",
            movementPatterns = movementPatterns,
            planesOfMotion = listOf("Sagittal"),
            demoUrl = null,
            explanationUrl = null,
            canonicalDescription = null,
            generatedDescription = null,
            synonyms = emptyList(),
        )
    }

    private fun profile(
        id: Long,
        name: String,
        difficulty: String = "Intermediate",
        target: String,
        equipment: String,
        mechanics: String = "Compound",
        primeMover: String?,
        movementPatterns: List<String>,
        hidden: Boolean = false,
        banned: Boolean = false,
    ): ExerciseFamilyProfile {
        return ExerciseFamilyProfile(
            summary = summary(
                id = id,
                name = name,
                difficulty = difficulty,
                target = target,
                equipment = equipment,
                mechanics = mechanics,
                hidden = hidden,
                banned = banned,
            ),
            primeMover = primeMover,
            secondaryMuscle = null,
            tertiaryMuscle = null,
            posture = "Supine",
            laterality = "Bilateral",
            classification = "Bodybuilding",
            movementPatterns = movementPatterns,
            planesOfMotion = listOf("Sagittal"),
        )
    }

    private fun summary(
        id: Long,
        name: String,
        difficulty: String,
        target: String,
        equipment: String,
        mechanics: String,
        hidden: Boolean = false,
        banned: Boolean = false,
    ): ExerciseSummary {
        return ExerciseSummary(
            id = id,
            name = name,
            difficulty = difficulty,
            bodyRegion = if (target in setOf("Chest", "Back", "Shoulders", "Biceps")) "Upper Body" else "Lower Body",
            targetMuscleGroup = target,
            equipment = equipment,
            secondaryEquipment = null,
            mechanics = mechanics,
            favorite = false,
            hidden = hidden,
            banned = banned,
            preferenceScoreDelta = 0.0,
            recommendationBias = RecommendationBias.Neutral,
            loggedSessionCount = 0,
        )
    }
}
