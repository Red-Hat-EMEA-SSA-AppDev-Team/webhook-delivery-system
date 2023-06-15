// camel-k: language=java property-file=kafka.properties
// camel-k: dependency=camel:gson
// camel-k: dependency=camel:jdbc
// camel-k: dependency=mvn:io.quarkus:quarkus-jdbc-postgresql
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
//import org.apache.camel.processor.idempotent.kafka.KafkaIdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;

public class dispatcher extends RouteBuilder {

    @Override
    public void configure() throws Exception {
     
    //KafkaIdempotentRepository kafkaIdempotentRepository = new KafkaIdempotentRepository("idempotent-orders-repo-topic", "my-cluster-kafka-bootstrap:9092");

    MemoryIdempotentRepository memoryIdempotentRepository =  new MemoryIdempotentRepository(); 
         
        from("kafka:order-created-event")
        .setProperty("notification", simple("${body}"))
        .unmarshal().json(JsonLibrary.Gson)
        .idempotentConsumer(simple("${body[eventId]}"), memoryIdempotentRepository)    
        .log("Order created with eventID: ${body[eventId]}")
        .setBody(constant("select app_id from  wh_registration where  product_id=130"))
        .to("jdbc:default")   
        .split(body()).streaming().parallelProcessing()
        .log("${body}")
        .removeHeaders("*")
                .setHeader("Exchange.HTTP_METHOD", constant("GET"))
                .setHeader("Exchange.CONTENT_TYPE", constant("application/json"))
                .setHeader("Exchange.HTTP_QUERY", simple("access_token=d6e6ce0d9c11d1ed93b35cb0674da280f58d52ac7b86ece632c7d9e093f1858d"))
                .setHeader("Exchange.HTTP_QUERY", simple("application_id=427"))
                //.setHeader("Exchange.HTTP_QUERY", simple("customer_id=${exchangeProperty.customer_id}"))
        
        .to("https://t1-admin.apps.cluster.ocp-hamid.com/admin/api/applications/find.json?bridgeEndpoint=true&throwExceptionOnFailure=false")
        .choice()
        .when(header(Exchange.HTTP_RESPONSE_CODE).isEqualTo("200"))
        .unmarshal().json(JsonLibrary.Gson)
        .log("user_key=${body[user_key]} webhook-url=${body[webhook-url]}")
        .setProperty("user_key",simple("${body[user_key]}"))
        .setProperty("webhook-url",simple("${body[webhook-url]}"))
        .removeHeaders("*")
        .setHeader("Exchange.HTTP_METHOD", constant("POST"))
        .setHeader("Exchange.CONTENT_TYPE", constant("application/json"))
        .setHeader("user_key", exchangeProperty("user_key"))
        .setHeader("url", exchangeProperty("webhook-url"))
        //set body of kafka message
        .setBody(exchangeProperty("notification"))
        
        .to("https://order-created-notification-t1-apicast-staging.apps.cluster.ocp-hamid.com?bridgeEndpoint=true&throwExceptionOnFailure=false")

        //webhook-url user_ke
        //call 3scale apicast with apikey and header name url=<<webhook endpoint>>
        .choice()
        .when(header(Exchange.HTTP_RESPONSE_CODE).isEqualTo("200"))
         //.to("")       

         
          
                 
        ;
    }

}
