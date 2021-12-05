// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm.concurrent.task.backward;

import static org.sosy_lab.cpachecker.core.AnalysisDirection.BACKWARD;
import static org.sosy_lab.cpachecker.core.algorithm.Algorithm.AlgorithmStatus.SOUND_AND_PRECISE;
import static org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition.getDefaultPartition;
import static org.sosy_lab.cpachecker.util.AbstractStates.extractLocation;
import static org.sosy_lab.cpachecker.util.AbstractStates.extractStateByType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.blockgraph.Block;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm.AlgorithmStatus;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.message.MessageFactory;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.task.Task;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.util.ErrorOrigin;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.util.ShareableBooleanFormula;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.composite.BlockAwareAnalysisContinuationState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.SolverException;

@Options(prefix = "concurrent.task.backward")
public class BackwardAnalysisCore extends Task {
  private final Block target;
  private final ErrorOrigin origin;
  private final Solver solver;
  private final FormulaManagerView fMgr;
  private final BackwardAnalysisCoreStatistics statistics;
  private boolean hasCreatedContinuationRequest = false;
  
  private AlgorithmStatus status = SOUND_AND_PRECISE;
  
  public BackwardAnalysisCore(
      final Configuration pGlobalConfiguration, 
      final Block pBlock,
      final ReachedSet pReachedSet,
      final ErrorOrigin pOrigin,
      final Algorithm pAlgorithm,
      final ARGCPA pCPA,
      final Solver pSolver,
      final MessageFactory pMessageFactory,
      final LogManager pLogManager,
      final ShutdownNotifier pShutdownNotifier) throws InvalidConfigurationException {
    super(pCPA, pAlgorithm, pReachedSet, pMessageFactory, pLogManager, pShutdownNotifier);
    pGlobalConfiguration.inject(this);
        
    solver = pSolver;
    fMgr = solver.getFormulaManager();
    target = pBlock;
    origin = pOrigin;
    statistics = new BackwardAnalysisCoreStatistics(target);
  }

  @Override
  protected void execute() throws Exception {
    statistics.setTaskStarted();
    logManager.log(Level.FINE, "BackwardAnalysisCore on", target);

    AlgorithmStatus newStatus = algorithm.run(reached);
    status = status.update(newStatus);

    for (final AbstractState reachedState : reached.asCollection()) {
      processReachedState(reachedState);
    }

    Collection<AbstractState> waiting = new ArrayList<>(reached.getWaitlist());
    reached.clear();
    for (final AbstractState waitingState : waiting) {
      CFANode location = AbstractStates.extractLocation(waitingState);
      assert location != null;

      if (location != target.getEntry()) {
        reached.add(waitingState, cpa.getInitialPrecision(location, getDefaultPartition()));
      } else if (location.isLoopStart()) {
        assert waitingState instanceof ARGState;

        ARGState newStart =
            BlockAwareAnalysisContinuationState.createFromSource(waitingState, target, BACKWARD);
        reached.add(newStart, cpa.getInitialPrecision(location, getDefaultPartition()));
      }
    }

    shutdownNotifier.shutdownIfNecessary();
    if (reached.hasWaitingState()) {
      hasCreatedContinuationRequest = true;
      messageFactory.sendBackwardAnalysisContinuationRequest(
          target, origin, reached, algorithm, cpa, solver
      );
    }

    logManager.log(Level.FINE, "Completed BackwardAnalysis on", target);
    messageFactory.sendTaskCompletedMessage(this, status, statistics);
    statistics.setTaskCompleted();
  }

  private void processReachedState(final AbstractState state)
      throws InterruptedException, CPAException, InvalidConfigurationException {
    CFANode location = extractLocation(state);
    assert location != null;

    if (location == target.getEntry()) {
      PredicateAbstractState predState =
          extractStateByType(state, PredicateAbstractState.class);
      assert predState != null;

      PathFormula condition;
      if (predState.isAbstractionState()) {
        condition = predState.getAbstractionFormula().getBlockFormula();
      } else {
        condition = predState.getPathFormula();
      }

      ShareableBooleanFormula shareableCondition =
          new ShareableBooleanFormula(fMgr, condition);

      for (final Block predecessor : target.getPredecessors()) {
        messageFactory.sendBackwardAnalysisRequest(
            predecessor, location, target, origin, shareableCondition);
      }

      if (target.getPredecessors().isEmpty()) {
        try {
          if (solver.isUnsat(condition.getFormula())) {
            logManager.log(Level.INFO, "Verdict: Error condition unsatisfiable", condition);
          } else {
            logManager.log(Level.INFO, "Verdict: Satisfiable error condition!", condition);
            messageFactory.sendErrorReachedProgramEntryMessage(origin, status);
          }
        } catch (SolverException ignored) {
          logManager.log(Level.WARNING, "Unhandled Solver Exception!");
        }
      }
    }
  }

  @Override public String toString() {
    return "BackwardAnalysisCore on block with entry location " + target.getEntry();
  }
  
  public boolean hasCreatedContinuationRequest() {
    return hasCreatedContinuationRequest;
  }
}