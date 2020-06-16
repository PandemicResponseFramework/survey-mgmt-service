/**
 *
 */
package one.tracking.framework.web;

import java.io.IOException;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import one.tracking.framework.dto.TokenResponseDto;
import one.tracking.framework.dto.VerificationDto;
import one.tracking.framework.service.AuthService;

/**
 * @author Marko Voß
 *
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

  @Autowired
  private AuthService authService;

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/verify")
  public TokenResponseDto verify(
      @RequestBody
      @Valid
      final VerificationDto verification) throws IOException {

    return TokenResponseDto.builder().token(this.authService.verifyEmail(verification)).build();
  }

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/verify",
      produces = MediaType.TEXT_HTML_VALUE)
  public String handleVerification(
      @RequestParam(name = "token", required = true)
      final String verificationToken,
      @RequestParam(name = "userToken", required = false)
      final String userToken) {

    return this.authService.handleVerificationRequest(verificationToken, userToken);
  }
}
