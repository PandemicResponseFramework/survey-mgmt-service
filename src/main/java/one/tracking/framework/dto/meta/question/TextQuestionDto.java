/**
 *
 */
package one.tracking.framework.dto.meta.question;

import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.validation.groups.Default;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import one.tracking.framework.dto.validation.Update;
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
@Schema
public class TextQuestionDto extends QuestionDto {

  @NotNull(groups = {Default.class, Update.class})
  private Boolean multiline;

  @NotNull(groups = {Default.class, Update.class})
  @Max(value = DataConstants.TEXT_ANSWER_MAX_LENGTH, groups = {Default.class, Update.class})
  private Integer length;

  @Override
  public QuestionType getType() {
    return QuestionType.TEXT;
  }
}
