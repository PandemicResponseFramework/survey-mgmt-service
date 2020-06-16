/**
 *
 */
package one.tracking.framework.domain;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * @author Marko Voß
 *
 */
@Data
@Builder
public class PushNotificationRequest {

  private String title;
  private String message;
  private String group;
  private Map<String, String> data;
}
