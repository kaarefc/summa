#!/bin/bash

set -e
DEPLOY=`dirname $0`/..

#
# EDIT HERE
# Variables prepended with ### are suitable for replacement 
# by autogenerated scripts
#

###MAINJAR=
export MAINCLASS=dk.statsbiblioteket.summa.common.filter.FilterControl
export CODEBASE_BASEURL="file://$DEPLOY/lib"

export PRINT_CONFIG=
export NAME=summa-filter
###LIBDIRS=
###JAVA_HOME=
#JVM_OPTS="$JVM_OPTS -Dsumma.configuration=$1"
###SECURITY_POLICY=
###ENABLE_JMX=

###JMX_PORT=
###JMX_SSL=
###JMX_ACCESS=
###JMX_PASS=

export CONFIGURATION=$1
if [ ! -f "$1" ]; then
        echo "You must specify a configuration as first parameter" 1>&2
        exit 1
fi

exec $DEPLOY/bin/generic_start.sh
