// camel-k: language=java
// camel-k: name=consumer-webhook-receiver
// camel-k: dependency=camel:camel-quarkus-direct
// camel-k: resource=file:./resources/api/openapi.json
// camel-k: trait=openapi.enabled=true trait=openapi.configmaps=consumer-webhook-receiver-oas
// camel-k: trait=prometheus.enabled=true trait=tracing.enabled=true
// camel-k: trait=tracing.endpoint=http://jaeger-all-in-one-inmemory-collector.webhook-delivery-system.svc:14268/api/traces
// camel-k: property=api.resources.path=file:/etc/camel/resources

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;

public class ConsumerWebhookReceiver extends RouteBuilder {

  private static String logName = ConsumerWebhookReceiver.class.getName();

  public static final String DIRECT_GENERATE_ERROR_MESSAGE_ENDPOINT = "direct:generateErrorResponse";
	public static final String DIRECT_LOG_EVENT = "direct:logEvent";
	public static final String DIRECT_PING_WEBHOOK_ENDPOINT = "direct:pingWebhook";
  
  @Override
  public void configure() throws Exception {

    /**
		 * Catch unexpected exceptions
		 */
		onException(java.lang.Exception.class)
      .handled(true)
      .maximumRedeliveries(0)
      .log(LoggingLevel.ERROR, logName, ">>> ${routeId} - Caught exception: ${exception.stacktrace}")
      .to(DIRECT_GENERATE_ERROR_MESSAGE_ENDPOINT)
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - OUT: headers:[${headers}] - body:[${body}]")
    ;

    /**
		 * REST endpoint for the Service OpenAPI document 
		 */
		rest()
			.produces(MediaType.APPLICATION_JSON)
			.get("/openapi.json")
				.id("openapi-route")
				.description("Gets the OpenAPI specification for this service in JSON format")
				.to("direct:getOAS")
	  	;

    // Returns the OAS
		from("direct:getOAS")
			.routeId("get-oas-route")
			.log(LoggingLevel.INFO, logName, ">>> IN: headers:[${headers}] - body:[${body}]")
			.setHeader(Exchange.CONTENT_TYPE, constant("application/vnd.oai.openapi+json"))
			.setBody()
        .constant("resource:{{api.resources.path}}/openapi.json")
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - OUT: headers:[${headers}] - body:[${body}]")
		;
  
    
    // Route that handles the webhook ping
    from(DIRECT_PING_WEBHOOK_ENDPOINT)
			.routeId("ping-webhook-route")
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - Received a ping event: in.headers[${headers}] - in.body[${body}]")
			.setBody(constant("{\n" + //
          "    \"status\": \"OK\"\n" + //
          "}"))
      .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON))
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - pingWebhook response: headers:[${headers}] - body:[${body}]")
		;

    // Route that sends RHOAM Admin/Developer Portal webhook event to an AMQP broker 
    from(DIRECT_LOG_EVENT)
      .routeId("log-event-route")
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - Received event: in.body[${body}]")
      .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON))
      .setBody(constant("{\n" + //
          "    \"status\": \"OK\"\n" + //
          "}"))
    ;

    /**
		 * Route that returns the error response message in JSON format
		 * The following properties are expected to be set on the incoming Camel Exchange Message if customization is needed:
		 * <br>- CamelHttpResponseCode ({@link org.apache.camel.Exchange#HTTP_RESPONSE_CODE})
		 * <br>- CamelHttpResponseText ({@link org.apache.camel.Exchange#HTTP_RESPONSE_TEXT})
		 */
		from(DIRECT_GENERATE_ERROR_MESSAGE_ENDPOINT)
      .routeId("generate-error-response-route")
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - IN: headers:[${headers}] - body:[${body}]")
      .filter(simple("${in.header.CamelHttpResponseCode} == null")) // Defaults to 500 HTTP Code
        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()))
        .setHeader(Exchange.HTTP_RESPONSE_TEXT, constant(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase()))
      .end() // end filter
      .setHeader("errorMessage", simple("${exception}"))
      // Generate the error response message
      .setBody(constant("{\n" + //
          "    \"status\": \"KO\",\n" + //
          "    \"error\": {\n" + //
          "        \"code\": \"${header.CamelHttpResponseCode}\",\n" + //
          "        \"description\": \"${header.CamelHttpResponseText}\",\n" + //
          "        \"message\": \"${exception}" + //
          "    }\n" + //
          "}"))
      .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON))
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - OUT: headers:[${headers}] - body:[${body}]")
    ;

  }

}