# =============================================================================
# Distributed Matrix Multiplication – Makefile  (Windows CMD + Linux/macOS)
#
# STARTUP ORDER (each in its own CMD window):
#   1. make worker                        (on each worker machine)
#   2. make master WORKER_IPS=127.0.0.1  (on master machine, wait for "READY")
#   3. make backup MASTER_IP=127.0.0.1 WORKER_IPS=127.0.0.1  (after master is READY)
#   4. make client MASTER_IP=127.0.0.1   (connect and type your matrices)
#
# Single machine testing uses 127.0.0.1 for all IPs.
# Real network:  use actual IP addresses of each machine.
# =============================================================================

# ── Hardcoded IPs – change these to match your network ───────────────────────
MASTER_IP  :=  172.21.80.1
WORKER_IPS :=  172.21.80.1

# ── OS detection ──────────────────────────────────────────────────────────────
ifeq ($(OS),Windows_NT)
    SHELL       := cmd.exe
    .SHELLFLAGS := /C
    MKDIR       = if not exist "$(OUT_DIR)" mkdir "$(OUT_DIR)"
    CLEAN_CMD   = if exist "$(OUT_DIR)" rmdir /S /Q "$(OUT_DIR)"
    SENTINEL    = type nul > "$(OUT_DIR)\.compiled"
    FIND_JAVA   = dir /B /S "$(SRC_DIR)\*.java" > .sources.tmp
    COMPILE_CMD = $(JAVAC) $(JFLAGS) @.sources.tmp && del .sources.tmp
    ECHO        = echo
else
    SHELL       := /bin/sh
    MKDIR       = mkdir -p "$(OUT_DIR)"
    CLEAN_CMD   = rm -rf "$(OUT_DIR)"
    SENTINEL    = touch "$(OUT_DIR)/.compiled"
    FIND_JAVA   = find $(SRC_DIR) -name "*.java" > .sources.tmp
    COMPILE_CMD = $(JAVAC) $(JFLAGS) @.sources.tmp && rm -f .sources.tmp
    ECHO        = echo
endif

# ── Paths & Java ──────────────────────────────────────────────────────────────
SRC_DIR  := src/main/java
OUT_DIR  := out
JAVAC    := javac
JAVA     := java
JFLAGS   := -d $(OUT_DIR) -sourcepath $(SRC_DIR) -encoding UTF-8
PKG      := com.distributed.matmul

# ── Targets ───────────────────────────────────────────────────────────────────
.PHONY: all
all: compile
	@$(ECHO) Build complete.

.PHONY: compile
compile:
	@$(MKDIR)
	@$(FIND_JAVA)
	@$(COMPILE_CMD)
	@$(SENTINEL)
	@$(ECHO) Compilation successful.

# Step 1 – Run on each WORKER machine (no arguments needed)
.PHONY: worker
worker: all
	$(JAVA) -cp $(OUT_DIR) $(PKG).worker.WorkerNode

# Step 2 – Run on MASTER machine (registers on RMI immediately, no user input)
#          Pass worker IPs as space-separated list
#          Example: make master WORKER_IPS="192.168.1.10 192.168.1.11"
.PHONY: master
master: all
	$(JAVA) -cp $(OUT_DIR) $(PKG).master.MasterNode $(WORKER_IPS)

# Step 3 – Run AFTER master is showing "READY"
#          Example: make backup MASTER_IP=192.168.1.5 WORKER_IPS="192.168.1.10 192.168.1.11"
.PHONY: backup
backup: all
	$(JAVA) -cp $(OUT_DIR) $(PKG).backup.BackupNode $(MASTER_IP) $(WORKER_IPS)

# Step 4 – Run AFTER backup is showing "ping() -> true"
#          This is where you type your matrices
#          Example: make client MASTER_IP=192.168.1.5
.PHONY: client
client: all
	$(JAVA) -cp $(OUT_DIR) $(PKG).client.MatrixClient $(MASTER_IP)

.PHONY: clean
clean:
	@$(CLEAN_CMD)
	@$(ECHO) Clean done.

.PHONY: help
help:
	@$(ECHO) .
	@$(ECHO) === Distributed Matrix Multiplication ===
	@$(ECHO) .
	@$(ECHO) STARTUP ORDER (one CMD window each):
	@$(ECHO)   1. make worker
	@$(ECHO)   2. make master    (wait for READY message)
	@$(ECHO)   3. make backup    (wait for ping() -> true)
	@$(ECHO)   4. make client    (type your matrices here)
	@$(ECHO) .
	@$(ECHO) Override IPs at runtime:
	@$(ECHO)   make master WORKER_IPS=192.168.1.10
	@$(ECHO)   make backup MASTER_IP=192.168.1.5 WORKER_IPS=192.168.1.10
	@$(ECHO)   make client MASTER_IP=192.168.1.5
	@$(ECHO) .
