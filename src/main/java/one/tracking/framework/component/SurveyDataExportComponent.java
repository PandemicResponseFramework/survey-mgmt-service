/**
 *
 */
package one.tracking.framework.component;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import one.tracking.framework.domain.SurveyResponseData;
import one.tracking.framework.entity.health.StepCount;

/**
 * @author Marko Voß
 *
 */
@Component
public class SurveyDataExportComponent {

  private static final Logger LOG = LoggerFactory.getLogger(SurveyDataExportComponent.class);

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  private EntityManager entityManager;

  public void export(final Instant startTime, final Instant endTime, final OutputStream outStream) throws IOException {

    LOG.debug("Exporting data for interval: {} - {}", startTime, endTime);

    try (final Workbook workbook = WorkbookFactory.create(true)) {

      final CellStyle cellStyleDateTime = workbook.createCellStyle();
      final CreationHelper createHelper = workbook.getCreationHelper();
      cellStyleDateTime.setDataFormat(createHelper.createDataFormat().getFormat("m/d/yy h:mm"));

      exportSurveyResponseData(workbook, startTime, endTime, cellStyleDateTime);
      exportStepCounter(workbook, startTime, endTime, cellStyleDateTime);
      workbook.write(outStream);
    }
  }

  private void exportStepCounter(final Workbook workbook, final Instant startTime, final Instant endTime,
      final CellStyle cellStyleDateTime) {

    final Sheet sheet = workbook.createSheet("stepcount");
    // Create header
    final Row headerRow = sheet.createRow(0);
    headerRow.createCell(0).setCellValue("UserId");
    headerRow.createCell(1).setCellValue("StartTime");
    headerRow.createCell(2).setCellValue("EndTime");
    headerRow.createCell(3).setCellValue("StepCount");
    headerRow.createCell(4).setCellValue("Timestamp");

    int offset = 0;
    int rowIndex = 1;
    List<StepCount> stepCounts = getStepCountData(startTime, endTime, offset);

    while (!stepCounts.isEmpty()) {

      for (final StepCount data : stepCounts) {

        final Row row = sheet.createRow(rowIndex++);

        row.createCell(0).setCellValue(data.getUser().getId());

        Cell cell = row.createCell(1);
        cell.setCellValue(LocalDateTime.ofInstant(data.getStartTime(), ZoneOffset.UTC));
        cell.setCellStyle(cellStyleDateTime);

        cell = row.createCell(2);
        cell.setCellValue(LocalDateTime.ofInstant(data.getEndTime(), ZoneOffset.UTC));
        cell.setCellStyle(cellStyleDateTime);

        row.createCell(3).setCellValue(data.getStepCount());

        cell = row.createCell(4);
        cell.setCellValue(data.getUpdatedAt() == null
            ? LocalDateTime.ofInstant(data.getCreatedAt(), ZoneOffset.UTC)
            : LocalDateTime.ofInstant(data.getUpdatedAt(), ZoneOffset.UTC));
        cell.setCellStyle(cellStyleDateTime);
      }

      offset += stepCounts.size();
      stepCounts = getStepCountData(startTime, endTime, offset);
    }
  }

  private void exportSurveyResponseData(final Workbook workbook, final Instant startTime, final Instant endTime,
      final CellStyle cellStyleDateTime) {

    final Sheet sheet = workbook.createSheet("survey");
    // Create header
    final Row headerRow = sheet.createRow(0);
    headerRow.createCell(0).setCellValue("Survey");
    headerRow.createCell(1).setCellValue("StartTime");
    headerRow.createCell(2).setCellValue("EndTime");
    headerRow.createCell(3).setCellValue("UserId");
    headerRow.createCell(4).setCellValue("QuestionOrder");
    headerRow.createCell(5).setCellValue("QuestionType");
    headerRow.createCell(6).setCellValue("Question");
    headerRow.createCell(7).setCellValue("Answer");
    headerRow.createCell(8).setCellValue("AnswerVersion");
    headerRow.createCell(9).setCellValue("Skipped");
    headerRow.createCell(10).setCellValue("Valid");
    headerRow.createCell(11).setCellValue("AnswerTimestamp");

    int offset = 0;
    int rowIndex = 1;
    List<SurveyResponseData> responses = getSurveyResponseData(startTime, endTime, offset);

    while (!responses.isEmpty()) {

      for (final SurveyResponseData data : responses) {

        final Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(data.getNameId());

        Cell cell = row.createCell(1);
        cell.setCellValue(data.getStartTime().toInstant().atZone(ZoneOffset.UTC).toLocalDateTime());
        cell.setCellStyle(cellStyleDateTime);

        cell = row.createCell(2);
        cell.setCellValue(data.getEndTime().toInstant().atZone(ZoneOffset.UTC).toLocalDateTime());
        cell.setCellStyle(cellStyleDateTime);

        row.createCell(3).setCellValue(data.getUserId());
        row.createCell(4).setCellValue(data.getOrder().toString());
        row.createCell(5).setCellValue(data.getQuestionType());

        row.createCell(6).setCellValue(data.getCheckListEntry() == null ? data.getQuestion()
            : data.getQuestion() + ": " + data.getCheckListEntry());

        row.createCell(7).setCellValue(
            data.getBoolAnswer() != null ? data.getBoolAnswer().toString()
                : data.getTextAnswer() != null ? data.getTextAnswer()
                    : data.getNumberAnswer() != null ? data.getNumberAnswer().toString()
                        : data.getPredefinedAnswer() != null ? data.getPredefinedAnswer() : null);

        row.createCell(8).setCellValue(data.getVersion().toString());

        row.createCell(9).setCellValue(data.getSkipped());
        row.createCell(10).setCellValue(data.getValid());

        cell = row.createCell(11);
        cell.setCellValue(data.getCreatedAt().toInstant().atZone(ZoneOffset.UTC).toLocalDateTime());
        cell.setCellStyle(cellStyleDateTime);
      }

      offset += responses.size();
      responses = getSurveyResponseData(startTime, endTime, offset);
    }
  }

  private List<SurveyResponseData> getSurveyResponseData(final Instant startTime, final Instant endTime,
      final int offset) {

    final TypedQuery<SurveyResponseData> query = this.entityManager.createNamedQuery(
        "SurveyResponse.nativeFindByCreatedAtBetween", SurveyResponseData.class);
    query.setParameter(1, startTime);
    query.setParameter(2, endTime);
    query.setFirstResult(offset);
    query.setMaxResults(500);

    return this.transactionTemplate.execute(status -> {
      return query.getResultList();
    });
  }

  private List<StepCount> getStepCountData(final Instant startTime, final Instant endTime,
      final int offset) {

    final TypedQuery<StepCount> query = this.entityManager.createNamedQuery(
        "StepCount.findByCreatedAtBetween", StepCount.class);
    query.setParameter(1, startTime);
    query.setParameter(2, endTime);
    query.setFirstResult(offset);
    query.setMaxResults(500);

    return this.transactionTemplate.execute(status -> {
      return query.getResultList();
    });
  }
}
