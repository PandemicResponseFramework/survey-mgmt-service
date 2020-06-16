/**
 *
 */
package one.tracking.framework.domain;

/**
 * @author Marko Voß
 *
 */
public enum NotificationParameter {

  SOUND("default"),
  COLOR("#FFFF00");

  private String value;

  NotificationParameter(final String value) {
    this.value = value;
  }

  public String getValue() {
    return this.value;
  }
}
