/**
 *
 */
package one.tracking.framework.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * @author Marko Voß
 *
 */
@Configuration
public class OpenAPIConfig {

  @Value("${app.name:#{null}}")
  private String name;

  @Value("${app.description:#{null}}")
  private String description;

  @Value("${app.version:#{null}}")
  private String version;

  @Bean
  public OpenAPI springShopOpenAPI() {
    return new OpenAPI()
        .info(new Info().title(this.name)
            .description(this.description)
            .version(this.version)
            .license(new License().name("MIT")));
  }
}
