# Third-party notices / 第三方声明

## DEFY

Copyright (C) 2026 zhengmoumou13 and DEFY contributors.

Except where otherwise noted, original DEFY source code and project assets are distributed under the GNU Affero General Public License, version 3 (AGPL-3.0). See [LICENSE](LICENSE). Third-party material retains its own copyright and license. This project is provided without warranty under that license.

## openGym

Copyright (C) 2026 Duarte Santos.

DEFY adapts exercise catalogue text, Chinese translations and starter routines from [DuarteSantos8/openGym](https://github.com/DuarteSantos8/openGym) at commit `b6cfe2bbd3f099523ffbf2677438864e9a09a06f` into native Kotlin/Compose workflows.

- [Bundled upstream LICENSE](app/src/main/assets/opengym/LICENSE)
- [Unmodified upstream NOTICE](app/src/main/assets/opengym/NOTICE.md)
- [Version and scope](app/src/main/assets/opengym/SOURCE.txt)
- [Importer](scripts/import_opengym.py)

The upstream project is AGPL-3.0. Its notices also attribute certain original exercise names/instructions to ExerciseDB v1 / AscendAPI via hasaneyldrm/exercises-dataset under MIT. That MIT notice is retained in the upstream NOTICE.

The upstream NOTICE describes additional components used by openGym itself. DEFY does not import its server, account system, JavaScript UI, muscle-map geometry or QR-code modules. Retaining the complete notice is not a claim that those modules are included.

**Gym visual exercise stills and animations are separately licensed and are not included in DEFY.** No license to those media is granted by this repository. Do not extract or copy them into a contribution without an appropriate separate license. The upstream app-store exception is retained as upstream text; DEFY does not represent it as a new permission from every contributor or media rightsholder.

## USDA FoodData Central

U.S. Department of Agriculture, Agricultural Research Service. FoodData Central, SR Legacy April 2018.

The 28 reference records in `app/src/main/assets/nutrition/foods.json` retain FDC IDs, original food descriptions and per-100 g nutrient values. USDA states these data are public domain and published under CC0. See the [official license statement](https://fdc.nal.usda.gov/api-guide/#bkmk-9), bundled [SOURCE.txt](app/src/main/assets/nutrition/SOURCE.txt), and [import script](scripts/import_food_composition.py).

## Kotlin, Android and runtime dependencies

The application resolves Kotlin / kotlinx, AndroidX (Compose, Material, Navigation, Room, DataStore, WorkManager, ExifInterface), OkHttp and their transitive dependencies through Gradle. These projects retain their own licenses; see their published artifacts and repositories:

- [Kotlin](https://github.com/JetBrains/kotlin) and [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization): Apache-2.0.
- [AndroidX](https://android.googlesource.com/platform/frameworks/support/): Apache-2.0 for the libraries used here.
- [OkHttp](https://github.com/square/okhttp): Apache-2.0.
- [JUnit 4](https://github.com/junit-team/junit4): EPL-1.0, used for tests.
- [Gradle](https://github.com/gradle/gradle): Apache-2.0; the Gradle Wrapper is included for building.

Dependency versions are recorded in `gradle/libs.versions.toml` and `app/build.gradle.kts`. APK distributors must retain any applicable notices from the exact dependency artifacts they distribute.

## Original visuals and imported user material

The DEFY vector mark/wordmark and the supported Canvas exercise diagrams are original project assets, distributed with the project's source license. Third-party brands mentioned in documentation remain those of their respective owners; no endorsement is implied.

User-imported videos, photos, documents and recordings are not included in this repository and do not become openly licensed merely by being imported into DEFY. The Tan Chengyi reference tool cites a source video and selected parameters; the video, imagery and verbatim transcript are not redistributed. See [data sources](docs/DATA_SOURCES.md).
