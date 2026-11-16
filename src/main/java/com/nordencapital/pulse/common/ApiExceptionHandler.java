package com.nordencapital.pulse.common;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns every error into the same JSON shape ({@link ApiError}).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> handleApiException(ApiException ex) {
		return ResponseEntity.status(ex.getStatus()).body(new ApiError(ex.getCode(), ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map((error) -> error.getField() + ": " + error.getDefaultMessage())
			.sorted()
			.findFirst()
			.orElse("Invalid request");
		return ResponseEntity.badRequest().body(new ApiError("validation_error", message));
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	ResponseEntity<ApiError> handleBadRequest(Exception ex) {
		return ResponseEntity.badRequest().body(new ApiError("bad_request", "The request is not valid"));
	}

	/**
	 * Spring MVC errors that already carry a status (404 no such path, 405 wrong method,
	 * 415 wrong content type, ...) keep it. Anything else is a bug: log it, answer 500
	 * without details.
	 */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleOther(Exception ex) {
		if (ex instanceof ErrorResponse errorResponse) {
			HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
			if (status != null) {
				return ResponseEntity.status(status)
					.body(new ApiError(status.name().toLowerCase(Locale.ROOT), status.getReasonPhrase()));
			}
		}
		log.error("Unhandled error", ex);
		return ResponseEntity.internalServerError()
			.body(new ApiError("internal_error", "Something went wrong"));
	}

}
