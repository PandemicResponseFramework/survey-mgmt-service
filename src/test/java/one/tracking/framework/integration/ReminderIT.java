/**
 *
 */
package one.tracking.framework.integration;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.SendResponse;
import one.tracking.framework.SurveyManagementApplication;
import one.tracking.framework.component.ReminderComponent;
import one.tracking.framework.domain.PushNotificationRequest;
import one.tracking.framework.domain.ReminderTaskResult;
import one.tracking.framework.domain.ReminderTaskResult.StateType;
import one.tracking.framework.entity.DeviceToken;
import one.tracking.framework.entity.meta.Survey;
import one.tracking.framework.service.FirebaseService;

/**
 * @author Marko Voß
 *
 */
@TestPropertySource(locations = "classpath:application-it.properties")
@Import(ITConfiguration.class)
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SurveyManagementApplication.class)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("dev")
public class ReminderIT {

  @MockBean
  private FirebaseService firebaseService;

  @Autowired
  private ReminderComponent reminderComponent;

  @Autowired
  private HelperBean helperBean;

  private List<DeviceToken> deviceTokens;

  @Before
  public void before() throws Exception {
    /*
     * Setup DeviceTokens
     */
    final int amountDeviceTokens = 10000;
    final List<DeviceToken> deviceTokens = new ArrayList<>(amountDeviceTokens);
    for (int i = 0; i < amountDeviceTokens; i++) {
      deviceTokens.add(this.helperBean.addDeviceToken(this.helperBean.createUser(null), i + ""));
    }
    this.deviceTokens = deviceTokens;

    /*
     * Setup FirebaseService BatchResponses
     */

    final int fcmBatchSize = 500;
    // final int fcmDelay = 5 * 1000;
    final int fcmDelay = 0;

    final SendResponse sendResponseSuccess = mock(SendResponse.class);
    when(sendResponseSuccess.isSuccessful()).thenReturn(true);
    when(sendResponseSuccess.getMessageId()).thenReturn("Ok");

    final FirebaseMessagingException exNotRegistered = mock(FirebaseMessagingException.class);
    when(exNotRegistered.getErrorCode())
        .thenReturn(ReminderComponent.ERROR_CODE_REGISTRATION_TOKEN_NOT_REGISTERED);

    final FirebaseMessagingException exInvalidToken = mock(FirebaseMessagingException.class);
    when(exInvalidToken.getErrorCode())
        .thenReturn(ReminderComponent.ERROR_CODE_INVALID_REGISTRATION_TOKEN);

    final FirebaseMessagingException exOther = mock(FirebaseMessagingException.class);
    when(exOther.getErrorCode())
        .thenReturn("messaging/server-unavailable");

    final SendResponse sendResponseNotRegistered = mock(SendResponse.class);
    when(sendResponseNotRegistered.isSuccessful()).thenReturn(false);
    when(sendResponseNotRegistered.getException()).thenReturn(exNotRegistered);

    final SendResponse sendResponseInvalidToken = mock(SendResponse.class);
    when(sendResponseInvalidToken.isSuccessful()).thenReturn(false);
    when(sendResponseInvalidToken.getException()).thenReturn(exInvalidToken);

    final SendResponse sendResponseOtherError = mock(SendResponse.class);
    when(sendResponseOtherError.isSuccessful()).thenReturn(false);
    when(sendResponseOtherError.getException()).thenReturn(exOther);

    final List<SendResponse> sendResponses = new ArrayList<>(fcmBatchSize);

    int countSuccessPerFcmBatch = 0;
    int countFailPerFcmBatch = 0;

    for (int i = 0; i < fcmBatchSize; i++) {

      if (i % 4 == 0) {
        sendResponses.add(sendResponseSuccess);
        countSuccessPerFcmBatch++;
      } else if (i % 4 == 1) {
        sendResponses.add(sendResponseNotRegistered); // invalid DeviceToken -> do no longer use this token
        countFailPerFcmBatch++;
      } else if (i % 4 == 2) {
        sendResponses.add(sendResponseInvalidToken); // invalid DeviceToken -> do no longer use this token
        countFailPerFcmBatch++;
      } else {
        sendResponses.add(sendResponseOtherError); // failed to send -> keep using the DeviceToken
        countFailPerFcmBatch++;
      }
    }

    final BatchResponse batchResponse = mock(BatchResponse.class);
    when(batchResponse.getSuccessCount()).thenReturn(countSuccessPerFcmBatch);
    when(batchResponse.getFailureCount()).thenReturn(countFailPerFcmBatch);
    when(batchResponse.getResponses()).thenReturn(sendResponses);

    when(this.firebaseService.isAvailable()).thenReturn(true);

    // Batch size = 1000 & FCM batch size = 500 -> return 2 BatchResponses
    when(this.firebaseService.sendMessages(any(PushNotificationRequest.class), anyList()))
        .then(new AnswersWithDelay(fcmDelay, new Returns(Arrays.asList(batchResponse, batchResponse))));

  }

  @Test
  public void testNotAvailable() throws Exception {

    /*
     * TEST FirebaseService not available
     */
    this.helperBean.createSurvey("UNAVAILABLE");

    when(this.firebaseService.isAvailable()).thenReturn(false);

    final ReminderTaskResult result = this.reminderComponent.sendReminder("UNAVAILABLE");

    assertThat(result, is(not(nullValue())));
    assertThat(result, is(equalTo(ReminderTaskResult.NOOP)));
  }

  @Test
  public void testExceptionOnFirebaseServiceCall() throws Exception {

    /*
     * TEST Exception handling
     */
    this.helperBean.createSurvey("TESTEX");

    when(this.firebaseService.isAvailable()).thenReturn(true);
    when(this.firebaseService.sendMessages(any(PushNotificationRequest.class), anyList()))
        .thenThrow(ExecutionException.class);

    final ReminderTaskResult result = this.reminderComponent.sendReminder("TESTEX");

    assertThat(result, is(not(nullValue())));
    assertThat(result.getState(), is(StateType.EXECUTED));
    assertThat(result.getCountNotifications(), is(0));
    assertThat(result.getCountDeviceTokens(), is(10000));
  }

  @Test
  public void testNonExistantSurvey() throws Exception {
    /*
     * TEST reminder for non-existing survey
     */
    final ReminderTaskResult result = this.reminderComponent.sendReminder("TEST_NONEXISTANT");

    assertThat(result, is(not(nullValue())));
    assertThat(result, is(equalTo(ReminderTaskResult.NOOP)));
  }

  @Test
  public void testDoNotRepeatReminders() throws Exception {

    /*
     * Test sending reminders not being performed for already sent reminders
     */

    this.helperBean.createSurvey("TEST");

    ReminderTaskResult result = this.reminderComponent.sendReminder("TEST");

    assertThat(result, is(not(nullValue())));
    assertThat(result.getState(), is(StateType.EXECUTED));
    assertThat(result.getCountDeviceTokens(), is(10000));
    assertThat(result.getCountNotifications(), is(2500));

    result = this.reminderComponent.sendReminder("TEST");

    assertThat(result, is(not(nullValue())));
    assertThat(result.getState(), is(StateType.EXECUTED));
    assertThat(result.getCountDeviceTokens(), is(0));
    assertThat(result.getCountNotifications(), is(0));
  }

  @Test
  public void testNoReminderSetup() throws Exception {

    /*
     * Test that reminders are not being sent for surveys having no reminder setup
     */
    this.helperBean.createSimpleSurvey("NOREMINDER", false);

    final ReminderTaskResult result = this.reminderComponent.sendReminder("NOREMINDER");

    assertThat(result, is(not(nullValue())));
    assertThat(result, is(equalTo(ReminderTaskResult.NOOP)));

  }

  @Test
  public void testUnsatisfiedSurveyDependency() throws Exception {

    /*
     * Test survey dependency -> do not send PNs for surveys, which depend on another survey as long as
     * the other survey has not been completed yet.
     */
    final Survey previous = this.helperBean.createSimpleSurvey("PREVIOUS", false);
    this.helperBean.createSimpleSurvey("NEXT", true, previous);

    final ReminderTaskResult result = this.reminderComponent.sendReminder("NEXT");

    assertThat(result, is(not(nullValue())));
    assertThat(result.getState(), is(StateType.EXECUTED));
    assertThat(result.getCountDeviceTokens(), is(0));
    assertThat(result.getCountNotifications(), is(0));
  }

  @Test
  public void testSatisfiedSurveyDependency() throws Exception {

    final Survey previous = this.helperBean.createSimpleSurvey("PREVIOUS", false);
    this.helperBean.createSimpleSurvey("NEXT", true, previous);

    /*
     * Complete the depending survey for half the users and test again
     */
    for (int i = 0; i < this.deviceTokens.size(); i += 2) {
      this.helperBean.completeSimpleSurvey(this.deviceTokens.get(i).getUser(), previous);
    }

    final ReminderTaskResult result = this.reminderComponent.sendReminder("NEXT");

    assertThat(result, is(not(nullValue())));
    assertThat(result.getState(), is(StateType.EXECUTED));
    assertThat(result.getCountDeviceTokens(), is(5000));
    assertThat(result.getCountNotifications(), is(2500));
  }
}
