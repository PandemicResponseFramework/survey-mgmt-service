/**
 *
 */
package one.tracking.framework.service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.apache.commons.collections4.IterableUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import one.tracking.framework.component.SurveyDataExportComponent;
import one.tracking.framework.domain.ContainerQuestionRelation;
import one.tracking.framework.domain.CopyResult;
import one.tracking.framework.domain.Period;
import one.tracking.framework.domain.SearchResult;
import one.tracking.framework.domain.SurveyOverviewElement;
import one.tracking.framework.domain.TraversalResult;
import one.tracking.framework.dto.meta.AnswerDto;
import one.tracking.framework.dto.meta.SurveyEditDto;
import one.tracking.framework.dto.meta.container.BooleanContainerDto;
import one.tracking.framework.dto.meta.container.ChoiceContainerDto;
import one.tracking.framework.dto.meta.container.ContainerDto;
import one.tracking.framework.dto.meta.question.BooleanQuestionDto;
import one.tracking.framework.dto.meta.question.ChecklistEntryDto;
import one.tracking.framework.dto.meta.question.ChecklistQuestionDto;
import one.tracking.framework.dto.meta.question.ChoiceQuestionDto;
import one.tracking.framework.dto.meta.question.NumberQuestionDto;
import one.tracking.framework.dto.meta.question.QuestionDto;
import one.tracking.framework.dto.meta.question.RangeQuestionDto;
import one.tracking.framework.dto.meta.question.TextQuestionDto;
import one.tracking.framework.entity.meta.Answer;
import one.tracking.framework.entity.meta.IntervalType;
import one.tracking.framework.entity.meta.ReleaseStatusType;
import one.tracking.framework.entity.meta.ReminderType;
import one.tracking.framework.entity.meta.Survey;
import one.tracking.framework.entity.meta.container.BooleanContainer;
import one.tracking.framework.entity.meta.container.ChoiceContainer;
import one.tracking.framework.entity.meta.container.Container;
import one.tracking.framework.entity.meta.question.BooleanQuestion;
import one.tracking.framework.entity.meta.question.ChecklistEntry;
import one.tracking.framework.entity.meta.question.ChecklistQuestion;
import one.tracking.framework.entity.meta.question.ChoiceQuestion;
import one.tracking.framework.entity.meta.question.NumberQuestion;
import one.tracking.framework.entity.meta.question.Question;
import one.tracking.framework.entity.meta.question.QuestionType;
import one.tracking.framework.entity.meta.question.RangeQuestion;
import one.tracking.framework.entity.meta.question.TextQuestion;
import one.tracking.framework.exception.ConflictException;
import one.tracking.framework.repo.AnswerRepository;
import one.tracking.framework.repo.ContainerRepository;
import one.tracking.framework.repo.QuestionRepository;
import one.tracking.framework.repo.SurveyRepository;
import one.tracking.framework.support.ServiceUtility;

/**
 * @author Marko Voß
 *
 */
@Slf4j
@Service
public class SurveyManagementService {

  @Autowired
  private SurveyRepository surveyRepository;

  @Autowired
  private ContainerRepository containerRepository;

  @Autowired
  private QuestionRepository questionRepository;

  @Autowired
  private AnswerRepository answerRepository;

  @Autowired
  private ServiceUtility utility;

  @Autowired
  private SurveyDataExportComponent exportComponent;

  @PersistenceContext
  private EntityManager entityManager;

  public void exportData(final Instant startTime, final Instant endTime, final OutputStream outStream)
      throws IOException {

    this.exportComponent.export(startTime, endTime, outStream);
  }

  public List<SurveyOverviewElement> getSurveyOverview() {

    return this.surveyRepository.findCurrentVersions().stream().map(survey -> SurveyOverviewElement.builder()
        .survey(survey)
        .isEditable(survey.getReleaseStatus() == ReleaseStatusType.EDIT)
        .isDeletable(isDeletable(survey))
        .isReleasable(isReleasable(survey))
        .isVersionable(isVersionable(survey))
        .build())
        .collect(Collectors.toList());
  }

  private boolean isReleasable(final Survey survey) {

    if (survey.getReleaseStatus() != ReleaseStatusType.EDIT)
      return false;

    // Dependency must own a released version
    if (survey.getDependsOn() != null && !this.surveyRepository
        .existsTopByNameIdAndReleaseStatusOrderByVersionDesc(survey.getDependsOn(), ReleaseStatusType.RELEASED))
      return false;

    // Check for empty containers (collect empty containers)
    final TraversalResult result = this.utility.traverseContainer(survey,
        q -> false, // ignore questions
        q -> {// do nothing
        },
        c -> c.getQuestions().size() == 0,
        c -> { // do nothing
        });

    if (result.getConsumedContainers().size() > 0)
      return false;

    return true;
  }

  private boolean isDeletable(final Survey survey) {

    // Thoughts:
    // A dependsOn B = A(B)
    // A dependsOn NULL = A()
    // v(n) = version n
    // ---
    // A(B) v1 RELEASED & ACTIVE -> B v(n) RELEASED
    // A(B) v2 RELEASED & INACTIVE -> B v(n) RELEASED
    // A() v2 EDIT
    //
    // DELETE B v(n+1) -> OK because of existing release of B v(n)
    // ---
    // A(B) v1 RELEASED & ACTIVE -> B v(n) RELEASED
    // A() v2 RELEASED & INACTIVE
    //
    // DELETE B v(n+1) -> OK because of existing release of B v(n)

    return survey.getReleaseStatus() == ReleaseStatusType.EDIT
        && (this.surveyRepository.existsTopByNameIdAndReleaseStatusOrderByVersionDesc(survey.getNameId(),
            ReleaseStatusType.RELEASED) || !this.surveyRepository.existsAnyNameIdByDependsOn(survey.getNameId()));
  }

  private boolean isVersionable(final Survey survey) {

    if (survey.getReleaseStatus() != ReleaseStatusType.RELEASED)
      return false;

    return this.surveyRepository
        .findTopByNameIdOrderByVersionDesc(survey.getNameId()).get().getId()
        .equals(survey.getId());
  }

  public Survey createSurvey(final SurveyEditDto data) {

    // Check if nameId exists already.
    if (this.surveyRepository.existsByNameId(data.getNameId())) {
      throw new IllegalArgumentException("Specified NameId is already reserved.");
    }

    validateSurveyData(data);

    final Survey survey = Survey.builder()
        .dependsOn(data.getDependsOn())
        .description(data.getDescription())
        .nameId(data.getNameId())
        .title(data.getTitle())
        .releaseStatus(ReleaseStatusType.EDIT)
        .build();

    if (data.isIntervalEnabled()) {
      survey.setIntervalStart(data.getIntervalStart());
      survey.setIntervalType(data.getIntervalType());
      survey.setIntervalValue(data.getIntervalValue());
    } else {
      survey.setIntervalType(IntervalType.NONE);
    }

    if (data.isReminderEnabled()) {
      survey.setReminderType(data.getReminderType());
      survey.setReminderValue(data.getReminderValue());
    } else {
      survey.setReminderType(ReminderType.NONE);
    }

    return this.surveyRepository.save(survey);
  }

  private void validateSurveyData(final SurveyEditDto data) {

    if (data.getDependsOn() != null) {

      // Check if the survey depends on itself
      if (data.getDependsOn().equals(data.getNameId()))
        throw new IllegalArgumentException("A survey must not depend on itself.");

      // Check if dependsOn exists
      if (!this.surveyRepository.existsByNameId(data.getDependsOn()))
        throw new IllegalArgumentException("Specified DependsOn does not exist.");

      // TODO: Check dependency cycle
    }
  }

  public Survey getSurvey(final Long surveyId) {

    return this.surveyRepository.findById(surveyId).get();
  }

  /**
   *
   * @param nameId
   * @return
   */
  public Survey createNewSurveyVersion(final Long surveyId) {

    final Optional<Survey> surveyOp = this.surveyRepository.findById(surveyId);

    if (surveyOp.isEmpty())
      throw new IllegalArgumentException("No survey found for ID: " + surveyId);

    final Survey survey = surveyOp.get();

    final Optional<Survey> topOp = this.surveyRepository.findTopByNameIdOrderByVersionDesc(survey.getNameId());

    if (topOp.isEmpty())
      throw new IllegalStateException(
          "Unexpected behavior: No entry found when querying for top most version of nameId: " + survey.getNameId());

    final Survey topVersion = topOp.get();

    if (!topVersion.getId().equals(survey.getId()))
      throw new IllegalArgumentException(
          "Specified survey ID is not the most current version. Expected: " + topVersion.getId());

    if (topVersion.getReleaseStatus() != ReleaseStatusType.RELEASED)
      throw new IllegalArgumentException("Specified survey is not released. Survey ID: " + surveyId);

    return this.surveyRepository.save(survey.newVersion());
  }

  public Survey updateSurvey(final Long surveyId, final SurveyEditDto data) {

    final Optional<Survey> surveyOp = this.surveyRepository.findById(surveyId);

    if (surveyOp.isEmpty())
      throw new IllegalArgumentException("No survey found for id: " + surveyId);

    final Survey survey = surveyOp.get();

    if (survey.getReleaseStatus() == ReleaseStatusType.RELEASED)
      throw new ConflictException("Survey got released already. ID: " + surveyId);

    if (!survey.getNameId().equals(data.getNameId()))
      throw new ConflictException("Changing NameIds after intial setup is not allowed.");

    // Check if previous release exists. (No change in interval definition allowed)
    final Optional<Survey> previousReleaseOp =
        this.surveyRepository.findTopByNameIdAndReleaseStatusOrderByVersionDesc(survey.getNameId(),
            ReleaseStatusType.RELEASED);

    if (previousReleaseOp.isPresent()) {

      final Survey previousRelease = previousReleaseOp.get();
      if (!previousRelease.getIntervalStart().equals(data.getIntervalStart()) ||
          !previousRelease.getIntervalType().equals(data.getIntervalType()) ||
          !previousRelease.getIntervalValue().equals(data.getIntervalValue()))

        throw new IllegalArgumentException("Changing interval defintion of an already active survey is not allowed.");
    }

    validateSurveyData(data);

    return this.surveyRepository.save(survey.toBuilder()
        .description(data.getDescription())
        .intervalStart(data.getIntervalStart())
        .intervalType(data.getIntervalType())
        .intervalValue(data.getIntervalValue())
        .releaseStatus(ReleaseStatusType.EDIT)
        .reminderType(data.getReminderType())
        .reminderValue(data.getReminderValue())
        .title(data.getTitle())
        .dependsOn(data.getDependsOn())
        .build());
  }

  public Survey releaseSurvey(final Long surveyId) {

    final Optional<Survey> surveyOp = this.surveyRepository.findById(surveyId);

    if (surveyOp.isEmpty())
      throw new IllegalArgumentException("No survey found for ID: " + surveyId);

    final Survey survey = surveyOp.get();

    if (survey.getReleaseStatus() == ReleaseStatusType.RELEASED)
      throw new IllegalArgumentException("Specified survey got released already. ID: " + surveyId);

    final Period nextPeriod = this.utility.getNextSurveyInstancePeriod(survey, ZonedDateTime.now(ZoneOffset.UTC));

    // Check dependsOn availability before this survey gets released
    if (survey.getDependsOn() != null) {
      final Optional<Survey> dependency = this.surveyRepository
          .findTopByNameIdAndReleaseStatusOrderByVersionDesc(survey.getDependsOn(), ReleaseStatusType.RELEASED);

      if (dependency.isEmpty())
        throw new IllegalArgumentException("The dependency of this survey has not yet been released.");
    }

    // Check validity & update question release state
    // - No empty containers
    final TraversalResult result = this.utility.traverseContainer(survey,
        q -> q.getReleaseStatus() != ReleaseStatusType.RELEASED,
        q -> q.setReleaseStatus(ReleaseStatusType.RELEASED),
        c -> c.getQuestions().size() == 0,
        c -> {
        });

    if (result.getConsumedContainers().size() > 0)
      throw new IllegalArgumentException("Empty containers are not allowed when releasing a survey.");

    final Survey storedSurvey = this.surveyRepository.save(survey.toBuilder()
        .releaseStatus(ReleaseStatusType.RELEASED)
        .intervalStart(survey.getIntervalType() == IntervalType.NONE ? null : nextPeriod.getStart())
        .build());

    if (result.getConsumedQuestions().size() > 0)
      this.questionRepository.saveAll(result.getConsumedQuestions());

    return storedSurvey;
  }

  /**
   * @param data
   * @return
   */
  public Question updateQuestion(final Long questionId, final QuestionDto data) {

    final Question question = this.questionRepository.findById(questionId).get();

    if (!question.getType().equals(data.getType()))
      throw new IllegalArgumentException("The question type does not match the expected question type. Expected: "
          + question.getType() + "; Received: " + data.getType());

    if (question.getType() == QuestionType.CHECKLIST)
      throw new IllegalArgumentException("Update of CHECKLIST question is not supported.");

    /*
     * We have to check, if the specified ID in data is actually part of the current survey entity. So
     * we have to look for it and because of that, we do not need to request the question entity via the
     * question repository.
     */
    final SearchResult result = null;// searchQuestion(question);

    // Survey must not be released
    // if (result.getSurvey().getReleaseStatus() == ReleaseStatusType.RELEASED)
    // throw new ConflictException(
    // "Related survey with nameId: " + result.getSurvey().getNameId() + " got released already.");

    final int currentRanking = question.getRanking();

    // if (data.getOrder() >= result.getContainer().getQuestions().size())
    // throw new IllegalArgumentException("The specified order is greater than the possible value.
    // Expected: "
    // + result.getContainer().getQuestions().size() + " Received: " + data.getOrder());

    final Question updatedQuestion = updateQuestionData(question, data);

    if (updatedQuestion == null) {
      log.debug("Updating question skipped. QuestionType: {}", question.getType());
      return null;

    } else {
      log.debug("Updating question {} done.", question.getId());

      // Update ranking of siblings if required
      // if (question.getRanking() != currentRanking)
      // updateRankings(result.getContainer(), updatedQuestion);

      log.debug("Updating question rankings done.");

      return updatedQuestion;
    }
  }

  public Question addQuestion(final Long containerId, final QuestionDto data) {

    final Container container = this.containerRepository.findById(containerId).get();

    Container toEdit = null;
    if (container instanceof Survey) {

      final Survey survey = (Survey) container;
      if (survey.getReleaseStatus() == ReleaseStatusType.RELEASED)
        throw new IllegalArgumentException("Adding questions to a released survey is not allowed.");

      toEdit = survey;

    } else {
      toEdit = getQuestionEditVersion(container.getParent()).getContainer();
    }

    Question createdQuestion = null;

    switch (data.getType()) {
      case BOOL:
        createdQuestion = createQuestion(toEdit, (BooleanQuestionDto) data);
        break;
      case NUMBER:
        createdQuestion = createQuestion(toEdit, (NumberQuestionDto) data);
        break;
      case CHOICE:
        createdQuestion = createQuestion(toEdit, (ChoiceQuestionDto) data);
        break;
      case RANGE:
        createdQuestion = createQuestion(toEdit, (RangeQuestionDto) data);
        break;
      case TEXT:
        createdQuestion = createQuestion(toEdit, (TextQuestionDto) data);
        break;
      default:
        break;
    }

    toEdit.getQuestions().add(createdQuestion);
    this.containerRepository.save(toEdit);

    return createdQuestion;
  }

  /**
   *
   * @param containerId
   * @param questionId
   */
  public void deleteQuestion(final Long questionId) {

    final Question question = this.questionRepository.findById(questionId).get();

    if (question.hasContainer())
      throw new IllegalArgumentException("Question does own a container and cannot be deleted.");

    final Question toDelete = getQuestionEditVersion(question);

    log.info("Q: " + toDelete.getId());

    final List<Container> containers = this.containerRepository.findByQuestionsId(toDelete.getId());

    if (containers == null || containers.size() == 0 || containers.size() > 1)
      throw new IllegalStateException(
          "No parent container for copied question found or found multiple parent containers. Copied question ID: "
              + toDelete.getId());

    final Container container = containers.get(0);

    log.info("C: " + container.getId());

    container.getQuestions().remove(toDelete);
    this.containerRepository.save(container);
    this.questionRepository.delete(toDelete);
  }

  public void deleteContainer(final Long containerId) {

    final Container container = this.containerRepository.findById(containerId).get();

    if (container instanceof Survey)
      deleteSurvey((Survey) container);
    else
      deleteQuestionContainer(container);
  }

  private void deleteSurvey(final Survey survey) {

    if (survey.getReleaseStatus() != ReleaseStatusType.EDIT)
      throw new IllegalArgumentException("Only surveys with release status EDIT can be deleted.");

    /*
     * If a released version exists already, no dependency check must be executed, since the released
     * version serves as a fallback.
     */
    final boolean latestReleaseExists = this.surveyRepository.existsTopByNameIdAndReleaseStatusOrderByVersionDesc(
        survey.getNameId(),
        ReleaseStatusType.RELEASED);

    if (latestReleaseExists)
      this.surveyRepository.deleteById(survey.getId());
    else {
      // Dependency check
      if (!this.surveyRepository.existsAnyNameIdByDependsOn(survey.getNameId()))
        this.surveyRepository.deleteById(survey.getId());
      else
        throw new IllegalArgumentException("Cannot delete unreleased survey, on which other surveys depend on.");
    }
  }

  private void deleteQuestionContainer(final Container container) {

    if (!container.getQuestions().isEmpty())
      throw new IllegalArgumentException("Container does own at least one child and cannot be deleted.");

    final Question question = container.getParent();
    question.clearContainer();
    this.questionRepository.save(question);
  }

  private Question createQuestion(final Container parentContainer, final BooleanQuestionDto data) {

    return this.questionRepository.save(BooleanQuestion.builder()
        .question(data.getQuestion())
        .ranking(parentContainer.getQuestions().size())
        .optional(data.getOptional())
        .releaseStatus(ReleaseStatusType.EDIT)
        .defaultAnswer(data.getDefaultAnswer())
        .build());
  }

  private Question createQuestion(final Container parentContainer, final ChoiceQuestionDto data) {

    final List<Answer> answers = new ArrayList<>(data.getAnswers().size());

    for (final AnswerDto answerData : data.getAnswers()) {
      final Answer answer = this.answerRepository.save(Answer.builder()
          .value(answerData.getValue())
          .build());
      answers.add(answer);
    }

    return this.questionRepository.save(ChoiceQuestion.builder()
        .question(data.getQuestion())
        .ranking(parentContainer.getQuestions().size())
        .optional(data.getOptional())
        .releaseStatus(ReleaseStatusType.EDIT)
        .multiple(data.getMultiple())
        .answers(answers)
        .build());
  }

  private Question createQuestion(final Container parentContainer, final RangeQuestionDto data) {

    return this.questionRepository.save(RangeQuestion.builder()
        .question(data.getQuestion())
        .ranking(parentContainer.getQuestions().size())
        .optional(data.getOptional())
        .releaseStatus(ReleaseStatusType.EDIT)
        .defaultAnswer(data.getDefaultAnswer())
        .minText(data.getMinText())
        .maxText(data.getMaxText())
        .minValue(data.getMinValue())
        .maxValue(data.getMaxValue())
        .build());
  }

  private Question createQuestion(final Container parentContainer, final TextQuestionDto data) {

    return this.questionRepository.save(TextQuestion.builder()
        .question(data.getQuestion())
        .ranking(parentContainer.getQuestions().size())
        .optional(data.getOptional())
        .releaseStatus(ReleaseStatusType.EDIT)
        .multiline(data.getMultiline())
        .length(data.getLength())
        .build());
  }

  private Question createQuestion(final Container parentContainer, final NumberQuestionDto data) {

    final NumberQuestion question = this.questionRepository.save(NumberQuestion.builder()
        .question(data.getQuestion())
        .ranking(parentContainer.getQuestions().size())
        .optional(data.getOptional())
        .releaseStatus(ReleaseStatusType.EDIT)
        .defaultAnswer(data.getDefaultAnswer())
        .maxValue(data.getMaxValue())
        .minValue(data.getMinValue())
        .build());

    return question;
  }

  /**
   * @param searchResult
   * @param updatedQuestion
   */
  private void updateRankings(final Container container, final Question updatedQuestion) {

    for (int i = 0; i < container.getQuestions().size(); i++) {

      final Question currentSibling = container.getQuestions().get(i);

      // Skip updating updatedQuestion
      if (currentSibling.getId().equals(updatedQuestion.getId()))
        continue;

      if (currentSibling.getRanking() <= updatedQuestion.getRanking()) {

        currentSibling.setRanking(i);
        this.questionRepository.save(currentSibling);

      } else {
        break;
      }
    }
  }

  private List<SearchResult> searchSurveys(final Question question) {

    if (question == null || question.getId() == null)
      return null;

    final List<Container> originContainers =
        this.containerRepository.findByQuestionsId(question.getId());

    if (originContainers.isEmpty())
      throw new IllegalStateException(
          "Unexpected state. No container found containing question id: " + question.getId());

    boolean done = false;
    List<ContainerQuestionRelation> currentList = originContainers.stream()
        .map(container -> ContainerQuestionRelation.builder()
            .container(container)
            .childQuestion(question)
            .build())
        .collect(Collectors.toList());

    while (!done) {
      final List<ContainerQuestionRelation> result = new ArrayList<>();
      done = findParentContainers(currentList, result);
      currentList = result;
    }

    return currentList.stream().map(relation -> SearchResult.builder()
        .survey((Survey) relation.getContainer())
        .rootQuestion(relation.getChildQuestion())
        .build())
        .collect(Collectors.toList());
  }

  private boolean findParentContainers(
      final List<ContainerQuestionRelation> currentRelations,
      final List<ContainerQuestionRelation> result) {

    boolean done = true;

    for (final ContainerQuestionRelation relation : currentRelations) {

      if (relation.getContainer().getParent() == null) {
        result.add(relation);
      } else {
        result.addAll(
            this.containerRepository.findByQuestionsId(relation.getContainer().getParent().getId()).stream()
                .map(container -> ContainerQuestionRelation.builder()
                    .container(container)
                    .childQuestion(relation.getContainer().getParent())
                    .build())
                .collect(Collectors.toList()));
        done = false;
      }
    }

    return done;
  }

  private Question updateQuestionData(final Question question, final QuestionDto data) {

    if (!checkIfModified(question, data)) {
      log.debug("Question update skipped: {}", question.getId());
      return question;
    }

    final Question toEdit = getQuestionEditVersion(question);

    toEdit.setQuestion(data.getQuestion());
    toEdit.setRanking(data.getOrder());
    toEdit.setOptional(data.getOptional());

    switch (data.getType()) {
      case BOOL:
        return updateQuestion((BooleanQuestion) toEdit, (BooleanQuestionDto) data);
      case CHECKLIST:
        return updateQuestion((ChecklistQuestion) toEdit, (ChecklistQuestionDto) data);
      case CHOICE:
        return updateQuestion((ChoiceQuestion) toEdit, (ChoiceQuestionDto) data);
      case RANGE:
        return updateQuestion((RangeQuestion) toEdit, (RangeQuestionDto) data);
      case NUMBER:
        return updateQuestion((NumberQuestion) toEdit, (NumberQuestionDto) data);
      case TEXT:
        return updateQuestion((TextQuestion) toEdit, (TextQuestionDto) data);
      default:
        return null;
    }
  }

  private Question updateQuestion(final BooleanQuestion question, final BooleanQuestionDto data) {

    question.setDefaultAnswer(data.getDefaultAnswer());
    return this.questionRepository.save(question);
  }

  private Question updateQuestion(final ChecklistQuestion question, final ChecklistQuestionDto data) {

    final List<ChecklistEntry> newEntries = updateChecklistEntries(question, data);
    final Set<Long> newEntryIds = newEntries.stream().map(f -> f.getId()).collect(Collectors.toSet());

    // Collect entries to be deleted
    final List<ChecklistEntry> removedEntries =
        question.getEntries().stream().filter(p -> !newEntryIds.contains(p.getId())).collect(Collectors.toList());

    if (log.isDebugEnabled()) {
      for (final ChecklistEntry entry : removedEntries) {
        log.debug("Deleting entry: {}", entry.getId());
      }
    }

    question.setEntries(newEntries);

    final Question updatedQuestion = this.questionRepository.save(question);
    this.questionRepository.deleteAll(removedEntries);
    return updatedQuestion;
  }

  private List<ChecklistEntry> updateChecklistEntries(final ChecklistQuestion question,
      final ChecklistQuestionDto data) {

    // FIXME check if data updates are necessary

    final List<ChecklistEntry> newEntries = new ArrayList<>(data.getEntries().size());

    // Create or update entries
    for (final ChecklistEntryDto entryData : data.getEntries()) {

      final Optional<ChecklistEntry> existingEntryOp =
          question.getEntries().stream().filter(p -> p.getId().equals(entryData.getId())).findFirst();

      if (existingEntryOp.isPresent()) {
        final ChecklistEntry entry = existingEntryOp.get();
        entry.setDefaultAnswer(entryData.getDefaultAnswer());
        entry.setOptional(entryData.getOptional());
        entry.setQuestion(entryData.getQuestion());
        entry.setRanking(entryData.getOrder());
        newEntries.add(entry);

        log.debug("Updating entry: {}", entry.getId());

      } else {
        final ChecklistEntry entry = this.questionRepository.save(ChecklistEntry.builder()
            .defaultAnswer(entryData.getDefaultAnswer())
            .optional(entryData.getOptional())
            .question(entryData.getQuestion())
            .ranking(entryData.getOrder())
            .build());
        newEntries.add(entry);

        log.debug("Creating entry: {}", entry.getId());
      }
    }
    return newEntries;
  }

  private Question updateQuestion(final ChoiceQuestion question, final ChoiceQuestionDto data) {

    // Validate default answer
    if (data.getDefaultAnswer() != null &&
        (data.getDefaultAnswer() < 0 || data.getDefaultAnswer() >= data.getAnswers().size()))
      throw new IllegalArgumentException("DefaultAnswer must be an index of provided answer list.");

    question.setMultiple(data.getMultiple());

    final List<Answer> newAnswers = updateAnswers(question, data);
    final List<Answer> oldAnswers = question.getAnswers();

    // Update default answer if set
    if (data.getDefaultAnswer() != null) {
      question.setDefaultAnswer(newAnswers.get(data.getDefaultAnswer().intValue()));
    }

    if (newAnswers != oldAnswers)
      question.setAnswers(newAnswers);

    final Question updatedQuestion = this.questionRepository.save(question);

    if (newAnswers != oldAnswers) {

      if (question.hasContainer()) {
        // All answers got replaced -> clear dependsOn of container
        final ChoiceContainer container = question.getContainer();
        container.getDependsOn().clear();
        this.containerRepository.save(container);
      }

      this.answerRepository.deleteAll(oldAnswers);
    }

    return updatedQuestion;
  }

  private List<Answer> updateAnswers(final ChoiceQuestion question, final ChoiceQuestionDto data) {

    // Check if existing answers equal new data
    if (question.getAnswers().size() == data.getAnswers().size()) {

      boolean isEqual = true;
      for (int i = 0; i < question.getAnswers().size(); i++) {

        if (!question.getAnswers().get(i).getValue().equals(data.getAnswers().get(i).getValue())) {
          isEqual = false;
          break;
        }
      }

      if (isEqual)
        return question.getAnswers();
    }

    final List<Answer> result = new ArrayList<>();
    for (final AnswerDto answer : data.getAnswers()) {
      result.add(this.answerRepository.save(Answer.builder()
          .value(answer.getValue())
          .build()));
    }

    return result;

  }

  private Question updateQuestion(final RangeQuestion question, final RangeQuestionDto data) {

    question.setDefaultAnswer(data.getDefaultAnswer());
    question.setMaxText(data.getMaxText());
    question.setMaxValue(data.getMaxValue());
    question.setMinText(data.getMinText());
    question.setMinValue(data.getMinValue());

    return this.questionRepository.save(question);
  }

  private Question updateQuestion(final NumberQuestion question, final NumberQuestionDto data) {

    question.setDefaultAnswer(data.getDefaultAnswer());
    question.setMaxValue(data.getMaxValue());
    question.setMinValue(data.getMinValue());

    return this.questionRepository.save(question);
  }

  private Question updateQuestion(final TextQuestion question, final TextQuestionDto data) {

    question.setLength(data.getLength());
    question.setMultiline(data.getMultiline());

    return this.questionRepository.save(question);
  }

  private boolean checkIfModified(final Question question, final QuestionDto data) {

    if (!question.getQuestion().equals(data.getQuestion()))
      return true;
    if (question.getRanking() != data.getOrder())
      return true;
    if (question.isOptional() != data.getOptional().booleanValue())
      return true;

    switch (question.getType()) {
      case BOOL:
        return checkIfModified((BooleanQuestion) question, (BooleanQuestionDto) data);
      case CHOICE:
        return checkIfModified((ChoiceQuestion) question, (ChoiceQuestionDto) data);
      case RANGE:
        return checkIfModified((RangeQuestion) question, (RangeQuestionDto) data);
      case NUMBER:
        return checkIfModified((NumberQuestion) question, (NumberQuestionDto) data);
      case TEXT:
        return checkIfModified((TextQuestion) question, (TextQuestionDto) data);
      default:
        return false;
    }
  }

  private boolean checkIfModified(final BooleanQuestion question, final BooleanQuestionDto data) {

    if (question.getDefaultAnswer() == null && data.getDefaultAnswer() != null)
      return true;
    if (question.getDefaultAnswer() != null && !question.getDefaultAnswer().equals(data.getDefaultAnswer()))
      return true;

    return false;
  }

  private boolean checkIfModified(final ChoiceQuestion question, final ChoiceQuestionDto data) {

    if (question.getAnswers().size() != data.getAnswers().size())
      return true;

    for (int i = 0; i < question.getAnswers().size(); i++) {
      if (!question.getAnswers().get(i).getValue().equals(data.getAnswers().get(i).getValue())) {
        return true;
      }
    }

    if (question.getDefaultAnswer() == null && data.getDefaultAnswer() != null)
      return true;
    if (question.getDefaultAnswer() != null && data.getDefaultAnswer() == null)
      return true;

    if (data.getDefaultAnswer() != null && data.getAnswers().size() <= data.getDefaultAnswer()) {
      throw new IllegalArgumentException(
          "Provided default answer as an index is greater or equal the size of the provided answers.");
    }

    if (question.getDefaultAnswer() != null && data.getDefaultAnswer() != null
        && !question.getDefaultAnswer().getValue()
            .equals(data.getAnswers().get(data.getDefaultAnswer().intValue()).getValue()))
      return true;

    if (!question.getMultiple().equals(data.getMultiple()))
      return true;

    return false;
  }

  private boolean checkIfModified(final RangeQuestion question, final RangeQuestionDto data) {

    if (question.getDefaultAnswer() == null && data.getDefaultAnswer() != null)
      return true;
    if (question.getDefaultAnswer() != null && !question.getDefaultAnswer().equals(data.getDefaultAnswer()))
      return true;

    if (question.getMinValue() != data.getMinValue())
      return true;
    if (question.getMaxValue() != data.getMaxValue())
      return true;

    if (question.getMinText() == null && data.getMinText() != null)
      return true;
    if (question.getMinText() != null && !question.getMinText().equals(data.getMinText()))
      return true;
    if (question.getMaxText() == null && data.getMaxText() != null)
      return true;
    if (question.getMaxText() != null && !question.getMaxText().equals(data.getMaxText()))
      return true;

    return false;
  }

  private boolean checkIfModified(final NumberQuestion question, final NumberQuestionDto data) {

    if (question.getDefaultAnswer() == null && data.getDefaultAnswer() != null)
      return true;
    if (question.getDefaultAnswer() != null && !question.getDefaultAnswer().equals(data.getDefaultAnswer()))
      return true;

    if (question.getMinValue() != data.getMinValue())
      return true;
    if (question.getMaxValue() != data.getMaxValue())
      return true;

    return false;
  }

  private boolean checkIfModified(final TextQuestion question, final TextQuestionDto data) {

    if (!question.getMultiline().equals(data.getMultiline()))
      return true;
    if (!question.getLength().equals(data.getLength()))
      return true;

    return false;
  }

  /**
   *
   * @param questions
   * @param concernedQuestionId
   * @return
   */
  private List<CopyResult<Question>> copyQuestions(final List<Question> questions, final Long concernedQuestionId) {

    if (questions == null)
      return null;

    return questions.stream().map(m -> copyQuestion(m, concernedQuestionId)).collect(Collectors.toList());
  }

  /**
   *
   * @param question
   * @param concernedQuestionId
   * @return
   */
  private CopyResult<Question> copyQuestion(final Question question, final Long concernedQuestionId) {

    if (question.getReleaseStatus() != ReleaseStatusType.RELEASED)
      throw new IllegalStateException(
          "Creating a copy of a question which is not released is causing an invalid state!");

    CopyResult<Question> result = null;

    switch (question.getType()) {
      case BOOL:
        result = copyQuestion((BooleanQuestion) question, concernedQuestionId);
        break;
      case CHECKLIST:
        result = copyQuestion((ChecklistQuestion) question, concernedQuestionId);
        break;
      case CHOICE:
        result = copyQuestion((ChoiceQuestion) question, concernedQuestionId);
        break;
      case RANGE:
        result = copyQuestion((RangeQuestion) question, concernedQuestionId);
        break;
      case TEXT:
        result = copyQuestion((TextQuestion) question, concernedQuestionId);
        break;
      case NUMBER:
        result = copyQuestion((NumberQuestion) question, concernedQuestionId);
        break;
      default:
        throw new IllegalStateException("Question type not handled! Type: " + question.getType());
    }

    return result;
  }

  /**
   *
   * @param question
   * @param concernedQuestionId
   * @return
   */
  private CopyResult<Question> copyQuestion(final ChecklistQuestion question, final Long concernedQuestionId) {

    final List<ChecklistEntry> entries =
        question.getEntries().stream().map(m -> copyQuestion(m)).collect(Collectors.toList());

    final Question copy = this.questionRepository.save(ChecklistQuestion.builder()
        .entries(entries)
        .optional(question.isOptional())
        .previousVersion(question)
        .question(question.getQuestion())
        .ranking(question.getRanking())
        .build());

    return CopyResult.<Question>builder()
        .copy(copy)
        .concernedCopiedQuestion(question.getId().equals(concernedQuestionId) ? copy : null)
        .build();
  }

  /**
   *
   * @param entry
   * @return
   */
  private ChecklistEntry copyQuestion(final ChecklistEntry entry) {

    return this.questionRepository.save(ChecklistEntry.builder()
        .defaultAnswer(entry.getDefaultAnswer())
        .optional(entry.isOptional())
        .previousVersion(entry)
        .question(entry.getQuestion())
        .ranking(entry.getRanking())
        .build());
  }

  /**
   *
   * @param question
   * @param concernedQuestionId
   * @return
   */
  private CopyResult<Question> copyQuestion(final BooleanQuestion question, final Long concernedQuestionId) {

    BooleanQuestion copy = this.questionRepository.save(BooleanQuestion.builder()
        .defaultAnswer(question.getDefaultAnswer())
        .optional(question.isOptional())
        .question(question.getQuestion())
        .ranking(question.getRanking())
        .previousVersion(question)
        .build());

    final CopyResult<BooleanContainer> containerCopy =
        copyContainer(question.getContainer(), copy, concernedQuestionId);

    if (containerCopy != null)
      copy.setContainer(containerCopy.getCopy());
    copy = this.questionRepository.save(copy);

    log.debug("Copied question: {} -> {}", question.getId(), copy.getId());

    return CopyResult.<Question>builder()
        .copy(copy)
        .concernedCopiedQuestion(
            question.getId().equals(concernedQuestionId)
                ? copy
                : containerCopy == null
                    ? null
                    : containerCopy.getConcernedCopiedQuestion())
        .build();
  }

  /**
   *
   * @param question
   * @param concernedQuestionId
   * @return
   */
  private CopyResult<Question> copyQuestion(final RangeQuestion question, final Long concernedQuestionId) {

    final Question copy = this.questionRepository.save(RangeQuestion.builder()
        .defaultAnswer(question.getDefaultAnswer())
        .maxText(question.getMaxText())
        .maxValue(question.getMaxValue())
        .minText(question.getMinText())
        .minValue(question.getMinValue())
        .optional(question.isOptional())
        .previousVersion(question)
        .question(question.getQuestion())
        .ranking(question.getRanking())
        .build());

    log.debug("Copied question: {} -> {}", question.getId(), copy.getId());

    return CopyResult.<Question>builder()
        .copy(copy)
        .concernedCopiedQuestion(question.getId().equals(concernedQuestionId) ? copy : null)
        .build();
  }

  /**
   *
   * @param question
   * @param concernedQuestionId
   * @return
   */
  private CopyResult<Question> copyQuestion(final TextQuestion question, final Long concernedQuestionId) {

    final Question copy = this.questionRepository.save(TextQuestion.builder()
        .length(question.getLength())
        .multiline(question.getMultiline())
        .optional(question.isOptional())
        .previousVersion(question)
        .question(question.getQuestion())
        .ranking(question.getRanking())
        .build());

    log.debug("Copied question: {} -> {}", question.getId(), copy.getId());

    return CopyResult.<Question>builder()
        .copy(copy)
        .concernedCopiedQuestion(question.getId().equals(concernedQuestionId) ? copy : null)
        .build();
  }

  private CopyResult<Question> copyQuestion(final NumberQuestion question, final Long concernedQuestionId) {

    final Question copy = this.questionRepository.save(NumberQuestion.builder()
        .defaultAnswer(question.getDefaultAnswer())
        .maxValue(question.getMaxValue())
        .minValue(question.getMinValue())
        .optional(question.isOptional())
        .previousVersion(question)
        .question(question.getQuestion())
        .ranking(question.getRanking())
        .build());

    log.debug("Copied question: {} -> {}", question.getId(), copy.getId());

    return CopyResult.<Question>builder()
        .copy(copy)
        .concernedCopiedQuestion(question.getId().equals(concernedQuestionId) ? copy : null)
        .build();
  }

  /**
   *
   * @param question
   * @param concernedQuestionId
   * @return
   */
  private CopyResult<Question> copyQuestion(final ChoiceQuestion question, final Long concernedQuestionId) {

    final List<Answer> answers = copyAnswers(question.getAnswers());

    final Optional<Answer> defaultAnswerOp = question.getDefaultAnswer() == null
        ? Optional.empty()
        : answers.stream().filter(p -> p.getValue().equals(question.getDefaultAnswer().getValue())).findAny();

    ChoiceQuestion copy = this.questionRepository.save(ChoiceQuestion.builder()
        .defaultAnswer(defaultAnswerOp.isPresent() ? defaultAnswerOp.get() : null)
        .multiple(question.getMultiple())
        .optional(question.isOptional())
        .previousVersion(question)
        .question(question.getQuestion())
        .ranking(question.getRanking())
        .answers(answers)
        .build());

    final CopyResult<ChoiceContainer> containerCopy =
        copyContainer(question.getContainer(), answers, copy, concernedQuestionId);

    if (containerCopy != null)
      copy.setContainer(containerCopy.getCopy());
    copy = this.questionRepository.save(copy);

    log.debug("Copied question: {} -> {}", question.getId(), copy.getId());

    return CopyResult.<Question>builder()
        .copy(copy)
        .concernedCopiedQuestion(
            question.getId().equals(concernedQuestionId)
                ? copy
                : containerCopy == null
                    ? null
                    : containerCopy.getConcernedCopiedQuestion())
        .build();
  }

  /**
   *
   * @param answers
   * @return
   */
  private List<Answer> copyAnswers(final List<Answer> answers) {

    if (answers == null || answers.isEmpty())
      return null;

    final List<Answer> copies = new ArrayList<>(answers.size());

    for (final Answer answer : answers) {

      final Answer copy = this.answerRepository.save(Answer.builder()
          .value(answer.getValue())
          .build());

      log.debug("Copied answer: {} -> {}", answer.getId(), copy.getId());

      copies.add(copy);
    }

    return copies;
  }

  /**
   *
   * @param container
   * @param newParent
   * @param concernedQuestionId
   * @return
   */
  private CopyResult<BooleanContainer> copyContainer(final BooleanContainer container, final BooleanQuestion newParent,
      final Long concernedQuestionId) {

    if (container == null)
      return null;

    final List<CopyResult<Question>> questionCopies = copyQuestions(container.getQuestions(), concernedQuestionId);

    final BooleanContainer containerCopy = this.containerRepository.save(BooleanContainer.builder()
        .dependsOn(container.getDependsOn())
        .questions(questionCopies.stream().map(m -> m.getCopy()).collect(Collectors.toList()))
        .parent(newParent)
        .build());

    final Optional<Question> concernedCopiedQuestionOp =
        questionCopies.stream().map(m -> m.getConcernedCopiedQuestion()).filter(p -> p != null).findAny();

    log.debug("Copied container:  {} -> {}", container.getId(), containerCopy.getId());

    return CopyResult.<BooleanContainer>builder()
        .copy(containerCopy)
        .concernedCopiedQuestion(concernedCopiedQuestionOp.isPresent() ? concernedCopiedQuestionOp.get() : null)
        .build();
  }

  /**
   *
   * @param container
   * @param answersCopy
   * @param newParent
   * @param concernedQuestionId
   * @return
   */
  private CopyResult<ChoiceContainer> copyContainer(final ChoiceContainer container, final List<Answer> answersCopy,
      final ChoiceQuestion newParent, final Long concernedQuestionId) {

    if (container == null)
      return null;

    final List<CopyResult<Question>> questionCopies = copyQuestions(container.getQuestions(), concernedQuestionId);

    List<Answer> dependsOn = null;

    if (container.getDependsOn() != null && !container.getDependsOn().isEmpty()) {

      dependsOn = answersCopy.stream()
          .filter(answer -> container.getDependsOn().stream().anyMatch(m -> m.getValue().equals(answer.getValue())))
          .collect(Collectors.toList());
    }

    final Optional<Question> concernedCopiedQuestionOp =
        questionCopies.stream().map(m -> m.getConcernedCopiedQuestion()).filter(p -> p != null).findAny();

    final ChoiceContainer containerCopy = this.containerRepository.save(ChoiceContainer.builder()
        .dependsOn(dependsOn)
        .parent(newParent)
        .questions(questionCopies.stream().map(m -> m.getCopy()).collect(Collectors.toList()))
        .build());

    log.debug("Copied container:  {} -> {}", container.getId(), containerCopy.getId());

    return CopyResult.<ChoiceContainer>builder()
        .copy(containerCopy)
        .concernedCopiedQuestion(concernedCopiedQuestionOp.isPresent() ? concernedCopiedQuestionOp.get() : null)
        .build();
  }

  /**
   *
   * @return
   */
  public List<String> getSurveyNameIds() {
    return this.surveyRepository.findAllNameIds();
  }

  private SearchResult getCurrentSurveyEdit(final Question question) {

    // There must be only ONE survey version available, which is NOT released!
    final Optional<SearchResult> resultOp = searchSurveys(question).stream()
        .filter(p -> p.getSurvey().getReleaseStatus() == ReleaseStatusType.EDIT).findFirst();

    if (resultOp.isEmpty())
      throw new IllegalArgumentException("Cannot delete question. No new survey version found for this question.");

    return resultOp.get();
  }

  /**
   * @param surveyId
   * @param questionId
   * @param data
   */
  public Container createContainer(final Long questionId, final ContainerDto data) {

    final Question question = this.questionRepository.findById(questionId).get();

    // We do currently support one container per question only
    if (question.hasContainer())
      throw new IllegalArgumentException("The specified question does own a container already.");

    if (data.getType() != question.getType())
      throw new IllegalArgumentException("The specified container of type " + data.getType()
          + " is not valid for question type " + question.getType());

    final Question toEdit = getQuestionEditVersion(question);

    switch (data.getType()) {
      case BOOL:
        return createContainer((BooleanQuestion) toEdit, (BooleanContainerDto) data);
      case CHOICE:
        // if question got copied -> answer IDs in DTO are no longer up-to-date -> seek new IDs
        final ChoiceContainerDto choiceData = (ChoiceContainerDto) data;
        if (toEdit != question) {
          final ChoiceQuestion originQuestion = (ChoiceQuestion) question; // origin
          final ChoiceQuestion copyQuestion = (ChoiceQuestion) toEdit; // copy

          choiceData.getChoiceDependsOn()
              .replaceAll(id -> {
                final String value = originQuestion.getAnswers().stream().filter(p -> p.getId().equals(id))
                    .findFirst().get().getValue();
                return copyQuestion.getAnswers().stream().filter(p -> p.getValue().equals(value))
                    .findFirst().get().getId();
              });
        }
        return createContainer((ChoiceQuestion) toEdit, choiceData);
      default:
        throw new IllegalStateException("No container creation implemented for type: " + data.getType());
    }
  }

  /**
   * @param question
   * @param data
   * @return
   */
  private BooleanContainer createContainer(final BooleanQuestion question, final BooleanContainerDto data) {

    final BooleanContainer container = this.containerRepository.save(BooleanContainer.builder()
        .dependsOn(data.getBoolDependsOn())
        .parent(question)
        .build());

    question.setContainer(container);
    this.questionRepository.save(question);

    return container;
  }

  /**
   * @param question
   * @param data
   * @return
   */
  private ChoiceContainer createContainer(final ChoiceQuestion question, final ChoiceContainerDto data) {

    final Iterable<Answer> answers = this.answerRepository.findAllById(data.getChoiceDependsOn());

    final ChoiceContainer container = this.containerRepository.save(ChoiceContainer.builder()
        .dependsOn(IterableUtils.toList(answers))
        .parent(question)
        .build());

    question.setContainer(container);
    this.questionRepository.save(question);

    return container;
  }

  /**
   * @param questionId
   * @param containerId
   * @param data
   */
  public void updateContainer(final Long containerId, final ContainerDto data) {

    final Container container = this.containerRepository.findById(containerId).get();
    final Question question = container.getParent();

    if (data.getType() != question.getType())
      throw new IllegalArgumentException("The specified container of type " + data.getType()
          + " is not valid for question type " + question.getType());

    final Question toEdit = getQuestionEditVersion(question);

    switch (data.getType()) {
      case BOOL:
        updateContainer((BooleanContainer) toEdit.getContainer(), (BooleanContainerDto) data);
        break;
      case CHOICE:
        // if question got copied -> answer IDs in DTO are no longer up-to-date -> seek new IDs
        final ChoiceContainerDto choiceData = (ChoiceContainerDto) data;
        if (toEdit != question) {
          final ChoiceQuestion originQuestion = (ChoiceQuestion) question; // origin
          final ChoiceQuestion copyQuestion = (ChoiceQuestion) toEdit; // copy

          choiceData.getChoiceDependsOn()
              .replaceAll(id -> {
                final String value = originQuestion.getAnswers().stream().filter(p -> p.getId().equals(id))
                    .findFirst().get().getValue();
                return copyQuestion.getAnswers().stream().filter(p -> p.getValue().equals(value))
                    .findFirst().get().getId();
              });
        }
        updateContainer((ChoiceContainer) toEdit.getContainer(), choiceData);
        break;
      default:
        break;
    }
  }

  private Question getQuestionEditVersion(final Question question) {

    final SearchResult result = getCurrentSurveyEdit(question);
    final Survey survey = result.getSurvey();

    Question toEdit = null;

    if (question.getReleaseStatus() == ReleaseStatusType.RELEASED) {
      // Create copy of sub tree of root question containing this question
      final CopyResult<Question> copyResult = copyQuestion(result.getRootQuestion(), question.getId());

      survey.getQuestions().replaceAll(origin -> origin.getId().equals(result.getRootQuestion().getId())
          ? copyResult.getCopy()
          : origin);

      if (copyResult.getConcernedCopiedQuestion() == null)
        throw new IllegalStateException(
            "No copy for specified origin question available! Origin question id: " + question.getId());

      toEdit = copyResult.getConcernedCopiedQuestion();
    } else {
      toEdit = question;
    }
    return toEdit;
  }

  /**
   * @param container
   * @param choiceData
   */
  private void updateContainer(final ChoiceContainer container, final ChoiceContainerDto data) {

    final Iterable<Answer> answers = this.answerRepository.findAllById(data.getChoiceDependsOn());
    container.setDependsOn(IterableUtils.toList(answers));
    this.containerRepository.save(container);
  }

  /**
   * @param container
   * @param data
   */
  private void updateContainer(final BooleanContainer container, final BooleanContainerDto data) {

    container.setDependsOn(data.getBoolDependsOn());
    this.containerRepository.save(container);
  }
}
