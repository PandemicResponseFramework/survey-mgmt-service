/**
 *
 */
package one.tracking.framework.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * @author Marko Voß
 *
 */
@TestConfiguration
public class ITConfiguration {

  @Bean
  public HelperBean healperBean() {
    return new HelperBean();
  }
}
