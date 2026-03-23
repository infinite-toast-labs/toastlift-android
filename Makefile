SHELL := /bin/bash

APP_ID := com.fitlib.app
MAIN_ACTIVITY := $(APP_ID)/.MainActivity
DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk
RELEASE_APK := app/build/outputs/apk/release/app-release-unsigned.apk

ADB_HOST ?= host.docker.internal
ADB_PORT ?= 5037
ADB_SERIAL ?= emulator-5560
DEVICE_SERIAL ?=

export ANDROID_ADB_HOST := $(ADB_HOST)
export ANDROID_ADB_PORT := $(ADB_PORT)
export ANDROID_ADB_SERIAL := $(ADB_SERIAL)

GRADLE := ./gradlew --no-daemon --console=plain
ADB := android-adb
EMULATOR_ADB := android-emulator-adb

.PHONY: help clean test lint build-debug build-release assemble apk-paths devices \
	check-emulator check-device install-debug launch-debug install-device-debug \
	install-device-debug-no-build sync-device-custom-exercises

help:
	@echo "Targets:"
	@echo "  make test                         - Run unit tests"
	@echo "  make lint                         - Run Android lint for debug"
	@echo "  make build-debug                  - Build debug APK"
	@echo "  make build-release                - Build unsigned release APK"
	@echo "  make assemble                     - Build debug and release APKs"
	@echo "  make install-debug                - Build and install debug APK on the configured emulator"
	@echo "  make launch-debug                 - Launch the app on the configured emulator"
	@echo "  make install-device-debug         - Build and install debug APK on \$$DEVICE_SERIAL or the first physical adb device"
	@echo "  make install-device-debug-no-build - Install existing debug APK on \$$DEVICE_SERIAL or the first physical adb device"
	@echo "  make sync-device-custom-exercises - Sync post-install custom exercises from \$$DEVICE_SERIAL or the first physical adb device"
	@echo "  make devices                      - List devices through the configured ADB bridge"
	@echo "  make apk-paths                    - Print APK output paths"
	@echo "  make clean                        - Remove Gradle build outputs"

clean:
	$(GRADLE) clean

test:
	$(GRADLE) testDebugUnitTest

lint:
	$(GRADLE) lintDebug

build-debug:
	$(GRADLE) assembleDebug

build-release:
	$(GRADLE) assembleRelease

assemble: build-debug build-release

apk-paths:
	@echo "Debug APK:   $(DEBUG_APK)"
	@echo "Release APK: $(RELEASE_APK)"

devices:
	$(ADB) devices -l

check-emulator:
	@set -euo pipefail; \
	if ! $(EMULATOR_ADB) devices -l | grep -qE '^$(ADB_SERIAL)[[:space:]]+device\b'; then \
		echo "External emulator bridge unavailable: expected $(ADB_SERIAL) on $(ADB_HOST):$(ADB_PORT)" >&2; \
		echo "Run the host-side Android sandbox connection flow and retry." >&2; \
		exit 1; \
	fi

check-device:
	@set -euo pipefail; \
	serial="$(DEVICE_SERIAL)"; \
	if [[ -z "$$serial" ]]; then \
		serial="$$( $(ADB) devices | awk 'NR > 1 && $$2 == "device" && $$1 !~ /^emulator-/ { print $$1; exit }' )"; \
	fi; \
	if [[ -z "$$serial" ]]; then \
		echo "ADB device unavailable: no physical device found on $(ADB_HOST):$(ADB_PORT)" >&2; \
		echo "Set DEVICE_SERIAL=<serial> or connect a physical device through the host ADB bridge and retry." >&2; \
		exit 1; \
	fi; \
	if ! $(ADB) devices -l | grep -qE "^$$serial[[:space:]]+device\\b"; then \
		echo "ADB device unavailable: expected $$serial on $(ADB_HOST):$(ADB_PORT)" >&2; \
		echo "Connect the physical device through the host ADB bridge and retry." >&2; \
		exit 1; \
	fi

install-debug: check-emulator build-debug
	$(EMULATOR_ADB) install -r $(DEBUG_APK)

launch-debug: check-emulator
	$(EMULATOR_ADB) shell am start -W -n $(MAIN_ACTIVITY)

install-device-debug: check-device build-debug
	@set -euo pipefail; \
	serial="$(DEVICE_SERIAL)"; \
	if [[ -z "$$serial" ]]; then \
		serial="$$( $(ADB) devices | awk 'NR > 1 && $$2 == "device" && $$1 !~ /^emulator-/ { print $$1; exit }' )"; \
	fi; \
	$(ADB) -s "$$serial" install -r $(DEBUG_APK)

install-device-debug-no-build: check-device
	@set -euo pipefail; \
	if [[ ! -f "$(DEBUG_APK)" ]]; then \
		echo "Debug APK not found at $(DEBUG_APK)" >&2; \
		echo "Run 'make build-debug' first." >&2; \
		exit 1; \
	fi; \
	serial="$(DEVICE_SERIAL)"; \
	if [[ -z "$$serial" ]]; then \
		serial="$$( $(ADB) devices | awk 'NR > 1 && $$2 == "device" && $$1 !~ /^emulator-/ { print $$1; exit }' )"; \
	fi; \
	$(ADB) -s "$$serial" install -r $(DEBUG_APK)

sync-device-custom-exercises: check-device
	@set -euo pipefail; \
	serial="$(DEVICE_SERIAL)"; \
	if [[ -z "$$serial" ]]; then \
		serial="$$( $(ADB) devices | awk 'NR > 1 && $$2 == "device" && $$1 !~ /^emulator-/ { print $$1; exit }' )"; \
	fi; \
	python3 scripts/sync_custom_exercises_from_device.py --serial "$$serial"
