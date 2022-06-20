/**
 *
 */
package one.tracking.framework.web;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Parameter;
import one.tracking.framework.dto.InvitationFeedbackDto;
import one.tracking.framework.dto.Mapper;
import one.tracking.framework.dto.ParticipantDto;
import one.tracking.framework.dto.ParticipantImportFeedbackDto;
import one.tracking.framework.dto.ParticipantInvitationDto;
import one.tracking.framework.dto.TokenResponseDto;
import one.tracking.framework.dto.meta.SurveyDto;
import one.tracking.framework.dto.meta.SurveyEditDto;
import one.tracking.framework.dto.meta.container.ContainerDto;
import one.tracking.framework.dto.meta.question.QuestionDto;
import one.tracking.framework.dto.validation.Update;
import one.tracking.framework.service.ParticipantService;
import one.tracking.framework.service.SurveyManagementService;

/**
 * @author Marko Voß
 *
 */
@RestController
@RequestMapping("/manage")
public class SurveyManagementController {

  @Autowired
  private ParticipantService participantService;

  @Autowired
  private SurveyManagementService surveyManagementService;

  /*
   * Participants
   */

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/participant")
  public List<ParticipantDto> getParticipants(
      @RequestParam(name = "maxTimestamp", required = true)
      @Min(0)
      final Long maxTimestamp,
      @RequestParam(name = "startIndex", required = false)
      @Min(0)
      final Integer startIndex,
      @RequestParam(name = "limit", required = false)
      @Min(0)
      final Integer limit) {

    return this.participantService.getParticipants(Instant.ofEpochMilli(maxTimestamp), startIndex, limit);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/participant/invite")
  public InvitationFeedbackDto registerParticipant(
      @RequestBody
      @Valid
      final ParticipantInvitationDto registration) throws IOException {

    return InvitationFeedbackDto.builder()
        .feedback(this.participantService.registerParticipant(
            registration.getEmail(),
            registration.getConfirmationToken(),
            true))
        .build();
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      path = "/participant")
  public void deleteParticipant(
      @RequestParam("email")
      @NotBlank
      final String email) {

    this.participantService.deleteParticipant(email);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/participant/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public TokenResponseDto importParticipants(
      @RequestParam("file")
      final MultipartFile file,
      @RequestParam("headerIndex")
      final int selectedHeaderIndex) throws Exception {

    final String importToken = this.participantService.importParticipants(file, selectedHeaderIndex);
    return TokenResponseDto.builder().token(importToken).build();
  }

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/participant/import/{importId}")
  public ParticipantImportFeedbackDto getImportedData(
      @PathVariable(name = "importId")
      final String importId,
      @RequestParam(name = "startIndex", required = false)
      @Min(0)
      final Integer startIndex,
      @RequestParam(name = "limit", required = false)
      @Min(0)
      final Integer limit) {

    return this.participantService.getImportedParticipants(importId, startIndex, limit);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/participant/import/{importId}")
  public void cancelImport(
      @PathVariable(name = "importId")
      final String importId,
      @RequestParam(name = "cancel", required = true)
      final Boolean cancel) {

    if (cancel)
      this.participantService.cancelImport(importId);
  }

  /*
   * Export
   */

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/export")
  public void export(
      @RequestParam("from")
      final Long from,
      @RequestParam("to")
      final Long to,
      @Parameter(hidden = true)
      final HttpServletResponse response) throws IOException {

    final LocalDateTime startTime = Instant.ofEpochMilli(from).atOffset(ZoneOffset.UTC).toLocalDateTime();
    final LocalDateTime endTime = Instant.ofEpochMilli(to).atOffset(ZoneOffset.UTC).toLocalDateTime();

    Assert.isTrue(startTime.isBefore(endTime), "'from' datetime value must be before 'to' datetime value.");

    final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("YYYYMMdd_HHmmss")
        .withZone(ZoneOffset.UTC);

    final String filename = "export_" + formatter.format(Instant.now()) + ".xlsx";

    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

    this.surveyManagementService.exportData(
        startTime.toInstant(ZoneOffset.UTC),
        endTime.toInstant(ZoneOffset.UTC),
        response.getOutputStream());
  }

  /*
   * Surveys
   */

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/survey")
  public List<SurveyDto> getSurveys() {

    return this.surveyManagementService.getCurrentSurveys().stream().map(f -> SurveyDto.builder()
        .dependsOn(f.getDependsOn())
        .description(f.getDescription())
        .id(f.getId())
        .nameId(f.getNameId())
        .title(f.getTitle())
        .version(f.getVersion())
        .releaseStatus(f.getReleaseStatus())
        .intervalStart(f.getIntervalStart())
        .build())
        .collect(Collectors.toList());
  }

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/nameid")
  public List<String> getSurveyNameIds() {

    return this.surveyManagementService.getSurveyNameIds();
  }

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/survey/{surveyId}")
  public SurveyEditDto getSurvey(
      @PathVariable("surveyId")
      final Long surveyId) {

    return Mapper.SurveyEdit.map(this.surveyManagementService.getSurvey(surveyId));
  }

  // TODO: Return ID instead
  @RequestMapping(
      method = RequestMethod.POST,
      path = "/survey")
  public SurveyEditDto createSurvey(
      @RequestBody
      @Valid
      final SurveyEditDto data) {

    return Mapper.SurveyEdit.map(this.surveyManagementService.createSurvey(data));
  }

  // TODO: Return ID instead
  @RequestMapping(
      method = RequestMethod.POST,
      path = "/survey/{surveyId}/version")
  public SurveyEditDto createNewSurveyVersion(
      @PathVariable("surveyId")
      final Long surveyId) {

    return Mapper.SurveyEdit.map(this.surveyManagementService.createNewSurveyVersion(surveyId));
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/survey/{surveyId}")
  public SurveyEditDto updateSurvey(
      @PathVariable("surveyId")
      final Long surveyId,
      final SurveyEditDto data) {

    return Mapper.SurveyEdit.map(this.surveyManagementService.updateSurvey(surveyId, data));
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/survey/{surveyId}/release")
  public void releaseSurvey(
      @PathVariable("surveyId")
      final Long surveyId) {

    this.surveyManagementService.releaseSurvey(surveyId);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      path = "/survey/{surveyId}")
  public void deleteSurvey(
      @PathVariable("surveyId")
      final Long surveyId) {

    this.surveyManagementService.deleteContainer(surveyId);
  }

  /*
   * Questions
   */

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/question/{questionId}")
  public void getQuestion(/* TODO */
      @PathVariable("questionId")
      final Long questionId) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/question/{questionId}/container")
  public Long createContainer(
      @PathVariable("questionId")
      final Long questionId,
      @RequestBody
      @Validated(Update.class)
      final ContainerDto data) {

    return this.surveyManagementService.createContainer(questionId, data).getId();
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/question/{questionId}")
  public void updateQuestion(
      @PathVariable("questionId")
      final Long questionId,
      @RequestBody
      @Validated(Update.class)
      final QuestionDto data) {

    this.surveyManagementService.updateQuestion(questionId, data);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      path = "/question/{questionId}")
  public void deleteQuestion(
      @PathVariable("questionId")
      final Long questionId) {

    this.surveyManagementService.deleteQuestion(questionId);
  }

  /*
   * Container
   */

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/container/{containerId}")
  public void updateContainer(
      @PathVariable("containerId")
      final Long containerId,
      @RequestBody
      @Validated(Update.class)
      final ContainerDto data) {

    this.surveyManagementService.updateContainer(containerId, data);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/container/{containerId}/question")
  public Long createQuestion(/* TODO */
      @PathVariable("containerId")
      final Long containerId,
      @RequestBody
      @Validated(Update.class)
      final QuestionDto data) {
    return this.surveyManagementService.addQuestion(containerId, data).getId();
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      path = "/container/{containerId}")
  public void deleteContainer(
      @PathVariable("containerId")
      final Long containerId) {

    this.surveyManagementService.deleteContainer(containerId);
  }
}
