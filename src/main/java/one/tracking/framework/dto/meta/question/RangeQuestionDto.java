/**
 *
 */
package one.tracking.framework.dto.meta.question;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import one.tracking.framework.entity.meta.question.QuestionType;

/**
 * @author Marko Voß
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@ApiModel(parent = QuestionDto.class)
public class RangeQuestionDto extends QuestionDto {

  @NotNull
  private Integer minValue;

  @NotNull
  private Integer maxValue;

  @Size(max = 64)
  private String minText;

  @Size(max = 64)
  private String maxText;

  private Integer defaultValue;

  @Override
  public QuestionType getType() {
    return QuestionType.RANGE;
  }
}
