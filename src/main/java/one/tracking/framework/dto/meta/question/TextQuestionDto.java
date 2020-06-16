/**
 *
 */
package one.tracking.framework.dto.meta.question;

import javax.validation.constraints.Max;
import io.swagger.annotations.ApiModel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import one.tracking.framework.entity.DataConstants;
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
public class TextQuestionDto extends QuestionDto {

  private boolean multiline;

  @Max(value = DataConstants.TEXT_ANSWER_MAX_LENGTH)
  private int length;

  @Override
  public QuestionType getType() {
    return QuestionType.TEXT;
  }
}
