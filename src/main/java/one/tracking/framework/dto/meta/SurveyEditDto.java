/**
 *
 */
package one.tracking.framework.dto.meta;

import java.time.Instant;
import java.util.List;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import one.tracking.framework.dto.meta.question.QuestionDto;
import one.tracking.framework.entity.meta.IntervalType;
import one.tracking.framework.entity.meta.ReleaseStatusType;
import one.tracking.framework.entity.meta.ReminderType;

/**
 * @author Marko Voß
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema
public class SurveyEditDto {

  private Long id;

  @NotBlank
  @Size(max = 32)
  private String nameId;

  @NotBlank
  @Size(max = 64)
  private String title;

  @Size(max = 256)
  private String description;

  private String dependsOn;

  @Min(0)
  private Integer version;

  private boolean intervalEnabled;

  @NotNull
  private IntervalType intervalType;

  private Instant intervalStart;

  private Integer intervalValue;

  private boolean reminderEnabled;

  @NotNull
  private ReminderType reminderType;

  private Integer reminderValue;

  private ReleaseStatusType releaseStatus;

  private List<QuestionDto> questions;
}
