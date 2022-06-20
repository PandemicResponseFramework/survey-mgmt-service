/**
 *
 */
package one.tracking.framework.dto.meta.container;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
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
public class ChoiceContainerDto extends ContainerDto {

  @NotEmpty
  private List<@NotNull Long> choiceDependsOn;

  @Override
  public QuestionType getType() {
    return QuestionType.CHOICE;
  }
}
