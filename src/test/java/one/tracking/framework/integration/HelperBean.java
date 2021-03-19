/**
 *
 */
package one.tracking.framework.integration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import one.tracking.framework.domain.Period;
import one.tracking.framework.entity.DeviceToken;
import one.tracking.framework.entity.SurveyInstance;
import one.tracking.framework.entity.SurveyResponse;
import one.tracking.framework.entity.User;
import one.tracking.framework.entity.meta.Answer;
import one.tracking.framework.entity.meta.IntervalType;
import one.tracking.framework.entity.meta.ReleaseStatusType;
import one.tracking.framework.entity.meta.ReminderType;
import one.tracking.framework.entity.meta.Survey;
import one.tracking.framework.entity.meta.container.BooleanContainer;
import one.tracking.framework.entity.meta.container.ChoiceContainer;
import one.tracking.framework.entity.meta.question.BooleanQuestion;
import one.tracking.framework.entity.meta.question.ChecklistEntry;
import one.tracking.framework.entity.meta.question.ChecklistQuestion;
import one.tracking.framework.entity.meta.question.ChoiceQuestion;
import one.tracking.framework.entity.meta.question.NumberQuestion;
import one.tracking.framework.entity.meta.question.Question;
import one.tracking.framework.entity.meta.question.RangeQuestion;
import one.tracking.framework.entity.meta.question.TextQuestion;
import one.tracking.framework.repo.AnswerRepository;
import one.tracking.framework.repo.ContainerRepository;
import one.tracking.framework.repo.DeviceTokenRepository;
import one.tracking.framework.repo.QuestionRepository;
import one.tracking.framework.repo.SurveyInstanceRepository;
import one.tracking.framework.repo.SurveyRepository;
import one.tracking.framework.repo.SurveyResponseRepository;
import one.tracking.framework.repo.UserRepository;
import one.tracking.framework.support.ServiceUtility;

/**
 * @author Marko Voß
 *
 */
public class HelperBean {

  @Autowired
  private AnswerRepository answerRepository;

  @Autowired
  private QuestionRepository questionRepository;

  @Autowired
  private ContainerRepository containerRepository;

  @Autowired
  private SurveyRepository surveyRepository;

  @Autowired
  private SurveyInstanceRepository surveyInstanceRepository;

  @Autowired
  private SurveyResponseRepository surveyResponseRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private DeviceTokenRepository deviceTokenRepository;

  @Autowired
  private ServiceUtility utility;

  public User createUser(final String userToken) {
    return this.userRepository.save(User.builder().userToken(userToken).build());
  }

  public DeviceToken addDeviceToken(final User user, final String deviceToken) {
    return this.deviceTokenRepository.save(DeviceToken.builder().user(user).token(deviceToken).build());
  }

  public void completeSimpleSurvey(final User user, final Survey survey) {

    final Period period = this.utility.getCurrentSurveyInstancePeriod(survey);

    final Optional<SurveyInstance> instanceOp =
        this.surveyInstanceRepository.findBySurveyAndStartTimeAndEndTime(survey, period.getStart(), period.getEnd());

    SurveyInstance instance;

    if (instanceOp.isEmpty()) {
      instance = this.surveyInstanceRepository.save(SurveyInstance.builder()
          .startTime(period.getStart())
          .endTime(period.getEnd())
          .survey(survey)
          .token("TOKEN")
          .build());
    } else {
      instance = instanceOp.get();
    }

    this.surveyResponseRepository.save(SurveyResponse.builder()
        .boolAnswer(true)
        .surveyInstance(instance)
        .question(survey.getQuestions().get(0))
        .user(user)
        .valid(true)
        .build());
  }

  public Survey createSimpleSurvey(final String nameId, final boolean withInterval) {
    return createSimpleSurvey(nameId, withInterval, null);
  }

  public Survey createSimpleSurvey(final String nameId, final boolean withInterval, final Survey dependsOn) {

    return this.surveyRepository.save(Survey.builder()
        .dependsOn(dependsOn == null ? null : dependsOn.getNameId())
        .questions(Collections.singletonList(createBoolQuestion("Q1", 0)))
        .nameId(nameId)
        .title("TITLE")
        .description("DESCRIPTION")
        .intervalStart(withInterval ? Instant.parse("2020-05-11T12:00:00Z") : null)
        .intervalType(withInterval ? IntervalType.WEEKLY : IntervalType.NONE)
        .intervalValue(withInterval ? 1 : null)
        .reminderType(withInterval ? ReminderType.AFTER_DAYS : ReminderType.NONE)
        .reminderValue(withInterval ? 2 : null)
        .releaseStatus(ReleaseStatusType.RELEASED)
        .build());
  }

  public Survey createSurvey(final String nameId) {
    return createSurvey(nameId, null);
  }

  public Survey createSurvey(final String nameId, final Survey dependsOn) {

    int order = 0;
    final List<Question> questions = new ArrayList<>(12);

    // boolean - no children
    questions.add(createBoolQuestion(
        "Q1", order++));

    // boolean - with children
    questions.add(createBoolQuestion(
        "Q2", order++,
        true,
        Collections.singletonList(createBoolQuestion("Q2C1", 0))));

    // single choice - no children
    questions.add(createChoiceQuestion(
        "Q3",
        order++, false,
        Arrays.asList(
            "Q3A1",
            "Q3A2",
            "Q3A3")));

    // multiple choice - no children
    questions.add(createChoiceQuestion(
        "Q4",
        order++, true,
        Arrays.asList(
            "Q4A1",
            "Q4A2",
            "Q4A3")));


    // single choice - with children
    questions.add(createChoiceQuestion(
        "Q5",
        order++, false,
        Arrays.asList(
            "Q5A1",
            "Q5A2",
            "Q5A3"),
        Arrays.asList(
            "Q5A1",
            "Q5A2"),
        Collections.singletonList(createBoolQuestion("Q5C1", 0))));

    // multiple choice - with children
    questions.add(createChoiceQuestion(
        "Q6",
        order++, true,
        Arrays.asList(
            "Q6A1",
            "Q6A2",
            "Q6A3"),
        Arrays.asList(
            "Q6A1",
            "Q6A2"),
        Collections.singletonList(createBoolQuestion("Q6C1", 0))));

    // checklist
    questions.add(createChecklistQuestion(
        "Q7",
        order++,
        Arrays.asList(
            createChecklistEntry("Q7E1", 0),
            createChecklistEntry("Q7E2", 1),
            createChecklistEntry("Q7E3", 2))));

    // Range - no children
    questions.add(createRangeQuestion(
        "Q8",
        order++,
        1, 10,
        5,
        "Q8MIN", "Q8MAX"));

    // TextField - no children
    questions.add(createTextQuestion(
        "Q9",
        order++,
        false,
        256));

    // TextArea - no children
    questions.add(createTextQuestion(
        "Q10",
        order++,
        true,
        512));

    // Number - no children
    questions.add(createNumberQuestion(
        "Q11",
        order++,
        0, 10, 5));

    return this.surveyRepository.save(Survey.builder()
        .dependsOn(dependsOn == null ? null : dependsOn.getNameId())
        .questions(questions)
        .nameId(nameId)
        .title("TITLE")
        .description("DESCRIPTION")
        .intervalStart(Instant.parse("2020-05-11T12:00:00Z"))
        .intervalType(IntervalType.WEEKLY)
        .intervalValue(1)
        .reminderType(ReminderType.AFTER_DAYS)
        .reminderValue(2)
        .releaseStatus(ReleaseStatusType.RELEASED)
        .build());
  }

  public Answer createAnswer(final String answer) {
    return this.answerRepository.save(Answer.builder()
        .value(answer)
        .build());
  }

  public Question createChoiceQuestion(
      final String question,
      final int order,
      final boolean multiple,
      final List<String> answers) {

    return createChoiceQuestion(question, order, multiple, answers, null, null);
  }

  public Question createChoiceQuestion(
      final String question,
      final int order,
      final boolean multiple,
      final List<String> answers,
      final List<String> dependsOn,
      final List<Question> questions) {

    if (answers == null || answers.isEmpty())
      throw new IllegalArgumentException("Answers must not be null or empty.");

    final List<Answer> answerEntities = answers.stream().map(f -> createAnswer(f)).collect(Collectors.toList());

    ChoiceQuestion parent = this.questionRepository.save(ChoiceQuestion.builder()
        .question(question)
        .ranking(order)
        .answers(answerEntities)
        .multiple(multiple)
        .optional(true)
        .build());

    if (questions != null && !questions.isEmpty()) {

      final List<Answer> dependsOnAnswers = dependsOn == null || dependsOn.isEmpty() ? null
          : answerEntities.stream().filter(p -> dependsOn.contains(p.getValue())).collect(Collectors.toList());

      final ChoiceContainer container = this.containerRepository.save(ChoiceContainer.builder()
          .dependsOn(dependsOnAnswers)
          .questions(questions)
          .parent(parent)
          .build());

      parent.setContainer(container);
      parent = this.questionRepository.save(parent);
    }

    return parent;
  }

  public BooleanQuestion createBoolQuestion(
      final String question,
      final int order) {

    return createBoolQuestion(question, order, null, null);
  }

  public BooleanQuestion createBoolQuestion(
      final String question,
      final int order,
      final Boolean dependsOn,
      final List<Question> questions) {

    BooleanQuestion parent = this.questionRepository.save(BooleanQuestion.builder()
        .question(question)
        .ranking(order)
        .optional(true)
        .build());

    if (questions != null && !questions.isEmpty()) {

      final BooleanContainer container = this.containerRepository.save(BooleanContainer.builder()
          .questions(questions)
          .dependsOn(dependsOn)
          .parent(parent)
          .build());

      parent.setContainer(container);
      parent = this.questionRepository.save(parent);
    }

    return parent;
  }

  public ChecklistEntry createChecklistEntry(
      final String question,
      final int order) {

    return this.questionRepository.save(ChecklistEntry.builder()
        .question(question)
        .ranking(order)
        .optional(true)
        .build());
  }

  public Question createTextQuestion(
      final String question,
      final int order,
      final boolean multiline,
      final int length) {

    final TextQuestion parent = this.questionRepository.save(TextQuestion.builder()
        .question(question)
        .multiline(multiline)
        .ranking(order)
        .length(length)
        .optional(true)
        .build());

    return parent;
  }

  public Question createChecklistQuestion(
      final String question,
      final int order,
      final List<ChecklistEntry> entries) {

    if (entries == null || entries.isEmpty())
      throw new IllegalArgumentException("Entries must not be null or empty.");

    return this.questionRepository.save(ChecklistQuestion.builder()
        .question(question)
        .entries(entries)
        .ranking(order)
        .optional(true)
        .build());
  }

  public Question createRangeQuestion(
      final String question,
      final int order,
      final int minValue, final int maxValue,
      final Integer defaultValue,
      final String minText, final String maxText) {

    final RangeQuestion parent = this.questionRepository.save(RangeQuestion.builder()
        .question(question)
        .minValue(minValue)
        .maxValue(maxValue)
        .defaultAnswer(defaultValue)
        .minText(minText)
        .maxText(maxText)
        .ranking(order)
        .optional(true)
        .build());

    return parent;
  }

  public Question createNumberQuestion(
      final String question,
      final int order,
      final Integer minValue, final Integer maxValue,
      final Integer defaultValue) {

    final NumberQuestion parent = this.questionRepository.save(NumberQuestion.builder()
        .question(question)
        .minValue(minValue)
        .maxValue(maxValue)
        .defaultAnswer(defaultValue)
        .ranking(order)
        .optional(true)
        .build());

    return parent;
  }
}
