/**
 *
 */
package one.tracking.framework.service.mail;

import javax.annotation.PostConstruct;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Marko Voß
 *
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.mail.host")
public class SmtpService implements EmailService {

  @Autowired
  private JavaMailSender emailSender;

  @Value("${app.email.reply.to:#{null}}")
  private String replyTo;

  @Value("${app.email.from:#{null}}")
  private String from;

  @Value("${app.email.enabled:true}")
  private boolean enabled;

  @PostConstruct
  public void init() {
    log.info("Initializing SmtpService");
    if (!this.enabled)
      log.info("SmtpService is disabled!");
  }

  @Override
  public boolean sendText(final String to, final String subject, final String body) {

    final SimpleMailMessage message = new SimpleMailMessage();

    if (this.from != null)
      message.setFrom(this.from);
    if (this.replyTo != null)
      message.setReplyTo(this.replyTo);

    message.setTo(to);
    message.setSubject(subject);
    message.setText(body);

    try {
      this.emailSender.send(message);
      return true;

    } catch (final MailException e) {
      log.error(e.getMessage(), e);
      return false;
    }
  }

  @Override
  public boolean sendHTML(final String to, final String subject, final String body) {

    final MimeMessage message = this.emailSender.createMimeMessage();

    try {

      final MimeMessageHelper helper = new MimeMessageHelper(message, true);

      if (this.from != null)
        helper.setFrom(this.from);
      if (this.replyTo != null)
        helper.setReplyTo(this.replyTo);

      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(body, true);

      this.emailSender.send(message);
      return true;

    } catch (final MessagingException | MailException e) {
      log.error(e.getMessage(), e);
      return false;
    }
  }
}
