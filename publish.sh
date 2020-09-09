#!/bin/bash
lein clean
lein uberwar
scp target/maladroit.war humpre:/srv/wildfly/standalone/deployments/maladroit/maladroit.war
