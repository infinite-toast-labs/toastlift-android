# Changelog

All notable changes to ToastLift are documented here. Releases follow
[Semantic Versioning](https://semver.org/), and entries are generated from
Conventional Commit-style pull-request titles.

## [1.1.0](https://github.com/infinite-toast-labs/toastlift-android/compare/v1.0.1...v1.1.0) (2026-08-28)


### Features

* add bounty cards feature with database entities and tests ([5d335ac](https://github.com/infinite-toast-labs/toastlift-android/commit/5d335ace723abb5aa91b805b4f44fa778457e6cd))
* add exercise description detail ([2ac05e1](https://github.com/infinite-toast-labs/toastlift-android/commit/2ac05e1d7ef11d199c7e94e4e815027c4fc42235))
* add live freshness gap actions ([e6fa53f](https://github.com/infinite-toast-labs/toastlift-android/commit/e6fa53f16a2c7d8a70e77ca46d18396f1bd32016))
* **ai-search:** add AI-powered exercise name matching and synonym management ([ff8655d](https://github.com/infinite-toast-labs/toastlift-android/commit/ff8655d811b0844e78b2fc2ec462424f50c02264))
* **appreveal:** add active workout overview replay ([f8fd5d6](https://github.com/infinite-toast-labs/toastlift-android/commit/f8fd5d6ebd8ea56724f941662ba97c7fa7d81968))
* **data:** add personal data restore workflow ([#15](https://github.com/infinite-toast-labs/toastlift-android/issues/15)) ([07a1b4a](https://github.com/infinite-toast-labs/toastlift-android/commit/07a1b4a1530e3d2ceee394b4c4e7d0cb323e1e0d))
* **debug:** add route full-scroll captures ([9100f11](https://github.com/infinite-toast-labs/toastlift-android/commit/9100f11b2b2e5f48e2368222e494ae5a7ef04016))
* design revamp — canvas-style UI, 3-tab nav, custom icons ([#4](https://github.com/infinite-toast-labs/toastlift-android/issues/4)) ([3b09366](https://github.com/infinite-toast-labs/toastlift-android/commit/3b093669278be714157501fae52cd2e4a00838eb))
* **freshness:** add re-entry mode for returning after breaks ([da168a8](https://github.com/infinite-toast-labs/toastlift-android/commit/da168a8e0d57760c31b9c89a0aa5132e40daa620))
* **library:** add custom exercise flow on Library nav screen ([#3](https://github.com/infinite-toast-labs/toastlift-android/issues/3)) ([963614c](https://github.com/infinite-toast-labs/toastlift-android/commit/963614cad03ee561144b17ffbea4adacc89dee5d))
* **library:** open muscle target filters from history ([e703fb5](https://github.com/infinite-toast-labs/toastlift-android/commit/e703fb5fdb74a387cda591de7c92410f361ffeb8))
* redesign history calendar with daily summary, workout thread, and improved medallion ([fe310fb](https://github.com/infinite-toast-labs/toastlift-android/commit/fe310fb0f01efd3ea7711db90b0db9ace522ad27))
* **release:** add deterministic Play listing assets ([6152b9b](https://github.com/infinite-toast-labs/toastlift-android/commit/6152b9b60dc109079fd3c8df7d12fca32750b9c7))
* **release:** add private Zstore publication path ([dfb1a31](https://github.com/infinite-toast-labs/toastlift-android/commit/dfb1a318e1f65c7687161a5b7731a26a45b8c2c0))
* **release:** prepare production launch ([#12](https://github.com/infinite-toast-labs/toastlift-android/issues/12)) ([b5aa325](https://github.com/infinite-toast-labs/toastlift-android/commit/b5aa325eb97f64d8879ba23e5cbfdb23a34fb9c9))
* **session:** show active muscle target impact ([e6a2607](https://github.com/infinite-toast-labs/toastlift-android/commit/e6a2607d44590a9332841ef8b5b578b88c6f4176))
* **ui:** debit stale freshness in token wallet ([2b766ac](https://github.com/infinite-toast-labs/toastlift-android/commit/2b766acbcaf050ee07f64c67adf2091d32bddb3b))
* **ui:** expand history and template navigation ([29788d0](https://github.com/infinite-toast-labs/toastlift-android/commit/29788d0c0879e65f3f9276dd0af35c0111fb405a))
* **ui:** preserve history and filter active workouts ([dbe8dba](https://github.com/infinite-toast-labs/toastlift-android/commit/dbe8dba334b39839b9805f5213758054d791ab29))
* **ui:** support edge back on close screens ([5f2af1c](https://github.com/infinite-toast-labs/toastlift-android/commit/5f2af1c536df90a1881af7ee72adfd4a1ae54194))
* **workout:** improve active exercise picking ([487bf7e](https://github.com/infinite-toast-labs/toastlift-android/commit/487bf7e56650ae0009d90c9679f77a27df872524))
* **workouts:** add muscle target tracking ([164c538](https://github.com/infinite-toast-labs/toastlift-android/commit/164c538cd117bdef1eabc73564afcf93bc872d74))


### Bug Fixes

* attribute workouts to their start time ([aae647a](https://github.com/infinite-toast-labs/toastlift-android/commit/aae647a025f462efed54b0b318c5057014bc3f65))
* **ci:** remove ripgrep dependency from release checks ([89b458e](https://github.com/infinite-toast-labs/toastlift-android/commit/89b458ef40a2e9709e92e5386ca837a95951b915))
* contrast, snackbar ([1bab275](https://github.com/infinite-toast-labs/toastlift-android/commit/1bab275dd8b35229b9bde6c6b53de2d6320adad3))
* **release:** bind Zstore provenance to clean sources ([26f5f8a](https://github.com/infinite-toast-labs/toastlift-android/commit/26f5f8a13286989a652c238e60aacff869b49fd3))
* **release:** harden Play bundle signing workflow ([f38de05](https://github.com/infinite-toast-labs/toastlift-android/commit/f38de05e52c2dfdcb03a460936a72449db05f16b))
* **release:** keep Zstore artifacts private ([83b88f2](https://github.com/infinite-toast-labs/toastlift-android/commit/83b88f27be79a5476469d1042b75640381b485d5))
* reorder session exercises by status then recency instead of activity sequence ([34ea559](https://github.com/infinite-toast-labs/toastlift-android/commit/34ea559755068a0527139427c3136824ea114f3a))
* **training:** avoid muscle target false positives ([c482668](https://github.com/infinite-toast-labs/toastlift-android/commit/c482668a85f7a6e34b45f6165fd73b89b35afc32))
* **training:** roll up muscle target contributions ([9ced0e0](https://github.com/infinite-toast-labs/toastlift-android/commit/9ced0e0a64fbdcbeab93129f1d9fe5062b29827d))
* **ui:** clarify active workout exercise status ([da50e6f](https://github.com/infinite-toast-labs/toastlift-android/commit/da50e6f1c61a26f93529ff0110987bb6ff488dbb))
* **ui:** deprioritize MinimalTodayScreen early return so template/exercise flows take precedence ([8c4128e](https://github.com/infinite-toast-labs/toastlift-android/commit/8c4128e97d88871077505c387444a3abc22cbb9d))
* **ui:** refine active workout session colors and text for light theme ([706bf70](https://github.com/infinite-toast-labs/toastlift-android/commit/706bf703260029dcda2b8fc7b2a05893a5af0e16))
* **ui:** refine set logging row colors and add outlineColor param to text fields ([#14](https://github.com/infinite-toast-labs/toastlift-android/issues/14)) ([c3f8f04](https://github.com/infinite-toast-labs/toastlift-android/commit/c3f8f048e97d12560e70c637dfb06eb08c304213))
* **ui:** widen history calendar cards ([f7dc6c3](https://github.com/infinite-toast-labs/toastlift-android/commit/f7dc6c31b3a8883187c7f30542e6172f35ab2822))
* **workouts:** close prescription audit gaps ([356e3ad](https://github.com/infinite-toast-labs/toastlift-android/commit/356e3addaf943cd9529ad7b4006994d0c608ca13))
* **workouts:** harden generated prescriptions ([fb8d9eb](https://github.com/infinite-toast-labs/toastlift-android/commit/fb8d9eb3e7af0255a1b1105b2a7b2cc3c61d19cf))

## [1.0.1] - 2026-08-28

### Fixed

- Attribute training freshness, workout-day status, weekly progress, exercise history, and recommendation recency to when a workout started, so finishing an old session later does not make that training look recent.

## [1.0.0] - 2026-07-13

### Added

- Reviewed initial public Android release baseline.
