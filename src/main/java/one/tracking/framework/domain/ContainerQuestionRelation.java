/**
 *
 */
package one.tracking.framework.domain;

import lombok.Builder;
import lombok.Data;
import one.tracking.framework.entity.meta.container.Container;
import one.tracking.framework.entity.meta.question.Question;

/**
 * @author Marko Voß
 *
 */
@Data
@Builder
public class ContainerQuestionRelation {

  private Container container;
  private Question childQuestion;
}
