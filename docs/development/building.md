# Building & Testing

## Building from source

ClickSorted depends on [KcMcLib](https://github.com/kccricket/KcMcLib), a shared library consumed as a git submodule via a Gradle composite build (`includeBuild("KcMcLib")` in `settings.gradle.kts`). Clone with submodules, or the build will fail to resolve `net.kccricket:kcmclib`:

```bash
git clone --recurse-submodules https://github.com/kccricket/clicksorted.git
cd clicksorted
./gradlew clean build
```

If you already cloned without `--recurse-submodules`, run `git submodule update --init` first.

The build compiles with a Java 25 toolchain (auto-provisioned by Gradle via the foojay resolver if no local JDK 25 matches) but still emits Java 21 bytecode — the plugin's runtime requirement is unchanged.

The output JAR will be at `build/libs/clicksorted-<version>.jar`.

## Running the test suite

```bash
./gradlew clean test
```

## Maintainer tooling

`./gradlew generateItemNames` regenerates `tools/items.generated.yml`, a variant-aware Material/ItemType sort-key table, from the current `paper-api` version. It lives in a standalone `tools` Gradle source set and is never bundled into the plugin jar.

## License

ClickSorted retains the original **GNU GPL v3** license from the original ClickSort plugin by Des Herriott.
See [LICENSE](../../LICENSE) or the [full licence text](http://www.gnu.org/licenses/gpl-3.0.html).
