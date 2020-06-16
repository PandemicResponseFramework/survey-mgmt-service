/**
 *
 */
package one.tracking.framework.integration;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.SendResponse;
import one.tracking.framework.SurveyManagementApplication;
import one.tracking.framework.component.ReminderComponent;
import one.tracking.framework.domain.PushNotificationRequest;
import one.tracking.framework.domain.ReminderTaskResult;
import one.tracking.framework.domain.ReminderTaskResult.StateType;
import one.tracking.framework.entity.DeviceToken;
import one.tracking.framework.repo.ReminderRepository;
import one.tracking.framework.service.FirebaseService;

/**
 * @author Marko Voß
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class ReminderConcurrencyIT {

  private final ExecutorService executorService = Executors.newFixedThreadPool(10);

  private ConfigurableApplicationContext ctx1;
  private ConfigurableApplicationContext ctx2;

  private FirebaseService firebaseService1;
  private FirebaseService firebaseService2;

  private ReminderComponent s1;
  private ReminderComponent s2;

  private HelperBean helperBean;

  @Before
  public void before() throws Exception {

    final MockBeanInitializer beanInitializer = new MockBeanInitializer(FirebaseService.class);

    final Resource resource = new DefaultResourceLoader().getResource("classpath:application-it.properties");
    final Properties properties = new Properties();
    properties.load(resource.getInputStream());

    // Instance 1
    this.ctx1 = new SpringApplicationBuilder(SurveyManagementApplication.class)
        .properties(properties).properties("server.port=8081", "spring.jpa.hibernate.ddl-auto=create-drop")
        .initializers(beanInitializer).profiles("dev").build().run();

    // Instance 2
    this.ctx2 = new SpringApplicationBuilder(SurveyManagementApplication.class)
        .properties(properties).properties("server.port=8082", "spring.jpa.hibernate.ddl-auto=validate")
        .initializers(beanInitializer).profiles("dev").build().run();

    this.firebaseService1 = this.ctx1.getBean(FirebaseService.class);
    this.firebaseService2 = this.ctx2.getBean(FirebaseService.class);

    this.s1 = this.ctx1.getBean(ReminderComponent.class);
    this.s2 = this.ctx2.getBean(ReminderComponent.class);

    // Add HelperBean to context of instance 1
    this.helperBean = this.ctx1.getBean(HelperBean.class);
  }

  @After
  public void after() {

    if (this.ctx1 != null)
      this.ctx1.close();

    if (this.ctx2 != null)
      this.ctx2.close();
  }

  @Test
  public void testReminderComponent() throws Exception {

    /*
     * Setup DeviceTokens
     */
    final int amountDeviceTokens = 10000;
    final List<DeviceToken> deviceTokens = new ArrayList<>(amountDeviceTokens);
    for (int i = 0; i < amountDeviceTokens; i++) {
      deviceTokens.add(this.helperBean.addDeviceToken(this.helperBean.createUser(null), i + ""));
    }

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

    when(this.firebaseService1.isAvailable()).thenReturn(true);
    when(this.firebaseService2.isAvailable()).thenReturn(true);

    // Batch size = 1000 & FCM batch size = 500 -> return 2 BatchResponses
    when(this.firebaseService1.sendMessages(any(PushNotificationRequest.class), anyList()))
        .then(new AnswersWithDelay(fcmDelay, new Returns(Arrays.asList(batchResponse, batchResponse))));

    // Batch size = 1000 & FCM batch size = 500 -> return 2 BatchResponses
    when(this.firebaseService2.sendMessages(any(PushNotificationRequest.class), anyList()))
        .then(new AnswersWithDelay(fcmDelay, new Returns(Arrays.asList(batchResponse, batchResponse))));

    /*
     * Test locking
     */

    this.helperBean.createSurvey("TEST");

    final Future<ReminderTaskResult> future1 = this.executorService.submit(() -> this.s1.sendReminder("TEST"));
    final Future<ReminderTaskResult> future2 = this.executorService.submit(() -> this.s2.sendReminder("TEST"));

    await().atMost(Duration.ofMinutes(5)).until(() -> {
      return future1.isDone() && future2.isDone();
    });

    assertThat(future1.isCancelled(), is(false));
    assertThat(future2.isCancelled(), is(false));

    assertThat(future1.get(), is(not(nullValue())));
    assertThat(future2.get(), is(not(nullValue())));

    // Only one execution must be executed
    assertThat(future1.get().getState(), is(not(equalTo(future2.get().getState()))));

    if (future1.get().getState() == StateType.EXECUTED) {

      assertThat(future1.get().getSurveyNameId(), is("TEST"));
      assertThat(future1.get().getCountDeviceTokens(), is(10000));
      assertThat(future1.get().getCountNotifications(), is(2500));

      assertThat(future2.get(), is(equalTo(ReminderTaskResult.NOOP)));

    } else {

      assertThat(future2.get().getSurveyNameId(), is("TEST"));
      assertThat(future2.get().getCountDeviceTokens(), is(10000));
      assertThat(future2.get().getCountNotifications(), is(2500));

      assertThat(future1.get(), is(equalTo(ReminderTaskResult.NOOP)));
    }

    this.helperBean.createSurvey("TEST_A");
    this.helperBean.createSurvey("TEST_B");

    /*
     * Test locking is not interfering for different surveys
     */

    final Future<ReminderTaskResult> futureA = this.executorService.submit(() -> this.s1.sendReminder("TEST_A"));
    final Future<ReminderTaskResult> futureB = this.executorService.submit(() -> this.s2.sendReminder("TEST_B"));

    await().atMost(Duration.ofMinutes(5)).until(() -> {
      return futureA.isDone() && futureB.isDone();
    });

    assertThat(futureA.isCancelled(), is(false));
    assertThat(futureB.isCancelled(), is(false));

    assertThat(futureA.get(), is(not(nullValue())));
    assertThat(futureB.get(), is(not(nullValue())));

    // Different topics -> both must be executed
    assertThat(futureA.get().getState(), is(equalTo(StateType.EXECUTED)));
    assertThat(futureB.get().getState(), is(equalTo(StateType.EXECUTED)));

    assertThat(futureA.get().getSurveyNameId(), is("TEST_A"));
    assertThat(futureB.get().getSurveyNameId(), is("TEST_B"));

    // FIXME: Implement dynamic SendResponses depending on DeviceTokens size: 5000/1250 is not correct

    assertThat(futureA.get().getCountDeviceTokens(), is(5000));
    assertThat(futureA.get().getCountNotifications(), is(1250));

    assertThat(futureB.get().getCountDeviceTokens(), is(5000));
    assertThat(futureB.get().getCountNotifications(), is(1250));
  }

  @Test
  public void stressTestLocking() throws Exception {

    when(this.firebaseService1.isAvailable()).thenReturn(true);
    when(this.firebaseService2.isAvailable()).thenReturn(true);

    final SendResponse sendResponseSuccess = mock(SendResponse.class);
    when(sendResponseSuccess.isSuccessful()).thenReturn(true);
    when(sendResponseSuccess.getMessageId()).thenReturn("Ok");

    final List<SendResponse> sendResponses = new ArrayList<>(10);
    for (int i = 0; i < 10; i++) {
      sendResponses.add(sendResponseSuccess);
    }

    final BatchResponse batchResponse = mock(BatchResponse.class);
    when(batchResponse.getSuccessCount()).thenReturn(10);
    when(batchResponse.getFailureCount()).thenReturn(0);
    when(batchResponse.getResponses()).thenReturn(sendResponses);

    when(this.firebaseService1.sendMessages(any(PushNotificationRequest.class), anyList()))
        .then(new AnswersWithDelay(100, new Returns(Arrays.asList(batchResponse))));
    when(this.firebaseService2.sendMessages(any(PushNotificationRequest.class), anyList()))
        .then(new AnswersWithDelay(100, new Returns(Arrays.asList(batchResponse))));

    final ReminderComponent s1 = this.ctx1.getBean(ReminderComponent.class);
    final ReminderComponent s2 = this.ctx2.getBean(ReminderComponent.class);

    final ReminderRepository reminderRepository = this.ctx1.getBean(ReminderRepository.class);

    // Add HelperBean to context of instance 1
    final HelperBean helperBean = this.ctx1.getBean(HelperBean.class);

    helperBean.createSurvey("TEST");

    /*
     * Setup DeviceTokens
     */
    final int amountDeviceTokens = 10;
    final List<DeviceToken> deviceTokens = new ArrayList<>(amountDeviceTokens);
    for (int i = 0; i < amountDeviceTokens; i++) {
      deviceTokens.add(helperBean.addDeviceToken(helperBean.createUser(null), i + ""));
    }

    for (int i = 0; i < 100; i++) {

      final Future<ReminderTaskResult> future1 = this.executorService.submit(() -> s1.sendReminder("TEST"));
      final Future<ReminderTaskResult> future2 = this.executorService.submit(() -> s2.sendReminder("TEST"));

      await().atMost(Duration.ofSeconds(10)).until(() -> {
        return future1.isDone() && future2.isDone();
      });

      assertThat(future1.isCancelled(), is(false));
      assertThat(future2.isCancelled(), is(false));

      assertThat(future1.get(), is(not(nullValue())));
      assertThat(future2.get(), is(not(nullValue())));

      // Only one execution must be executed
      assertThat(future1.get().getState(), is(not(equalTo(future2.get().getState()))));

      reminderRepository.deleteAll();
    }
  }
}
