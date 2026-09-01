# Two stages: the jar is assembled in a Maven image, and only the jar is carried into a
# JRE image. Nothing that builds the jar — Maven, the compiler, the source tree, the .m2
# cache — needs to exist in order to run a tile build, and shipping it only widens what
# has to be patched.
#
# Pin 21, not a later JDK. pom.xml compiles to 21 and its enforcer requires 21, so this is
# the runtime the project actually declares. 21 is also the current LTS: the previous base
# image here was JDK 22, which is non-LTS, and its tag stopped being rebuilt when 22 went
# end-of-life — the image shipped an unpatched JDK and Alpine userland for close to two
# years without that being visible anywhere.
#
# Alpine (musl) on both stages is deliberate rather than incidental. Planetiler loads
# native code — jffi, snappy-java, lz4-java, zstd-jni, and nestedvm behind sqlite-jdbc —
# and this image has always been Alpine, so those libraries are known to resolve against
# musl here. Moving to a glibc base would be an unforced change to that.

# ---- build stage ----
FROM maven:3-eclipse-temurin-21-alpine AS build
WORKDIR /build

# pom.xml alone first, so the dependency download is cached and only re-runs when the
# dependencies themselves change rather than on every source edit.
COPY pom.xml pom.xml
RUN mvn dependency:go-offline -B

COPY src src
# Tests are compiled here, so a test that does not build still fails the image, but they
# are not run: surefire resolves its JUnit provider lazily at execution time, and
# `dependency:go-offline` above cannot know to pre-fetch it, so running tests offline
# fails on a missing surefire-junit-platform jar. The test suite runs in CI instead.
RUN mvn package -o -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-alpine

# Must stay /tiles. Builder resolves its inputs against the relative path data/sources/,
# and the documented invocation mounts a host directory at /tiles/data (see RUNNING.md).
# Changing this breaks every documented command without any error that names the cause.
WORKDIR /tiles

# Copied to a fixed path so the entrypoint does not encode pom.xml's <version>. The source
# still names the versioned artifact, so a version bump fails loudly here at build time
# rather than producing an image with an entrypoint pointing at nothing.
COPY --from=build /build/target/sourdough-builder-HEAD-with-deps.jar /opt/sourdough-builder.jar

# No USER directive, because no uid is right for every host. A fixed non-root uid fails
# when it does not match the owner of the bind-mounted data/ directory; root fails
# differently and less visibly — it writes sources and temp files into that directory as
# root, and the next build run by an ordinary host user then dies with AccessDeniedException
# on files it cannot delete. That is not hypothetical, it has happened here.
#
# Neither default is safe, so the caller chooses: RUNNING.md documents passing
# --user "$(id -u):$(id -g)", which makes the container write files the invoking user owns.
ENTRYPOINT ["java", "-jar", "/opt/sourdough-builder.jar"]
