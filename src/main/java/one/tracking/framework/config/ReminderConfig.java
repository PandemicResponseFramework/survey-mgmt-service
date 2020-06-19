/**
 *
 */
package one.tracking.framework.config;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.util.Assert;
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
public class ReminderConfig implements SchedulingConfigurer {

  private static final Logger LOG = LoggerFactory.getLogger(ReminderConfig.class);

  @Autowired
  private ReminderComponent reminderComponent;

  @Autowired
  private SurveyRepository surveyRepository;

  private final Map<String, ScheduledFuture<?>> futures = new HashMap<>();

  @Bean
  public TaskScheduler reminderTaskScheduler() {
    final ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
    threadPoolTaskScheduler.setPoolSize(10);
    threadPoolTaskScheduler.setThreadNamePrefix(
        "ReminderTaskScheduler");
    return threadPoolTaskScheduler;
  }

  /*
   * This method is called on application startup
   */
  @Override
  public void configureTasks(final ScheduledTaskRegistrar taskRegistrar) {
    updateSchedule();
  }

  public Map<String, Long> getSchedule(final TimeUnit timeUnit) {

    Assert.notNull(timeUnit, "TimeUnit must not be null");

    return this.futures.entrySet().stream().collect(Collectors.toMap(
        e -> e.getKey(),
        e -> e.getValue().getDelay(timeUnit)));
  }

  public void updateSchedule() {

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

      // Avoid survey object to keep existing in TriggerContext scope
      final Instant intervalStart = survey.getIntervalStart();
      final IntervalType intervalType = survey.getIntervalType();
      final Integer intervalValue = survey.getIntervalValue();
      final ReminderType reminderType = survey.getReminderType();
      final Integer reminderValue = survey.getReminderValue();

      this.futures.put(nameId,
          reminderTaskScheduler().schedule(
              (Runnable) () -> this.reminderComponent.sendReminder(nameId),
              triggerContext -> {

                final Date nextExecution = getNextExecutionTime(
                    intervalStart, intervalType, intervalValue,
                    reminderType, reminderValue);

                LOG.debug("Scheduling reminder task for survey {} to {}", nameId, nextExecution);

                return nextExecution;
              }));
    }
  }

  private Date getNextExecutionTime(
      final Instant intervalStart, final IntervalType intervalType, final Integer intervalValue,
      final ReminderType reminderType, final Integer reminderValue) {

    final ZonedDateTime start = intervalStart.atZone(ZoneOffset.UTC).plus(reminderValue, reminderType.toChronoUnit())
        .truncatedTo(ChronoUnit.DAYS)
        .plusHours(12);

    final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

    if (start.isAfter(now)) {
      return new Date(start.toInstant().toEpochMilli());
    }

    final int weekStart = start.get(WeekFields.ISO.weekOfWeekBasedYear());
    final int weekNow = now.get(WeekFields.ISO.weekOfWeekBasedYear());

    final int weekDelta = (int) (Math.floor((weekNow - weekStart) / (double) intervalValue));

    ZonedDateTime startTime = start.plusWeeks(weekDelta * intervalValue);

    if (startTime.isBefore(now))
      startTime = startTime.plus(intervalValue, intervalType.toChronoUnit());

    return new Date(startTime.toInstant().toEpochMilli());
  }
}
