#!/bin/sh 
export MAVEN_OPTS="-Xmx18g"
mvn exec:java -pl event-coref -Dexec.mainClass="edu.cmu.cs.lti.event_coref.pipeline.ChineseEventMentionPipeline" -Dexec.args=$1
