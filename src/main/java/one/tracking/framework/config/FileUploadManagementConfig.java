/**
 *
 */
package one.tracking.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @author Marko Voß
 *
 */
@Configuration
@EnableScheduling
public class FileUploadManagementConfig {

  @Bean(name = "FileUploadManagementTaskScheduler")
  public TaskScheduler participantImportTaskScheduler() {
    final ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
    threadPoolTaskScheduler.setPoolSize(10);
    threadPoolTaskScheduler.setThreadNamePrefix(
        "FileUploadManagementTaskScheduler");
    return threadPoolTaskScheduler;
  }
}
