// camel-k: language=java 
// camel-k: property-file=kafka.properties
// camel-k: dependency=camel:http
// camel-k: dependency=camel:gson
// camel-k: dependency=mvn:javax.servlet:servlet-api:jar:2.5
// camel-k: dependency=mvn:commons-logging:commons-logging:jar:1.2

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;


public class OrderEventProducer extends RouteBuilder {

  @Override
  public void configure() throws Exception {

    
     from("timer:java?period=120000")
         .to("https://raw.githubusercontent.com/Red-Hat-EMEA-SSA-AppDev-Team/webhook-delivery-system/main/Integrations/dispatcher/orders.json")
         .split().jsonpathWriteAsString("$.orders[*]")
         .log(" ${body}")
        .to("kafka:order-created-event")
        .log(" message has been sent to kafka topic");
  }
}
