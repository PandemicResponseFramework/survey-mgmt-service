/**
 *
 */
package one.tracking.framework.domain;

import java.util.List;
import com.google.firebase.messaging.BatchResponse;
import lombok.Builder;
import lombok.Data;
import one.tracking.framework.entity.DeviceToken;

/**
 * @author Marko Voß
 *
 */
@Data
@Builder
public class ReminderBatchResult {

  private List<BatchResponse> batchResponses;

  private List<DeviceToken> invalidDeviceTokens;

  private List<DeviceToken> validDeviceTokens;
}
