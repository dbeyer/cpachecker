/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.arg.witnessexport;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Queues;
import java.io.IOException;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.Specification;
import org.sosy_lab.cpachecker.core.counterexample.AssumptionToEdgeAllocator;
import org.sosy_lab.cpachecker.core.counterexample.ConcreteState;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.ExpressionTreeReportingState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState;
import org.sosy_lab.cpachecker.cpa.value.refiner.ValueAnalysisConcreteErrorPathAllocator;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.WitnessType;
import org.sosy_lab.cpachecker.util.automaton.VerificationTaskMetaData;
import org.sosy_lab.cpachecker.util.expressions.ExpressionTree;
import org.sosy_lab.cpachecker.util.expressions.ExpressionTreeFactory;
import org.sosy_lab.cpachecker.util.expressions.ExpressionTrees;
import org.sosy_lab.cpachecker.util.expressions.LeafExpression;
import org.sosy_lab.cpachecker.util.expressions.Simplifier;

public class WitnessExporter {

  private final WitnessOptions options;

  private final CFA cfa;

  private final AssumptionToEdgeAllocator assumptionToEdgeAllocator;

  private final ExpressionTreeFactory<Object> factory = ExpressionTrees.newCachingFactory();
  private final Simplifier<Object> simplifier = ExpressionTrees.newSimplifier(factory);

  private final VerificationTaskMetaData verificationTaskMetaData;

  public WitnessExporter(
      final Configuration pConfig,
      final LogManager pLogger,
      final Specification pSpecification,
      final CFA pCFA)
      throws InvalidConfigurationException {
    Preconditions.checkNotNull(pConfig);
    options = new WitnessOptions();
    pConfig.inject(options);
    this.cfa = pCFA;
    this.assumptionToEdgeAllocator =
        AssumptionToEdgeAllocator.createWithoutPointerConstants(
            pConfig, pLogger, pCFA.getMachineModel());
    this.verificationTaskMetaData = new VerificationTaskMetaData(pConfig, pSpecification);
  }

  public void writeErrorWitness(
      Appendable pTarget,
      final ARGState pRootState,
      final Predicate<? super ARGState> pIsRelevantState,
      Predicate<? super Pair<ARGState, ARGState>> pIsRelevantEdge,
      CounterexampleInfo pCounterExample)
      throws IOException {

    String defaultFileName = getInitialFileName(pRootState);
    WitnessWriter writer =
        new WitnessWriter(
            options,
            cfa,
            verificationTaskMetaData,
            factory,
            simplifier,
            defaultFileName,
            WitnessType.VIOLATION_WITNESS,
            InvariantProvider.TrueInvariantProvider.INSTANCE);
    writer.writePath(
        pTarget,
        pRootState,
        pIsRelevantState,
        pIsRelevantEdge,
        Optional.of(pCounterExample),
        GraphBuilder.ARG_PATH);
  }

  public void writeProofWitness(
      Appendable pTarget,
      final ARGState pRootState,
      final Predicate<? super ARGState> pIsRelevantState,
      Predicate<? super Pair<ARGState, ARGState>> pIsRelevantEdge)
      throws IOException {
    writeProofWitness(
        pTarget,
        pRootState,
        pIsRelevantState,
        pIsRelevantEdge,
        new InvariantProvider() {

          @Override
          public ExpressionTree<Object> provideInvariantFor(
              CFAEdge pEdge, Optional<? extends Collection<? extends ARGState>> pStates) {
            // TODO interface for extracting the information from states, similar to FormulaReportingState
            Set<ExpressionTree<Object>> stateInvariants = new HashSet<>();
            if (!pStates.isPresent()) {
              return ExpressionTrees.getTrue();
            }
            for (ARGState state : pStates.get()) {
              ValueAnalysisState valueAnalysisState =
                  AbstractStates.extractStateByType(state, ValueAnalysisState.class);
              ExpressionTree<Object> stateInvariant = ExpressionTrees.getTrue();
              if (valueAnalysisState != null) {
                ConcreteState concreteState =
                    ValueAnalysisConcreteErrorPathAllocator.createConcreteState(valueAnalysisState);
                Iterable<AExpressionStatement> invariants =
                    WitnessWriter.ASSUMPTION_FILTER.apply(
                        assumptionToEdgeAllocator.allocateAssumptionsToEdge(pEdge, concreteState))
                    .getExpStmts();
                for (AExpressionStatement expressionStatement : invariants) {
                  stateInvariant =
                      factory.and(
                          stateInvariant,
                          LeafExpression.of((Object) expressionStatement.getExpression()));
                }
              }

              String functionName = pEdge.getSuccessor().getFunctionName();
              for (ExpressionTreeReportingState etrs :
                  AbstractStates.asIterable(state).filter(ExpressionTreeReportingState.class)) {
                stateInvariant =
                    factory.and(
                        stateInvariant,
                        etrs.getFormulaApproximation(
                            cfa.getFunctionHead(functionName), pEdge.getSuccessor()));
              }
              stateInvariants.add(stateInvariant);
            }
            ExpressionTree<Object> invariant = factory.or(stateInvariants);
            return invariant;
          }
        });
  }

  public void writeProofWitness(
      Appendable pTarget,
      final ARGState pRootState,
      final Predicate<? super ARGState> pIsRelevantState,
      Predicate<? super Pair<ARGState, ARGState>> pIsRelevantEdge,
      InvariantProvider pInvariantProvider)
      throws IOException {
    Preconditions.checkNotNull(pTarget);
    Preconditions.checkNotNull(pRootState);
    Preconditions.checkNotNull(pIsRelevantState);
    Preconditions.checkNotNull(pIsRelevantEdge);
    Preconditions.checkNotNull(pInvariantProvider);

    String defaultFileName = getInitialFileName(pRootState);
    WitnessWriter writer =
        new WitnessWriter(
            options,
            cfa,
            verificationTaskMetaData,
            factory,
            simplifier,
            defaultFileName,
            WitnessType.CORRECTNESS_WITNESS,
            pInvariantProvider);
    writer.writePath(
        pTarget,
        pRootState,
        pIsRelevantState,
        pIsRelevantEdge,
        Optional.empty(),
        GraphBuilder.CFA_FROM_ARG);
  }

  private String getInitialFileName(ARGState pRootState) {
    Deque<CFANode> worklist = Queues.newArrayDeque(AbstractStates.extractLocations(pRootState));
    Set<CFANode> visited = new HashSet<>();
    while (!worklist.isEmpty()) {
      CFANode l = worklist.pop();
      visited.add(l);
      for (CFAEdge e : CFAUtils.leavingEdges(l)) {
        Set<FileLocation> fileLocations = CFAUtils.getFileLocationsFromCfaEdge(e);
        if (fileLocations.size() > 0) {
          String fileName = fileLocations.iterator().next().getFileName();
          if (fileName != null) {
            return fileName;
          }
        }
        if (!visited.contains(e.getSuccessor())) {
          worklist.push(e.getSuccessor());
        }
      }
    }

    throw new RuntimeException("Could not determine file name based on abstract state!");
  }
}
