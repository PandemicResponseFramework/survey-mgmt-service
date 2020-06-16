package one.tracking.framework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import one.tracking.framework.config.ServicesConfig;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = ServicesConfig.class)
public class SurveyManagementApplication {

  public static void main(final String[] args) {
    SpringApplication.run(SurveyManagementApplication.class, args);
  }

}
