/**
 *
 */
package one.tracking.framework.domain;

import lombok.Builder;
import lombok.Data;
import one.tracking.framework.entity.meta.Survey;
import one.tracking.framework.entity.meta.container.Container;

/**
 * @author Marko Voß
 *
 */
@Data
@Builder
public class SearchResult {

  private Container container;
  private Survey survey;
}
