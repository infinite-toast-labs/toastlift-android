#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import shutil
import sqlite3
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any


DEFAULT_APP_ID = "com.fitlib.app"
DEFAULT_REPO_DB = Path("functional_fitness_workout_generator.sqlite")
DEFAULT_DEVICE_DB_RELATIVE_PATH = "databases/fitlib.db"


@dataclass
class SyncResult:
    name: str
    slug: str
    device_exercise_id: int
    repo_exercise_id: int | None
    status: str
    detail: str


def run_command(args: list[str], capture_output: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        check=False,
        text=True,
        capture_output=capture_output,
    )


def build_adb_prefix(adb_bin: str, serial: str | None) -> list[str]:
    adb_cmd = [adb_bin]
    if serial:
        adb_cmd += ["-s", serial]
    return adb_cmd


def extract_device_file(
    adb_bin: str,
    serial: str | None,
    app_id: str,
    device_relative_path: str,
    output_path: Path,
    required: bool,
) -> None:
    adb_cmd = build_adb_prefix(adb_bin, serial) + [
        "exec-out",
        "run-as",
        app_id,
        "cat",
        device_relative_path,
    ]
    with output_path.open("wb") as handle:
        process = subprocess.run(adb_cmd, check=False, stdout=handle, stderr=subprocess.PIPE)
    if process.returncode == 0 and output_path.exists() and output_path.stat().st_size > 0:
        return

    if output_path.exists():
        output_path.unlink()
    stderr = process.stderr.decode("utf-8", errors="replace")
    if not required:
        return
    raise SystemExit(
        "Could not extract the app database from the device.\n"
        "This script supports debug builds only because it relies on `run-as`.\n"
        f"ADB command: {' '.join(adb_cmd)}\n"
        f"stderr:\n{stderr}"
    )


def extract_device_db(adb_bin: str, serial: str | None, app_id: str, device_db_relative_path: str) -> Path:
    temp_dir = Path(tempfile.mkdtemp(prefix="fitlib-custom-sync-"))
    output_path = temp_dir / "device-fitlib.db"
    extract_device_file(
        adb_bin=adb_bin,
        serial=serial,
        app_id=app_id,
        device_relative_path=device_db_relative_path,
        output_path=output_path,
        required=True,
    )
    extract_device_file(
        adb_bin=adb_bin,
        serial=serial,
        app_id=app_id,
        device_relative_path=f"{device_db_relative_path}-wal",
        output_path=temp_dir / "device-fitlib.db-wal",
        required=False,
    )
    extract_device_file(
        adb_bin=adb_bin,
        serial=serial,
        app_id=app_id,
        device_relative_path=f"{device_db_relative_path}-shm",
        output_path=temp_dir / "device-fitlib.db-shm",
        required=False,
    )
    return output_path


def validate_sqlite_header(db_path: Path) -> None:
    with db_path.open("rb") as handle:
        header = handle.read(16)
    if header.startswith(b"SQLite format 3"):
        return
    preview = header.decode("utf-8", errors="replace")
    raise SystemExit(
        "Extracted device database is not a readable SQLite file.\n"
        f"Path: {db_path}\n"
        f"Header preview: {preview!r}"
    )


def load_custom_exercises(db_path: Path) -> list[dict[str, Any]]:
    validate_sqlite_header(db_path)
    try:
        conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    except sqlite3.OperationalError as exc:
        raise SystemExit(f"Could not open extracted device database at {db_path}: {exc}") from exc
    conn.row_factory = sqlite3.Row
    try:
        rows = conn.execute(
            """
            SELECT
                exercise_id, source_row, slug, name, short_demo_label, short_demo_url, in_depth_label, in_depth_url,
                difficulty_level, target_muscle_group, prime_mover_muscle, secondary_muscle, tertiary_muscle,
                primary_equipment, primary_item_count, secondary_equipment, secondary_item_count, posture, arm_usage,
                arm_pattern, grip, load_position_ending, leg_pattern, foot_elevation, combination_type,
                movement_pattern_1, movement_pattern_2, movement_pattern_3,
                plane_of_motion_1, plane_of_motion_2, plane_of_motion_3,
                body_region, force_type, mechanics, laterality, primary_exercise_classification,
                equipment_slot_count, muscle_slot_count, has_short_demo, has_in_depth_explanation,
                is_post_install_llm_generated, created_at_utc, updated_at_utc, generation_model, generation_prompt_version
            FROM exercises
            WHERE COALESCE(is_post_install_llm_generated, 0) = 1
            ORDER BY created_at_utc, exercise_id
            """
        ).fetchall()
        results: list[dict[str, Any]] = []
        for row in rows:
            exercise_id = row["exercise_id"]
            results.append(
                {
                    "exercise": dict(row),
                    "muscles": [dict(item) for item in conn.execute(
                        "SELECT sequence_no, muscle_role, muscle_name FROM exercise_muscles WHERE exercise_id = ? ORDER BY sequence_no",
                        (exercise_id,),
                    ).fetchall()],
                    "equipment": [dict(item) for item in conn.execute(
                        "SELECT sequence_no, equipment_role, equipment_name, item_count FROM exercise_equipment WHERE exercise_id = ? ORDER BY sequence_no",
                        (exercise_id,),
                    ).fetchall()],
                    "movement_patterns": [dict(item) for item in conn.execute(
                        "SELECT sequence_no, movement_pattern FROM exercise_movement_patterns WHERE exercise_id = ? ORDER BY sequence_no",
                        (exercise_id,),
                    ).fetchall()],
                    "planes_of_motion": [dict(item) for item in conn.execute(
                        "SELECT sequence_no, plane_of_motion FROM exercise_planes_of_motion WHERE exercise_id = ? ORDER BY sequence_no",
                        (exercise_id,),
                    ).fetchall()],
                    "synonyms": [dict(item) for item in conn.execute(
                        """
                        SELECT synonym_name, synonym_name_normalized, synonym_type, source, confidence_score, created_at_utc
                        FROM exercise_synonyms
                        WHERE exercise_id = ?
                        ORDER BY synonym_name
                        """,
                        (exercise_id,),
                    ).fetchall()],
                }
            )
        return results
    finally:
        conn.close()


def ensure_repo_schema(conn: sqlite3.Connection) -> None:
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS exercise_synonyms (
            synonym_id INTEGER PRIMARY KEY AUTOINCREMENT,
            exercise_id INTEGER NOT NULL,
            synonym_name TEXT NOT NULL,
            synonym_name_normalized TEXT NOT NULL,
            synonym_type TEXT NOT NULL,
            source TEXT NOT NULL,
            confidence_score REAL,
            created_at_utc TEXT NOT NULL,
            UNIQUE (exercise_id, synonym_name_normalized),
            FOREIGN KEY (exercise_id) REFERENCES exercises (exercise_id) ON DELETE CASCADE
        )
        """
    )
    conn.execute(
        "CREATE INDEX IF NOT EXISTS idx_exercise_synonyms_norm ON exercise_synonyms (synonym_name_normalized)"
    )


def sync_into_repo(repo_db_path: Path, exercises: list[dict[str, Any]], dry_run: bool) -> list[SyncResult]:
    conn = sqlite3.connect(repo_db_path)
    conn.row_factory = sqlite3.Row
    results: list[SyncResult] = []
    try:
        if not dry_run:
            conn.execute("PRAGMA foreign_keys = ON")
            ensure_repo_schema(conn)
            conn.execute("BEGIN")
        else:
            ensure_repo_schema(conn)

        for payload in exercises:
            row = payload["exercise"]
            exercise_id = int(row["exercise_id"])
            slug = row["slug"]
            name = row["name"]

            existing_by_id = conn.execute(
                "SELECT exercise_id, name FROM exercises WHERE exercise_id = ?",
                (exercise_id,),
            ).fetchone()
            if existing_by_id is not None:
                results.append(
                    SyncResult(
                        name=name,
                        slug=slug,
                        device_exercise_id=exercise_id,
                        repo_exercise_id=int(existing_by_id["exercise_id"]),
                        status="already_present",
                        detail="Matching exercise_id already exists in the repo database.",
                    ),
                )
                continue

            existing_by_slug = conn.execute(
                "SELECT exercise_id, name FROM exercises WHERE slug = ?",
                (slug,),
            ).fetchone()
            if existing_by_slug is not None:
                results.append(
                    SyncResult(
                        name=name,
                        slug=slug,
                        device_exercise_id=exercise_id,
                        repo_exercise_id=int(existing_by_slug["exercise_id"]),
                        status="slug_conflict",
                        detail=f"Slug already exists in repo DB as {existing_by_slug['name']}.",
                    ),
                )
                continue

            if dry_run:
                results.append(
                    SyncResult(
                        name=name,
                        slug=slug,
                        device_exercise_id=exercise_id,
                        repo_exercise_id=exercise_id,
                        status="would_sync",
                        detail="Dry run only; exercise would be inserted.",
                    ),
                )
                continue

            insert_exercise(conn, payload)
            results.append(
                SyncResult(
                    name=name,
                    slug=slug,
                    device_exercise_id=exercise_id,
                    repo_exercise_id=exercise_id,
                    status="synced",
                    detail="Inserted exercise row plus child rows into repo DB.",
                ),
            )

        if dry_run:
            conn.rollback()
        else:
            conn.commit()
        return results
    finally:
        conn.close()


def insert_exercise(conn: sqlite3.Connection, payload: dict[str, Any]) -> None:
    row = payload["exercise"]
    conn.execute(
        """
        INSERT INTO exercises (
            exercise_id, source_row, slug, name, short_demo_label, short_demo_url, in_depth_label, in_depth_url,
            difficulty_level, target_muscle_group, prime_mover_muscle, secondary_muscle, tertiary_muscle,
            primary_equipment, primary_item_count, secondary_equipment, secondary_item_count, posture, arm_usage,
            arm_pattern, grip, load_position_ending, leg_pattern, foot_elevation, combination_type,
            movement_pattern_1, movement_pattern_2, movement_pattern_3,
            plane_of_motion_1, plane_of_motion_2, plane_of_motion_3,
            body_region, force_type, mechanics, laterality, primary_exercise_classification,
            equipment_slot_count, muscle_slot_count, has_short_demo, has_in_depth_explanation,
            is_post_install_llm_generated, created_at_utc, updated_at_utc, generation_model, generation_prompt_version
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            row["exercise_id"],
            row["source_row"],
            row["slug"],
            row["name"],
            row["short_demo_label"],
            row["short_demo_url"],
            row["in_depth_label"],
            row["in_depth_url"],
            row["difficulty_level"],
            row["target_muscle_group"],
            row["prime_mover_muscle"],
            row["secondary_muscle"],
            row["tertiary_muscle"],
            row["primary_equipment"],
            row["primary_item_count"],
            row["secondary_equipment"],
            row["secondary_item_count"],
            row["posture"],
            row["arm_usage"],
            row["arm_pattern"],
            row["grip"],
            row["load_position_ending"],
            row["leg_pattern"],
            row["foot_elevation"],
            row["combination_type"],
            row["movement_pattern_1"],
            row["movement_pattern_2"],
            row["movement_pattern_3"],
            row["plane_of_motion_1"],
            row["plane_of_motion_2"],
            row["plane_of_motion_3"],
            row["body_region"],
            row["force_type"],
            row["mechanics"],
            row["laterality"],
            row["primary_exercise_classification"],
            row["equipment_slot_count"],
            row["muscle_slot_count"],
            row["has_short_demo"],
            row["has_in_depth_explanation"],
            row["is_post_install_llm_generated"],
            row["created_at_utc"],
            row["updated_at_utc"],
            row["generation_model"],
            row["generation_prompt_version"],
        ),
    )

    for muscle in payload["muscles"]:
        conn.execute(
            "INSERT INTO exercise_muscles (exercise_id, sequence_no, muscle_role, muscle_name) VALUES (?, ?, ?, ?)",
            (row["exercise_id"], muscle["sequence_no"], muscle["muscle_role"], muscle["muscle_name"]),
        )
    for equipment in payload["equipment"]:
        conn.execute(
            """
            INSERT INTO exercise_equipment (exercise_id, sequence_no, equipment_role, equipment_name, item_count)
            VALUES (?, ?, ?, ?, ?)
            """,
            (
                row["exercise_id"],
                equipment["sequence_no"],
                equipment["equipment_role"],
                equipment["equipment_name"],
                equipment["item_count"],
            ),
        )
    for pattern in payload["movement_patterns"]:
        conn.execute(
            "INSERT INTO exercise_movement_patterns (exercise_id, sequence_no, movement_pattern) VALUES (?, ?, ?)",
            (row["exercise_id"], pattern["sequence_no"], pattern["movement_pattern"]),
        )
    for plane in payload["planes_of_motion"]:
        conn.execute(
            "INSERT INTO exercise_planes_of_motion (exercise_id, sequence_no, plane_of_motion) VALUES (?, ?, ?)",
            (row["exercise_id"], plane["sequence_no"], plane["plane_of_motion"]),
        )
    for synonym in payload["synonyms"]:
        conn.execute(
            """
            INSERT OR IGNORE INTO exercise_synonyms
                (exercise_id, synonym_name, synonym_name_normalized, synonym_type, source, confidence_score, created_at_utc)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (
                row["exercise_id"],
                synonym["synonym_name"],
                synonym["synonym_name_normalized"],
                synonym["synonym_type"],
                synonym["source"],
                synonym["confidence_score"],
                synonym["created_at_utc"],
            ),
        )


def print_report(results: list[SyncResult], dry_run: bool) -> None:
    synced_status = "would_sync" if dry_run else "synced"
    synced_count = sum(1 for item in results if item.status == synced_status)
    already_count = sum(1 for item in results if item.status == "already_present")
    conflict_count = sum(1 for item in results if item.status == "slug_conflict")

    print(f"Custom exercises found on device: {len(results)}")
    print(f"{'Would sync' if dry_run else 'Newly synced'}: {synced_count}")
    print(f"Already present: {already_count}")
    print(f"Conflicts/skips: {conflict_count}")
    print()

    if not results:
        print("No post-install AI-generated custom exercises were found on the device.")
        return

    print("Per-exercise details:")
    for item in results:
        print(
            json.dumps(
                {
                    "status": item.status,
                    "name": item.name,
                    "slug": item.slug,
                    "device_exercise_id": item.device_exercise_id,
                    "repo_exercise_id": item.repo_exercise_id,
                    "detail": item.detail,
                },
                ensure_ascii=True,
            )
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Sync post-install AI-generated custom exercises from a debug Android install into the repo SQLite DB.",
    )
    parser.add_argument("--adb-bin", default="android-adb", help="ADB binary to use. Defaults to android-adb.")
    parser.add_argument("--serial", default=None, help="Optional device serial.")
    parser.add_argument("--app-id", default=DEFAULT_APP_ID, help="Android application ID.")
    parser.add_argument(
        "--device-db-path",
        default=DEFAULT_DEVICE_DB_RELATIVE_PATH,
        help="Relative DB path inside the app sandbox. Defaults to databases/fitlib.db.",
    )
    parser.add_argument(
        "--repo-db-path",
        default=str(DEFAULT_REPO_DB),
        help="Path to the repo SQLite DB. Defaults to functional_fitness_workout_generator.sqlite.",
    )
    parser.add_argument("--dry-run", action="store_true", help="Report what would be synced without modifying the repo DB.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo_db_path = Path(args.repo_db_path).resolve()
    if not repo_db_path.exists():
        raise SystemExit(f"Repo DB not found: {repo_db_path}")

    device_db = extract_device_db(
        adb_bin=args.adb_bin,
        serial=args.serial,
        app_id=args.app_id,
        device_db_relative_path=args.device_db_path,
    )
    try:
        exercises = load_custom_exercises(device_db)
        results = sync_into_repo(repo_db_path, exercises, dry_run=args.dry_run)
        print_report(results, dry_run=args.dry_run)
        return 0
    finally:
        shutil.rmtree(device_db.parent, ignore_errors=True)


if __name__ == "__main__":
    raise SystemExit(main())
