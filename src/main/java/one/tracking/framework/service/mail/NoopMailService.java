/**
 *
 */
package one.tracking.framework.service.mail;

import javax.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Marko Voß
 *
 */
@Slf4j
@Service
@ConditionalOnMissingBean(value = {SendGridService.class, SmtpService.class})
public class NoopMailService implements EmailService {

  @PostConstruct
  public void init() {
    log.info("Running NoopMailService!");
  }

  @Override
  public boolean sendText(final String to, final String subject, final String body) {
    log.warn("NOOP EmailService is active. No email will be sent.");
    return false;
  }

  @Override
  public boolean sendHTML(final String to, final String subject, final String body) {
    log.warn("NOOP EmailService is active. No email will be sent.");
    return false;
  }

}
