/**
 *
 */
package one.tracking.framework.component;

import static one.tracking.framework.entity.DataConstants.TOKEN_SURVEY_LENGTH;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import one.tracking.framework.domain.Period;
import one.tracking.framework.domain.PushNotificationRequest;
import one.tracking.framework.domain.ReminderBatchResult;
import one.tracking.framework.domain.ReminderTaskResult;
import one.tracking.framework.domain.ReminderTaskResult.StateType;
import one.tracking.framework.domain.SurveyStatusType;
import one.tracking.framework.entity.DeviceToken;
import one.tracking.framework.entity.Reminder;
import one.tracking.framework.entity.SurveyInstance;
import one.tracking.framework.entity.SurveyResponse;
import one.tracking.framework.entity.meta.IntervalType;
import one.tracking.framework.entity.meta.ReleaseStatusType;
import one.tracking.framework.entity.meta.ReminderType;
import one.tracking.framework.entity.meta.Survey;
import one.tracking.framework.service.FirebaseService;
import one.tracking.framework.support.ServiceUtility;

/**
 * @author Marko Voß
 *
 */
@Component
public class ReminderComponent {

  private static final Logger LOG = LoggerFactory.getLogger(ReminderComponent.class);

  private static final String TASK_REMINDER_PREFIX = "REMINDER_";

  private static final String KEY_SURVEY_NAME_ID = "surveyNameId";

  @Autowired
  private LockerComponent lockerComponent;

  @Autowired
  private ServiceUtility utility;

  @Autowired
  private FirebaseService firebaseService;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  private EntityManager entityManager;

  @Value("${app.reminder.title}")
  private String reminderTitle;

  @Value("${app.reminder.message}")
  private String reminderMessage;

  @Value("${app.task.reminder.batchSize:1000}")
  private int batchSize;

  /**
   *
   * Perform sending reminders to users, which did not yet participate on the current survey instance
   * identified by the specified <code>nameId</code>. The {@link Transactional} definition uses no
   * roleback rule for all exceptions as the transactions are managed manually in this implementation.
   * This is required as this is a batch job able to deal with millions of data entries.
   *
   * @param nameId
   * @return
   */
  @Transactional(noRollbackFor = Throwable.class)
  public ReminderTaskResult sendReminder(final String nameId) {

    if (!isAvailable()) {
      LOG.debug("Executing scheduled job '{}{}' NOT AVAILABLE", TASK_REMINDER_PREFIX, nameId);
      return ReminderTaskResult.NOOP;
    }

    LOG.debug("Executing scheduled job '{}{}' START", TASK_REMINDER_PREFIX, nameId);

    try {

      final ReminderTaskResult result = lockAndSendReminder(nameId);
      if (result == ReminderTaskResult.NOOP)
        LOG.debug("Executing scheduled job '{}{}' CANCELLED", TASK_REMINDER_PREFIX, nameId);
      else
        LOG.debug("Executing scheduled job '{}{}' DONE", TASK_REMINDER_PREFIX, nameId);

      return result;

    } catch (final Exception e) {
      LOG.debug("Executing scheduled job '{}{}' ERROR", TASK_REMINDER_PREFIX, nameId);
      LOG.error(e.getMessage(), e);
      return ReminderTaskResult.NOOP;
    }
  }

  private ReminderTaskResult lockAndSendReminder(final String nameId) {

    final String taskName = TASK_REMINDER_PREFIX + nameId;

    if (this.lockerComponent.lock(taskName)) {

      final ReminderTaskResult result = performSendReminder(nameId);

      this.lockerComponent.unlock(taskName);

      return result;
    }

    return ReminderTaskResult.NOOP;
  }

  private ReminderTaskResult performSendReminder(final String nameId) {

    LOG.debug("Sending reminders for survey '{}'...", nameId);

    final Survey currentSurvey = getSurveyWithReminder(nameId);

    if (currentSurvey == null)
      return ReminderTaskResult.NOOP;

    // Instance might be null, if no user participated on the current survey yet.
    final SurveyInstance currentInstance = getCurrentSurveyInstance(currentSurvey, true);
    SurveyInstance dependsOnInstance = null;

    if (currentSurvey.getDependsOn() != null) {
      dependsOnInstance = getCurrentSurveyInstance(getSurvey(currentSurvey.getDependsOn()), false);
    }
    /*
     * If the dependOn instance has not yet been created no participant performed the depending survey
     * yet and because of this, there is nothing left to do.
     */
    if (currentSurvey.getDependsOn() != null && dependsOnInstance == null)
      return ReminderTaskResult.empty(nameId);

    return performSendReminder(currentSurvey, currentInstance, dependsOnInstance);
  }

  private SurveyInstance getCurrentSurveyInstance(final Survey survey, final boolean create) {

    if (survey == null)
      return null;

    final Period period = this.utility.getCurrentSurveyInstancePeriod(survey);

    final TypedQuery<SurveyInstance> query = this.entityManager.createNamedQuery(
        "SurveyInstance.findBySurveyIdAndStartTimeAndEndTime", SurveyInstance.class);
    query.setParameter(1, survey.getId());
    query.setParameter(2, period.getStart());
    query.setParameter(3, period.getEnd());

    return this.transactionTemplate.execute(status -> {
      status.flush();
      try {
        return query.getSingleResult();

      } catch (final NoResultException e) {

        if (create) {
          final SurveyInstance entity = SurveyInstance.builder()
              .survey(survey)
              .startTime(period.getStart())
              .endTime(period.getEnd())
              .token(this.utility.generateString(TOKEN_SURVEY_LENGTH))
              .build();
          this.entityManager.persist(entity);
          return entity;
        }
        return null;
      }
    });
  }

  private Survey getSurveyWithReminder(final String nameId) {

    final TypedQuery<Survey> query = this.entityManager.createNamedQuery(
        "Survey.findByNameIdAndReleaseStatusAndReminderTypeNotAndIntervalTypeNot", Survey.class);
    query.setParameter(1, nameId);
    query.setParameter(2, ReleaseStatusType.RELEASED);
    query.setParameter(3, ReminderType.NONE);
    query.setParameter(4, IntervalType.NONE);
    query.setFirstResult(0);
    query.setMaxResults(1);

    return this.transactionTemplate.execute(status -> {
      status.flush();
      try {
        return query.getSingleResult();
      } catch (final NoResultException e) {
        return null;
      }
    });
  }

  private Survey getSurvey(final String nameId) {

    final TypedQuery<Survey> query = this.entityManager.createNamedQuery(
        "Survey.findByNameIdAndReleaseStatus", Survey.class);
    query.setParameter(1, nameId);
    query.setParameter(2, ReleaseStatusType.RELEASED);
    query.setFirstResult(0);
    query.setMaxResults(1);

    return this.transactionTemplate.execute(status -> {
      status.flush();
      try {
        return query.getSingleResult();
      } catch (final NoResultException e) {
        return null;
      }
    });
  }

  private List<DeviceToken> getDeviceTokens(final SurveyInstance instance, final int offset,
      final Instant maxTimestamp) {

    LOG.debug("{}: Retrieving DeviceTokens for offset: {}", instance.getSurvey().getNameId(), offset);

    final TypedQuery<DeviceToken> query = this.entityManager.createNamedQuery(
        "DeviceToken.findByCreatedAtBefore", DeviceToken.class);
    query.setFirstResult(offset);
    query.setMaxResults(this.batchSize);
    query.setParameter(1, maxTimestamp);

    return this.transactionTemplate.execute(status -> {
      status.flush();
      return query.getResultList();
    });
  }

  private ReminderTaskResult performSendReminder(
      final Survey currentSurvey,
      final SurveyInstance currentInstance,
      final SurveyInstance dependsOnInstance) {

    final Instant now = Instant.now();
    final int batchSize = 1000;

    int successCount = 0;
    int tokenCount = 0;
    int offset = 0;

    List<DeviceToken> deviceTokens = getDeviceTokens(currentInstance, offset, now);

    while (!deviceTokens.isEmpty()) {

      LOG.debug("{}: DeviceToken pages: Offset {} | Batch Size: {} | Page Size: {}",
          currentSurvey.getNameId(),
          offset,
          batchSize,
          deviceTokens.size());

      final List<DeviceToken> inactiveDeviceTokens = checkDependsOnCompletion(
          currentSurvey,
          dependsOnInstance,
          checkRemindersAndResponses(currentInstance, deviceTokens));

      if (inactiveDeviceTokens.isEmpty()) {

        LOG.debug("{}: No DeviceTokens available to send messages to. Skipping sending messages.",
            currentSurvey.getNameId());
        offset += batchSize;

      } else {

        try {
          final ReminderBatchResult batchResponse = performSendReminderBatch(currentSurvey, inactiveDeviceTokens);

          if (!batchResponse.getInvalidDeviceTokens().isEmpty()) {

            removeInvalidDeviceTokens(currentSurvey, batchResponse.getInvalidDeviceTokens());

            offset += batchSize - batchResponse.getInvalidDeviceTokens().size();

          } else {
            offset += batchSize;
          }

          if (!batchResponse.getValidDeviceTokens().isEmpty())
            persistSentReminders(currentSurvey, currentInstance, batchResponse);

          successCount += batchResponse.getBatchResponses().stream().mapToInt(f -> f.getSuccessCount()).sum();

        } catch (InterruptedException | ExecutionException e) {
          offset += batchSize;
          LOG.error(e.getMessage(), e);
        }
        tokenCount += inactiveDeviceTokens.size();

      }

      deviceTokens = getDeviceTokens(currentInstance, offset, now);
    }

    return ReminderTaskResult.builder()
        .countDeviceTokens(tokenCount)
        .countNotifications(successCount)
        .state(StateType.EXECUTED)
        .surveyNameId(currentSurvey.getNameId())
        .build();
  }

  private void removeInvalidDeviceTokens(final Survey survey, final List<DeviceToken> deviceTokens) {

    LOG.debug("{}: Deleting reminders for {} invalid DeviceTokens", survey.getNameId(), deviceTokens.size());

    for (final DeviceToken deviceToken : deviceTokens) {

      this.transactionTemplate.executeWithoutResult(status -> {
        final Query query = this.entityManager.createNamedQuery("Reminder.deleteByDeviceTokenId");
        query.setParameter(1, deviceToken.getId());
        query.executeUpdate();
        status.flush();
      });
    }

    LOG.debug("{}: Deleting {} invalid DeviceTokens", survey.getNameId(), deviceTokens.size());

    for (final DeviceToken deviceToken : deviceTokens) {

      this.transactionTemplate.executeWithoutResult(status -> {
        final Query query = this.entityManager.createNamedQuery("DeviceToken.deleteById");
        query.setParameter(1, deviceToken.getId());
        query.executeUpdate();
        status.flush();
      });
    }

    LOG.debug("{}: Deletion completed", survey.getNameId(), deviceTokens.size());
  }

  private void persistSentReminders(final Survey survey, final SurveyInstance instance,
      final ReminderBatchResult batchResponse) {

    LOG.debug("{}: Storing reminders for {} DeviceTokens", survey.getNameId(),
        batchResponse.getValidDeviceTokens().size());

    for (final DeviceToken deviceToken : batchResponse.getValidDeviceTokens()) {

      this.transactionTemplate.executeWithoutResult(status -> {
        this.entityManager.persist(Reminder.builder()
            .deviceToken(deviceToken)
            .surveyInstance(instance)
            .build());
        status.flush();
      });
    }

    LOG.debug("{}: Storing reminders for {} DeviceTokens DONE", survey.getNameId(),
        batchResponse.getValidDeviceTokens().size());
  }

  private List<DeviceToken> checkRemindersAndResponses(
      final SurveyInstance currentInstance,
      final List<DeviceToken> deviceTokens) {

    LOG.debug("{}: Checking existance of reminders for {} DeviceTokens.", currentInstance.getSurvey().getNameId(),
        deviceTokens.size());

    final Set<Long> deviceTokenIdsToCheck = deviceTokens.stream().map(DeviceToken::getId).collect(Collectors.toSet());

    /*
     * First, filter out all DeviceTokens, a reminder for the current instance has been set to already
     */

    final TypedQuery<Reminder> reminderQuery = this.entityManager.createNamedQuery(
        "Reminder.findBySurveyInstanceIdAndDeviceTokenId", Reminder.class);
    reminderQuery.setParameter(1, currentInstance.getId());
    reminderQuery.setParameter(2, deviceTokenIdsToCheck);

    final List<Reminder> reminders = this.transactionTemplate.execute(status -> {
      status.flush();
      return reminderQuery.getResultList();
    });

    LOG.debug("{}: {} existing Reminders.", currentInstance.getSurvey().getNameId(), reminders.size());

    final Set<Long> reminderDeviceTokenIds = reminders.stream().map(m -> m.getDeviceToken().getId())
        .collect(Collectors.toSet());

    final List<DeviceToken> deviceTokensToCheck =
        deviceTokens.stream().filter(f -> !reminderDeviceTokenIds.contains(f.getId())) // Set -> O(1)
            .collect(Collectors.toList());

    LOG.debug("{}: {} DeviceTokens left to check.", currentInstance.getSurvey().getNameId(),
        deviceTokensToCheck.size());

    if (deviceTokensToCheck.isEmpty())
      return deviceTokensToCheck;

    LOG.debug("{}: Checking existance of SurveyResponses.", currentInstance.getSurvey().getNameId());

    /*
     * Second, filter out all DeviceTokens, a SurveyResponse for the current SurveyInstance exists
     * already
     */

    final Set<String> userIds = deviceTokensToCheck.stream().map(f -> f.getUser().getId()).collect(Collectors.toSet());

    final TypedQuery<String> responseQuery =
        this.entityManager.createNamedQuery("SurveyResponse.findBySurveyInstanceIdAndUserIdIn",
            String.class);
    responseQuery.setParameter(1, currentInstance.getId());
    responseQuery.setParameter(2, userIds);

    final Set<String> existingUserIds = new HashSet<>(this.transactionTemplate.execute(status -> {
      status.flush();
      return responseQuery.getResultList();
    }));

    final List<DeviceToken> result = new ArrayList<>(deviceTokensToCheck.size());

    for (final DeviceToken deviceToken : deviceTokensToCheck) {
      if (!existingUserIds.contains(deviceToken.getUser().getId()))
        result.add(deviceToken);
    }

    LOG.debug("{}: {} DeviceTokens left.", currentInstance.getSurvey().getNameId(), result.size());

    return result;

  }

  private List<DeviceToken> checkDependsOnCompletion(
      final Survey currentSurvey,
      final SurveyInstance dependsOnInstance,
      final List<DeviceToken> deviceTokens) {

    LOG.debug("{}: Checking depends on completion for {} DeviceTokens.", currentSurvey.getNameId(),
        deviceTokens.size());

    if (currentSurvey.getDependsOn() == null)
      return deviceTokens;

    if (dependsOnInstance == null)
      return Collections.emptyList();

    final TypedQuery<SurveyResponse> query = this.entityManager.createNamedQuery(
        "SurveyResponse.findBySurveyInstanceIdAndUserIdAndMaxVersion", SurveyResponse.class);

    final List<DeviceToken> result = new ArrayList<>();

    for (final DeviceToken deviceToken : deviceTokens) {

      query.setParameter(1, dependsOnInstance.getId());
      query.setParameter(2, deviceToken.getUser().getId());

      final List<SurveyResponse> surveyResponses = this.transactionTemplate.execute(status -> {
        status.flush();
        return query.getResultList();
      });

      LOG.trace("{}: Calculating survey status for DeviceToken '{}' having {} SurveyResponses.",
          currentSurvey.getNameId(), deviceToken.getToken(), surveyResponses.size());

      final SurveyStatusType status =
          this.utility.calculateSurveyStatus(dependsOnInstance.getSurvey(), surveyResponses);

      LOG.trace("{}: Calculating survey status for DeviceToken '{}' DONE.",
          currentSurvey.getNameId(), deviceToken.getToken());

      if (status == SurveyStatusType.INCOMPLETE)
        continue;

      result.add(deviceToken);
    }

    LOG.debug("{}: {} DeviceTokens left.", currentSurvey.getNameId(), result.size());

    return result;
  }

  private ReminderBatchResult performSendReminderBatch(final Survey survey, final List<DeviceToken> deviceTokens)
      throws InterruptedException, ExecutionException {

    LOG.debug("{}: Sending reminders for {} DeviceTokens.", survey.getNameId(), deviceTokens.size());

    final List<BatchResponse> batchResponses = this.firebaseService.sendMessages(PushNotificationRequest.builder()
        .title(this.reminderTitle)
        .message(this.reminderMessage)
        .data(Collections.singletonMap(KEY_SURVEY_NAME_ID, survey.getNameId()))
        .build(),
        deviceTokens.stream().map(m -> m.getToken()).collect(Collectors.toList()));

    final List<DeviceToken> validDeviceTokens = new ArrayList<>();
    final List<DeviceToken> invalidDeviceTokens = new ArrayList<>();

    final List<SendResponse> sendResponses =
        batchResponses.stream().flatMap(f -> f.getResponses().stream()).collect(Collectors.toList());

    // Check, which DeviceTokens are in-/valid
    for (int i = 0; i < deviceTokens.size(); i++) {

      final SendResponse response = sendResponses.get(i);
      final DeviceToken currentToken = deviceTokens.get(i);

      if (!response.isSuccessful()) {

        if (response.getException().getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT ||
            response.getException().getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {

          LOG.debug("{}: Invalid DeviceToken: {}", survey.getNameId(), currentToken.getToken());
          invalidDeviceTokens.add(currentToken);

        } else {

          LOG.warn("{}: Sending message to DeviceToken '{}' failed. Error code: {}, M-Error code: {}",
              survey.getNameId(),
              currentToken.getToken(),
              response.getException().getErrorCode(),
              response.getException().getMessagingErrorCode());
          validDeviceTokens.add(currentToken);
        }
      } else {

        LOG.debug("{}: Message send to DeviceToken: {}", survey.getNameId(), currentToken.getToken());
        validDeviceTokens.add(currentToken);
      }
    }

    return ReminderBatchResult.builder()
        .batchResponses(batchResponses)
        .invalidDeviceTokens(invalidDeviceTokens)
        .validDeviceTokens(validDeviceTokens)
        .build();
  }

  public boolean isAvailable() {
    return this.firebaseService.isAvailable();
  }
}
