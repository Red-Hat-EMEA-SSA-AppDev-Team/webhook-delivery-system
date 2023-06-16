# consumer-webhook-receiver

Exposed HTTP endpoints:
- `/webhook/consumer` : 
    - Webhook ping endpoint through the `GET` HTTP method.
    - Logs the received payload (`POST` HTTP method).
- `/openapi.json`: returns the OpenAPI 3.0 specification for the service.
- `/q/health` : returns the _Camel Quarkus MicroProfile_ health checks
- `/q/metrics` : the _Camel Quarkus MicroProfile_ metrics

## Prerequisites

- A running [_Red Hat OpenShift 4_](https://access.redhat.com/documentation/en-us/openshift_container_platform) cluster
- User has the following CLI tools installed:
    - OpenShift CLI tool (`oc`)
    - `kamel` CLI

## Deploy to OpenShift instructions

1. Create a configmap holding the Consumer Webhook Receiver OAS

    ```script shell
    oc create cm consumer-webhook-receiver-oas --from-file ./resources/api/openapi.json
    ```
2. Run and deploy the Camel-K dispatcher integration
    ```script shell
    kamel run ConsumerWebhookReceiver.java
    ```