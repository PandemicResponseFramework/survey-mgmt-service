/**
 *
 */
package one.tracking.framework.dto;

import lombok.Builder;
import lombok.Data;
import one.tracking.framework.entity.VerificationState;

/**
 * @author Marko Voß
 *
 */
@Data
@Builder
public class ParticipantDto {

  private String email;
  private VerificationState state;
}
