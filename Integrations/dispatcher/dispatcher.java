// camel-k: language=java property-file=kafka.properties
// camel-k: dependency=camel:gson

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.processor.idempotent.kafka.KafkaIdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;

public class IdempotentOrderConsumer extends RouteBuilder {

    @Override
    public void configure() throws Exception {
     
    //KafkaIdempotentRepository kafkaIdempotentRepository = new KafkaIdempotentRepository("idempotent-orders-repo-topic", "my-cluster-kafka-bootstrap:9092");

    MemoryIdempotentRepository memoryIdempotentRepository =  new MemoryIdempotentRepository(); 
         
        from("kafka:orders-duplicate-topic")
        .unmarshal().json(JsonLibrary.Gson)
        .idempotentConsumer(simple("${body[orderId]}"), memoryIdempotentRepository)    
        .log("Order Sent: ${body[orderId]}")
        .marshal().json(JsonLibrary.Gson)
        .to("kafka:orders-filtered-topic");
    }

}
