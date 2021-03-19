/**
 *
 */
package one.tracking.framework.domain;

import lombok.Builder;
import lombok.Data;
import one.tracking.framework.entity.meta.question.Question;

/**
 * @author Marko Voß
 *
 */
@Data
@Builder
public class CopyResult<T> {

  private T copy;

  private Question concernedCopiedQuestion;

  public static <U> CopyResult<U> empty() {
    return new CopyResult<>(null, null);
  }
}
