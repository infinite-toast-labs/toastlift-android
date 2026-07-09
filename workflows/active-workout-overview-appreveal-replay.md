# Active Workout Overview AppReveal Replay

Use this prompt in a fresh session:

```text
We are in /home/gem/workspace/itl/toastlift-android.

Goal: continue UI work on the active workout overview regression using emulator-only AppReveal replay screenshots.

Important artifact:
examples/active-workout-overview-replay/phone.fixture.json

This fixture was captured from the physical phone and contains the active workout state to replay. Use it to restore the workout overview on the emulator, then screenshot the emulator for review. Do not use the physical phone unless I explicitly ask.

The repo has AppReveal support for:
- screen key: active.workout_overview
- tools: debug_begin, restore_screen_fixture, open_screen, screenshot, debug_end

Workflow:
1. Build/install debug APK on emulator:
   ./gradlew --no-daemon --console=plain assembleDebug
   android-emulator-adb -s emulator-5560 install -r -d -g --no-incremental app/build/outputs/apk/debug/app-debug.apk

2. Start app and discover AppReveal session URL/token from logcat.

3. Call AppReveal MCP tools against emulator:
   - debug_begin
   - restore_screen_fixture with:
     examples/active-workout-overview-replay/phone.fixture.json
   - open_screen with screen_key active.workout_overview
   - screenshot
   - debug_end

4. Save each run under android-e2e/<timestamp>-<description>-<5-char-slug>/.
   The screenshot should be named emulator-workout-overview.png.

5. First, create a fresh baseline emulator screenshot from the fixture and critique the visual state. The issue is that completed vs in-progress exercises are hard to distinguish. Note the progress ring around the exercise badge, but judge whether it is visually strong enough.

6. Then iteratively make UI fixes in emulator only:
   - edit code
   - rebuild/install
   - replay the same fixture
   - capture a new screenshot
   - compare against prior screenshot
   - repeat until the overview clearly communicates completed, in-progress, and upcoming exercises.

7. Keep generated android-e2e artifacts out of git. Do not force-add them.

Existing known-good replay screenshot for reference:
examples/active-workout-overview-replay/emulator-workout-overview-known-good.png

Before final response, report:
- latest screenshot path
- what changed
- what was verified
- any remaining visual concerns
```
