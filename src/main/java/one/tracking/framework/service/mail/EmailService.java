/**
 *
 */
package one.tracking.framework.service.mail;

/**
 * @author Marko Voß
 *
 */
public interface EmailService {

  boolean sendText(final String to, final String subject, final String body);

  boolean sendHTML(final String to, final String subject, final String body);
}
