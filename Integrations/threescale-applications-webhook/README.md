# threescale-applications-webhook project

This project leverages **Red Hat build of Quarkus 2.13.x**, the Supersonic Subatomic Java Framework. More specifically, the project is implemented using [**Red Hat Camel Extensions for Quarkus 2.13.x**](https://access.redhat.com/documentation/en-us/red_hat_integration/2023.q1/html/getting_started_with_camel_extensions_for_quarkus/index).

It exposes the following RESTful service endpoints  using **Apache Camel REST DSL** and the **Apache Camel Quarkus Platform HTTP** extension:
- `/webhook/applicationurl` : 
    - Webhook ping endpoint through the `GET` HTTP method.
    - Handles a 3scale Admin/Developer Portal application webhook event and saves its webhook-url value in a database table (`POST` HTTP method).
- `/openapi.json`: returns the OpenAPI 3.0 specification for the service.
- `/q/health` : returns the _Camel Quarkus MicroProfile_ health checks
- `/q/metrics` : the _Camel Quarkus MicroProfile_ metrics

## Prerequisites
- JDK 17 installed with `JAVA_HOME` configured appropriately
- Apache Maven 3.8.1+
- A running PostgreSQL database with the [`wh_registration`](./src/main/resources/sql/create-wh_registration-table.sql) table. You may need to adjust the connection parameters when running with the PROD profile:
    - Locally, in the [`application.yml`](./src/main/resources/application.yml)

        ```yaml
        "%prod":
            quarkus:
                ## Camel Quarkus JDBC config
                datasource:
                    camel:
                        username: admin
                        password: admin
                        jdbc:
                            url: jdbc:postgresql://postgresql.postgresql.svc:5432/sampledb
        ```
    - On OpenShift, either:
        - Adjust the [`openshift.yml`](./src/main/kubernetes/openshift.yml) file before building and deploying using the quarkus maven plugin
        - Or, adjust connection values in the created `threescale-applications-webhook-config` configMap and `threescale-applications-webhook-secret` secret.

- **OPTIONAL**: [**Jaeger**](https://www.jaegertracing.io/), a distributed tracing system for observability ([_open tracing_](https://opentracing.io/)). :bulb: A simple way of starting a Jaeger tracing server is with `docker` or `podman`:
    1. Start the Jaeger tracing server:
        ```
        podman run --rm -e COLLECTOR_ZIPKIN_HOST_PORT=:9411 -e COLLECTOR_OTLP_ENABLED=true \
        -p 6831:6831/udp -p 6832:6832/udp \
        -p 5778:5778 -p 16686:16686 -p 4317:4317 -p 4318:4318 -p 14250:14250  -p 14268:14268 -p 14269:14269 -p 9411:9411 \
        quay.io/jaegertracing/all-in-one:latest
        ```
    2. While the server is running, browse to http://localhost:16686 to view tracing events.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application locally

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -Dquarkus.kubernetes-config.enabled=false -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## :bulb: Packaging and running the application on Red Hat OpenShift

### Pre-requisites
- Access to a [Red Hat OpenShift](https://access.redhat.com/documentation/en-us/openshift_container_platform) cluster
- User has self-provisioner privilege or has access to a working OpenShift project

1. Login to the OpenShift cluster
    ```shell script
    oc login ...
    ```

2. Create an OpenShift project or use your existing OpenShift project. For instance, use `webhook-delivery-system`
    ```shell script
    oc project webhook-delivery-system
    ```
        
3. Create an `allInOne` Jaeger instance.
    1. **IF NOT ALREADY INSTALLED**:
        1. Install, via OLM, the `Red Hat OpenShift distributed tracing platform` (Jaeger) operator with an `AllNamespaces` scope. :warning: Needs `cluster-admin` privileges
            ```shell script
            oc apply -f - <<EOF
            apiVersion: operators.coreos.com/v1alpha1
            kind: Subscription
            metadata:
                name: jaeger-product
                namespace: openshift-operators
            spec:
                channel: stable
                installPlanApproval: Automatic
                name: jaeger-product
                source: redhat-operators
                sourceNamespace: openshift-marketplace
            EOF
            ```
        2. Verify the successful installation of the `Red Hat OpenShift distributed tracing platform` operator
            ```shell script
            watch oc get sub,csv
            ```
    2. Create the `allInOne` Jaeger instance.
        ```shell script
        oc apply -f - <<EOF
        apiVersion: jaegertracing.io/v1
        kind: Jaeger
        metadata:
            name: jaeger-all-in-one-inmemory
        spec:
            allInOne:
                options:
                log-level: info
            strategy: allInOne
        EOF
        ```

4. Use the _**S2I binary workflow**_ or _**S2I source workflow**_ to deploy the `threescale-applications-webhook` app

    ```shell script
    ./mvnw clean package -Dquarkus.kubernetes.deploy=true -Dquarkus.container-image.group=webhook-delivery-system
    ```

## :bulb: Testing the application on OpenShift

### Pre-requisites

- [**`curl`**](https://curl.se/) or [**`HTTPie`**](https://httpie.io/) command line tools. 
- [**`HTTPie`**](https://httpie.io/) has been used in the tests.

### Testing instructions:

1. Get the OpenShift route hostname
    ```shell script
    URL="http://$(oc get route threescale-applications-webhook -o jsonpath='{.spec.host}')"
    ```
    
2. Test the `/webhook/applicationurl` endpoint

    - `GET /webhook/applicationurl` :

        ```shell script
        http -v $URL/webhook/applicationurl
        ```
        ```shell script
        [...]
        HTTP/1.1 200 OK
        [...]
        Content-Type: application/json
        [...]
        breadcrumbId: 43EB8F0221CD24E-0000000000000001
        transfer-encoding: chunked

        {
            "status": "OK"
        }
        ```

    - `POST /webhook/applicationurl` :

        - `OK` response:

            ```shell script
            echo '<?xml version="1.0" encoding="UTF-8"?>
            <event>
                <action>created</action>
                <type>application</type>
                <object>
                    <application>
                        <id>6992</id>
                        <created_at>2023-06-14T15:37:32+02:00</created_at>
                        <updated_at>2023-06-14T15:37:32+02:00</updated_at>
                        <state>pending</state>
                        <user_account_id>1791</user_account_id>
                        <first_traffic_at />
                        <first_daily_traffic_at />
                        <service_id>1319</service_id>
                        <user_key>ae61cf0a5e082f3cd0b54dc53cfba280</user_key>
                        <provider_verification_key>191222d51a5bceabd47a2084acea90f1</provider_verification_key>
                        <plan custom="false" default="false">
                            <id>4296</id>
                            <name>RHOAM Open API Plan</name>
                            <type>application_plan</type>
                            <state>published</state>
                            <approval_required>true</approval_required>
                            <setup_fee>5.0</setup_fee>
                            <cost_per_month>2.99</cost_per_month>
                            <trial_period_days />
                            <cancellation_period>0</cancellation_period>
                            <service_id>1319</service_id>
                        </plan>
                        <name>Red Hat App</name>
                        <description>Red Hat application to the RHOAM Quarkus API</description>
                        <extra_fields>
                            <webhook-url>https://webhook.site/c24da183-1a6c-454f-82a5-ed9f188d2f5b</webhook-url>
                        </extra_fields>
                    </application>
                </object>
            </event>' | http -v POST $URL/webhook/applicationurl content-type:application/xml
            ```
            ```shell script
            [...]
            HTTP/1.1 200 OK
            Access-Control-Allow-Headers: Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers
            Access-Control-Allow-Methods: GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS, CONNECT, PATCH
            Access-Control-Allow-Origin: *
            Access-Control-Max-Age: 3600
            Content-Type: application/json
            RHOAM_EVENT_ACTION: updated
            RHOAM_EVENT_TYPE: account
            Set-Cookie: 0d5acfcb0ca2b6f2520831b8d4bd4031=f3580c9af577adb49be04813506f5ec6; path=/; HttpOnly
            breadcrumbId: 43EB8F0221CD24E-0000000000000002
            transfer-encoding: chunked

            {
                "status": "OK"
            }
            ```

        - `KO` response:

            ```shell script
            echo 'PLAIN TEXT' | http -v POST $URL/webhook/applicationurl content-type:application/xml
            ```
            ```shell script
            [...]
            HTTP/1.1 400 Bad Request
            Access-Control-Allow-Headers: Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers
            Access-Control-Allow-Methods: GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS, CONNECT, PATCH
            Access-Control-Allow-Origin: *
            Access-Control-Max-Age: 3600
            Content-Type: application/json
            Set-Cookie: 0d5acfcb0ca2b6f2520831b8d4bd4031=f3580c9af577adb49be04813506f5ec6; path=/; HttpOnly
            breadcrumbId: 43EB8F0221CD24E-0000000000000003
            transfer-encoding: chunked

            {
                "error": {
                    "code": "400",
                    "description": "Bad Request",
                    "message": "org.apache.camel.TypeConversionException: Error during type conversion from type: java.lang.String to the required type: org.w3c.dom.Document with value PLAIN TEXT\n due to org.xml.sax.SAXParseException: Content is not allowed in prolog."
                },
                "status": "KO"
            }
            ```