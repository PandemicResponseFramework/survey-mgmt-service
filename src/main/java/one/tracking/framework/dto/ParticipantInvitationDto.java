/**
 *
 */
package one.tracking.framework.dto;

import static one.tracking.framework.entity.DataConstants.TOKEN_CONFIRM_LENGTH;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Marko Voß
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel
public class ParticipantInvitationDto {

  @NotBlank
  @Size(max = 256)
  private String email;

  @Size(max = TOKEN_CONFIRM_LENGTH)
  private String confirmationToken;
}
