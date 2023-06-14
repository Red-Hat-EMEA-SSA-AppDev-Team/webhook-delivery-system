#!/usr/bin/env bash

NAMESPACE=webhook-delivery-system

# Create the Red Hat Data Grid cluster namespace
oc apply -f ./8.4_manifests/namespace.yaml

## Create the namespace OperatorGroup
oc apply -f ./8.4_manifests/webhook-delivery-system-operatorgroup.yaml -n $NAMESPACE

## The _Red Hat Data Grid_ operator subscription 
oc apply -f ./8.4_manifests/datagrid-operator-subscription.yaml -n $NAMESPACE

# Wait for Operators to be installed
watch oc get sub,csv,installPlan -n $NAMESPACE

# Create an authentication secret
oc create secret generic authentication-secret \
--from-file=./8.4_manifests/identities.yaml \
-n $NAMESPACE

# Create a custom encryption secret (TLS)
oc create secret generic datagrid-cluster-tls-secret \
--from-literal=alias=datagrid-cluster \
--from-literal=password=P@ssw0rd \
--from-file=./ssl/keys/keystore.p12 \
-n $NAMESPACE

# Create Red Hat Data Grid cluster
oc apply -f ./8.4_manifests/datagrid-cluster_cr.yaml -n $NAMESPACE

# Wait for the cluster creation
echo 'Waiting for the cluster creation...'
oc wait --for condition=wellFormed --timeout=240s infinispan/datagrid-cluster -n $NAMESPACE

# Create cache
oc apply -f ./8.4_manifests/idempotency-replicated-cache_cr.yaml -n $NAMESPACE