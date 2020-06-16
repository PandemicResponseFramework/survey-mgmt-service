/**
 *
 */
package one.tracking.framework.dto.meta;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.tracking.framework.dto.meta.question.QuestionDto;

/**
 * @author Marko Voß
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel
public class SurveyDto {

  @NotNull
  private Long id;

  private String dependsOn;

  @NotEmpty
  private List<@Valid QuestionDto> questions;

  @NotBlank
  private String nameId;

  @NotNull
  @Size(max = 64)
  private String title;

  @Size(max = 256)
  private String description;

  @NotNull
  @Min(0)
  private Integer version;

}
