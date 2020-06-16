/**
 *
 */
package one.tracking.framework.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import one.tracking.framework.component.ReminderComponent;
import one.tracking.framework.entity.meta.IntervalType;
import one.tracking.framework.entity.meta.ReleaseStatusType;
import one.tracking.framework.entity.meta.ReminderType;
import one.tracking.framework.entity.meta.Survey;
import one.tracking.framework.repo.SurveyRepository;

/**
 * @author Marko Voß
 *
 */
@Configuration
@EnableScheduling
@Profile("dev")
public class SchedulerTestConfig implements SchedulingConfigurer {

  private static final Logger LOG = LoggerFactory.getLogger(SchedulerTestConfig.class);

  private static final int DELAY = 15;

  @Autowired
  private ReminderComponent reminderComponent;

  @Autowired
  private SurveyRepository surveyRepository;

  @Autowired
  private TaskScheduler taskScheduler;

  private final Map<String, ScheduledFuture<?>> futures = new HashMap<>();

  /*
   * This method is called on application startup
   */
  @Override
  public void configureTasks(final ScheduledTaskRegistrar taskRegistrar) {
    LOG.debug("Initial schedule of DEV reminder job.");
    updateSchedule();
  }

  private void updateSchedule() {

    if (!this.reminderComponent.isAvailable()) {
      LOG.debug("Reminder component is not available. Skipping scheduler setup.");
      return;
    }

    final List<Survey> surveys =
        this.surveyRepository.findAllByReleaseStatusAndReminderTypeNotAndIntervalTypeNotOrderByNameIdAscVersionDesc(
            ReleaseStatusType.RELEASED, ReminderType.NONE, IntervalType.NONE);

    final List<String> nameIds = new ArrayList<>();

    for (final Survey survey : surveys) {

      final String nameId = survey.getNameId();

      if (nameIds.contains(nameId))
        continue;

      nameIds.add(nameId);

      final ScheduledFuture<?> future = this.futures.get(survey.getNameId());
      if (future != null)
        future.cancel(false);

      this.futures.put(nameId,
          this.taskScheduler.schedule(
              (Runnable) () -> this.reminderComponent.sendReminder(nameId),
              triggerContext -> {

                final Date nextExecution = getNextExecutionTime(triggerContext.lastCompletionTime());

                LOG.debug("Scheduling reminder DEV task for survey {} to {}", nameId, nextExecution);

                return nextExecution;
              }));
    }
  }

  private Date getNextExecutionTime(final Date lastCompletionTime) {

    if (lastCompletionTime == null)
      return new Date(Instant.now().plus(DELAY, ChronoUnit.MINUTES).toEpochMilli());

    return new Date(Instant.ofEpochMilli(lastCompletionTime.getTime()).plus(DELAY, ChronoUnit.MINUTES).toEpochMilli());
  }
}
