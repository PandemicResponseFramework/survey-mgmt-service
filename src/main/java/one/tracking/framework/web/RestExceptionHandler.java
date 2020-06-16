/**
 *
 */
package one.tracking.framework.web;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import one.tracking.framework.exception.ConflictException;

/**
 * This is the default {@link ResponseEntityExceptionHandler} used to map {@link Exception}s to HTTP
 * responses. If this handler is being loaded by Spring it is active by default.<br/>
 * <br/>
 * You can disable this handler by setting the property:
 * <code>app.web.exceptionhandler.active=false</code>
 *
 * @author Marko Voß
 *
 */
@RestControllerAdvice
@ConditionalOnWebApplication
@ConditionalOnProperty(name = "app.web.exceptionhandler.active", havingValue = "true", matchIfMissing = true)
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(RestExceptionHandler.class);

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(final MethodArgumentNotValidException ex,
      final HttpHeaders headers, final HttpStatus status, final WebRequest request) {

    final Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", OffsetDateTime.now());
    body.put("status", status.value());

    // Get all errors
    final List<String> errors = ex.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(RestExceptionHandler::getErrorMessage)
        .collect(Collectors.toList());

    body.put("errors", errors);

    return new ResponseEntity<>(body, headers, status);
  }

  private static final String getErrorMessage(final FieldError error) {
    return "Field error in object '" + error.getObjectName() + "' on field '" + error.getField() +
        "': rejected value [" + ObjectUtils.nullSafeToString(error.getRejectedValue()) + "]; ";
  }

  @ExceptionHandler(value = {IllegalArgumentException.class, NoSuchElementException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ResponseEntity<String> badRequest(final Exception e) {

    if (!LOG.isTraceEnabled())
      LOG.debug(e.getMessage());
    else
      LOG.trace(e.getMessage(), e);

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
  }

  @ExceptionHandler(value = {ConflictException.class})
  @ResponseStatus(HttpStatus.CONFLICT)
  public ResponseEntity<String> conflictRequest(final Exception e) {

    if (!LOG.isTraceEnabled())
      LOG.debug(e.getMessage());
    else
      LOG.trace(e.getMessage(), e);

    return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
  }

  @ExceptionHandler(value = {UnsupportedOperationException.class})
  @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
  public ResponseEntity<String> notImplemented(final Exception e) {

    if (!LOG.isTraceEnabled())
      LOG.debug(e.getMessage());
    else
      LOG.trace(e.getMessage(), e);

    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(e.getMessage());
  }
}
