/**
 *
 */
package one.tracking.framework.dto.meta;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
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
public class AnswerDto {

  @NotNull
  private Long id;

  @NotEmpty
  @Size(max = 256)
  private String value;

}
