/**
 *
 */
package one.tracking.framework.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * @author Marko Voß
 *
 */
@ControllerAdvice
public class MyAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final List<String> excludedPaths;

  public MyAuthenticationEntryPoint(final String... excludedPaths) {
    this.excludedPaths = Arrays.asList(excludedPaths);
  }

  @Override
  public void commence(final HttpServletRequest request, final HttpServletResponse response,
      final AuthenticationException authException)
      throws IOException, ServletException {

    if (isIncluded(request))
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication Failed");
  }

  @ExceptionHandler(value = {AccessDeniedException.class})
  public void commence(final HttpServletRequest request, final HttpServletResponse response,
      final AccessDeniedException accessDeniedException) throws IOException {

    if (isIncluded(request))
      response.sendError(HttpServletResponse.SC_FORBIDDEN,
          "Authorization Failed : " + accessDeniedException.getMessage());
  }

  @ExceptionHandler(value = {Exception.class})
  public void commence(final HttpServletRequest request, final HttpServletResponse response,
      final Exception exception) throws IOException {

    if (isIncluded(request))
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Internal Server Error : " + exception.getMessage());
  }

  private boolean isIncluded(final HttpServletRequest request) {
    final String path = request.getRequestURI().substring(request.getContextPath().length());
    return this.excludedPaths.stream().noneMatch(p -> p.equalsIgnoreCase(path));
  }
}
