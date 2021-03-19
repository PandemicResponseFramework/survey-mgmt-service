/**
 *
 */
package one.tracking.framework.dto.meta.container;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.groups.Default;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import one.tracking.framework.dto.meta.question.BooleanQuestionDto;
import one.tracking.framework.dto.meta.question.ChecklistQuestionDto;
import one.tracking.framework.dto.meta.question.ChoiceQuestionDto;
import one.tracking.framework.dto.meta.question.QuestionDto;
import one.tracking.framework.dto.meta.question.RangeQuestionDto;
import one.tracking.framework.dto.meta.question.TextQuestionDto;
import one.tracking.framework.entity.meta.question.QuestionType;

/**
 * @author Marko Voß
 *
 */
@Data
@SuperBuilder
@NoArgsConstructor
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type")
@JsonSubTypes({
    @Type(name = "BOOL", value = BooleanContainerDto.class),
    @Type(name = "CHOICE", value = ChoiceContainerDto.class)
})
@ApiModel(discriminator = "type", subTypes = {
    BooleanContainerDto.class,
    ChoiceContainerDto.class})
@Schema(discriminatorProperty = "type",
    discriminatorMapping = {
        @DiscriminatorMapping(value = "BOOL", schema = BooleanContainerDto.class),
        @DiscriminatorMapping(value = "CHOICE", schema = ChoiceContainerDto.class)
    })
public abstract class ContainerDto {

  @NotNull(groups = {Default.class})
  private Long id;

  @Schema(type = "array", oneOf = {
      BooleanQuestionDto.class,
      ChoiceQuestionDto.class,
      RangeQuestionDto.class,
      TextQuestionDto.class,
      ChecklistQuestionDto.class})
  @NotEmpty(groups = {Default.class})
  protected List<@Valid QuestionDto> subQuestions;

  @JsonIgnore
  public abstract QuestionType getType();
}
