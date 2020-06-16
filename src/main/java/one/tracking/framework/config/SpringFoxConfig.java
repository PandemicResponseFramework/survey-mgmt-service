/**
 *
 */
package one.tracking.framework.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import com.fasterxml.classmate.TypeResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import one.tracking.framework.web.SurveyManagementController;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * @author Marko Voß
 *
 */
@Configuration
@EnableSwagger2
public class SpringFoxConfig {

  @Value("${app.name:#{null}}")
  private String name;

  @Value("${app.description:#{null}}")
  private String description;

  @Value("${app.version:#{null}}")
  private String version;

  @Bean
  @Primary
  public Docket api(final TypeResolver typeResolver) {
    return new Docket(DocumentationType.SWAGGER_2)
        .select()
        .apis(RequestHandlerSelectors.basePackage(SurveyManagementController.class.getPackageName()))
        .paths(PathSelectors.any())
        .build()
        .apiInfo(new ApiInfoBuilder()
            .title(this.name)
            .version(this.version)
            .description(this.description)
            .license("MIT")
            .build());
  }

  @Bean
  public OpenAPI customOpenAPI() {

    return new OpenAPI()
        .info(new Info()
            .title(this.name)
            .description(this.description)
            .version(this.version)
            .license(new License().name("MIT")));

  }


}
