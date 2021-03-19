/**
 *
 */
package one.tracking.framework.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Marko Voß
 *
 */
@RestController
@RequestMapping("/ui")
public class UIController {

  private static final Pattern META_APP_SERVER_URL = Pattern.compile("(.*name=\"appServerUrl\" content=\")(.*?)(\".*)");

  private static String processedHTML = null;

  @Value("${app.public.url}")
  private String publicUrl;

  @Value("classpath:index.html")
  private Resource resourceFile;

  @RequestMapping(
      method = RequestMethod.GET,
      produces = MediaType.TEXT_HTML_VALUE)
  public String getUI() throws IOException {

    if (processedHTML == null) {
      try (Stream<String> lines =
          new BufferedReader(new InputStreamReader(this.resourceFile.getInputStream(), StandardCharsets.UTF_8))
              .lines()) {
        processedHTML = lines.map(line -> {
          final Matcher matcher = META_APP_SERVER_URL.matcher(line);
          if (matcher.find()) {
            return matcher.group(1) + this.publicUrl + matcher.group(3);
          }
          return line;
        }).collect(Collectors.joining("\n"));
      }
    }
    return processedHTML;
  }
}
