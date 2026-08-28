#!/usr/bin/env bash
set -euo pipefail

apk="${1:-app/build/outputs/apk/zstore/app-zstore-unsigned.apk}"
expected_package="${2:-dev.toastlabs.toastlift.zstore}"

[[ -f "$apk" ]] || { echo "Zstore APK not found: $apk" >&2; exit 1; }
[[ "$expected_package" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]] || {
  echo "Invalid expected package: $expected_package" >&2
  exit 1
}

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
build_tools_version="${ANDROID_BUILD_TOOLS:-36.0.0}"
build_tools="$sdk_root/build-tools/$build_tools_version"
for tool in aapt2 apksigner zipalign; do
  [[ -x "$build_tools/$tool" ]] || {
    echo "Missing Android build tool: $build_tools/$tool" >&2
    exit 1
  }
done

version_output="$(scripts/check_version.sh)"
expected_version_name="$(printf '%s\n' "$version_output" | sed -n 's/^versionName=//p')"
expected_version_code="$(printf '%s\n' "$version_output" | sed -n 's/^versionCode=//p')"
badging="$($build_tools/aapt2 dump badging "$apk")"
package_line="$(printf '%s\n' "$badging" | sed -n 's/^package: //p' | head -n 1)"

actual_package="$(printf '%s\n' "$package_line" | sed -n "s/^name='\([^']*\)'.*/\1/p")"
actual_version_code="$(printf '%s\n' "$package_line" | sed -n "s/^name='[^']*' versionCode='\([^']*\)'.*/\1/p")"
actual_version_name="$(printf '%s\n' "$package_line" | sed -n "s/^name='[^']*' versionCode='[^']*' versionName='\([^']*\)'.*/\1/p")"

[[ "$actual_package" == "$expected_package" ]] || {
  echo "Unexpected Zstore package: $actual_package" >&2
  exit 1
}
[[ "$actual_version_code" == "$expected_version_code" ]] || {
  echo "Unexpected Zstore versionCode: $actual_version_code (expected $expected_version_code)" >&2
  exit 1
}
[[ "$actual_version_name" == "$expected_version_name" ]] || {
  echo "Unexpected Zstore versionName: $actual_version_name (expected $expected_version_name)" >&2
  exit 1
}
[[ "$package_line" != *" split='"* ]] || {
  echo "Zstore requires one universal APK, not a split APK." >&2
  exit 1
}
printf '%s\n' "$badging" | grep -qx 'application-debuggable' || {
  echo "Zstore candidate must be debuggable." >&2
  exit 1
}

for permission in \
  android.permission.INTERNET \
  android.permission.ACCESS_WIFI_STATE \
  android.permission.CHANGE_WIFI_MULTICAST_STATE; do
  if printf '%s\n' "$badging" | grep -Fq "uses-permission: name='$permission'"; then
    echo "Zstore candidate unexpectedly declares $permission." >&2
    exit 1
  fi
done

if "$build_tools/apksigner" verify "$apk" >/dev/null 2>&1; then
  echo "Zstore candidate must be unsigned; signing belongs to the trusted Zstore host." >&2
  exit 1
fi
"$build_tools/zipalign" -c -P 16 -v 4 "$apk" >/dev/null

build_config="app/build/generated/source/buildConfig/zstore/dev/toastlabs/toastlift/BuildConfig.java"
[[ -f "$build_config" ]] || { echo "Generated Zstore BuildConfig not found." >&2; exit 1; }
grep -Fq 'public static final boolean INTERNAL_TOOLS_ENABLED = false;' "$build_config"
grep -Fq 'public static final boolean PRODUCTION_FEATURE_CONFIG = true;' "$build_config"
grep -Fq 'public static final String FEATURE_CONFIG_ASSET = "feature-config.production.json";' "$build_config"
for field in \
  GEMINI_API_KEY GEMINI_PRIMARY_MODEL CUSTOM_EXERCISE_AI_PROVIDER \
  OPENCODE_API_KEY OPENCODE_MODEL OPENCODE_CHAT_COMPLETIONS_URL \
  OPENROUTER_API_KEY OPENROUTER_MODEL OPENROUTER_CHAT_COMPLETIONS_URL \
  OPENROUTER_GENERATION_URL; do
  grep -Fq "public static final String $field = \"\";" "$build_config" || {
    echo "Zstore BuildConfig field $field is not empty." >&2
    exit 1
  }
done

echo "Verified unsigned Zstore candidate: $actual_package $actual_version_name ($actual_version_code)"
