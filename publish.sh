#!/bin/bash
lein clean
lein immutant war
scp target/maladroit.war humpre:/srv/wildfly/standalone/deployments/maladroit/maladroit.war
