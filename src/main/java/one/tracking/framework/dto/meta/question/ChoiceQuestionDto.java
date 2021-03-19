/**
 *
 */
package one.tracking.framework.dto.meta.question;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.groups.Default;
import io.swagger.annotations.ApiModel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import one.tracking.framework.dto.meta.AnswerDto;
import one.tracking.framework.dto.meta.container.ChoiceContainerDto;
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
public class ChoiceQuestionDto extends QuestionDto {

  @NotEmpty(groups = {Default.class, Update.class})
  private List<@Valid AnswerDto> answers;

  /**
   * The ID of the answer, which shall be used as the default answer.<br/>
   * On update, this will be the index of the provided answer {@link List}.<br/>
   * Optional
   */
  private Long defaultAnswer;

  @NotNull(groups = {Default.class, Update.class})
  private Boolean multiple;

  @Valid
  private ChoiceContainerDto container;

  @Override
  public List<QuestionDto> getSubQuestions() {
    return this.container == null ? null : this.container.getSubQuestions();
  }

  @Override
  public QuestionType getType() {
    return QuestionType.CHOICE;
  }
}
