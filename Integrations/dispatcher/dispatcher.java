// camel-k: language=java property-file=kafka.properties
// camel-k: dependency=camel:gson
// camel-k: dependency=camel:jdbc
// camel-k: dependency=mvn:io.quarkus:quarkus-jdbc-postgresql
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.processor.idempotent.kafka.KafkaIdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;

public class dispatcher extends RouteBuilder {

    @Override
    public void configure() throws Exception {
     
    //KafkaIdempotentRepository kafkaIdempotentRepository = new KafkaIdempotentRepository("idempotent-orders-repo-topic", "my-cluster-kafka-bootstrap:9092");

    MemoryIdempotentRepository memoryIdempotentRepository =  new MemoryIdempotentRepository(); 
         
      from("kafka:order-created-event")
        .unmarshal().json(JsonLibrary.Gson)
        .idempotentConsumer(simple("${body[eventId]}"), memoryIdempotentRepository)    
        .log("Order created with eventID: ${body[eventId]}")
        .setBody(constant("SELECT app_id FROM  where  product_id=12"))
        .to("jdbc:default")   
        .split(body()).streaming().parallelProcessing()
        .log("${body}")
          //call 3scale api 
        //.to("http://sampleurl.com?bridgeEndpoint=true&throwExceptionOnFailure=false")
          
                 
       //.marshal().json(JsonLibrary.Gson)
        //.to("kafka:orders-filtered-topic");
    }

}
