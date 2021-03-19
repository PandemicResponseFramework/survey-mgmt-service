/**
 *
 */
package one.tracking.framework.dto.meta.question;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.groups.Default;
import io.swagger.annotations.ApiModel;
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
@ApiModel(parent = QuestionDto.class)
public class ChecklistQuestionDto extends QuestionDto {

  @NotEmpty(groups = {Default.class, Update.class})
  private List<@Valid ChecklistEntryDto> entries;

  @Override
  public QuestionType getType() {
    return QuestionType.CHECKLIST;
  }
}
