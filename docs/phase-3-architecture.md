# Phase 3 - Priorities and Processing

## 1. Summary

Every notification has a priority (HIGH, NORMAL or LOW). Submitting a notification only stores it as `PENDING`; a pool of background processors sends it later, always taking the highest-priority work first. The database is the queue: each processor claims a delivery with one atomic `UPDATE`, sends it, and records the result. A claimed delivery carries a lock with an expiry (a lease), so work interrupted by a crash or restart is released and sent again. Notifications that wait too long are promoted ahead of their priority, so nothing is starved. The state of the queue can be watched at `/monitor` without involving the processors.

## 2. Architectural changes (Phase 2 to Phase 3)

| Area | Phase 2 | Phase 3 |
|------|---------|---------|
| Pending notifications | `PENDING` deliveries, read in `id` order | Same table used as a queue: ordered by priority, aging and arrival |
| Delivery states | `PENDING`, `SENT`, `FAILED` | Adds `PROCESSING` (claimed by a processor) |
| Delivery fields | status, attempts, sentAt | Adds `lockedBy` (processor id) and `lockedAt` (lease start) |
| Processing logic | Inside `NotificationService.sendDelivery`, one long transaction | Moved to `DeliveryProcessor`: claim, send, record result, recover |
| Background work | One scheduled thread looping over all pending deliveries | Thread pool; the scheduled tick only recovers expired locks and wakes idle processors |
| Concurrency control | None needed (one worker) | Atomic compare-and-set `UPDATE`s plus a lock lease |
| Monitoring | Notification list and details pages | Adds `/monitor` (counts by state and priority, who is processing what, recent sends) |
| Failure simulation | Failures only | Adds a delay per send, so `PROCESSING` can be observed |

Unchanged from Phase 2: the notification, email and attempt model, the `NotificationSender` contract and its implementations, the maximum number of attempts, and the attempt history.

## 3. Components

| Component | Responsibility |
|-----------|----------------|
| `NotificationController`, `NotificationService` | Create notifications (deliveries start as `PENDING`, nothing is sent) and read them. `sendDelivery` is kept as a manual trigger and goes through the same claim and process path. |
| `DeliveryProcessor` | Chooses the next delivery, claims it, sends it, records the result, and releases expired locks. |
| `BackgroundWorker` | Owns the thread pool and the periodic tick. Decides when processors run, not what they do. |
| `NotificationDeliveryRepository` | Queue queries (aged, by priority), the `claim` and `finish` updates, and the monitoring queries. |
| `NotificationSender` (`EmailNotificationSender`, `SimulatedEmailSender`) | Performs the actual delivery. Unchanged contract. |
| `MonitorController`, `MonitorService`, `QueueSummaryDto`, `MonitorItemDto` | Read-only view of the queue. |

Request flow: `POST /notifications` creates the rows and returns immediately. Later, a processor started by the worker calls `DeliveryProcessor.processNext`: it selects a candidate, claims it, sends it through the matching `NotificationSender`, and records the outcome.

## 4. Delivery life cycle

| From | To | When | Done by |
|------|----|------|---------|
| (new) | `PENDING` | Notification created | `NotificationService` |
| `PENDING` | `PROCESSING` | A processor wins the claim (attempt count + 1) | `DeliveryProcessor` |
| `PROCESSING` | `SENT` | The sender succeeded | `DeliveryProcessor` |
| `PROCESSING` | `PENDING` | The sender failed and attempts remain | `DeliveryProcessor` |
| `PROCESSING` | `FAILED` | The sender failed and no attempts remain | `DeliveryProcessor` |
| `PROCESSING` | `PENDING` or `FAILED` | The lock expired (processing was interrupted) | `DeliveryProcessor.recoverExpiredLocks`, called by the worker tick |

Every attempt, including an interrupted one, is stored as a `DeliveryAttempt` with its number, result and error message. `SENT` and `FAILED` are final.

## 5. Justification of the decisions

### 5.1 How pending notifications are organized

The `notification_deliveries` table is the queue. A delivery is waiting when its status is `PENDING` and it still has attempts left.

- The Phase 2 model already stores state and history durably, so no second structure has to be kept in sync with it.
- An in-memory queue (for example a `PriorityBlockingQueue`) would lose every waiting notification on restart and could not be shared between instances.
- Priority lives on the `Notification`, and each delivery points to its notification, so the queue query joins the two tables.

### 5.2 How the processing order is determined

1. Aged deliveries first (see below), oldest notification first.
2. Otherwise, priority levels in the order of their `weight`: HIGH (1), then NORMAL (2), then LOW (3).
3. Inside one level, first in, first out: notification creation time, then id.

The order is applied in Java, one query per priority level, each limited to a small window of candidates (`claim-window`, default 10). The weights come from the `NotificationPriority` enum, so a new level only needs a new enum value, and the rule can be unit-tested without a database. A single `ORDER BY` on the stored priority would follow the alphabetical order of the names (HIGH, LOW, NORMAL), not the weights.

Preventing starvation: any delivery whose notification is older than `aging-threshold-seconds` (default 300) is served before everything else, oldest first. A continuous flow of HIGH notifications therefore delays a LOW notification by at most the threshold plus the time to serve the older aged work, which is finite because new arrivals are never older than it. Retried deliveries keep their original creation time, so an old notification that failed once stays ahead of newer ones.

Priority is not preemptive: a delivery that is already being sent is never interrupted. A HIGH notification that arrives waits only for the next free processor.

### 5.3 How background processing is performed

`BackgroundWorker` owns a `ThreadPoolTaskExecutor` with `notifications.worker.threads` threads (default 4).

- A scheduled tick runs every `polling-interval-ms` (default 10 s). It never sends anything itself. It first releases expired locks, then starts a processing loop on each idle thread.
- A processing loop claims and sends deliveries one after another until the queue is empty, then ends. A counter guarantees there are never more loops than threads.
- The user who submits a notification does not wait: the request only inserts the rows and returns.
- On shutdown, the loops stop and the pool waits up to 30 seconds for the sends in progress, so a normal stop leaves nothing in `PROCESSING`.

### 5.4 How multiple simultaneous processing operations are handled

Up to `threads` deliveries are processed at the same time in one instance, and more instances can be started on the same database. Each processor has an id (`instanceId/threadName`), stored in `lockedBy` while it holds a delivery. No database transaction spans the whole claim, send and record sequence, so nothing is locked while an email is being sent:

1. `claim`: a short transaction (one `UPDATE`).
2. Send: its own transaction, so the sender can load the email details lazily.
3. Record the result: another short transaction (`finish` plus the attempt row).

### 5.5 How conflicts are avoided when two processing units attempt the same notification

The claim is a compare-and-set in a single statement:

```
UPDATE NotificationDelivery d
SET status = PROCESSING, lockedBy = :processor, lockedAt = :now, attemptCount = attemptCount + 1
WHERE d.id = :id AND d.status = PENDING AND d.attemptCount < d.maxAttempts
```

The database serializes updates to the same row, so only one processor sees one affected row. Every other processor gets zero and moves on to the next candidate. Choosing candidates is only a hint; the update decides who wins. This needs no optimistic-lock exceptions and no row locks held during the send.

The result is recorded with the same technique: `finish` only changes the row if it is still `PROCESSING` and still locked by the same processor. If the lock was lost during a slow send (it expired and the delivery was taken over), the late result is discarded and logged instead of being recorded twice.

The attempt is counted when the delivery is claimed, as in Phase 2 ("count the attempt before calling the sender"). A processor that crashes mid-send therefore still consumes an attempt, which stops a notification that always crashes the sender from looping forever.

### 5.6 How continuity of processing is ensured after a failure or restart

- A sender failure is caught, recorded as a `FAILURE` attempt, and the delivery goes back to `PENDING` while attempts remain, or to `FAILED` when they are used up (the Phase 2 retry rules). The processor then continues with the next delivery.
- The claim writes a lock lease (`lockedAt`). If the application stops or crashes while a delivery is `PROCESSING`, the row keeps its lock. Once the lock is older than `lease-timeout-seconds` (default 60), the first worker tick, in this or any other instance, releases it: `PENDING` if attempts remain, otherwise `FAILED`. The attempt history records "Processing interrupted: the lock held by <processor> expired." and the delivery is processed again.
- All state is in the database, so a restart needs no extra recovery step: the first tick after startup does it.
- The release uses the same compare-and-set as `finish`, so two instances recovering at the same time record the interruption only once.

Delivery is at-least-once. If a send takes longer than the lease, another processor may take over and the notification can be sent twice. The lease must therefore be longer than the slowest send.

### 5.7 How the solution can evolve if the number of notifications increases significantly

Without changing the design:
- Increase `threads`, `claim-window` and shorten `polling-interval-ms`.
- Start more application instances on the same database. Claiming and lock recovery are already safe across instances.
- Add database indexes on `notification_deliveries (status, attempt_count)` and `notifications (priority, created_at)`, and archive or delete old `SENT` rows so the queue stays small.

Small extensions:
- A `nextAttemptAt` column to delay retries (see the trade-offs below).
- A heartbeat that renews `lockedAt` during long sends, so the lease can be short without risking duplicates.
- `SELECT ... FOR UPDATE SKIP LOCKED` (available in MySQL 8) to reduce contention when claiming.

Beyond the limits of a database queue:
- Move the waiting work to a message broker (RabbitMQ with priority queues, or Kafka with one topic per priority) and keep the database for state and history. `DeliveryProcessor`, the senders and the monitor keep their roles; only the way work is handed to processors changes.

## 6. Requirements coverage

| Requirement | How it is met |
|-------------|---------------|
| At least three priorities | `NotificationPriority`: HIGH, NORMAL, LOW |
| Higher priority considered first | Priority levels visited by weight (5.2) |
| Submitter does not wait | Creation only stores `PENDING` rows; sending happens in the pool (5.3) |
| Phase 2 retry rules | Maximum attempts, `PENDING` until exhausted, then `FAILED` (5.6) |
| State and history | Delivery status plus one attempt row per attempt, including interrupted ones |
| No indefinite wait | Aging promotes old notifications (5.2) |
| Monitoring independent of processing | `/monitor` reads the database only (section 8) |
| Multiple simultaneous processing requests | Thread pool and atomic claims (5.4, 5.5) |

## 7. Situations and tests

The tests use Mockito, in the same style as `DeliveryScenariosTest`. The repositories are mocks, and a small helper (`FakeDeliveryDatabase`) gives them the behaviour of the claim and finish updates. The real `DeliveryProcessor`, `NotificationService`, `BackgroundWorker` and `MonitorService` run on top of it, including with several threads.

| Situation | Behaviour | Tests |
|-----------|-----------|-------|
| Many notifications at once | Sent by priority, then arrival order; candidates read in small windows | `PriorityQueueTest`: `processNext_WhenManyNotificationsExist_ShouldSendByPriorityThenByArrival`, `claimNext_ShouldRequestOnlyTheConfiguredWindowOfCandidates` |
| HIGH arrives while NORMAL are pending | The next claim returns the HIGH one; work in progress is not interrupted | `PriorityQueueTest`: `processNext_WhenHighArrivesWhileNormalArePending_ShouldProcessHighNext` |
| Failure during processing | Attempt recorded, back to `PENDING` or `FAILED`, other deliveries continue | `DeliveryScenariosTest` (retry rules); `ProcessingScenariosTest`: `processNext_WhenOneDeliveryFails_ShouldContinueWithTheNextOne`; `PriorityQueueTest`: `processNext_WhenHighFailsAndAttemptsRemain_ShouldRetryItBeforeLowerPriorities` |
| Several processors at the same time | Exactly one winner per delivery; each delivery is sent once | `ProcessingScenariosTest`: `claim_WhenManyProcessorsClaimAtTheSameTime_ShouldOnlyOneSucceed`, `processNext_WhenManyProcessorsRunAtTheSameTime_ShouldSendEachDeliveryExactlyOnce`, `process_WhenLockWasLostDuringSend_ShouldDiscardTheResult`; `BackgroundWorkerTest`: `processPendingDeliveries_ShouldRunOneProcessorPerThread` |
| Shutdown or restart during processing | Expired locks are released, the interruption is recorded, the delivery is sent again | `ProcessingScenariosTest`: `recoverExpiredLocks_*`, `processNext_AfterRestart_ShouldSendTheInterruptedDeliveryAgain` |
| Waiting too long | Old LOW notification is served before HIGH ones | `PriorityQueueTest`: `claimNext_WhenLowIsOlderThanAgingThreshold_ShouldClaimItBeforeHigh`, `claimNext_WhenSeveralDeliveriesAreAged_ShouldClaimOldestFirstRegardlessOfPriority` |
| Submitter does not wait | The tick returns without waiting for the sends | `BackgroundWorkerTest`: `processPendingDeliveries_ShouldReturnWithoutWaitingForTheProcessing` |
| Monitoring | Counts by state and priority, current processor, last sends, no sender involved | `MonitorServiceTest` |

Run all tests with `./mvnw test` (`mvnw.cmd test` on Windows). The Spring context test uses H2 and the `test` profile, with the worker disabled.

## 8. Monitoring

`GET /monitor` shows, refreshed every 3 seconds:
- the number of deliveries in each state (`PENDING`, `PROCESSING`, `SENT`, `FAILED`);
- the pending deliveries by priority;
- the deliveries being processed, with the processor id, the time of the claim and the attempt number;
- the last ten deliveries sent.

It only reads the database through `MonitorService`, so it works whether or not the worker is running (`notifications.worker.enabled=false`) and never calls a sender. The notification details page also shows `PROCESSING` and the attempt history of each delivery. The notification list has a link to the monitor.

## 9. Configuration

| Property | Default | Meaning |
|----------|---------|---------|
| `notifications.delivery.max-attempts` | 3 | Attempts per delivery (Phase 2) |
| `notifications.worker.enabled` | true | Turns the background worker on or off |
| `notifications.worker.polling-interval-ms` | 10000 | Time between ticks |
| `notifications.worker.threads` | 4 | Parallel processors in one instance |
| `notifications.worker.claim-window` | 10 | Candidates read per query |
| `notifications.worker.lease-timeout-seconds` | 60 | Time after which a lock is considered abandoned |
| `notifications.worker.aging-threshold-seconds` | 300 | Age at which a notification is promoted ahead of its priority |
| `notifications.simulation.delay-ms` | 0 | Delay per send (`simulation` profile only; also set on the `/simulation` page) |

## 10. Database note (MySQL)

`ddl-auto=update` adds the new `locked_by` and `locked_at` columns by itself, but it does not change the existing `status` column, which MySQL stores as a native `ENUM`. Before running the new version on an existing database, either run

```
ALTER TABLE notification_deliveries MODIFY status ENUM('FAILED','PENDING','PROCESSING','SENT') NOT NULL;
```

or recreate the database with `docker compose down -v` followed by `docker compose up -d`.

## 11. Demonstration

1. Start MySQL (`docker compose up -d`) and run the application with the `simulation` profile (no real emails are sent).
2. Open `/simulation` and set "Delay per send" to 2000 ms, so each delivery stays in `PROCESSING` for two seconds.
3. Open `/monitor` in another tab.
4. Create about eight NORMAL notifications and a few LOW ones in `/notifications/new`. At the next tick, four processors start, take the NORMAL ones first, and the monitor shows four deliveries `PROCESSING` with different processor ids.
5. While they are running, create a HIGH notification. It is the next one claimed by the first free processor, ahead of the NORMAL and LOW ones still pending.
6. On `/simulation`, set "Fail the next N attempts" to 2 (or tick "Always fail") and create another notification: the details page shows the two failed attempts and then the success; with "Always fail" it ends as `FAILED` after three attempts.
7. To show recovery, stop the application while a delivery is `PROCESSING` and start it again: after the lease expires (60 s) the delivery returns to `PENDING`, the interruption appears in its attempt history, and it is sent.

## 12. Trade-offs and limits

- A failed delivery goes back to `PENDING` and is retried as soon as a processor is free, in priority order. Phase 2 retried at the next polling cycle. There is no waiting time between attempts; adding `nextAttemptAt` is the natural next step.
- Delivery is at-least-once, not exactly-once (see 5.6).
- A new notification can wait up to one polling interval before a processor wakes up.
- A database queue is limited by the throughput of the database (see 5.7 for the path beyond it).
