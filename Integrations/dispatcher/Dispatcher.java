
// camel-k: language=java property-file=kafka.properties
// camel-k: dependency=camel:gson
// camel-k: dependency=camel:jdbc
// camel-k: dependency=mvn:io.quarkus:quarkus-jdbc-postgresql
// camel-k: trait=tracing.endpoint=http://jaeger-all-in-one-inmemory-collector.webhook-delivery-system.svc:14268/api/traces
// camel-k: trait=keda.enabled=true
// camel-k: trait=keda.polling-interval=1
// camel-k: trait=keda.cooldown-period=200
// camel-k: trait=keda.min-replica-count=1
// camel-k: trait=keda.max-replica-count=10
// camel-k: trait=keda.triggers[0].type=kafka
// camel-k: dependency=camel:infinispan
// camel-k: resource=secret:dispatcher-truststore-secret@/mnt/ssl


// camel-k: trait=keda.triggers[0].metadata.topic=order-created-event
// camel-k: trait=keda.triggers[0].metadata.bootstrapServers=my-cluster-kafka-bootstrap.kafka.svc.cluster.local:9092
// camel-k: trait=keda.triggers[0].metadata.consumerGroup=order-webhook-delivery



import org.apache.camel.component.infinispan.remote.InfinispanRemoteIdempotentRepository;
import org.apache.camel.component.infinispan.remote.InfinispanRemoteConfiguration;
import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.impl.ConfigurationProperties;
import org.infinispan.client.hotrod.RemoteCacheManager; 
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
//import org.apache.camel.processor.idempotent.kafka.KafkaIdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;

public class Dispatcher extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.addServer()
          .host("datagrid-cluster.webhook-delivery-system.svc")
          .port(ConfigurationProperties.DEFAULT_HOTROD_PORT)
        .security()
          .ssl()
            .trustStoreFileName("/mnt/ssl/truststore.p12")
            .trustStorePassword("P@ssw0rd".toCharArray())
          .authentication()
            .username("cameluser")
            .password("P@ssw0rd")
            .realm("default")
            .saslMechanism("PLAIN")
            .clientIntelligence(ClientIntelligence.HASH_DISTRIBUTION_AWARE);
      
      RemoteCacheManager remoteCacheManager = new RemoteCacheManager(builder.build());
      
      // MemoryIdempotentRepository memoryIdempotentRepository = new MemoryIdempotentRepository();
      InfinispanRemoteIdempotentRepository infinispanRemoteIdempotentRepository = new InfinispanRemoteIdempotentRepository("idempotency-replicated-cache");
      infinispanRemoteIdempotentRepository.setCacheContainer(remoteCacheManager);
       

        //MemoryIdempotentRepository memoryIdempotentRepository = new MemoryIdempotentRepository();

        from("kafka:order-created-event?groupId=order-webhook-delivery")
                .setProperty("notification", simple("${body}"))
                .unmarshal().json(JsonLibrary.Gson)
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
