/**
 *
 */
package one.tracking.framework.dto.meta.question;

import java.util.List;
import javax.validation.Valid;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import one.tracking.framework.dto.meta.container.BooleanContainerDto;
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
public class BooleanQuestionDto extends QuestionDto {

  private Boolean defaultAnswer;

  @Valid
  private BooleanContainerDto container;

  @Override
  public List<QuestionDto> getSubQuestions() {
    return this.container == null ? null : this.container.getSubQuestions();
  }

  @Override
  public QuestionType getType() {
    return QuestionType.BOOL;
  }
}
