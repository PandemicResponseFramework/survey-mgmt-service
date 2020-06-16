/**
 *
 */
package one.tracking.framework.web;

import java.io.IOException;
import java.time.Instant;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import one.tracking.framework.dto.ParticipantInvitationDto;
import one.tracking.framework.service.AuthService;
import springfox.documentation.annotations.ApiIgnore;

/**
 * @author Marko Voß
 *
 */
@RestController
@RequestMapping("/manage")
public class SurveyManagementController {

  @Autowired
  private AuthService authService;

  @RequestMapping(path = "/test")
  public String testAD(
      @ApiIgnore
      final Authentication authentication) {

    return authentication.toString();
  }
  /*
   * Participants
   */

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/invite")
  public void registerParticipant(
      @RequestBody
      @Valid
      final ParticipantInvitationDto registration,
      @ApiIgnore
      final Authentication authentication) throws IOException {

    this.authService.registerParticipant(registration, true);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Long importParticipants(
      @RequestParam("file")
      final MultipartFile file) throws IOException {

    return this.authService.importParticipants(file);
  }

  /*
   * Export
   */

  @RequestMapping(path = "/export")
  public void export(/* TODO */
      @RequestParam("from")
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
      final Instant startDate,
      @RequestParam("to")
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
      final Instant endDate) {
    throw new UnsupportedOperationException();
  }

  /*
   * Surveys
   */

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/survey")
  public void getSurveys(/* TODO */) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/survey/{surveyId}")
  public void getSurvey(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/survey")
  public void createSurvey(/* TODO */) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/survey/{surveyId}")
  public void updateSurvey(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      path = "/survey/{surveyId}")
  public void deleteSurvey(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId) {
    throw new UnsupportedOperationException();
  }

  /*
   * Questions
   */

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/survey/{surveyId}/question")
  public void getQuestions(
      @PathVariable("surveyId")
      final Long surveyId/* TODO */) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/survey/{surveyId}/question/{questionId}")
  public void getQuestion(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId,
      @PathVariable("questionId")
      final Long questionId) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/survey/{surveyId}/question")
  public void createQuestion(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/survey/{surveyId}/question/{questionId}")
  public void updateQuestion(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId,
      @PathVariable("questionId")
      final Long questionId) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      path = "/survey/{surveyId}/question/{questionId}")
  public void deleteQuestion(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId,
      @PathVariable("questionId")
      final Long questionId) {
    throw new UnsupportedOperationException();
  }

  /*
   * Choice Answers (Bad Request if question is not of type CHOICE)
   */

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/survey/{surveyId}/question/{questionId}/answers")
  public void getAnswers(/* TODO */) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/survey/{surveyId}/question/{questionId}/answers/{answerId}")
  public void getAnswer(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId,
      @PathVariable("questionId")
      final Long questionId,
      @PathVariable("answerId")
      final Long answerId) {

    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/survey/{surveyId}/question/{questionId}/answer")
  public void createAnswer(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId,
      @PathVariable("questionId")
      final Long questionId) {

    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/survey/{surveyId}/question/{questionId}/answer/{answerId}")
  public void updateAnswer(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId,
      @PathVariable("questionId")
      final Long questionId,
      @PathVariable("answerId")
      final Long answerId) {

    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      path = "/survey/{surveyId}/question/{questionId}/answer/{answerId}")
  public void deleteAnswer(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId,
      @PathVariable("questionId")
      final Long questionId,
      @PathVariable("answerId")
      final Long answerId) {

    throw new UnsupportedOperationException();
  }
}
