#!/usr/bin/env python3

from __future__ import annotations

import json
import re
import sqlite3
import sys
import unicodedata
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.request import urlopen


SOURCE_URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json"
SOURCE_REPO_URL = "https://github.com/yuhonas/free-exercise-db"
RAW_IMAGE_ROOT = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises"
REPO_EXERCISE_ROOT = "https://github.com/yuhonas/free-exercise-db/tree/main/exercises"


TARGET_GROUP_MAP = {
    "abdominals": "Abdominals",
    "abductors": "Abductors",
    "adductors": "Adductors",
    "biceps": "Biceps",
    "calves": "Calves",
    "chest": "Chest",
    "forearms": "Forearms",
    "glutes": "Glutes",
    "hamstrings": "Hamstrings",
    "lats": "Back",
    "lower back": "Back",
    "middle back": "Back",
    "quadriceps": "Quadriceps",
    "shoulders": "Shoulders",
    "traps": "Trapezius",
    "triceps": "Triceps",
}

MUSCLE_MAP = {
    "abdominals": "Rectus Abdominis",
    "abductors": "Gluteus Medius",
    "adductors": "Adductor Magnus",
    "biceps": "Biceps Brachii",
    "calves": "Gastrocnemius",
    "chest": "Pectoralis Major",
    "forearms": "Brachioradialis",
    "glutes": "Gluteus Maximus",
    "hamstrings": "Biceps Femoris",
    "lats": "Latissimus Dorsi",
    "lower back": "Erector Spinae",
    "middle back": "Rhomboids",
    "quadriceps": "Quadriceps Femoris",
    "shoulders": "Anterior Deltoids",
    "traps": "Trapezius",
    "triceps": "Triceps Brachii",
}

BODY_REGION_GROUPS = {
    "Core": {"Abdominals"},
    "Lower Body": {"Abductors", "Adductors", "Calves", "Glutes", "Hamstrings", "Quadriceps"},
    "Upper Body": {"Back", "Biceps", "Chest", "Forearms", "Shoulders", "Trapezius", "Triceps"},
}


@dataclass
class PreparedExercise:
    source_id: str
    name: str
    slug: str
    short_demo_label: str | None
    short_demo_url: str | None
    in_depth_label: str | None
    in_depth_url: str | None
    difficulty_level: str
    target_muscle_group: str
    prime_mover_muscle: str | None
    secondary_muscle: str | None
    tertiary_muscle: str | None
    primary_equipment: str
    primary_item_count: int
    secondary_equipment: str | None
    secondary_item_count: int | None
    posture: str
    arm_usage: str
    arm_pattern: str
    grip: str
    load_position_ending: str
    leg_pattern: str
    foot_elevation: str
    combination_type: str
    movement_patterns: list[str]
    planes_of_motion: list[str]
    body_region: str
    force_type: str
    mechanics: str | None
    laterality: str
    primary_exercise_classification: str
    muscles: list[tuple[str, str]]
    source_payload: dict[str, Any]


def title_case_or_none(value: str | None) -> str | None:
    if not value:
        return None
    return " ".join(part.capitalize() for part in value.split())


def slugify(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode("ascii")
    normalized = normalized.lower()
    normalized = re.sub(r"[^a-z0-9]+", "-", normalized)
    return normalized.strip("-")


def load_source() -> list[dict[str, Any]]:
    with urlopen(SOURCE_URL, timeout=30) as response:
        return json.load(response)


def raw_image_url(images: list[str]) -> str | None:
    if not images:
        return None
    return f"{RAW_IMAGE_ROOT}/{images[0]}"


def repo_exercise_url(source_id: str) -> str:
    return f"{REPO_EXERCISE_ROOT}/{source_id}"


def infer_target_group(primary_muscles: list[str], name: str, category: str | None) -> str | None:
    for muscle in primary_muscles:
        mapped = TARGET_GROUP_MAP.get(muscle)
        if mapped:
            return mapped

    lowered = name.lower()
    if any(token in lowered for token in ("crunch", "ab ", "ab-", "abdom")):
        return "Abdominals"
    if any(token in lowered for token in ("squat", "leg press", "leg extension", "leg curl", "calf", "bike", "treadmill", "stair", "step mill", "lunge")):
        return "Quadriceps"
    if any(token in lowered for token in ("row", "shrug", "deadlift")):
        return "Back"
    if any(token in lowered for token in ("press", "fly")):
        return "Chest"
    if category == "cardio":
        return "Quadriceps"
    return None


def infer_body_region(target_group: str, name: str, category: str | None) -> str:
    for region, groups in BODY_REGION_GROUPS.items():
        if target_group in groups:
            return region
    lowered = name.lower()
    if category == "cardio" and any(token in lowered for token in ("rowing", "elliptical")):
        return "Full Body"
    return "Full Body"


def map_muscles(primary_muscles: list[str], secondary_muscles: list[str]) -> list[tuple[str, str]]:
    ordered: list[tuple[str, str]] = []
    for role, muscles in (
        ("prime_mover", primary_muscles[:1]),
        ("secondary", secondary_muscles[:1]),
        ("tertiary", secondary_muscles[1:2]),
    ):
        for muscle in muscles:
            mapped = MUSCLE_MAP.get(muscle)
            if mapped and mapped not in {existing for _, existing in ordered}:
                ordered.append((role, mapped))
    return ordered


def infer_posture(name: str, instructions: list[str]) -> str:
    lowered = name.lower()
    text = " ".join(instructions).lower()
    if "walking" in lowered:
        return "Walking"
    if any(token in lowered for token in ("jogging", "running")):
        return "Running"
    if any(token in lowered for token in ("seated", "bike", "bicycling", "rowing", "ab crunch machine")):
        return "Seated"
    if any(token in lowered for token in ("elliptical", "stairmaster", "step mill", "chair squat", "leg press", "hack squat", "squat", "calf raise", "deadlift", "upright row", "press")):
        return "Standing"
    if any(token in lowered for token in ("lying", "reverse hyperextension", "glute ham raise")):
        return "Prone"
    if "hip raise" in lowered:
        return "Supine"
    if "sit on the machine" in text or "seat yourself" in text or "sit down" in text:
        return "Seated"
    return "Other"


def infer_arm_usage(name: str, target_group: str, category: str | None) -> str:
    lowered = name.lower()
    if "one-arm" in lowered or "single-arm" in lowered:
        return "Single Arm"
    if category == "cardio" and any(token in lowered for token in ("bike", "treadmill", "stairmaster", "step mill", "walking", "running", "jogging")):
        return "No Arms"
    if target_group in {"Quadriceps", "Hamstrings", "Calves", "Abductors", "Adductors", "Glutes"} and "rowing" not in lowered and "elliptical" not in lowered:
        return "Double Arm"
    return "Double Arm"


def infer_arm_pattern(name: str) -> str:
    lowered = name.lower()
    if any(token in lowered for token in ("walking", "running", "jogging", "lunge sprint")):
        return "Alternating"
    return "Continuous"


def infer_grip(name: str, target_group: str, category: str | None) -> str:
    lowered = name.lower()
    if "reverse grip" in lowered:
        return "Supinated"
    if "behind the back" in lowered:
        return "Pronated"
    if category == "cardio" and any(token in lowered for token in ("bike", "treadmill", "stairmaster", "step mill")):
        return "No Grip"
    if target_group in {"Quadriceps", "Hamstrings", "Calves", "Abductors", "Adductors", "Glutes"}:
        return "Other"
    return "Neutral"


def infer_load_position(name: str, category: str | None) -> str:
    lowered = name.lower()
    if category == "cardio":
        return "No Load"
    if "overhead" in lowered or "shoulder press" in lowered or "military press" in lowered:
        return "Overhead"
    if any(token in lowered for token in ("bench press", "chest press", "decline press", "incline chest press", "dip machine", "butterfly", "fly")):
        return "Above Chest"
    return "Other"


def infer_leg_pattern(name: str) -> str:
    lowered = name.lower()
    if any(token in lowered for token in ("walking", "running", "jogging", "split squat", "single-leg", "lunge sprint", "pistol squat")):
        return "Alternating"
    return "Continuous"


def infer_force_type(force: str | None, name: str, category: str | None) -> str:
    if force == "push":
        return "Push"
    if force == "pull":
        return "Pull"
    lowered = name.lower()
    if category == "cardio" and any(token in lowered for token in ("rowing", "elliptical")):
        return "Push & Pull"
    if category == "cardio":
        return "Other"
    return "Other"


def infer_mechanics(mechanic: str | None) -> str | None:
    if mechanic == "compound":
        return "Compound"
    if mechanic == "isolation":
        return "Isolation"
    return None


def infer_laterality(name: str) -> str:
    lowered = name.lower()
    if any(token in lowered for token in ("one-arm", "single-arm", "single-leg", "split squat", "pistol squat")):
        return "Unilateral"
    return "Bilateral"


def infer_classification(category: str | None) -> str:
    if category == "powerlifting":
        return "Powerlifting"
    if category == "strength":
        return "Bodybuilding"
    return "Unsorted*"


def infer_movement_patterns(name: str, target_group: str, category: str | None) -> list[str]:
    lowered = name.lower()
    if "crunch" in lowered:
        return ["Spinal Flexion"]
    if "rowing" in lowered:
        return ["Horizontal Pull"]
    if "row" in lowered or "high row" in lowered or "t-bar row" in lowered:
        return ["Horizontal Pull"]
    if "upright row" in lowered:
        return ["Vertical Pull"]
    if "shoulder press" in lowered or "military press" in lowered or "overhead" in lowered:
        return ["Vertical Push"]
    if any(token in lowered for token in ("bench press", "chest press", "decline press", "incline chest press", "dip machine")):
        return ["Horizontal Push"]
    if "butterfly" in lowered:
        return ["Horizontal Adduction"]
    if "reverse machine flyes" in lowered:
        return ["Horizontal Pull"]
    if "bicep curl" in lowered or "preacher curl" in lowered:
        return ["Elbow Flexion"]
    if "triceps extension" in lowered:
        return ["Elbow Extension"]
    if "shrug" in lowered:
        return ["Scapular Elevation"]
    if "thigh abductor" in lowered:
        return ["Hip Abduction"]
    if "thigh adductor" in lowered:
        return ["Hip Adduction"]
    if any(token in lowered for token in ("deadlift", "reverse hyperextension", "hip raise", "glute ham raise", "stiff-legged deadlift")):
        return ["Hip Hinge"]
    if any(token in lowered for token in ("calf press", "calf raise", "standing calf raises", "seated calf raise")):
        if "reverse calf" in lowered:
            return ["Ankle Dorsiflexion"]
        return ["Ankle Plantar Flexion"]
    if any(token in lowered for token in ("squat", "leg press", "leg extension", "leg curl", "bike", "bicycling", "treadmill", "walking", "jogging", "running", "stairmaster", "step mill", "chair squat", "lunge sprint")):
        return ["Knee Dominant"]
    if category == "cardio":
        return ["Locomotion"]
    if target_group == "Back":
        return ["Horizontal Pull"]
    if target_group == "Chest":
        return ["Horizontal Push"]
    return ["Other"]


def infer_plane_of_motion(name: str, movement_patterns: list[str]) -> list[str]:
    lowered = name.lower()
    if any(token in lowered for token in ("thigh abductor", "thigh adductor")):
        return ["Frontal Plane"]
    if any(token in lowered for token in ("butterfly", "flyes")):
        return ["Transverse Plane"]
    if movement_patterns and movement_patterns[0] in {"Rotational", "Spinal Rotational"}:
        return ["Transverse Plane"]
    return ["Sagittal Plane"]


def prepare_exercise(source_exercise: dict[str, Any]) -> PreparedExercise | tuple[str, dict[str, Any]]:
    name = (source_exercise.get("name") or "").strip()
    if not name:
        return "missing_name", source_exercise

    primary_muscles = [muscle.strip().lower() for muscle in source_exercise.get("primaryMuscles") or [] if muscle]
    secondary_muscles = [muscle.strip().lower() for muscle in source_exercise.get("secondaryMuscles") or [] if muscle]
    category = source_exercise.get("category")

    target_group = infer_target_group(primary_muscles, name, category)
    if not target_group:
        return "unmapped_target_group", source_exercise

    muscles = map_muscles(primary_muscles, secondary_muscles)
    movement_patterns = infer_movement_patterns(name, target_group, category)
    planes = infer_plane_of_motion(name, movement_patterns)

    short_demo = raw_image_url(source_exercise.get("images") or [])
    source_id = source_exercise.get("id") or slugify(name)
    difficulty = title_case_or_none(source_exercise.get("level")) or "Beginner"
    if difficulty not in {"Beginner", "Intermediate", "Advanced", "Expert", "Master", "Grand Master", "Legendary", "Novice"}:
        difficulty = "Beginner"

    return PreparedExercise(
        source_id=source_id,
        name=name,
        slug=slugify(name),
        short_demo_label="Demo Image" if short_demo else None,
        short_demo_url=short_demo,
        in_depth_label="Source Folder",
        in_depth_url=repo_exercise_url(source_id),
        difficulty_level=difficulty,
        target_muscle_group=target_group,
        prime_mover_muscle=muscles[0][1] if len(muscles) > 0 else None,
        secondary_muscle=muscles[1][1] if len(muscles) > 1 else None,
        tertiary_muscle=muscles[2][1] if len(muscles) > 2 else None,
        primary_equipment="Machine",
        primary_item_count=1,
        secondary_equipment=None,
        secondary_item_count=None,
        posture=infer_posture(name, source_exercise.get("instructions") or []),
        arm_usage=infer_arm_usage(name, target_group, category),
        arm_pattern=infer_arm_pattern(name),
        grip=infer_grip(name, target_group, category),
        load_position_ending=infer_load_position(name, category),
        leg_pattern=infer_leg_pattern(name),
        foot_elevation="No Elevation",
        combination_type="Single Exercise",
        movement_patterns=movement_patterns[:3],
        planes_of_motion=planes[:3],
        body_region=infer_body_region(target_group, name, category),
        force_type=infer_force_type(source_exercise.get("force"), name, category),
        mechanics=infer_mechanics(source_exercise.get("mechanic")),
        laterality=infer_laterality(name),
        primary_exercise_classification=infer_classification(category),
        muscles=muscles[:3],
        source_payload=source_exercise,
    )


def write_conflicts(path: Path, conflicts: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(conflicts, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def import_machine_exercises(db_path: Path, conflict_path: Path) -> dict[str, int]:
    source_data = load_source()
    machine_exercises = [exercise for exercise in source_data if (exercise.get("equipment") or "").strip().lower() == "machine"]

    conflicts: list[dict[str, Any]] = []
    imported = 0

    connection = sqlite3.connect(db_path)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")

    try:
        existing_names = {row[0].lower() for row in connection.execute("SELECT name FROM exercises")}
        existing_slugs = {row[0] for row in connection.execute("SELECT slug FROM exercises")}
        next_exercise_id = connection.execute("SELECT COALESCE(MAX(exercise_id), 0) + 1 FROM exercises").fetchone()[0]
        next_source_row = connection.execute("SELECT COALESCE(MAX(source_row), 0) + 1 FROM exercises").fetchone()[0]

        connection.execute("BEGIN")
        for raw_exercise in machine_exercises:
            prepared = prepare_exercise(raw_exercise)
            if isinstance(prepared, tuple):
                reason, payload = prepared
                conflicts.append(
                    {
                        "reason": reason,
                        "name": payload.get("name"),
                        "slug": slugify(payload.get("name") or ""),
                        "source_id": payload.get("id"),
                        "source": "free-exercise-db",
                        "payload": payload,
                    }
                )
                continue

            if prepared.name.lower() in existing_names:
                conflicts.append(
                    {
                        "reason": "name_conflict",
                        "name": prepared.name,
                        "slug": prepared.slug,
                        "source_id": prepared.source_id,
                        "source": "free-exercise-db",
                        "payload": prepared.source_payload,
                    }
                )
                continue

            if prepared.slug in existing_slugs:
                conflicts.append(
                    {
                        "reason": "slug_conflict",
                        "name": prepared.name,
                        "slug": prepared.slug,
                        "source_id": prepared.source_id,
                        "source": "free-exercise-db",
                        "payload": prepared.source_payload,
                    }
                )
                continue

            exercise_id = next_exercise_id
            next_exercise_id += 1
            source_row = next_source_row
            next_source_row += 1

            connection.execute(
                """
                INSERT INTO exercises (
                    exercise_id,
                    source_row,
                    slug,
                    name,
                    short_demo_label,
                    short_demo_url,
                    in_depth_label,
                    in_depth_url,
                    difficulty_level,
                    target_muscle_group,
                    prime_mover_muscle,
                    secondary_muscle,
                    tertiary_muscle,
                    primary_equipment,
                    primary_item_count,
                    secondary_equipment,
                    secondary_item_count,
                    posture,
                    arm_usage,
                    arm_pattern,
                    grip,
                    load_position_ending,
                    leg_pattern,
                    foot_elevation,
                    combination_type,
                    movement_pattern_1,
                    movement_pattern_2,
                    movement_pattern_3,
                    plane_of_motion_1,
                    plane_of_motion_2,
                    plane_of_motion_3,
                    body_region,
                    force_type,
                    mechanics,
                    laterality,
                    primary_exercise_classification,
                    equipment_slot_count,
                    muscle_slot_count,
                    has_short_demo,
                    has_in_depth_explanation
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    exercise_id,
                    source_row,
                    prepared.slug,
                    prepared.name,
                    prepared.short_demo_label,
                    prepared.short_demo_url,
                    prepared.in_depth_label,
                    prepared.in_depth_url,
                    prepared.difficulty_level,
                    prepared.target_muscle_group,
                    prepared.prime_mover_muscle,
                    prepared.secondary_muscle,
                    prepared.tertiary_muscle,
                    prepared.primary_equipment,
                    prepared.primary_item_count,
                    prepared.secondary_equipment,
                    prepared.secondary_item_count,
                    prepared.posture,
                    prepared.arm_usage,
                    prepared.arm_pattern,
                    prepared.grip,
                    prepared.load_position_ending,
                    prepared.leg_pattern,
                    prepared.foot_elevation,
                    prepared.combination_type,
                    prepared.movement_patterns[0] if len(prepared.movement_patterns) > 0 else None,
                    prepared.movement_patterns[1] if len(prepared.movement_patterns) > 1 else None,
                    prepared.movement_patterns[2] if len(prepared.movement_patterns) > 2 else None,
                    prepared.planes_of_motion[0] if len(prepared.planes_of_motion) > 0 else None,
                    prepared.planes_of_motion[1] if len(prepared.planes_of_motion) > 1 else None,
                    prepared.planes_of_motion[2] if len(prepared.planes_of_motion) > 2 else None,
                    prepared.body_region,
                    prepared.force_type,
                    prepared.mechanics,
                    prepared.laterality,
                    prepared.primary_exercise_classification,
                    1,
                    len(prepared.muscles),
                    1 if prepared.short_demo_url else 0,
                    1 if prepared.in_depth_url else 0,
                ),
            )

            connection.execute(
                """
                INSERT INTO exercise_equipment (
                    exercise_id,
                    sequence_no,
                    equipment_role,
                    equipment_name,
                    item_count
                ) VALUES (?, 1, 'primary', ?, ?)
                """,
                (exercise_id, prepared.primary_equipment, prepared.primary_item_count),
            )

            for sequence_no, (role, muscle_name) in enumerate(prepared.muscles, start=1):
                connection.execute(
                    """
                    INSERT INTO exercise_muscles (
                        exercise_id,
                        sequence_no,
                        muscle_role,
                        muscle_name
                    ) VALUES (?, ?, ?, ?)
                    """,
                    (exercise_id, sequence_no, role, muscle_name),
                )

            for sequence_no, pattern in enumerate(prepared.movement_patterns, start=1):
                connection.execute(
                    """
                    INSERT INTO exercise_movement_patterns (
                        exercise_id,
                        sequence_no,
                        movement_pattern
                    ) VALUES (?, ?, ?)
                    """,
                    (exercise_id, sequence_no, pattern),
                )

            for sequence_no, plane in enumerate(prepared.planes_of_motion, start=1):
                connection.execute(
                    """
                    INSERT INTO exercise_planes_of_motion (
                        exercise_id,
                        sequence_no,
                        plane_of_motion
                    ) VALUES (?, ?, ?)
                    """,
                    (exercise_id, sequence_no, plane),
                )

            existing_names.add(prepared.name.lower())
            existing_slugs.add(prepared.slug)
            imported += 1

        timestamp = datetime.now(timezone.utc).isoformat()
        metadata = {
            "merge_free_exercise_db_machine_source_url": SOURCE_URL,
            "merge_free_exercise_db_machine_source_repo": SOURCE_REPO_URL,
            "merge_free_exercise_db_machine_imported_at_utc": timestamp,
            "merge_free_exercise_db_machine_imported_count": str(imported),
            "merge_free_exercise_db_machine_conflict_count": str(len(conflicts)),
        }
        for key, value in metadata.items():
            connection.execute(
                "INSERT OR REPLACE INTO import_metadata (metadata_key, metadata_value) VALUES (?, ?)",
                (key, value),
            )
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()

    write_conflicts(conflict_path, conflicts)
    return {
        "source_machine_count": len(machine_exercises),
        "imported_count": imported,
        "conflict_count": len(conflicts),
    }


def main(argv: list[str]) -> int:
    db_path = Path(argv[1]) if len(argv) > 1 else Path("functional_fitness_workout_generator.sqlite")
    conflict_path = Path(argv[2]) if len(argv) > 2 else Path("exercise-conflict/merge-conflict.json")

    if not db_path.exists():
        print(f"Database not found: {db_path}", file=sys.stderr)
        return 1

    result = import_machine_exercises(db_path, conflict_path)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
