#!/usr/bin/env bash

NAMESPACE=webhook-delivery-system

# Cleaning ./ssl/keys subdirectory
echo 'Cleaning ./ssl/keys subdirectory...'
mkdir -p ./ssl/keys
rm -vrf ./ssl/keys/*
# Generating RH Data Grid auto-signed key pair (private and public) keystore (keystore.p12)
echo 'Generating RH Data Grid auto-signed key pair (private and public) keystore (keystore.p12)...'
keytool -genkey -keypass P@ssw0rd -storepass P@ssw0rd -alias datagrid-cluster -keyalg RSA \
-dname "CN=datagrid-cluster.${NAMESPACE}.svc, OU=Red Hat EMEA App Svcs SSA, O=Red Hat" \
-validity 3600 -keystore ./ssl/keys/keystore.p12 -v \
-ext san=DNS:datagrid-cluster.${NAMESPACE}.svc,DNS:datagrid-cluster.${NAMESPACE}.svc.cluster.local,DNS:datagrid-cluster.apps.cluster.ocp-hamid.com
# Exporting RH Data Grid public auto-signed certificate (datagrid-cluster.crt)
echo 'Exporting RH Data Grid public auto-signed certificate (datagrid-cluster.crt)...'
keytool -exportcert -alias datagrid-cluster -keystore ./ssl/keys/keystore.p12 -file ./ssl/keys/datagrid-cluster.crt -storepass P@ssw0rd -v
# Creating a truststore (truststore.p12) containing the RH Data Grid public certificate (datagrid-cluster.crt)
echo 'Creating a truststore (truststore.p12) containing the RH Data Grid public certificate (datagrid-cluster.crt)...'
cp ${JAVA_HOME}/lib/security/cacerts ./ssl/keys/truststore.p12
keytool -storepasswd -keystore ./ssl/keys/truststore.p12 -storepass changeit -new 'P@ssw0rd'
keytool -importcert -trustcacerts -alias datagrid-cluster -keystore ./ssl/keys/truststore.p12 -file ./ssl/keys/datagrid-cluster.crt -storepass P@ssw0rd -v -noprompt

## Get the server certificate using SNI
# openssl s_client -showcerts -servername datagrid-cluster.apps.cluster.ocp-hamid.com -connect datagrid-cluster.apps.cluster.ocp-hamid.com:443