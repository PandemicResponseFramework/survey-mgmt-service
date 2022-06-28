/**
 *
 */
package one.tracking.framework.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import one.tracking.framework.entity.meta.Survey;

/**
 * @author Marko Voß
 *
 */
@Data
@Builder
@AllArgsConstructor
public class SurveyOverviewElement {

  private Survey survey;

  private boolean isEditable;

  private boolean isDeletable;

  private boolean isReleasable;

  private boolean isVersionable;
}
