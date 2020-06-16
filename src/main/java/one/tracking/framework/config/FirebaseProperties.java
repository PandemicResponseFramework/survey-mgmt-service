/**
 *
 */
package one.tracking.framework.config;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.validation.annotation.Validated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import one.tracking.framework.service.FirebaseService;

/**
 * Immutable configuration for {@link FirebaseService} setup.
 *
 * @author Marko Voß
 *
 */
@Getter
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@ConfigurationProperties(prefix = "app.fcm")
@ConstructorBinding
@Validated
public class FirebaseProperties {

  private final String configFile;

  private final String configJson;

  @NotNull
  @Min(50)
  @Max(500)
  private final Integer batchSize;
}
