package one.tracking.framework.filter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import one.tracking.framework.support.JWTHelper;

public abstract class BearerAuthenticationFilter extends BasicAuthenticationFilter {

  private static final Logger LOG = LoggerFactory.getLogger(BearerAuthenticationFilter.class);

  private static final String PREFIX_BEARER = "Bearer ";

  private final JWTHelper jwtHelper;

  public BearerAuthenticationFilter(final AuthenticationManager authManager, final JWTHelper jwtHelper) {
    super(authManager);
    this.jwtHelper = jwtHelper;
  }

  @Override
  protected void doFilterInternal(final HttpServletRequest req, final HttpServletResponse res, final FilterChain chain)
      throws IOException, ServletException {

    final String header = req.getHeader(HttpHeaders.AUTHORIZATION);

    if (header == null || !header.startsWith(PREFIX_BEARER)) {
      chain.doFilter(req, res);
      return;
    }

    try {
      final UsernamePasswordAuthenticationToken authentication = getAuthentication(header);
      SecurityContextHolder.getContext().setAuthentication(authentication);

    } catch (final JwtException e) {

      if (LOG.isDebugEnabled())
        LOG.debug(e.getMessage(), e);
      else
        LOG.warn(e.getMessage());

    } finally {
      chain.doFilter(req, res);
    }
  }

  /**
   *
   * @param request
   * @return
   */
  private UsernamePasswordAuthenticationToken getAuthentication(final String authHeader) {

    if (authHeader == null)
      return null;

    final String bearerToken = authHeader.replace(PREFIX_BEARER, "");

    final Claims claims = this.jwtHelper.decodeJWT(bearerToken);
    final String userId = claims.getSubject();

    if (userId == null)
      return null;

    if (!checkIfUserExists(userId))
      return null;

    @SuppressWarnings("unchecked")
    final List<String> roles = claims.get("scopes", List.class);
    final List<GrantedAuthority> authorities = roles == null ? Collections.emptyList()
        : roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());

    return new UsernamePasswordAuthenticationToken(userId, null, authorities);
  }

  protected abstract boolean checkIfUserExists(String userId);

}
