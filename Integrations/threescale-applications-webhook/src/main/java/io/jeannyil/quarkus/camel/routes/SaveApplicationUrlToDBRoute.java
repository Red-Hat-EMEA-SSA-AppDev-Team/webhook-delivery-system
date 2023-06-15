package io.jeannyil.quarkus.camel.routes;

import javax.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import io.jeannyil.quarkus.camel.constants.DirectEndpointConstants;

/* Route that handles a 3scale Admin/Developer Portal application webhook event and saves its webhook-url value in a database table.

/!\ The @ApplicationScoped annotation is required for @Inject and @ConfigProperty to work in a RouteBuilder. 
	Note that the @ApplicationScoped beans are managed by the CDI container and their life cycle is thus a bit 
	more complex than the one of the plain RouteBuilder. 
	In other words, using @ApplicationScoped in RouteBuilder comes with some boot time penalty and you should 
	therefore only annotate your RouteBuilder with @ApplicationScoped when you really need it. */
public class SaveApplicationUrlToDBRoute extends RouteBuilder {

    private static String logName = SaveApplicationUrlToDBRoute.class.getName();

    @Override
    public void configure() throws Exception {

        /**
		 * Catch unexpected exceptions
		 */
		onException(Exception.class)
            .handled(true)
            .maximumRedeliveries(0)
            .log(LoggingLevel.ERROR, logName, ">>> ${routeId} - Caught exception: ${exception.stacktrace}")
            .to(DirectEndpointConstants.DIRECT_GENERATE_ERROR_MESSAGE).id("generate-sendToAMQPQueue-500-errorresponse")
            .log(LoggingLevel.INFO, logName, ">>> ${routeId} - OUT: headers:[${headers}] - body:[${body}]")
        ;

        /**
		 * Catch unexpected exceptions
		 */
		onException(TypeConversionException.class)
            .handled(true)
            .maximumRedeliveries(0)
            .log(LoggingLevel.ERROR, logName, ">>> ${routeId} - Caught TypeConversionException: ${exception.stacktrace}")
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(Response.Status.BAD_REQUEST.getStatusCode())) // 400 Http Code
            .setProperty(Exchange.HTTP_RESPONSE_TEXT, constant(Response.Status.BAD_REQUEST.getReasonPhrase())) // 400 Http Code Text
            .to(DirectEndpointConstants.DIRECT_GENERATE_ERROR_MESSAGE)
            .log(LoggingLevel.INFO, logName, ">>> ${routeId} - OUT: headers:[${headers}] - body:[${body}]")
        ;
        
        from(DirectEndpointConstants.DIRECT_SAVE_APPLICATION_URL_TO_DB)
            .routeId("save-applicationurl-todb-route")
            .log(LoggingLevel.INFO, logName, ">>> ${routeId} - 3scale Admin/Developer Portal received event: in.headers[${headers}] - in.body[${body}]")
            .removeHeaders("*", "breadcrumbId")
            .setHeader("3SCALE_EVENT_TYPE").xpath("//event/type", String.class)
            .setHeader("3SCALE_EVENT_ACTION").xpath("//event/action", String.class)
            .choice()
                .when(simple("${header.3SCALE_EVENT_TYPE} contains 'application' && ${header.3SCALE_EVENT_ACTION} contains 'created'"))
                    .log(LoggingLevel.INFO, logName, ">>> ${routeId} - Saving event to database...")
                    .setHeader("appId").xpath("//event/object/application/id", Integer.class)
                    .setHeader("productId").xpath("//event/object/application/service_id", Integer.class)
                    .setHeader("consumerWebhookUrl").xpath("//event/object/application/extra_fields/webhook-url", String.class)
                    .setBody(simple("insert into wh_registration(app_id, product_id, wh_receiver_url) values(:?appId, :?productId, :?consumerWebhookUrl)"))
                    .to("jdbc:default?useHeadersAsParameters=true")
                    .log(LoggingLevel.INFO, logName, ">>> ${routeId} - DB operation output: [${headers}]")
                .otherwise()
                    .log(LoggingLevel.INFO, logName, ">>> ${routeId} - The event [type: ${header.3SCALE_EVENT_TYPE}, action; ${header.3SCALE_EVENT_ACTION}] is ignored.")
                .endChoice()
            .end()
            .setBody()
				.method("responseMessageHelper", "generateOKResponseMessage()")
				.id("set-OK-reponseMessage")
            .end()
            .marshal().json(JsonLibrary.Jackson, true)
            .log(LoggingLevel.INFO, logName, ">>> ${routeId} - saveApplicationUrlToDB response: headers:[${headers}] - body:[${body}]")
        ;

    }

}
