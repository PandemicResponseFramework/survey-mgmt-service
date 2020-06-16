/**
 *
 */
package one.tracking.framework.service;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * @author Marko Voß
 *
 */
@Service
public class SendGridService {

  private static final Logger LOG = LoggerFactory.getLogger(SendGridService.class);

  @Value("${app.email.reply.to}")
  private String replyTo;

  @Value("${app.email.from}")
  private String from;

  @Autowired
  private SendGrid sendGridClient;

  public boolean sendText(final String to, final String subject, final String body) throws IOException {
    return sendEmailType("text/plain", to, subject, body);
  }

  public boolean sendHTML(final String to, final String subject, final String body) throws IOException {
    return sendEmailType("text/html", to, subject, body);
  }

  private boolean sendEmailType(final String type, final String to, final String subject,
      final String body) throws IOException {

    final Response response = sendEmail(to, subject, new Content(type, body));
    LOG.debug("Email response: Status code: {}, Body: {}, Headers: {}",
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
