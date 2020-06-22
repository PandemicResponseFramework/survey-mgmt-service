/**
 *
 */
package one.tracking.framework.component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.ApplicationScope;
import org.threeten.bp.Instant;
import one.tracking.framework.config.TimeoutProperties;
import one.tracking.framework.domain.FileHandler;
import one.tracking.framework.domain.ScheduledDeletionFuture;
import one.tracking.framework.exception.ConflictException;

/**
 * @author Marko Voß
 *
 */
@Component
@ApplicationScope
@EnableScheduling
public class FileUploadManagementComponent {

  private static final Logger LOG = LoggerFactory.getLogger(FileUploadManagementComponent.class);

  @Resource(name = "FileUploadManagementTaskScheduler")
  private TaskScheduler scheduler;

  @Autowired
  private TimeoutProperties timeoutProperties;

  private final Map<String, ScheduledDeletionFuture<?>> scheduledTasks = new HashMap<>();

  public long addTempFile(final String userId, final Path tempFile) {

    LOG.debug("Adding managed temporary file. userId: {}; file: {}", userId, tempFile);

    this.scheduledTasks.get(userId);

    if (this.scheduledTasks.get(userId) != null) {
      this.scheduledTasks.get(userId).cancel(false);
    }

    final ScheduledDeletionFuture<?> task = createDeletionTask(userId, tempFile);
    this.scheduledTasks.put(userId, task);
    return task.getDelay(TimeUnit.MILLISECONDS);
  }

  @Async
  public <V> V performAction(final String userId, final FileHandler<V> fileHandler) throws Exception {

    final ScheduledDeletionFuture<?> future = this.scheduledTasks.get(userId);

    if (future == null)
      throw new ConflictException("The uploaded file is no longer available.");

    LOG.debug("Performing action for temporary file. userId: {}; file: {}", userId, future.getFile());

    if (!future.cancel(false)) {
      // Already deleted the file
      LOG.debug("Scheduled deletion task got finished already.");
      throw new ConflictException("The uploaded file is no longer available.");
    }

    try {
      return fileHandler.perform(future.getFile());
    } catch (final Exception e) {
      throw e;
    } finally {
      this.scheduledTasks.put(userId, createDeletionTask(userId, future.getFile()));
    }

  }

  private ScheduledDeletionFuture<?> createDeletionTask(final String userId, final Path file) {

    LOG.debug("Creating scheduled deletion task for file: {}", file);

    final ScheduledFuture<?> future = this.scheduler.schedule((Runnable) () -> {

      LOG.debug("Executing scheduled deletion task for userId: {}; file: {}", userId, file);

      deleteFile(file);
      this.scheduledTasks.remove(userId);

    }, triggerContext -> {
      final Date lastExecution = triggerContext.lastScheduledExecutionTime();
      final Date lastCompletion = triggerContext.lastCompletionTime();
      if (lastExecution == null || lastCompletion == null) {
        return new Date(Instant.now()
            .plusMillis(FileUploadManagementComponent.this.timeoutProperties.getUpload().toMillis()).toEpochMilli());
      }
      return null;
    });

    return new ScheduledDeletionFuture<>(future, file);
  }

  private void deleteFile(final Path path) {

    try {
      if (path != null) {
        Files.deleteIfExists(path);
      }
    } catch (final IOException e) {
      LOG.warn("Unable to delete temp file.", e);
    }
  }
}
