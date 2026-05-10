# Distributed Matrix Multiplication System

A real distributed computing system implemented in Java using RMI (Remote Method
Invocation). Workers run in separate processes, communicate over the network, and
the master tolerates worker failures with automatic local fallback.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT                                   │
│  calls master.multiply(A, B)                                    │
└────────────────────────────┬────────────────────────────────────┘
                             │ direct call (same process or RMI)
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                     MASTER NODE  (port 1099)                    │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ DistributedMaster                                         │  │
│  │  • Validates dimensions                                   │  │
│  │  • Builds TaskQueue (splits A by rows)                    │  │
│  │  • Launches one dispatcher thread per worker              │  │
│  │  • Each thread: poll task → send to worker → collect      │  │
│  │  • On failure: LocalFallback.compute(task)                │  │
│  │  • Merges TaskResults into final matrix                   │  │
│  │                                                           │  │
│  │  Implements MasterService { ping() }  ← heartbeat only    │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  WorkerAddress[] (in-memory array, no config files)             │
│    [0] localhost:2001/WorkerService-1                           │
│    [1] localhost:2002/WorkerService-2                           │
│    [2] localhost:2003/WorkerService-3                           │
└──────┬───────────────────┬──────────────────┬───────────────────┘
       │ RMI               │ RMI              │ RMI
       ▼                   ▼                  ▼
┌─────────────┐   ┌─────────────┐   ┌─────────────┐
│  WORKER 1   │   │  WORKER 2   │   │  WORKER 3   │
│  port 2001  │   │  port 2002  │   │  port 2003  │
│             │   │             │   │             │
│ compute()   │   │ compute()   │   │ compute()   │
│  ↓          │   │  ↓          │   │  ↓          │
│ MatrixUtils │   │ MatrixUtils │   │ MatrixUtils │
│ .multiply() │   │ .multiply() │   │ .multiply() │
└─────────────┘   └─────────────┘   └─────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    BACKUP NODE                                  │
│                                                                 │
│  ScheduledExecutor → heartbeatCycle() every 2 s                 │
│    │                                                            │
│    └→ pingExecutor.submit(master.ping())  timeout=1.5s          │
│         ├─ OK  → reset miss counter                             │
│         └─ FAIL → consecutiveMisses++                           │
│              └─ misses >= 3 → performTakeover()                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
distributed-matmul/
├── build-and-run.sh               # compile + run everything
├── README.md
└── src/main/java/com/distributed/matmul/
    ├── common/                    # shared interfaces + models (all Serializable)
    │   ├── MasterService.java     # Remote interface: ping() only
    │   ├── WorkerService.java     # Remote interface: compute(Task) only
    │   ├── Task.java              # Work unit shipped to a worker
    │   ├── TaskResult.java        # Partial rows returned by a worker
    │   ├── WorkerAddress.java     # Host + port + service name struct
    │   └── MatrixUtils.java       # Math helpers shared by master and workers
    │
    ├── worker/
    │   ├── WorkerServiceImpl.java # UnicastRemoteObject implementing compute()
    │   └── WorkerNode.java        # Entry point: binds worker to RMI registry
    │
    ├── master/
    │   ├── DistributedMaster.java # Core: task dispatch, threading, fault tolerance
    │   ├── MasterNode.java        # Entry point: registers master heartbeat
    │   ├── WorkerProxy.java       # Per-worker RMI handle with timeout
    │   ├── TaskQueue.java         # Thread-safe row-chunk work queue
    │   └── LocalFallback.java     # In-process computation on worker failure
    │
    ├── backup/
       └── BackupNode.java        # Heartbeat monitor + takeover logic
       
```

---

## RMI Interfaces

### WorkerService (computation only)

```java
public interface WorkerService extends Remote {
    TaskResult compute(Task task) throws RemoteException;
}
```

### MasterService (heartbeat only)

```java
public interface MasterService extends Remote {
    boolean ping() throws RemoteException;
}
```

---

## Data Flow

### Happy Path

```
master.multiply(A, B)
  │
  ├─ validate dimensions
  ├─ TaskQueue(A, B, numWorkers)
  │     splits A into row-chunks:
  │       rows=4, workers=3 → chunks [0,2), [2,3), [3,4)
  │
  ├─ ExecutorService(3 threads)
  │     Thread-0 → WorkerProxy[0].compute(Task[0,2))
  │     Thread-1 → WorkerProxy[1].compute(Task[2,3))
  │     Thread-2 → WorkerProxy[2].compute(Task[3,4))
  │
  ├─ Each WorkerProxy:
  │     RMI lookup (cached after first call)
  │     submit to single-thread executor
  │     Future.get(10s timeout)
  │
  ├─ Workers run concurrently:
  │     MatrixUtils.multiplyRows(aSubset, B, colsA, colsB)
  │     return TaskResult(startRow, endRow, rows[][])
  │
  ├─ Results arrive in BlockingQueue<TaskResult>
  │     merger loop: result[startRow + i] = tr.rows[i]
  │
  └─ return double[][] result
```

### Failure Path

```
WorkerProxy.compute(task) throws Exception
  │
  ├─ dispatcher thread catches the exception
  ├─ logs: "Worker X FAILED on Task[r1,r2)"
  └─ LocalFallback.compute(task)
       same MatrixUtils.multiplyRows() call
       returns TaskResult as if the worker succeeded
       → result is placed in BlockingQueue as normal
```

---

## Key Design Decisions

### 1. Single remote method per role

Workers expose only `compute(Task)`. The master exposes only `ping()`. This enforces
clean separation of concerns and prevents accidental coupling.

### 2. In-memory worker registry

Worker addresses are stored in a plain Java array inside `MasterNode`:

```java
public static final WorkerAddress[] WORKER_ADDRESSES = {
    new WorkerAddress("localhost", 2001, "WorkerService-1"),
    new WorkerAddress("localhost", 2002, "WorkerService-2"),
    new WorkerAddress("localhost", 2003, "WorkerService-3"),
};
```

No files, databases, or external registries.

### 3. Per-worker timeout via Future

RMI doesn't offer per-call timeouts. `WorkerProxy` runs each call in a dedicated
single-thread executor and uses `Future.get(10, SECONDS)` to enforce the limit.
On timeout the Future is cancelled and the stub is invalidated to force re-lookup.

### 4. Dynamic task distribution handles edge cases

| Scenario         | Behaviour                                          |
|------------------|----------------------------------------------------|
| rows > workers   | chunkSize = ⌈rows/workers⌉; last chunk may be smaller |
| workers > rows   | chunkSize = 1; excess workers poll empty queue and exit |
| workers = 0      | falls back to single local execution               |

### 5. Backup heartbeat with miss tolerance

The backup tolerates 3 consecutive missed beats (= 6 s) before declaring failure.
A transient network glitch resets on the next successful ping.

---


### Separate processes (real distributed)

```bash
# Terminal 1
java -cp out -Djava.rmi.server.hostname=localhost \
     com.distributed.matmul.worker.WorkerNode worker-1 2001 WorkerService-1

# Terminal 2
java -cp out -Djava.rmi.server.hostname=localhost \
     com.distributed.matmul.worker.WorkerNode worker-2 2002 WorkerService-2

# Terminal 3
java -cp out -Djava.rmi.server.hostname=localhost \
     com.distributed.matmul.worker.WorkerNode worker-3 2003 WorkerService-3

# Terminal 4
java -cp out -Djava.rmi.server.hostname=localhost \
     com.distributed.matmul.master.MasterNode

# Terminal 5
java -cp out -Djava.rmi.server.hostname=localhost \
     com.distributed.matmul.backup.BackupNode
```

To simulate a worker failure, just kill one of the worker terminals.
The master will log the failure and compute those rows locally.

To simulate master failure, kill terminal 4.
The backup will detect it within 6 seconds and print the takeover sequence.

---

## Demo Scenarios

| # | Scenario                        | What it verifies                                    |
|---|---------------------------------|-----------------------------------------------------|
| 1 | Normal 4×3 × 3×2               | Basic distributed computation + correctness         |
| 2 | rows > workers (5 rows, 2 wkrs) | Chunk distribution, last chunk smaller              |
| 3 | workers > rows (2 rows, 3 wkrs) | Third worker gets no tasks; still correct           |
| 4 | 2 dead workers out of 3        | LocalFallback fires; final result still correct     |
| 5 | Backup with ghost master        | Miss counter reaches 3; takeover sequence prints    |
| 6 | 20×15 × 15×12 random           | Stress test with larger matrices                    |

---

## Fault Tolerance Guarantees

- **Worker crash**: detected immediately (RemoteException); task computed locally.
- **Worker timeout**: detected after 10 s; task computed locally.
- **Multiple worker failures**: each dispatcher thread handles its own failures
  independently. All rows will be computed even if every worker dies.
- **Master crash**: backup detects within MAX_MISSED × INTERVAL ≤ 6 s.
- **Result correctness**: the merger loop always receives exactly one TaskResult
  per task (either from the worker or from LocalFallback), so every row of the
  result matrix is filled exactly once.
