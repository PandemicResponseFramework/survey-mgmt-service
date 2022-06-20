/**
 *
 */
package one.tracking.framework.dto.meta.question;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema
public class ChecklistEntryDto extends QuestionDto {

  private Boolean defaultAnswer;

  @Override
  public QuestionType getType() {
    return QuestionType.CHECKLIST_ENTRY;
  }
}
