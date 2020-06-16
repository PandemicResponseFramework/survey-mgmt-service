/**
 *
 */
package one.tracking.framework.dto.meta.container;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
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

  @Schema(type = "array", oneOf = {
      BooleanQuestionDto.class,
      ChoiceQuestionDto.class,
      RangeQuestionDto.class,
      TextQuestionDto.class,
      ChecklistQuestionDto.class})
  @NotEmpty
  protected List<@Valid QuestionDto> subQuestions;

}
