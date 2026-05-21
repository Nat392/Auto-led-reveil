#!/usr/bin/env sh

##############################################################################
##
##  Gradle startup script for UN*X
##
##############################################################################

set -eu

APP_HOME=$(cd "$(dirname "$0")"; pwd -P)
APP_BASE_NAME=$(basename "$0")

DEFAULT_JVM_OPTS=''

if [ -n "${JAVA_HOME:-}" ] ; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    JAVA_CMD=java
fi

if ! command -v "$JAVA_CMD" >/dev/null 2>&1; then
    printf '%s\n' 'ERROR: JAVA_HOME is not set and no java command could be found in your PATH.' >&2
    exit 1
fi

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar:$APP_HOME/gradle/wrapper/gradle-wrapper-shared-8.7.jar:$APP_HOME/gradle/wrapper/gradle-cli-8.7.jar"

exec "$JAVA_CMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS -Dorg.gradle.appname="$APP_BASE_NAME" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"