
// camel-k: language=java property=file:kafka.properties
// camel-k: dependency=camel:gson
// camel-k: dependency=camel:jdbc
// camel-k: dependency=camel-quarkus-infinispan
// camel-k: dependency=mvn:io.quarkus:quarkus-jdbc-postgresql
// camel-k: resource=secret:dispatcher-truststore-secret@/mnt/ssl
// camel-k: trait=tracing.endpoint=http://jaeger-all-in-one-inmemory-collector.webhook-delivery-system.svc:14268/api/traces
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.component.infinispan.remote.InfinispanRemoteIdempotentRepository;
import org.infinispan.client.hotrod.RemoteCacheManager;
import javax.inject.Inject;

public class dispatcher extends RouteBuilder {

    @Inject
    RemoteCacheManager remoteCacheManager;

    @Override
    public void configure() throws Exception {
      
      // MemoryIdempotentRepository memoryIdempotentRepository = new MemoryIdempotentRepository();
      InfinispanRemoteIdempotentRepository infinispanRemoteIdempotentRepository = new InfinispanRemoteIdempotentRepository("idempotency-replicated-cache");
      infinispanRemoteIdempotentRepository.setCacheContainer(remoteCacheManager);

      from("kafka:order-created-event")
        .setProperty("notification", simple("${body}"))
        .unmarshal().json(JsonLibrary.Gson)
        //.idempotentConsumer(simple("${body[eventId]}"), memoryIdempotentRepository)
        .idempotentConsumer(simple("${body[eventId]}"), infinispanRemoteIdempotentRepository)
        .log("Order created with eventID: ${body[eventId]}")
        .setBody(constant("select app_id from  wh_registration where  product_id=130"))
        .to("jdbc:default")
        .split(body()).streaming().parallelProcessing()
        .log("${body[app_id]} ")
        .removeHeaders("*")
        .setHeader("Exchange.HTTP_METHOD", constant("GET"))
        .setHeader("Exchange.CONTENT_TYPE", constant("application/json"))
        .setHeader("Exchange.HTTP_QUERY", simple(
                "application_id=${body[app_id]}&access_token=d6e6ce0d9c11d1ed93b35cb0674da280f58d52ac7b86ece632c7d9e093f1858d"))

        .to("https://t1-admin.apps.cluster.ocp-hamid.com/admin/api/applications/find.json?bridgeEndpoint=true&throwExceptionOnFailure=false")// &throwExceptionOnFailure=false
        .choice()
        .when(header(Exchange.HTTP_RESPONSE_CODE).isEqualTo("200")) 
        .setProperty("user_key").jsonpath("$.application.user_key", String.class)
        .setProperty("webhook-url").jsonpath("$.application.webhook-url", String.class)
        .log("user_key=${exchangeProperty.user_key} webhook-url=${exchangeProperty.webhook-url}")
        .removeHeaders("*")
        .setHeader("Exchange.HTTP_METHOD", constant("POST"))
        .setHeader("Exchange.CONTENT_TYPE", constant("application/json"))
        .setHeader("user_key", exchangeProperty("user_key"))
        .setHeader("url", exchangeProperty("webhook-url"))
        // set body of kafka message
        .setBody(exchangeProperty("notification"))

        .to("https://order-created-notification-t1-apicast-staging.apps.cluster.ocp-hamid.com?bridgeEndpoint=true&throwExceptionOnFailure=false")

        // webhook-url user_ke
        // call 3scale apicast with apikey and header name url=<<webhook endpoint>>
        .choice()
        .when(simple("${header.CamelHttpResponseCode} == '200'  || ${header.CamelHttpResponseCode} == '429'"))
        .log("here 200 or 429   , ${header.CamelHttpResponseCode}")

        .otherwise()
        .log("here send to kafka to topic for retry")
        .setBody(exchangeProperty("notification"))
        .to("kafka:order-created-event-retry")
      
      ;
    }

}
