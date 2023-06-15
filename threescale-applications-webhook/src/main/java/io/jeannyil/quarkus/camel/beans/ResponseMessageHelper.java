package io.jeannyil.quarkus.camel.beans;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;

import io.jeannyil.quarkus.camel.models.ErrorMessageType;
import io.jeannyil.quarkus.camel.models.ResponseMessage;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * 
 * Response Message helper bean 
 *
 */
@ApplicationScoped
@Named("responseMessageHelper")
@RegisterForReflection // Lets Quarkus register this class for reflection during the native build
public class ResponseMessageHelper {
	
	/**
	 * Generates an OK ResponseMessage
	 * @return OK ResponseMessage
	 */
	public ResponseMessage generateOKResponseMessage() {
		ResponseMessage responseMessage = new ResponseMessage();
		responseMessage.setStatus(ResponseMessage.Status.OK);
		return responseMessage;
	}
	
	/**
	 * Generates a KO ResponseMessage
	 * @param erroCode
	 * @param errorDescription
	 * @param errorMessage
	 * @return KO ResponseMessage
	 */
	public ResponseMessage generateKOResponseMessage(String errorCode, String errorDescription, String errorMessage) {
		ResponseMessage responseMessage = new ResponseMessage();
		ErrorMessageType errorMessageType = new ErrorMessageType();
		errorMessageType.setCode(errorCode);
		errorMessageType.setDescription(errorDescription);
		errorMessageType.setMessage(errorMessage);

		responseMessage.setStatus(ResponseMessage.Status.KO);
		responseMessage.setError(errorMessageType);
		
		return responseMessage;
	}

}
