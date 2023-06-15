package io.jeannyil.quarkus.camel.routes;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;

import io.jeannyil.quarkus.camel.constants.DirectEndpointConstants;
import io.jeannyil.quarkus.camel.models.ResponseMessage;


/* Exposes the threescale Webhook Events Handler API

/!\ The @ApplicationScoped annotation is required for @Inject and @ConfigProperty to work in a RouteBuilder. 
	Note that the @ApplicationScoped beans are managed by the CDI container and their life cycle is thus a bit 
	more complex than the one of the plain RouteBuilder. 
	In other words, using @ApplicationScoped in RouteBuilder comes with some boot time penalty and you should 
	therefore only annotate your RouteBuilder with @ApplicationScoped when you really need it. */
@ApplicationScoped
public class WebhookEventsHandlerApiRoute extends RouteBuilder {

	private static String logName = WebhookEventsHandlerApiRoute.class.getName();

	@Inject
	CamelContext camelctx;
	
	@Override
	public void configure() throws Exception {

		// Enable Stream caching
        camelctx.setStreamCaching(true);
        // Enable use of breadcrumbId
        camelctx.setUseBreadcrumb(true);
		
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
		
		/**
		 * REST configuration with Camel Quarkus Platform HTTP component
		 */
		restConfiguration()
			.component("platform-http")
			.enableCORS(true)
			.bindingMode(RestBindingMode.off) // RESTful responses will be explicitly marshaled for logging purposes
			.dataFormatProperty("prettyPrint", "true")
			.scheme("http")
			.host("0.0.0.0")
			.port("8080")
			.contextPath("/")
			.clientRequestValidation(true)
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
			.setBody().constant("resource:classpath:openapi/openapi.json")
			.log(LoggingLevel.INFO, logName, ">>> OUT: headers:[${headers}] - body:[${body}]")
		;
		
		/**
		 * REST endpoint for the Threescale Webhook Events Handler API
		 */
		rest("/webhook/applicationurl").id("threescale-webhook-events-handler-api")
				
			// Handles 3scale webhook ping
			.get()
				.id("webhook-ping-route")
				.description("Handles threescale webhook ping")
				.produces(MediaType.APPLICATION_JSON)
				.responseMessage()
					.code(Response.Status.OK.getStatusCode())
					.message(Response.Status.OK.getReasonPhrase())
					.responseModel(ResponseMessage.class)
				.endResponseMessage()
				// Call the WebhookPingRoute
				.to(DirectEndpointConstants.DIRECT_PING_WEBHOOK)
			
			// Handles a 3scale Admin/Developer Portal application webhook event and saves its webhook-url value in a database table
			.post()
				.id("webhook-events-handler-route")
				.consumes(MediaType.WILDCARD)
				.produces(MediaType.APPLICATION_JSON)
				.description("Handles a 3scale Admin/Developer Portal application webhook event and saves its webhook-url value in a database table")
				.param()
					.name("body")
					.type(RestParamType.body)
					.description("3scale Admin/Developer Portal XML event")
					.dataType("string")
					.required(true)
				.endParam()
				.responseMessage()
					.code(Response.Status.OK.getStatusCode())
					.message(Response.Status.OK.getReasonPhrase())
					.responseModel(ResponseMessage.class)
				.endResponseMessage()
				.responseMessage()
					.code(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())
					.message(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())
					.responseModel(ResponseMessage.class)
				.endResponseMessage()
				// call the SaveApplicationUrlToDBRoute
				.to(DirectEndpointConstants.DIRECT_SAVE_APPLICATION_URL_TO_DB)

		;
			
	}

}
