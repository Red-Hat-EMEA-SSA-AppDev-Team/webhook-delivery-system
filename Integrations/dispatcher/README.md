# dispatcher

## Prerequisites

- A running [_Red Hat OpenShift 4_](https://access.redhat.com/documentation/en-us/openshift_container_platform) cluster
- A running [_Red Hat Data Grid v8.4_](https://access.redhat.com/documentation/en-us/red_hat_data_grid/8.4) cluster with the `idempotency-replicated-cache` created. 
    >_**NOTE**_: The [`/ApplicationRuntimes/install-rh-datagrid/8.4_manifests/idempotency-replicated-cache_cr.yaml`](../../ApplicationRuntimes/install-rh-datagrid/8.4_manifests/idempotency-replicated-cache_cr.yaml) file defines the `idempotency-replicated-cache` _Cache Custom Resource_ for deployment purposes. For instance, the following command line would create the `idempotency-replicated-cache` replicated cache if the _Red Hat Data Grid_ cluster is deployed in the `webhook-delivery-system` namespace: `oc -n webhook-delivery-system apply -f ../../ApplicationRuntimes/install-rh-datagrid/8.4_manifests/idempotency-replicated-cache_cr.yaml`
- User has the following CLI tools installed:
    - OpenShift CLI tool (`oc`)
    - `kamel` CLI
- A truststore containing the [_Red Hat Data Grid v8.4_](https://access.redhat.com/documentation/en-us/red_hat_data_grid/8.4) server public certificate. Below are sample command lines to generate one:
    ```script shell
    # Use the Java cacerts as the basis for the truststore
    cp ${JAVA_HOME}/lib/security/cacerts ./tls-keys/truststore.p12
    keytool -storepasswd -keystore ./tls-keys/truststore.p12 -storepass changeit -new 'P@ssw0rd'
    # Importing the Red Hat Data Grid server public certificate into the truststore
    keytool -importcert -trustcacerts -alias datagrid-cluster -keystore ./tls-keys/truststore.p12 -file ./tls-keys/datagrid-cluster.crt -storepass P@ssw0rd -v -noprompt
    ```

    > :bulb: **Example on how to obtain the Red Hat Data Grid server public certificate:**
    ```script shell
    openssl s_client -showcerts -servername <Red Hat Data Grid cluster OpenShift route> -connect <Red Hat Data Grid cluster OpenShift route>:443
    ```
    with `<Red Hat Data Grid cluster OpenShift route>`: OpenShift route hostname for the Red Hat Data Grid cluster. E.g.: `datagrid-cluster.apps.cluster-sn4j7.sn4j7.sandbox1656.opentlc.com`

## Deploy to OpenShift

### Prerequisites

- The `idempotency-replicated-cache` cache has been created in the _Red Hat Data Grid_ cluster.

### Instructions

1. Create a secret containing the dispatcher truststore

    ```script shell
    oc create secret generic dispatcher-truststore-secret --from-file=./tls-keys/truststore.p12
    ```
2. Run and deploy the Camel-K dispatcher integration
    ```script shell
    kamel run dispatcher-testDG ./Dispatcher.java
    ```