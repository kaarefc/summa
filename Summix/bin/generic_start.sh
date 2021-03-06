#!/bin/bash

set -e
DEPLOY=`dirname $0`/..

#
# TEMPLATE FOR RUNNING JARS FROM BASH SCRIPTS
#
# Directory Structure:
# The following directory structure will be assumed
#
#    app_root/
#      bin/           # All executable scripts go here, ie derivatives of this template
#      lib/           # All 3rd party libs/jar
#      config/        # Any properties or other config files
#      MAINJAR        # jar file containing the main class
#
# Classpath Construction:
#  - Any .jar in lib/ will be added to the classpath
#  - config/ will be added to the classpath
#
#
# Config Options:
#    MAINJAR          # The jar containing the main classpath. Set to "." for no jar.
#    MAINCLASS        # The jar containing the main classpath
#    LIBDIRS          # Space separated list of paths in where to look for jar files. Defaults to ./lib
#    PRINT_CONFIG     # Optional. Print config to stderr. Set to empty to not print config.
#    JAVA_HOME        # Optional. If there's a global JAVA_HOME it will be used.
#    JVM_OPTS         # Optional arguments to the jvm.
#    SECURITY_POLICY  # Optional. The value of java.security.policy (path to .policy file)
#    ENABLE_JMX       # Optional. If set to "true" the JMX_* paramters will be used.
#    NAME             # Optional. If set the argument visualvm.display.name is set.
#
# JMX Options:
#    JMX_PORT         # Port to run JMX on (integer)
#    JMX_SSL          # Wheteher or not to use SSL. (true/false)
#    JMX_ACCESS       # Path to .access file
#    JMX_PASS         # Path to .password file
#
#
# DEFAULT_CONFIGURATION # The configuration to use if no explicit CONFIGURATION is given
# PROPERTIES_FILE       # Optional file with properties to pass on to the JVM
#

if [ "$CONFIGURATION" != "" ]; then
    # If LOG4J is not set in the environment, deduct it from the config name
    if [ -z "$LOG4J" ]; then
        LOG4J="$(basename $CONFIGURATION .xml).log4j.xml"
    fi
    CONFIGURATION="-Dsumma.configuration=$CONFIGURATION"
else
    CONFIGURATION="-Dsumma.configuration=$DEFAULT_CONFIGURATION"
fi;

# Helper function to set properties in a properties file
# $1 : property name
# $2 : property value
# $3 : property file
function set_property () {
    if [ -z "$1" ]; then
        echo "ERROR: set_property : no property name given" 1>&2
        exit 1
    fi;
    if [ -z "$2" ]; then
        echo "ERROR: set_property : no property value given" 1>&2
        exit 1
    fi;
    if [ -f "$1" ]; then
        echo "ERROR: set_property : property file: is not a regular file" 1>&2
        exit 1
    fi;

    echo "Setting property $1=$2 in $3"
    sed -i -e "s@\(<entry key=\"$1\">\)\(.*\)\(</entry>\)@\1$2\3@g" $3

    if [ "$?" != "0" ]; then
        echo "Unable to set property. Bailing out." 1>&2
        exit 1
    fi;
}

# Build classpath
if [ "$LIBDIRS" == "" ]; then
    LIBDIRS="$DEPLOY"/lib
fi
for libdir in $LIBDIRS # This is why Summix don't support spaces
do
    # As of Java 1.6, the singular wildcard '*' means all jar files
    if [ -z "$CLASSPATH" ]; then
        CLASSPATH="${libdir%/}/*"
    else
        CLASSPATH="$CLASSPATH:${libdir%/}/*"
    fi
    
#    for lib in `ls $libdir/*.jar`
#    do
#        if [ -z "$CLASSPATH" ]; then
#            CLASSPATH=$lib
#        else
#            CLASSPATH=$CLASSPATH:$lib
#        fi
#    done
    CLASSPATH=$CLASSPATH:$libdir
done
CLASSPATH="$CLASSPATH:$DEPLOY/config/:$MAINJAR"

# Check JAVA_HOME
DEFAULT_JAVA_HOME=/usr/lib/jvm/java
if [ "$JAVA_HOME" == "" ]; then
    JAVA_HOME=$DEFAULT_JAVA_HOME
    echo "No JAVA_HOME set. Using default JAVA_HOME=$JAVA_HOME" 1>&2
fi;
if [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "$JAVA_HOME/bin/java does not exist or is not an executable file." 1>&2
    exit 1
fi

# Check security policy
if [ "$SECURITY_POLICY" != "" ]; then
    SECURITY_POLICY="-Djava.security.policy=$SECURITY_POLICY"
fi;

# Check JMX
if [ "$ENABLE_JMX" == "true" ]; then
    JMX_PORT="-Dcom.sun.management.jmxremote.port=$JMX_PORT"
    JMX_SSL="-Dcom.sun.management.jmxremote.ssl=$JMX_SSL"
    JMX_ACCESS="-Dcom.sun.management.jmxremote.access.file=$JMX_ACCESS"
    JMX_PASS="-Dcom.sun.management.jmxremote.password.file=$JMX_PASS"
    JMX="$JMX_PORT $JMX_SSL $JMX_PASS $JMX_ACCESS";
fi;

if [ "$NAME" != "" ]; then
    VISUALVM_NAME="-Dvisualvm.display.name=$NAME"
fi

SYS_PROPS=""
if [ -f "$PROPERTIES_FILE" ]; then        
    while read prop; do
        # Discard empty lines and comments
        if [[ "$prop" != "" && ! ("$prop" =~ '#') ]]; then
            SYS_PROPS="$SYS_PROPS -D$prop"
        fi
    done < "$PROPERTIES_FILE"
fi

if [ ! -z "$LOG4J" ]; then
    SYS_PROPS="$SYS_PROPS -Dlog4j.configuration=$LOG4J"
fi

# The fix below ensures that localhost is always used as RMI server address.
# This is necessary to avoid problems with firewalls and routing outside
# of the local computer.
LOCALRMI="-Djava.rmi.server.hostname=localhost"

# The quotes around classpath are essential as we use wildcards
COMMAND="$JAVA_HOME/bin/java $LOCALRMI $JVM_OPTS $CONFIGURATION $SECURITY_POLICY $JMX $VISUALVM_NAME -cp \"$CLASSPATH\" $MAINCLASS"

# Report settings
if [ ! -z $PRINT_CONFIG ]; then
    echo -e "JavaHome:\t$JAVA_HOME" 1>&2
    echo -e "Classpath:\t$CLASSPATH" 1>&2
    echo -e "MainJar:\t$MAINJAR" 1>&2
    echo -e "MainClass:\t$MAINCLASS" 1>&2
    echo -e "Working dir:\t`pwd`" 1>&2
    echo -e "JMX enabled:\t$ENABLE_JMX" 1>&2
    echo -e "Security:\t$SECURITY_POLICY" 1>&2
    echo -e "Log4J:\t\t$LOG4J" 1>&2
    echo -e "Properties:\t$SYS_PROPS\n" 1>&2
    echo -e "Command line:\n$COMMAND\n" 1>&2
fi

if [ ! -z "$SYS_PROPS" ]; then
    # We need 'eval' to get system property quoting right
    eval "exec $JAVA_HOME/bin/java $LOCALRMI $SYS_PROPS $CONFIGURATION $JVM_OPTS $SECURITY_POLICY $JMX $VISUALVM_NAME -cp \"$CLASSPATH\" $MAINCLASS $@"
else
#echo "generic_start just exec: $COMMAND"
    exec $COMMAND "$@"
fi
