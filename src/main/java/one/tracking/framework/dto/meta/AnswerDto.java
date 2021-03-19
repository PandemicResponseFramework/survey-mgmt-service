/**
 *
 */
package one.tracking.framework.dto.meta;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.validation.groups.Default;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.tracking.framework.dto.validation.Update;

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

  @NotNull(groups = {Default.class})
  private Long id;

  @NotEmpty(groups = {Default.class, Update.class})
  @Size(max = 256, groups = {Default.class, Update.class})
  private String value;

}
