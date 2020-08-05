/**
 *
 */
package one.tracking.framework.service;

import java.io.IOException;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
public class SendGridService {

  @Value("${app.email.reply.to}")
  private String replyTo;

  @Value("${app.email.from}")
  private String from;

  @Value("${app.email.enabled:true}")
  private boolean enabled;

  @Autowired
  private SendGrid sendGridClient;

  @PostConstruct
  public void init() {
    if (!this.enabled)
      log.info("SendGrid service is disabled!");
  }

  public boolean sendText(final String to, final String subject, final String body) throws IOException {
    return sendEmailType("text/plain", to, subject, body);
  }

  public boolean sendHTML(final String to, final String subject, final String body) throws IOException {
    return sendEmailType("text/html", to, subject, body);
  }

  private boolean sendEmailType(final String type, final String to, final String subject,
      final String body) throws IOException {

    if (!this.enabled) {
      log.debug("Service is disabled. Skipping sending email");
      return false;
    }

    final Response response = sendEmail(to, subject, new Content(type, body));
    log.debug("Email response: Status code: {}, Body: {}, Headers: {}",
        response.getStatusCode(),
        response.getBody(),
        response.getHeaders());

    return response.getStatusCode() >= 200 && response.getStatusCode() < 300;
  }

  private Response sendEmail(final String to, final String subject, final Content content)
      throws IOException {

    final Mail mail = new Mail(new Email(this.from), subject, new Email(to), content);
    mail.setReplyTo(new Email(this.replyTo));

    final Request request = new Request();

    request.setMethod(Method.POST);
    request.setEndpoint("mail/send");
    request.setBody(mail.build());
    return this.sendGridClient.api(request);
  }

}
