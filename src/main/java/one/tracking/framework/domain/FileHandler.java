/**
 *
 */
package one.tracking.framework.domain;

import java.nio.file.Path;

/**
 * @author Marko Voß
 *
 */
public interface FileHandler<V> {

  V perform(Path file) throws Exception;
}
