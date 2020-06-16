/**
 *
 */
package one.tracking.framework.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.Lists;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import one.tracking.framework.config.FirebaseProperties;
import one.tracking.framework.domain.NotificationParameter;
import one.tracking.framework.domain.PushNotificationRequest;

/**
 * @author Marko Voß
 *
 */
@Service
public class FirebaseService {

  private static final Logger LOG = LoggerFactory.getLogger(FirebaseService.class);

  @Autowired
  private ResourceLoader resourceLoader;

  @Autowired
  private FirebaseProperties config;

  @PostConstruct
  public void initialize() {

    if (this.config.getConfigFile() == null && this.config.getConfigJson() == null) {
      LOG.warn("Firebase config file or json not set. Skipping FCM setup.");
      return;
    }

    InputStream stream = null;

    if (this.config.getConfigFile() != null) {
      try {
        stream = this.resourceLoader.getResource(this.config.getConfigFile()).getInputStream();
      } catch (final IOException e) {
        LOG.error(e.getMessage(), e);
        return;
      }
    } else if (this.config.getConfigJson() != null) {
      stream = new ByteArrayInputStream(this.config.getConfigJson().getBytes());
    }

    try {

      final FirebaseOptions options = new FirebaseOptions.Builder()
          .setCredentials(GoogleCredentials.fromStream(stream))
          .build();

      if (FirebaseApp.getApps().isEmpty()) {
        final FirebaseApp app = FirebaseApp.initializeApp(options);
        LOG.info("Firebase application has been initialized: {} [batch size: {}]", app.getName(),
            this.config.getBatchSize());
      }

    } catch (final Exception e) {
      LOG.error(e.getMessage(), e);
    }
  }

  public List<BatchResponse> sendMessages(final PushNotificationRequest request, final List<String> tokens)
      throws InterruptedException, ExecutionException {

    if (!isAvailable())
      return null;

    return sendMessagesAsync(request, tokens).get();
  }

  /**
   *
   * @param request
   * @param tokens
   * @see <a href=
   *      "https://firebase.google.com/docs/cloud-messaging/send-message#send-a-batch-of-messages">https://firebase.google.com/docs/cloud-messaging/send-message#send-a-batch-of-messages</a>
   * @return
   */
  public ApiFuture<List<BatchResponse>> sendMessagesAsync(final PushNotificationRequest request,
      final List<String> tokens) {

    if (!isAvailable())
      return null;

    final List<List<String>> partitions = Lists.partition(tokens, this.config.getBatchSize());

    final List<ApiFuture<BatchResponse>> futures = new ArrayList<>();

    final MulticastMessage.Builder messageBuilder = MulticastMessage.builder()
        .setAndroidConfig(getAndroidConfig(request.getGroup()))
        .setApnsConfig(getApnsConfig(request.getGroup()))
        .setNotification(Notification.builder()
            .setTitle(request.getTitle())
            .setBody(request.getMessage())
            .build())
        .putAllData(request.getData());

    for (final List<String> currentPartition : partitions) {

      futures.add(FirebaseMessaging.getInstance().sendMulticastAsync(
          messageBuilder.addAllTokens(currentPartition).build()));
    }

    return ApiFutures.allAsList(futures);
  }

  public String sendMessageToUser(final PushNotificationRequest request, final String token)
      throws InterruptedException, ExecutionException {

    if (!isAvailable())
      return null;

    return sendMessageToUserAsync(request, token).get();
  }

  public ApiFuture<String> sendMessageToUserAsync(final PushNotificationRequest request, final String token) {

    if (!isAvailable())
      return null;

    final Message message = Message.builder()
        .setApnsConfig(getApnsConfig(request.getGroup()))
        .setAndroidConfig(getAndroidConfig(request.getGroup()))
        .setNotification(Notification.builder()
            .setTitle(request.getTitle())
            .setBody(request.getMessage())
            .build())
        .setToken(token)
        .putAllData(request.getData())
        .build();

    return FirebaseMessaging.getInstance().sendAsync(message);
  }

  private AndroidConfig getAndroidConfig(final String topic) {

    return AndroidConfig.builder()
        .setTtl(Duration.ofMinutes(2).toMillis())
        .setCollapseKey(topic)
        .setPriority(AndroidConfig.Priority.HIGH)
        .setNotification(AndroidNotification.builder()
            .setSound(NotificationParameter.SOUND.getValue())
            .setColor(NotificationParameter.COLOR.getValue())
            .setTag(topic)
            .build())
        .build();
  }

  private ApnsConfig getApnsConfig(final String topic) {

    return ApnsConfig.builder()
        .setAps(Aps.builder()
            .setCategory(topic)
            .setThreadId(topic)
            .build())
        .build();
  }

  public boolean isAvailable() {
    return !FirebaseApp.getApps().isEmpty();
  }

}
