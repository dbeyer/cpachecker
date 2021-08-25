// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm.concurrent.task.backward;

import com.google.common.collect.Table;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.blockgraph.Block;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CoreComponentsFactory;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.ShareableBooleanFormula;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.task.Task;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.task.TaskExecutor;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.task.TaskInvalidatedException;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.task.TaskManager;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.task.TaskRequest;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.task.TaskValidity;
import org.sosy_lab.cpachecker.core.reachedset.AggregatedReachedSets;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.specification.Specification;
import org.sosy_lab.cpachecker.cpa.composite.BlockAwareCompositeCPA;
import org.sosy_lab.cpachecker.cpa.composite.CompositeCPA;
import org.sosy_lab.cpachecker.cpa.location.LocationCPA;
import org.sosy_lab.cpachecker.cpa.location.LocationCPABackwards;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;

@Options(prefix = "concurrent.task.backward")
public class BackwardAnalysisRequest implements TaskRequest {
  private static volatile Configuration backward = null;
  private final Block target;
  private final Block source;
  private final CFANode start;
  private final ShareableBooleanFormula errorCondition;
  private final Algorithm algorithm;
  private final ReachedSet reached;
  private final BlockAwareCompositeCPA cpa;
  private final TaskManager taskManager;
  private final LogManager logManager;
  private final ShutdownNotifier shutdownNotifier;
  private final FormulaManagerView formulaManager;
  private final Solver solver;

  @SuppressWarnings("FieldMayBeFinal")
  @Option(description = "Configuration file for backward analysis during concurrent analysis.")
  @FileOption(FileOption.Type.OPTIONAL_INPUT_FILE)
  private Path configFile = null;

  public BackwardAnalysisRequest(
      final Block pTarget,
      final CFANode pStart,
      @Nullable final Block pSource,
      @Nullable final ShareableBooleanFormula pErrorCondition,
      final Configuration pConfig,
      final LogManager pLogger,
      final ShutdownNotifier pShutdownNotifier,
      final CFA pCFA,
      final TaskManager pTaskManager)
      throws InvalidConfigurationException, CPAException, InterruptedException {
    pConfig.inject(this);
    loadBackwardConfig();

    target = pTarget;
    start = pStart;
    source = pSource;
    taskManager = pTaskManager;
    logManager = pLogger;
    shutdownNotifier = pShutdownNotifier;

    CoreComponentsFactory factory =
        new CoreComponentsFactory(
            backward, logManager, pShutdownNotifier, new AggregatedReachedSets());
    reached = factory.createReachedSet();

    Specification emptySpec = Specification.alwaysSatisfied();
    CompositeCPA compositeCpa = (CompositeCPA) factory.createCPA(pCFA, emptySpec);

    if (compositeCpa.retrieveWrappedCpa(PredicateCPA.class) == null) {
      throw new InvalidConfigurationException(
          "Backward analysis requires a composite CPA with predicateCPA as component CPA.");
    }
    if (compositeCpa.retrieveWrappedCpa(LocationCPA.class) == null
        && compositeCpa.retrieveWrappedCpa(LocationCPABackwards.class) == null) {
      throw new InvalidConfigurationException(
          "Backward analysis requires a composite CPA with locationCPA as component CPA.");
    }

    cpa =
        (BlockAwareCompositeCPA)
            BlockAwareCompositeCPA.factory()
                .setConfiguration(backward)
                .setLogger(pLogger)
                .setShutdownNotifier(pShutdownNotifier)
                .set(pCFA, CFA.class)
                .set(target, Block.class)
                .set(compositeCpa, CompositeCPA.class)
                .createInstance();

    algorithm = factory.createAlgorithm(cpa, pCFA, emptySpec);

    PredicateCPA predicateCPA = cpa.retrieveWrappedCpa(PredicateCPA.class);
    assert predicateCPA != null;

    solver = predicateCPA.getSolver();
    formulaManager = solver.getFormulaManager();

    if (pErrorCondition == null) {
      BooleanFormula condition = formulaManager.getBooleanFormulaManager().makeTrue();
      errorCondition = new ShareableBooleanFormula(formulaManager, condition);
    } else {
      errorCondition = pErrorCondition;
    }
  }

  private void loadBackwardConfig() throws InvalidConfigurationException {
    if (backward == null) {
      synchronized (BackwardAnalysisRequest.class) {
        if (backward == null) {
          if (configFile != null) {
            try {
              backward = Configuration.builder().loadFromFile(configFile).build();
            } catch (IOException ignored) {
              logManager.log(
                  Level.SEVERE,
                  "Failed to load file ",
                  configFile,
                  ". Using default configuration.");
            }
          }

          if (backward == null) {
            backward =
                Configuration.builder()
                    .loadFromResource(BackwardAnalysisRequest.class, "predicateBackward.properties")
                    .build();
          }
        }
      }
    }
  }

  /**
   * {@link #finalize(Table, Map)} executes in the context of the central scheduler thread and
   * operates with exclusive and thread-safe access on the global map of calculated block summaries
   * and summary version counters. Each task type overwrites this method to implement global
   * synchronization using these structures.
   *
   * <p>For {@link BackwardAnalysisRequest}, this method performs the following steps:
   *
   * <ol>
   *   <li>tbd
   * </ol>
   *
   * @param pSummaries Global map of block summaries
   * @param pSummaryVersions Global map of block summary versions
   * @return {@link TaskValidity#VALID} if the task remains valid, {@link TaskValidity#INVALID} if
   *     the method has determined the task to be outdated and requests its cancellation
   */
  @Override
  public Task finalize(
      Table<Block, Block, ShareableBooleanFormula> pSummaries, Map<Block, Integer> pSummaryVersions)
      throws TaskInvalidatedException {
    assert Thread.currentThread().getName().equals(TaskExecutor.getThreadName())
        : "Only " + TaskExecutor.getThreadName() + " may call finalize() on task";

    ShareableBooleanFormula blockSummary = null;
    if (source != null) {
      blockSummary = pSummaries.get(target, source);
    }

    if (blockSummary == null) {
      BooleanFormulaManager bfMgr = solver.getFormulaManager().getBooleanFormulaManager();
      blockSummary = new ShareableBooleanFormula(solver.getFormulaManager(), bfMgr.makeTrue());
    }

    return new BackwardAnalysis(
        target,
        start,
        errorCondition,
        blockSummary,
        reached,
        algorithm,
        cpa,
        solver,
        taskManager,
        logManager,
        shutdownNotifier);
  }
}
