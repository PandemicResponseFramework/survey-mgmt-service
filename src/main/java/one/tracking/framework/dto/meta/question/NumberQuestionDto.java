/**
 *
 */
package one.tracking.framework.dto.meta.question;

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
public class NumberQuestionDto extends QuestionDto {

  private Integer minValue;

  private Integer maxValue;

  private Integer defaultValue;

  @Override
  public QuestionType getType() {
    return QuestionType.NUMBER;
  }
}
