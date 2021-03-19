/**
 *
 */
package one.tracking.framework.dto.meta.question;

import java.util.List;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
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
import one.tracking.framework.dto.validation.Update;
import one.tracking.framework.entity.meta.ReleaseStatusType;
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
    @Type(name = "BOOL", value = BooleanQuestionDto.class),
    @Type(name = "CHOICE", value = ChoiceQuestionDto.class),
    @Type(name = "RANGE", value = RangeQuestionDto.class),
    @Type(name = "TEXT", value = TextQuestionDto.class),
    @Type(name = "NUMBER", value = NumberQuestionDto.class),
    @Type(name = "CHECKLIST", value = ChecklistQuestionDto.class),
    @Type(name = "CHECKLIST_ENTRY", value = ChecklistEntryDto.class)
})
@ApiModel(discriminator = "type", subTypes = {
    BooleanQuestionDto.class,
    ChoiceQuestionDto.class,
    RangeQuestionDto.class,
    TextQuestionDto.class,
    NumberQuestionDto.class,
    ChecklistQuestionDto.class,
    ChecklistEntryDto.class})
@Schema(discriminatorProperty = "type", discriminatorMapping = {
    @DiscriminatorMapping(value = "BOOL", schema = BooleanQuestionDto.class),
    @DiscriminatorMapping(value = "CHOICE", schema = ChoiceQuestionDto.class),
    @DiscriminatorMapping(value = "RANGE", schema = RangeQuestionDto.class),
    @DiscriminatorMapping(value = "TEXT", schema = TextQuestionDto.class),
    @DiscriminatorMapping(value = "NUMBER", schema = NumberQuestionDto.class),
    @DiscriminatorMapping(value = "CHECKLIST", schema = ChecklistQuestionDto.class),
    @DiscriminatorMapping(value = "CHECKLIST_ENTRY", schema = ChecklistEntryDto.class)
})
public abstract class QuestionDto {

  @NotNull(groups = {Default.class})
  private Long id;

  @NotEmpty(groups = {Default.class, Update.class,})
  @Size(max = 256, groups = {Default.class, Update.class})
  private String question;

  @NotNull(groups = {Default.class, Update.class})
  @Min(value = 0, groups = {Default.class, Update.class})
  private Integer order;

  @NotNull(groups = {Default.class, Update.class})
  private Boolean optional;

  @NotNull(groups = {Default.class})
  private ReleaseStatusType releaseStatus;

  private Long previousVersionId;

  @JsonIgnore
  public List<QuestionDto> getSubQuestions() {
    return null;
  }

  @JsonIgnore
  public abstract QuestionType getType();
}
