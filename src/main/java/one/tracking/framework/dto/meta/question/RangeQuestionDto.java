/**
 *
 */
package one.tracking.framework.dto.meta.question;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.validation.groups.Default;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import one.tracking.framework.dto.validation.Update;
import one.tracking.framework.entity.meta.question.QuestionType;

/**
 * @author Marko Voß
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@Schema
public class RangeQuestionDto extends QuestionDto {

  @NotNull(groups = {Default.class, Update.class})
  private Integer minValue;

  @NotNull(groups = {Default.class, Update.class})
  private Integer maxValue;

  @Size(max = 64)
  private String minText;

  @Size(max = 64)
  private String maxText;

  private Integer defaultAnswer;

  @Override
  public QuestionType getType() {
    return QuestionType.RANGE;
  }
}
