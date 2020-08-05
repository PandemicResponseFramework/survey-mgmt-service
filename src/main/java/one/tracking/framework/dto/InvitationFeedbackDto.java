/**
 *
 */
package one.tracking.framework.dto;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import one.tracking.framework.domain.InvitationFeedback;

/**
 * @author Marko Voß
 *
 */
@Data
@Builder
public class InvitationFeedbackDto {

  @NotNull
  private ParticipantDto participant;

  @NotNull
  private InvitationFeedback feedback;
}
