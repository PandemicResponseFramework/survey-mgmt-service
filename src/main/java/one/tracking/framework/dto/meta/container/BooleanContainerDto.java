/**
 *
 */
package one.tracking.framework.dto.meta.container;

import javax.validation.constraints.NotNull;
import io.swagger.annotations.ApiModel;
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
@ApiModel(parent = ContainerDto.class)
@Schema(allOf = {ContainerDto.class})
public class BooleanContainerDto extends ContainerDto {

  @NotNull
  private Boolean boolDependsOn;

  @Override
  public QuestionType getType() {
    return QuestionType.BOOL;
  }
}
