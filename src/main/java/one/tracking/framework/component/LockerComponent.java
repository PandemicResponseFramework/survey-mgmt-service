/**
 *
 */
package one.tracking.framework.component;

import java.time.Instant;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import one.tracking.framework.config.TimeoutProperties;
import one.tracking.framework.entity.SchedulerLock;

/**
 * @author Marko Voß
 *
 */
@Component
public class LockerComponent {

  private static final Logger LOG = LoggerFactory.getLogger(LockerComponent.class);

  @Autowired
  private TimeoutProperties timeoutConfig;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  private EntityManager entityManager;

  public boolean lock(final String task) {

    try {
      return lockInternal(task);

    } catch (final DataIntegrityViolationException e) {
      // In case of concurrency by multiple instances, failing to store the same entry is valid
      // Unique index or primary key violation is to be expected
      LOG.debug("Expected violation: {}", e.getMessage());
    }

    return false;
  }

  private boolean lockInternal(final String taskName) {

    final TypedQuery<SchedulerLock> query =
        this.entityManager.createQuery("SELECT l FROM SchedulerLock l WHERE l.taskName = ?1", SchedulerLock.class);
    query.setParameter(1, taskName);

    final SchedulerLock lock = this.transactionTemplate.execute(status -> {
      status.flush();
      try {
        return query.getSingleResult();
      } catch (final NoResultException e) {
        return null;
      }
    });

    if (lock == null) {

      LOG.debug("Creating lock for task: {}", taskName);

      this.transactionTemplate.executeWithoutResult(status -> {
        this.entityManager.persist(SchedulerLock.builder()
            .taskName(taskName)
            .timeout((int) this.timeoutConfig.getTaskLock().toSeconds())
            .build());
        status.flush();
      });

      return true;

    } else if (Instant.now().isAfter(lock.getCreatedAt().plusSeconds(lock.getTimeout()))) {

      LOG.debug("Updating lock for task: {}", taskName);

      this.transactionTemplate.executeWithoutResult(status -> {
        this.entityManager.persist(lock.toBuilder()
            .createdAt(Instant.now())
            .timeout((int) this.timeoutConfig.getTaskLock().toSeconds())
            .build());
        status.flush();
      });

      return true;
    }

    return false;
  }

  public boolean unlock(final String taskName) {

    final Query query = this.entityManager.createQuery("DELETE FROM SchedulerLock l WHERE l.taskName = ?1");
    query.setParameter(1, taskName);

    return this.transactionTemplate.execute(status -> {
      final int count = query.executeUpdate();
      status.flush();
      return count;
    }) > 0;
  }


  // private boolean lockInternal(final String task) {
  //
  // final Optional<SchedulerLock> lockOp = this.schedulerLockRepository.findByTaskName(task);
  //
  // if (lockOp.isEmpty()) {
  //
  // LOG.debug("Creating lock for task: {}", task);
  //
  // this.schedulerLockRepository.save(SchedulerLock.builder()
  // .taskName(task)
  // .timeout((int) this.timeoutConfig.getTaskLock().toSeconds())
  // .build());
  // return true;
  //
  // } else {
  //
  // final SchedulerLock lock = lockOp.get();
  // // If lock exists but timed out, create a new lock
  // // This will happen, if the task has been executed previously
  // if (Instant.now().isAfter(lock.getCreatedAt().plusSeconds(lock.getTimeout()))) {
  //
  // LOG.debug("Updating lock for task: {}", task);
  //
  // this.schedulerLockRepository.save(lock.toBuilder()
  // .createdAt(Instant.now())
  // .timeout((int) this.timeoutConfig.getTaskLock().toSeconds())
  // .build());
  // return true;
  //
  // }
  //
  // return false;
  // }
  // }
  //
  // @Transactional
  // public void free(final String task) {
  //
  // LOG.debug("Deleting lock for task: {}", task);
  // this.schedulerLockRepository.deleteByTaskName(task);
  // }
}
