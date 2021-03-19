/**
 *
 */
package one.tracking.framework.service.mail;

import java.io.IOException;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Marko Voß
 *
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.sendgrid.api.key")
public class SendGridService implements EmailService {

  @Autowired
  private SendGrid sendGridClient;

  @Value("${app.email.reply.to:#{null}}")
  private String replyTo;

  @Value("${app.email.from}")
  private String from;

  @Value("${app.email.enabled:true}")
  private boolean enabled;

  @PostConstruct
  public void init() {
    log.info("Initializing SendGridService");
    if (!this.enabled)
      log.info("SendGridService is disabled!");
  }

  @Override
  public boolean sendText(final String to, final String subject, final String body) {
    return sendEmailType("text/plain", to, subject, body);
  }

  @Override
  public boolean sendHTML(final String to, final String subject, final String body) {
    return sendEmailType("text/html", to, subject, body);
  }

  private boolean sendEmailType(final String type, final String to, final String subject, final String body) {

    if (!this.enabled) {
      log.debug("Service is disabled. Skipping sending email");
      return false;
    }

    Response response;
    try {
      response = sendEmail(to, subject, new Content(type, body));

      log.debug("Email response: Status code: {}, Body: {}, Headers: {}",
          response.getStatusCode(),
          response.getBody(),
          response.getHeaders());

      return response.getStatusCode() >= 200 && response.getStatusCode() < 300;

    } catch (final IOException e) {
      log.error(e.getMessage(), e);
      return false;
    }
  }

  private Response sendEmail(final String to, final String subject, final Content content) throws IOException {

    final Mail mail = new Mail(new Email(this.from), subject, new Email(to), content);

    if (this.replyTo != null)
      mail.setReplyTo(new Email(this.replyTo));

    final Request request = new Request();

    request.setMethod(Method.POST);
    request.setEndpoint("mail/send");
    request.setBody(mail.build());
    return this.sendGridClient.api(request);
  }

}
