package io.jeannyil.quarkus.camel.routes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import io.jeannyil.quarkus.camel.constants.DirectEndpointConstants;

/* Route that handles the webhook ping

/!\ The @ApplicationScoped annotation is required for @Inject and @ConfigProperty to work in a RouteBuilder. 
	Note that the @ApplicationScoped beans are managed by the CDI container and their life cycle is thus a bit 
	more complex than the one of the plain RouteBuilder. 
	In other words, using @ApplicationScoped in RouteBuilder comes with some boot time penalty and you should 
	therefore only annotate your RouteBuilder with @ApplicationScoped when you really need it. */
public class WebhookPingRoute extends RouteBuilder {

	private static String logName = WebhookPingRoute.class.getName();

    @Override
    public void configure() throws Exception {

		/**
		 * Catch unexpected exceptions
		 */
		onException(Exception.class)
            .handled(true)
            .maximumRedeliveries(0)
            .log(LoggingLevel.ERROR, logName, ">>> ${routeId} - Caught exception: ${exception.stacktrace}")
            .to(DirectEndpointConstants.DIRECT_GENERATE_ERROR_MESSAGE)
            .log(LoggingLevel.INFO, logName, ">>> ${routeId} - OUT: headers:[${headers}] - body:[${body}]")
        ;
        
        from(DirectEndpointConstants.DIRECT_PING_WEBHOOK)
			.routeId("ping-webhook-route")
			.log(LoggingLevel.INFO, logName, ">>> ${routeId} - pingWebhook request: in.headers[${headers}] - in.body[${body}]")
			.setBody()
				.method("responseMessageHelper", "generateOKResponseMessage()")
            .end()
            .marshal().json(JsonLibrary.Jackson, true)
			.log(LoggingLevel.INFO, logName, ">>> ${routeId} - pingWebhook response: headers:[${headers}] - body:[${body}]")
		;

    }

}
