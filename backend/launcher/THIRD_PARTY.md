# Third-party software

This portable application includes the project's Kotlin/Ktor backend and STM/exo
bridge, their JVM dependencies, OpenTripPlanner, and Java runtimes. The package
manifest records the exact shipped file hashes. `build-versions.toml` records the
project dependency versions. Runtime license notices are under `runtime/legal/`
and `services/otp-runtime/legal/`; dependency JARs retain their original notices.

- Kotlin and Ktor: Apache License 2.0, https://kotlinlang.org/ and https://ktor.io/
- kotlinx.serialization: Apache License 2.0, https://github.com/Kotlin/kotlinx.serialization
- OpenTripPlanner: LGPL-3.0, https://github.com/opentripplanner/OpenTripPlanner
  (use the source matching your packaged OTP JAR; see its META-INF metadata).
- Java: use redistributable OpenJDK runtimes; retain their bundled license and
  legal notices. This script uses the JDKs explicitly selected by the builder.
- Other transitive dependencies retain the licenses and notices inside their JARs.

Cloudflared is optional, separately installed from
https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/.
It is not included in the portable ZIP.

Graphs, STM/exo feeds, private credentials and account configuration are not
distributed in the executable package. Their data licenses and provider terms
continue to apply to separately imported data. ARTM zone source attribution:
see `backend/data/README.md` in the source repository.
