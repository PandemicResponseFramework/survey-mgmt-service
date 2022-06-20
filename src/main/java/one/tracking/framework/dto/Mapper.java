/**
 *
 */
package one.tracking.framework.dto;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import one.tracking.framework.dto.meta.AnswerDto;
import one.tracking.framework.dto.meta.SurveyEditDto;
import one.tracking.framework.dto.meta.container.BooleanContainerDto;
import one.tracking.framework.dto.meta.container.ChoiceContainerDto;
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

/**
 * @author Marko Voß
 *
 */
public final class Mapper {

  private Mapper() {}

  public static final class SurveyEdit {

    private SurveyEdit() {}

    public static SurveyEditDto map(final Survey survey) {

      return SurveyEditDto.builder()
          .dependsOn(survey.getDependsOn())
          .description(survey.getDescription())
          .id(survey.getId())
          .intervalEnabled(survey.getIntervalType() != IntervalType.NONE)
          .intervalStart(survey.getIntervalStart())
          .intervalType(survey.getIntervalType())
          .intervalValue(survey.getIntervalValue())
          .nameId(survey.getNameId())
          .releaseStatus(survey.getReleaseStatus())
          .reminderEnabled(survey.getReminderType() != ReminderType.NONE)
          .reminderType(survey.getReminderType())
          .reminderValue(survey.getReminderValue())
          .title(survey.getTitle())
          .version(survey.getVersion())
          .questions(survey.getQuestions() == null ? null
              : survey.getQuestions().stream().map(Mapper::map).collect(Collectors.toList()))
          .build();
    }
  }

  /**
   *
   * @param entity
   * @return
   */
  public static QuestionDto map(final Question entity) {

    if (entity instanceof BooleanQuestion)
      return map((BooleanQuestion) entity);
    if (entity instanceof ChoiceQuestion)
      return map((ChoiceQuestion) entity);
    if (entity instanceof RangeQuestion)
      return map((RangeQuestion) entity);
    if (entity instanceof TextQuestion)
      return map((TextQuestion) entity);
    if (entity instanceof NumberQuestion)
      return map((NumberQuestion) entity);
    if (entity instanceof ChecklistQuestion)
      return map((ChecklistQuestion) entity);

    return null;
  }

  /**
   *
   * @param entity
   * @return
   */
  public static BooleanQuestionDto map(final BooleanQuestion entity) {

    return BooleanQuestionDto.builder()
        .id(entity.getId())
        .previousVersionId(entity.getPreviousVersion() == null ? null : entity.getPreviousVersion().getId())
        .order(entity.getRanking())
        .optional(entity.isOptional())
        .question(entity.getQuestion())
        .releaseStatus(entity.getReleaseStatus())
        .defaultAnswer(entity.getDefaultAnswer())
        .container(map(entity.getContainer()))
        .build();
  }

  /**
   *
   * @param entity
   * @return
   */
  public static ChoiceQuestionDto map(final ChoiceQuestion entity) {

    return ChoiceQuestionDto.builder()
        .id(entity.getId())
        .previousVersionId(entity.getPreviousVersion() == null ? null : entity.getPreviousVersion().getId())
        .order(entity.getRanking())
        .optional(entity.isOptional())
        .question(entity.getQuestion())
        .releaseStatus(entity.getReleaseStatus())
        .defaultAnswer(entity.getDefaultAnswer() == null ? null : entity.getDefaultAnswer().getId())
        .answers(entity.getAnswers().stream().map(Mapper::map).collect(Collectors.toList()))
        .multiple(entity.getMultiple())
        .container(map(entity.getContainer()))
        .build();
  }

  public static ChecklistQuestionDto map(final ChecklistQuestion entity) {
    return ChecklistQuestionDto.builder()
        .id(entity.getId())
        .previousVersionId(entity.getPreviousVersion() == null ? null : entity.getPreviousVersion().getId())
        .order(entity.getRanking())
        .optional(entity.isOptional())
        .question(entity.getQuestion())
        .releaseStatus(entity.getReleaseStatus())
        .entries(entity.getEntries().stream().map(Mapper::map).collect(Collectors.toList()))
        .build();
  }

  public static ChecklistEntryDto map(final ChecklistEntry entity) {
    return ChecklistEntryDto.builder()
        .id(entity.getId())
        .previousVersionId(entity.getPreviousVersion() == null ? null : entity.getPreviousVersion().getId())
        .order(entity.getRanking())
        .question(entity.getQuestion())
        .releaseStatus(entity.getReleaseStatus())
        .build();
  }

  /**
   *
   * @param entity
   * @return
   */
  public static RangeQuestionDto map(final RangeQuestion entity) {

    return RangeQuestionDto.builder()
        .id(entity.getId())
        .previousVersionId(entity.getPreviousVersion() == null ? null : entity.getPreviousVersion().getId())
        .order(entity.getRanking())
        .optional(entity.isOptional())
        .question(entity.getQuestion())
        .releaseStatus(entity.getReleaseStatus())
        .defaultAnswer(entity.getDefaultAnswer())
        .minValue(entity.getMinValue())
        .maxValue(entity.getMaxValue())
        .minText(entity.getMinText())
        .maxText(entity.getMaxText())
        .build();
  }

  /**
   *
   * @param entity
   * @return
   */
  public static NumberQuestionDto map(final NumberQuestion entity) {

    return NumberQuestionDto.builder()
        .id(entity.getId())
        .previousVersionId(entity.getPreviousVersion() == null ? null : entity.getPreviousVersion().getId())
        .order(entity.getRanking())
        .optional(entity.isOptional())
        .question(entity.getQuestion())
        .releaseStatus(entity.getReleaseStatus())
        .defaultAnswer(entity.getDefaultAnswer())
        .minValue(entity.getMinValue())
        .maxValue(entity.getMaxValue())
        .build();
  }

  /**
   *
   * @param entity
   * @return
   */
  public static TextQuestionDto map(final TextQuestion entity) {

    return TextQuestionDto.builder()
        .id(entity.getId())
        .previousVersionId(entity.getPreviousVersion() == null ? null : entity.getPreviousVersion().getId())
        .order(entity.getRanking())
        .optional(entity.isOptional())
        .question(entity.getQuestion())
        .releaseStatus(entity.getReleaseStatus())
        .multiline(entity.getMultiline())
        .length(entity.getLength())
        .build();
  }

  /**
   *
   * @param entity
   * @return
   */
  public static BooleanContainerDto map(final BooleanContainer entity) {

    if (entity == null)
      return null;

    return BooleanContainerDto.builder()
        .id(entity.getId())
        .boolDependsOn(entity.getDependsOn())
        .subQuestions(map(entity.getQuestions()))
        .build();
  }

  /**
   *
   * @param entity
   * @return
   */
  public static ChoiceContainerDto map(final ChoiceContainer entity) {

    if (entity == null)
      return null;

    final List<Long> dependsOn =
        entity.getDependsOn() == null || entity.getDependsOn().isEmpty()
            ? Collections.emptyList()
            : entity.getDependsOn().stream().map(c -> c.getId()).collect(Collectors.toList());

    return ChoiceContainerDto.builder()
        .id(entity.getId())
        .choiceDependsOn(dependsOn)
        .subQuestions(map(entity.getQuestions()))
        .build();
  }

  /**
   *
   * @param subQuestions
   * @return
   */
  public static List<QuestionDto> map(final List<Question> subQuestions) {

    if (subQuestions == null || subQuestions.isEmpty())
      return null;

    return subQuestions.stream().map(Mapper::map).collect(Collectors.toList());
  }

  /**
   *
   * @param entity
   * @return
   */
  public static AnswerDto map(final Answer entity) {
    return AnswerDto.builder()
        .id(entity.getId())
        .value(entity.getValue())
        .build();
  }
}
