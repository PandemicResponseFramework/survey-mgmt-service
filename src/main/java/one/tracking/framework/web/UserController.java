/**
 *
 */
package one.tracking.framework.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Parameter;
import one.tracking.framework.dto.UserProfileDto;

/**
 * @author Marko Voß
 *
 */
@RestController
@RequestMapping("/user")
public class UserController {

  @RequestMapping(method = RequestMethod.GET)
  public UserProfileDto getUser(
      @Parameter(hidden = true)
      final Authentication authentication) {

    if (authentication.getPrincipal() instanceof OidcUser) {

      final OidcUser user = (OidcUser) authentication.getPrincipal();

      return UserProfileDto.builder()
          .username(user.getName())
          .build();

    } else {

      return UserProfileDto.builder()
          .username(authentication.getName())
          .build();
    }
  }
}
