/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.predicate;

import static com.google.common.collect.Iterables.skip;
import static com.google.common.collect.Lists.transform;
import static org.sosy_lab.cpachecker.util.AbstractElements.extractElementByType;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.Timer;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.cpa.art.ARTElement;
import org.sosy_lab.cpachecker.cpa.art.ARTReachedSet;
import org.sosy_lab.cpachecker.cpa.art.ARTUtils;
import org.sosy_lab.cpachecker.cpa.art.AbstractARTBasedRefiner;
import org.sosy_lab.cpachecker.cpa.art.Path;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractElements;
import org.sosy_lab.cpachecker.util.predicates.CounterexampleTraceInfo;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Lists;

/**
 * This class provides a basic refiner implementation for PredicateCPA.
 * When a counterexample is found, it creates a path for it and checks it for
 * feasibiltiy, getting the interpolants if possible.
 *
 * It does not define any strategy for using the interpolants to update the
 * abstraction, this is left to sub-classes.
 *
 * It does, however, produce a nice error path in case of a feasible counterexample.
 */
@Options(prefix="cpa.predicate.refinement")
abstract class AbstractInterpolationBasedRefiner extends AbstractARTBasedRefiner {

  @Option(name="msatCexFile", type=Option.Type.OUTPUT_FILE,
      description="where to dump the counterexample formula in case the error location is reached")
  private File dumpCexFile = new File("counterexample.msat");

  final Timer totalRefinement = new Timer();
  final Timer errorPathProcessing = new Timer();

  protected final LogManager logger;
  protected final PredicateCPA predicateCpa;

  private final PredicateRefinementManager formulaManager;

  // TODO: this should be private
  protected List<CFANode> lastErrorPath = null;

  protected AbstractInterpolationBasedRefiner(final ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
    super(pCpa);

    predicateCpa = this.getArtCpa().retrieveWrappedCpa(PredicateCPA.class);
    if (predicateCpa == null) {
      throw new InvalidConfigurationException(getClass().getSimpleName() + " needs a PredicateCPA");
    }

    predicateCpa.getConfiguration().inject(this, AbstractInterpolationBasedRefiner.class);
    logger = predicateCpa.getLogger();

    formulaManager = new PredicateRefinementManager(predicateCpa.getFormulaManager(),
                                                    predicateCpa.getPathFormulaManager(),
                                                    predicateCpa.getTheoremProver(),
                                                    predicateCpa.getPredicateManager(),
                                                    predicateCpa.getConfiguration(),
                                                    logger);

    predicateCpa.getStats().addRefiner(this);
  }

  @Override
  protected CounterexampleInfo performRefinement(ARTReachedSet pReached, Path pPath) throws CPAException, InterruptedException {
    totalRefinement.start();

    Set<ARTElement> elementsOnPath = ARTUtils.getAllElementsOnPathsTo(pPath.getLast().getFirst()); // TODO: make this lazy?

    logger.log(Level.FINEST, "Starting refinement for PredicateCPA");

    // create path with all abstraction location elements (excluding the initial element)
    // the last element is the element corresponding to the error location
    List<Pair<ARTElement, CFANode>> path = transformPath(pPath);

    logger.log(Level.ALL, "Abstraction trace is", path);

    // create list of formulas on path
    List<Formula> formulas = getFormulasForPath(path, pPath.getFirst().getFirst());
    assert path.size() == formulas.size();

    // build the counterexample
    CounterexampleTraceInfo counterexample = formulaManager.buildCounterexampleTrace(formulas, elementsOnPath);

    // if error is spurious refine
    if (counterexample.isSpurious()) {
      logger.log(Level.FINEST, "Error trace is spurious, refining the abstraction");

      performRefinement(pReached, path, counterexample);

      totalRefinement.stop();
      return CounterexampleInfo.spurious();

    } else {
      // we have a real error
      logger.log(Level.FINEST, "Error trace is not spurious");
      Pair<Path, CounterexampleTraceInfo> preciseCounterexample = findPreciseErrorPath(pPath, counterexample);

      Path targetPath;
      if (preciseCounterexample == null) {
        logger.log(Level.WARNING, "The error path and the satisfying assignment may be imprecise!");
        targetPath = pPath;

      } else {
        targetPath = preciseCounterexample.getFirst();
        counterexample = preciseCounterexample.getSecond();
      }

      CounterexampleInfo cex = CounterexampleInfo.feasible(targetPath, counterexample.getCounterexample());

      final CounterexampleTraceInfo counterexample2 = counterexample;
      cex.addFurtherInformation(new Object() {
        // lazily call formulaManager.dumpCounterexample()
        @Override
        public String toString() {
          return formulaManager.dumpCounterexample(counterexample2);
        }
      }, dumpCexFile);

      totalRefinement.stop();
      return cex;
    }
  }

  protected abstract void performRefinement(ARTReachedSet pReached,
      List<Pair<ARTElement, CFANode>> path,
      CounterexampleTraceInfo counterexample) throws CPAException;


  private List<Pair<ARTElement, CFANode>> transformPath(Path pPath) throws CPATransferException {
    List<Pair<ARTElement, CFANode>> result = Lists.newArrayList();

    for (ARTElement ae : skip(transform(pPath, Pair.<ARTElement>getProjectionToFirst()), 1)) {
      PredicateAbstractElement pe = extractElementByType(ae, PredicateAbstractElement.class);
      if (pe.isAbstractionElement()) {
        CFANode loc = AbstractElements.extractLocation(ae);
        result.add(Pair.of(ae, loc));
      }
    }

    assert pPath.getLast().getFirst() == result.get(result.size()-1).getFirst();
    return result;
  }

  private static final Function<PredicateAbstractElement, Formula> GET_BLOCK_FORMULA
                = new Function<PredicateAbstractElement, Formula>() {
                    @Override
                    public Formula apply(PredicateAbstractElement e) {
                      assert e.isAbstractionElement();
                      return e.getAbstractionFormula().getBlockFormula();
                    };
                  };

  /**
   * Get the block formulas from a path.
   * @param path A list of all abstraction elements
   * @param initialElement The initial element of the analysis (= the root element of the ART)
   * @return A list of block formulas for this path.
   * @throws CPATransferException
   */
  protected List<Formula> getFormulasForPath(List<Pair<ARTElement, CFANode>> path, ARTElement initialElement) throws CPATransferException {

    List<Formula> formulas = transform(path,
        Functions.compose(
            GET_BLOCK_FORMULA,
        Functions.compose(
            AbstractElements.extractElementByTypeFunction(PredicateAbstractElement.class),
            Pair.<ARTElement>getProjectionToFirst())));

    return formulas;
  }

  private Pair<Path, CounterexampleTraceInfo> findPreciseErrorPath(Path pPath, CounterexampleTraceInfo counterexample) {
    errorPathProcessing.start();
    try {

      Map<Integer, Boolean> preds = counterexample.getBranchingPredicates();
      if (preds.isEmpty()) {
        logger.log(Level.WARNING, "No information about ART branches available!");
        return null;
      }

      // find correct path
      Path targetPath;
      try {
        ARTElement root = pPath.getFirst().getFirst();
        ARTElement target = pPath.getLast().getFirst();
        Set<ARTElement> pathElements = ARTUtils.getAllElementsOnPathsTo(target);

        targetPath = ARTUtils.getPathFromBranchingInformation(root, target,
            pathElements, preds);

      } catch (IllegalArgumentException e) {
        logger.logUserException(Level.WARNING, e, null);
        return null;
      }

      // try to create a better satisfying assignment by replaying this single path
      CounterexampleTraceInfo info2;
      try {
        info2 = formulaManager.checkPath(targetPath.asEdgesList());

      } catch (CPATransferException e) {
        // path is now suddenly a problem
        logger.logUserException(Level.WARNING, e, "Could not replay error path");
        return null;
      }

      if (info2.isSpurious()) {
        logger.log(Level.WARNING, "Inconsistent replayed error path!");
        return null;

      } else {
        return Pair.of(targetPath, info2);
      }

    } finally {
      errorPathProcessing.stop();
    }
  }

  PredicateRefinementManager getRefinementManager() {
    return formulaManager;
  }
}