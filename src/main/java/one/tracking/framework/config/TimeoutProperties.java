/**
 *
 */
package one.tracking.framework.config;

import java.time.Duration;
import javax.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.validation.annotation.Validated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * @author Marko Voß
 *
 */
@Getter
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@ConfigurationProperties(prefix = "app.timeout")
@ConstructorBinding
@Validated
public class TimeoutProperties {

  @NotNull
  private final Duration taskLock;

  @NotNull
  private final Duration verification;

  @NotNull
  private final Duration access;

  @NotNull
  private final Duration upload;

}
