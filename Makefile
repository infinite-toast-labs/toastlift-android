SHELL := /bin/bash

APP_ID := dev.toastlabs.toastlift.debug
MAIN_ACTIVITY := $(APP_ID)/dev.toastlabs.toastlift.MainActivity
STAGE_APP_ID := dev.toastlabs.toastlift.staging
STAGE_MAIN_ACTIVITY := $(STAGE_APP_ID)/dev.toastlabs.toastlift.MainActivity
DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk
STAGE_APK := app/build/outputs/apk/staging/app-staging.apk
RELEASE_APK := app/build/outputs/apk/release/app-release-unsigned.apk
RELEASE_AAB := app/build/outputs/bundle/release/app-release.aab
RELEASE_MERGED_MANIFEST := app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml
LIVE_AI_SMOKE_TEST_REPORT := app/build/test-results/testDebugUnitTest/TEST-dev.toastlabs.toastlift.data.ExerciseMetadataGeneratorTest.xml
LIVE_AI_SEARCH_SMOKE_TEST_REPORT := app/build/test-results/testDebugUnitTest/TEST-dev.toastlabs.toastlift.data.ExerciseAiSearchServiceTest.xml

ADB_HOST ?= host.docker.internal
ADB_PORT ?= 5037
ADB_SERIAL ?= emulator-5560
DEVICE_SERIAL ?=
SCREEN_KEY ?=
MCP_PHONE_STARTUP_WAIT ?= 10
PLAYSTORE_ANDROID_SDK_ROOT ?= $(if $(ANDROID_SDK_ROOT),$(ANDROID_SDK_ROOT),$(if $(ANDROID_HOME),$(ANDROID_HOME),$(HOME)/Library/Android/sdk))
PLAYSTORE_JAVA_HOME ?= /Applications/Android Studio.app/Contents/jbr/Contents/Home
PLAYSTORE_OUTPUT_ROOT ?= play-store/listing/en-US/screenshots
PLAYSTORE_WORK_ROOT ?= artifacts/playstore
PLAYSTORE_KEEP_EMULATOR ?= 0
export ANDROID_ADB_HOST := $(ADB_HOST)
export ANDROID_ADB_PORT := $(ADB_PORT)
export ANDROID_ADB_SERIAL := $(ADB_SERIAL)

GRADLE := ./gradlew --no-daemon --console=plain
ADB := android-adb
EMULATOR_ADB := android-emulator-adb

.PHONY: help clean test lint check-version prepare-appreveal test-play-bundle-verifier build-debug build-stage build-release build-prod bundle-prod verify-release-no-internet verify-play-release assemble apk-paths devices \
	check-emulator check-device install-debug install-debug-appreveal launch-debug launch-stage install-device-debug \
	install-device-debug-no-build install-device-stage install-device-stage-no-build sync-device-custom-exercises live-ai-smoke-test \
	install-stage-appreveal mcp-screens-regular mcp-screens-sheets mcp-screens-all mcp-stage-screens-all mcp-phone-screens-all \
	mcp-full-scroll-regular mcp-full-scroll-sheets mcp-full-scroll-all mcp-full-scroll-screen \
	mcp-phone-full-scroll-all mcp-phone-full-scroll-screen playstore-build-stage \
	playstore-screenshots-phone playstore-screenshots-tablet-7 playstore-screenshots-tablet-10 \
	playstore-screenshots-tablets playstore-screenshots-all

help:
	@echo "Targets:"
	@echo "  make check-version                - Validate version.txt and its Android versionCode mapping"
	@echo "  make prepare-appreveal            - Clone/update the pinned local AppReveal composite build"
	@echo "  make test-play-bundle-verifier    - Test signed/unsigned Play bundle verification"
	@echo "  make test                         - Run unit tests"
	@echo "  make live-ai-smoke-test           - Run live Gemini/OpenCode custom exercise AI smoke tests and print outputs"
	@echo "  make lint                         - Run Android lint for debug"
	@echo "  make build-debug                  - Build debug APK"
	@echo "  make build-stage                  - Build production-configured, debug-signed staging APK"
	@echo "  make build-release                - Build unsigned release APK"
	@echo "  make build-prod                   - Build release APK; signs it when TOASTLIFT_PLAY_UPLOAD_* values are configured"
	@echo "  make bundle-prod                  - Build Play Store Android App Bundle"
	@echo "  make verify-release-no-internet   - Fail if the Play release manifest declares INTERNET"
	@echo "  make verify-play-release          - Build and verify a signed Play Store bundle"
	@echo "  make assemble                     - Build debug and release APKs"
	@echo "  make install-debug                - Build and install debug APK on the configured emulator"
	@echo "  make install-debug-appreveal      - Build and install debug APK on the configured emulator for AppReveal capture"
	@echo "  make install-stage-appreveal      - Build and install staging APK on the configured emulator for AppReveal capture"
	@echo "  make launch-debug                 - Launch the app on the configured emulator"
	@echo "  make launch-stage                 - Launch the production-configured staging app on the configured emulator"
	@echo "  make mcp-screens-regular         - Single-shot capture all non-bottom-sheet AppReveal screens from the configured emulator"
	@echo "  make mcp-screens-sheets          - Single-shot capture all AppReveal bottom sheets from the configured emulator"
	@echo "  make mcp-screens-all             - Single-shot capture regular screens and bottom sheets from the configured emulator"
	@echo "  make mcp-stage-screens-all       - Single-shot capture staging screens and sheets from the configured emulator"
	@echo "  make mcp-full-scroll-regular     - Full-scroll capture all non-bottom-sheet AppReveal screens from the configured emulator"
	@echo "  make mcp-full-scroll-sheets      - Full-scroll capture all AppReveal bottom sheets from the configured emulator"
	@echo "  make mcp-full-scroll-all         - Full-scroll capture regular screens and bottom sheets from the configured emulator"
	@echo "  make mcp-full-scroll-screen SCREEN_KEY=<key> - Full-scroll capture one AppReveal screen or bottom sheet from the configured emulator"
	@echo "  make mcp-phone-screens-all       - Capture regular screens and bottom sheets from \$$DEVICE_SERIAL or the first physical adb device"
	@echo "  make mcp-phone-full-scroll-all   - Full-scroll capture regular screens and bottom sheets from \$$DEVICE_SERIAL or the first physical adb device"
	@echo "  make mcp-phone-full-scroll-screen SCREEN_KEY=<key> - Full-scroll capture one AppReveal screen or bottom sheet from \$$DEVICE_SERIAL or the first physical adb device"
	@echo "  make playstore-screenshots-phone  - Capture exactly two Play phone screenshots on a dedicated local Mac AVD"
	@echo "  make playstore-screenshots-tablets - Capture exactly two screenshots for each Play tablet class on local Mac AVDs"
	@echo "    PLAYSTORE_KEEP_EMULATOR=1       - Keep dedicated Play AVDs running while iterating"
	@echo "  make install-device-debug         - Build and install debug APK on \$$DEVICE_SERIAL or the first physical adb device"
	@echo "  make install-device-debug-no-build - Install existing debug APK on \$$DEVICE_SERIAL or the first physical adb device"
	@echo "  make install-device-stage         - Build and install production-configured staging APK on a physical device"
	@echo "  make install-device-stage-no-build - Install existing staging APK on a physical device"
	@echo "  make sync-device-custom-exercises - Sync post-install custom exercises from \$$DEVICE_SERIAL or the first physical adb device"
	@echo "  make devices                      - List devices through the configured ADB bridge"
	@echo "  make apk-paths                    - Print APK output paths"
	@echo "  make clean                        - Remove Gradle build outputs"

clean:
	$(GRADLE) clean

check-version:
	scripts/check_version.sh

prepare-appreveal:
	scripts/prepare_appreveal_checkout.sh

test:
	$(GRADLE) testDebugUnitTest

live-ai-smoke-test:
	@set -euo pipefail; \
	rm -f "$(LIVE_AI_SMOKE_TEST_REPORT)" "$(LIVE_AI_SEARCH_SMOKE_TEST_REPORT)"; \
	status=0; \
	RUN_LIVE_AI_SMOKE_TESTS=true $(GRADLE) testDebugUnitTest \
		--tests dev.toastlabs.toastlift.data.ExerciseMetadataGeneratorTest.liveSmokeGeminiExerciseMetadataGenerator_returnsRealMetadata \
		--tests dev.toastlabs.toastlift.data.ExerciseMetadataGeneratorTest.liveSmokeOpenCodeDeepSeekV4FlashExerciseMetadataGenerator_returnsRealMetadata \
		--tests dev.toastlabs.toastlift.data.ExerciseMetadataGeneratorTest.liveSmokeOpenCodeGlm52ExerciseMetadataGenerator_returnsRealMetadata \
		--tests dev.toastlabs.toastlift.data.ExerciseMetadataGeneratorTest.liveSmokeOpenRouterGlm52ExerciseMetadataGenerator_returnsRealMetadata \
		--tests dev.toastlabs.toastlift.data.ExerciseAiSearchServiceTest.liveSmokeGeminiExerciseAiSearch_findsReversePecDeckByAlias \
		--tests dev.toastlabs.toastlift.data.ExerciseAiSearchServiceTest.liveSmokeGeminiExerciseAiSearch_returnsEmptyForMissingExercise || status=$$?; \
	found=0; \
	for report in "$(LIVE_AI_SMOKE_TEST_REPORT)" "$(LIVE_AI_SEARCH_SMOKE_TEST_REPORT)"; do \
		if [[ -f "$$report" ]]; then \
			found=1; \
			echo; \
			echo "Live AI smoke test outputs ($$report):"; \
			perl -0ne 'print $$1 if /<system-out><!\[CDATA\[(.*?)\]\]><\/system-out>/s' "$$report"; \
		fi; \
	done; \
	if [[ $$found -eq 0 ]]; then \
		echo "No live AI smoke test reports were created." >&2; \
	fi; \
	exit $$status

lint:
	$(GRADLE) lintDebug

build-debug:
	$(GRADLE) assembleDebug || { \
		echo "assembleDebug failed; retrying once after clean to recover stale Gradle/Kotlin build caches." >&2; \
		$(GRADLE) clean assembleDebug; \
	}

build-stage:
	$(GRADLE) assembleStaging

build-release:
	$(GRADLE) assembleRelease

build-prod: build-release

bundle-prod:
	$(GRADLE) bundleRelease

verify-release-no-internet: bundle-prod
	@set -euo pipefail; \
	if [[ ! -f "$(RELEASE_MERGED_MANIFEST)" ]]; then \
		echo "Merged release manifest not found at $(RELEASE_MERGED_MANIFEST)" >&2; \
		exit 1; \
	fi; \
	if grep -qE 'android\.permission\.INTERNET' "$(RELEASE_MERGED_MANIFEST)"; then \
		echo "Release manifest unexpectedly declares android.permission.INTERNET." >&2; \
		exit 1; \
	fi; \
	echo "Release manifest verified: android.permission.INTERNET is absent."

verify-play-release: verify-release-no-internet
	scripts/verify_play_bundle_signature.sh "$(RELEASE_AAB)"

test-play-bundle-verifier:
	scripts/test_verify_play_bundle_signature.sh

assemble: build-debug build-release

apk-paths:
	@echo "Debug APK:   $(DEBUG_APK)"
	@echo "Stage APK:   $(STAGE_APK)"
	@echo "Release APK: $(RELEASE_APK)"
	@echo "Release AAB: $(RELEASE_AAB)"

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

install-debug-appreveal: check-emulator build-debug
	@set -euo pipefail; \
	if ! output="$$( $(EMULATOR_ADB) install -r -d -g --no-incremental $(DEBUG_APK) 2>&1 )"; then \
		echo "$$output" >&2; \
		echo "Retrying after trimming emulator package caches." >&2; \
		$(EMULATOR_ADB) shell pm trim-caches 999G >/dev/null || true; \
		$(EMULATOR_ADB) install -r -d -g --no-incremental $(DEBUG_APK); \
	else \
		echo "$$output"; \
	fi

install-stage-appreveal: check-emulator build-stage
	@set -euo pipefail; \
	if ! output="$$( $(EMULATOR_ADB) install -r -d -g --no-incremental $(STAGE_APK) 2>&1 )"; then \
		echo "$$output" >&2; \
		echo "Retrying after trimming emulator package caches." >&2; \
		$(EMULATOR_ADB) shell pm trim-caches 999G >/dev/null || true; \
		$(EMULATOR_ADB) install -r -d -g --no-incremental $(STAGE_APK); \
	else \
		echo "$$output"; \
	fi

launch-debug: check-emulator
	$(EMULATOR_ADB) shell am start -W -n $(MAIN_ACTIVITY)

launch-stage: check-emulator
	$(EMULATOR_ADB) shell am start -W -n $(STAGE_MAIN_ACTIVITY)

mcp-screens-regular: install-debug-appreveal
	scripts/capture_appreveal_mcp_screens.sh \
		--group regular \
		--adb "$(EMULATOR_ADB)" \
		--serial "$(ADB_SERIAL)" \
		--app-id "$(APP_ID)" \
		--activity "$(MAIN_ACTIVITY)"

mcp-screens-sheets: install-debug-appreveal
	scripts/capture_appreveal_mcp_screens.sh \
		--group sheets \
		--adb "$(EMULATOR_ADB)" \
		--serial "$(ADB_SERIAL)" \
		--app-id "$(APP_ID)" \
		--activity "$(MAIN_ACTIVITY)"

mcp-screens-all: install-debug-appreveal
	scripts/capture_appreveal_mcp_screens.sh \
		--group all \
		--adb "$(EMULATOR_ADB)" \
		--serial "$(ADB_SERIAL)" \
		--app-id "$(APP_ID)" \
		--activity "$(MAIN_ACTIVITY)"

mcp-stage-screens-all: install-stage-appreveal
	scripts/capture_appreveal_mcp_screens.sh \
		--group all \
		--adb "$(EMULATOR_ADB)" \
		--serial "$(ADB_SERIAL)" \
		--app-id "$(STAGE_APP_ID)" \
		--activity "$(STAGE_MAIN_ACTIVITY)"

playstore-build-stage: prepare-appreveal
	@set -euo pipefail; \
	if [[ ! -x "$(PLAYSTORE_JAVA_HOME)/bin/java" ]]; then \
		echo "Android Studio JBR not found at $(PLAYSTORE_JAVA_HOME)." >&2; \
		echo "Set PLAYSTORE_JAVA_HOME to a supported JDK 17 or 21 and retry." >&2; \
		exit 1; \
	fi; \
	if [[ ! -d "$(PLAYSTORE_ANDROID_SDK_ROOT)" ]]; then \
		echo "Android SDK not found at $(PLAYSTORE_ANDROID_SDK_ROOT)." >&2; \
		echo "Set PLAYSTORE_ANDROID_SDK_ROOT and retry." >&2; \
		exit 1; \
	fi; \
	JAVA_HOME="$(PLAYSTORE_JAVA_HOME)" \
	ANDROID_HOME="$(PLAYSTORE_ANDROID_SDK_ROOT)" \
	ANDROID_SDK_ROOT="$(PLAYSTORE_ANDROID_SDK_ROOT)" \
	$(GRADLE) assembleStaging

playstore-screenshots-phone: playstore-build-stage
	ANDROID_SDK_ROOT="$(PLAYSTORE_ANDROID_SDK_ROOT)" \
	PLAYSTORE_KEEP_EMULATOR="$(PLAYSTORE_KEEP_EMULATOR)" \
		scripts/capture_playstore_screenshots.sh \
		--device phone \
		--out-root "$(PLAYSTORE_OUTPUT_ROOT)" \
		--work-root "$(PLAYSTORE_WORK_ROOT)"

playstore-screenshots-tablet-7: playstore-build-stage
	ANDROID_SDK_ROOT="$(PLAYSTORE_ANDROID_SDK_ROOT)" \
	PLAYSTORE_KEEP_EMULATOR="$(PLAYSTORE_KEEP_EMULATOR)" \
		scripts/capture_playstore_screenshots.sh \
		--device tablet-7 \
		--out-root "$(PLAYSTORE_OUTPUT_ROOT)" \
		--work-root "$(PLAYSTORE_WORK_ROOT)"

playstore-screenshots-tablet-10: playstore-build-stage
	ANDROID_SDK_ROOT="$(PLAYSTORE_ANDROID_SDK_ROOT)" \
	PLAYSTORE_KEEP_EMULATOR="$(PLAYSTORE_KEEP_EMULATOR)" \
		scripts/capture_playstore_screenshots.sh \
		--device tablet-10 \
		--out-root "$(PLAYSTORE_OUTPUT_ROOT)" \
		--work-root "$(PLAYSTORE_WORK_ROOT)"

playstore-screenshots-tablets: playstore-build-stage
	ANDROID_SDK_ROOT="$(PLAYSTORE_ANDROID_SDK_ROOT)" \
	PLAYSTORE_KEEP_EMULATOR="$(PLAYSTORE_KEEP_EMULATOR)" \
		scripts/capture_playstore_screenshots.sh \
		--device tablet-7 \
		--out-root "$(PLAYSTORE_OUTPUT_ROOT)" \
		--work-root "$(PLAYSTORE_WORK_ROOT)"
	ANDROID_SDK_ROOT="$(PLAYSTORE_ANDROID_SDK_ROOT)" \
	PLAYSTORE_KEEP_EMULATOR="$(PLAYSTORE_KEEP_EMULATOR)" \
		scripts/capture_playstore_screenshots.sh \
		--device tablet-10 \
		--out-root "$(PLAYSTORE_OUTPUT_ROOT)" \
		--work-root "$(PLAYSTORE_WORK_ROOT)"

playstore-screenshots-all: playstore-build-stage
	@set -euo pipefail; \
	for device in phone tablet-7 tablet-10; do \
		ANDROID_SDK_ROOT="$(PLAYSTORE_ANDROID_SDK_ROOT)" \
		PLAYSTORE_KEEP_EMULATOR="$(PLAYSTORE_KEEP_EMULATOR)" \
		scripts/capture_playstore_screenshots.sh \
			--device "$$device" \
			--out-root "$(PLAYSTORE_OUTPUT_ROOT)" \
			--work-root "$(PLAYSTORE_WORK_ROOT)"; \
	done

mcp-full-scroll-regular: install-debug-appreveal
	scripts/capture_appreveal_mcp_screens.sh \
		--group regular \
		--capture-mode full \
		--adb "$(EMULATOR_ADB)" \
		--serial "$(ADB_SERIAL)" \
		--app-id "$(APP_ID)" \
		--activity "$(MAIN_ACTIVITY)"

mcp-full-scroll-sheets: install-debug-appreveal
	scripts/capture_appreveal_mcp_screens.sh \
		--group sheets \
		--capture-mode full \
		--adb "$(EMULATOR_ADB)" \
		--serial "$(ADB_SERIAL)" \
		--app-id "$(APP_ID)" \
		--activity "$(MAIN_ACTIVITY)"

mcp-full-scroll-all: install-debug-appreveal
	scripts/capture_appreveal_mcp_screens.sh \
		--group all \
		--capture-mode full \
		--adb "$(EMULATOR_ADB)" \
		--serial "$(ADB_SERIAL)" \
		--app-id "$(APP_ID)" \
		--activity "$(MAIN_ACTIVITY)"

mcp-full-scroll-screen: install-debug-appreveal
	@set -euo pipefail; \
	if [[ -z "$(SCREEN_KEY)" ]]; then \
		echo "Set SCREEN_KEY=<appreveal key>, for example: make mcp-full-scroll-screen SCREEN_KEY=sheet.exercise_history" >&2; \
		exit 2; \
	fi; \
	scripts/capture_appreveal_mcp_screens.sh \
		--group all \
		--capture-mode full \
		--screen-key "$(SCREEN_KEY)" \
		--adb "$(EMULATOR_ADB)" \
		--serial "$(ADB_SERIAL)" \
		--app-id "$(APP_ID)" \
		--activity "$(MAIN_ACTIVITY)"

mcp-phone-screens-all: install-device-debug
	@set -euo pipefail; \
	serial="$(DEVICE_SERIAL)"; \
	if [[ -z "$$serial" ]]; then \
		serial="$$( $(ADB) devices | awk 'NR > 1 && $$2 == "device" && $$1 !~ /^emulator-/ { print $$1; exit }' )"; \
	fi; \
	scripts/capture_appreveal_mcp_screens.sh \
		--group all \
		--adb "$(ADB)" \
		--serial "$$serial" \
		--app-id "$(APP_ID)" \
		--activity "$(MAIN_ACTIVITY)" \
		--startup-wait "$(MCP_PHONE_STARTUP_WAIT)"

mcp-phone-full-scroll-all: install-device-debug
	@set -euo pipefail; \
	serial="$(DEVICE_SERIAL)"; \
	if [[ -z "$$serial" ]]; then \
		serial="$$( $(ADB) devices | awk 'NR > 1 && $$2 == "device" && $$1 !~ /^emulator-/ { print $$1; exit }' )"; \
	fi; \
	scripts/capture_appreveal_mcp_screens.sh \
		--group all \
		--capture-mode full \
		--adb "$(ADB)" \
		--serial "$$serial" \
		--app-id "$(APP_ID)" \
		--activity "$(MAIN_ACTIVITY)" \
		--startup-wait "$(MCP_PHONE_STARTUP_WAIT)"

mcp-phone-full-scroll-screen: install-device-debug
	@set -euo pipefail; \
	if [[ -z "$(SCREEN_KEY)" ]]; then \
		echo "Set SCREEN_KEY=<appreveal key>, for example: make mcp-phone-full-scroll-screen SCREEN_KEY=sheet.exercise_history" >&2; \
		exit 2; \
	fi; \
	serial="$(DEVICE_SERIAL)"; \
	if [[ -z "$$serial" ]]; then \
		serial="$$( $(ADB) devices | awk 'NR > 1 && $$2 == "device" && $$1 !~ /^emulator-/ { print $$1; exit }' )"; \
	fi; \
	scripts/capture_appreveal_mcp_screens.sh \
		--group all \
		--capture-mode full \
		--screen-key "$(SCREEN_KEY)" \
		--adb "$(ADB)" \
		--serial "$$serial" \
		--app-id "$(APP_ID)" \
		--activity "$(MAIN_ACTIVITY)" \
		--startup-wait "$(MCP_PHONE_STARTUP_WAIT)"

install-device-debug: check-device build-debug
	@set -euo pipefail; \
	serial="$(DEVICE_SERIAL)"; \
	if [[ -z "$$serial" ]]; then \
		serial="$$( $(ADB) devices | awk 'NR > 1 && $$2 == "device" && $$1 !~ /^emulator-/ { print $$1; exit }' )"; \
	fi; \
	if ! output="$$( $(ADB) -s "$$serial" install -r $(DEBUG_APK) 2>&1 )"; then \
		echo "$$output" >&2; \
		if grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE" <<< "$$output"; then \
			echo "Existing $(APP_ID) uses a different signing key than this debug APK." >&2; \
			echo "Refusing to uninstall because that would delete local app data. Build with the same signing key as the installed app to preserve data." >&2; \
			exit 1; \
		else \
			exit 1; \
		fi; \
	else \
		echo "$$output"; \
	fi

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
	if ! output="$$( $(ADB) -s "$$serial" install -r $(DEBUG_APK) 2>&1 )"; then \
		echo "$$output" >&2; \
		if grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE" <<< "$$output"; then \
			echo "Existing $(APP_ID) uses a different signing key than this debug APK." >&2; \
			echo "Refusing to uninstall because that would delete local app data. Build with the same signing key as the installed app to preserve data." >&2; \
			exit 1; \
		else \
			exit 1; \
		fi; \
	else \
		echo "$$output"; \
	fi

install-device-stage: check-device build-stage
	@set -euo pipefail; \
	serial="$(DEVICE_SERIAL)"; \
	if [[ -z "$$serial" ]]; then \
		serial="$$( $(ADB) devices | awk 'NR > 1 && $$2 == "device" && $$1 !~ /^emulator-/ { print $$1; exit }' )"; \
	fi; \
	$(ADB) -s "$$serial" install -r $(STAGE_APK)

install-device-stage-no-build: check-device
	@set -euo pipefail; \
	if [[ ! -f "$(STAGE_APK)" ]]; then \
		echo "Staging APK not found at $(STAGE_APK)" >&2; \
		echo "Run 'make build-stage' first." >&2; \
		exit 1; \
	fi; \
	serial="$(DEVICE_SERIAL)"; \
	if [[ -z "$$serial" ]]; then \
		serial="$$( $(ADB) devices | awk 'NR > 1 && $$2 == "device" && $$1 !~ /^emulator-/ { print $$1; exit }' )"; \
	fi; \
	$(ADB) -s "$$serial" install -r $(STAGE_APK)

sync-device-custom-exercises: check-device
	@set -euo pipefail; \
	serial="$(DEVICE_SERIAL)"; \
	if [[ -z "$$serial" ]]; then \
		serial="$$( $(ADB) devices | awk 'NR > 1 && $$2 == "device" && $$1 !~ /^emulator-/ { print $$1; exit }' )"; \
	fi; \
	python3 scripts/sync_custom_exercises_from_device.py --serial "$$serial"
