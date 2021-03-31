// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.value;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.MemoryHandler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.counterexample.ConcreteStatePath;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.DelegateAbstractDomain;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithConcreteCex;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.pcc.ProofChecker.ProofCheckerCPA;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.invariants.MemoryLocationExtractor;
import org.sosy_lab.cpachecker.cpa.predicate.PredicatePrecision;
import org.sosy_lab.cpachecker.cpa.predicate.PredicatePrecisionBootstrapper.InitialPredicatesOptions;
import org.sosy_lab.cpachecker.cpa.predicate.persistence.PredicateMapParser;
import org.sosy_lab.cpachecker.cpa.predicate.persistence.PredicatePersistenceUtils.PredicateParsingFailedException;
import org.sosy_lab.cpachecker.cpa.value.ExpressionValueVisitor.MemoryLocationEvaluator;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisPrecisionAdjustment.PrecAdjustmentOptions;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisPrecisionAdjustment.PrecAdjustmentStatistics;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisTransferRelation.ValueTransferOptions;
import org.sosy_lab.cpachecker.cpa.value.refiner.ValueAnalysisConcreteErrorPathAllocator;
import org.sosy_lab.cpachecker.cpa.value.symbolic.ConstraintsStrengthenOperator;
import org.sosy_lab.cpachecker.cpa.value.symbolic.SymbolicValueAnalysisPrecisionAdjustment;
import org.sosy_lab.cpachecker.cpa.value.symbolic.SymbolicValueAnalysisPrecisionAdjustment.SymbolicStatistics;
import org.sosy_lab.cpachecker.cpa.value.symbolic.SymbolicValueAssigner;
import org.sosy_lab.cpachecker.cpa.value.symbolic.type.SymbolicValue;
import org.sosy_lab.cpachecker.cpa.value.symbolic.util.MemoryLocationLocator;
import org.sosy_lab.cpachecker.util.StateToFormulaWriter;
import org.sosy_lab.cpachecker.util.predicates.AbstractionManager;
import org.sosy_lab.cpachecker.util.predicates.AbstractionPredicate;
import org.sosy_lab.cpachecker.util.predicates.bdd.BDDManagerFactory;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.MemoryRegion;
import org.sosy_lab.cpachecker.util.predicates.regions.RegionManager;
import org.sosy_lab.cpachecker.util.predicates.regions.SymbolicRegionManager;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;
import org.sosy_lab.cpachecker.util.states.MemoryLocationValueHandler;

@Options(prefix = "cpa.value")
public class ValueAnalysisCPA extends AbstractCPA
    implements ConfigurableProgramAnalysisWithBAM,
        StatisticsProvider,
        ProofCheckerCPA,
        ConfigurableProgramAnalysisWithConcreteCex {

  private enum UnknownValueStrategy {
    /** This strategy discards all unknown values from the value analysis state */
    DISCARD,
    /**
     * This strategy introduces a new {@link SymbolicValue} for each unknown value. Symbolic values
     * should probably be used in conjunction with the ConstraintsCPA. Otherwise, symbolic values
     * will be created, but not evaluated.
     */
    INTRODUCE_SYMBOLIC,
  }

  @Option(secure=true, name="merge", toUppercase=true, values={"SEP", "JOIN"},
      description="which merge operator to use for ValueAnalysisCPA")
  private String mergeType = "SEP";

  @Option(
      secure = true,
      name = "stop",
      toUppercase = true,
      values = {"SEP", "JOIN", "NEVER", "EQUALS"},
      description = "which stop operator to use for ValueAnalysisCPA")
  private String stopType = "SEP";

  @Option(secure=true, description="get an initial precision from file")
  @FileOption(FileOption.Type.OPTIONAL_INPUT_FILE)
  private Path initialPrecisionFile = null;

  @Option(secure = true, description = "get an initial precision from a predicate analysis file")
  @FileOption(FileOption.Type.OPTIONAL_INPUT_FILE)
  private Path initialPredicatePrecisionFile = null;

  @Option(
      secure = true,
      name = "unknownValueHandling",
      description = "Tells the value analysis how to handle unknown values.")
  private UnknownValueStrategy unknownValueStrategy = UnknownValueStrategy.DISCARD;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(ValueAnalysisCPA.class);
  }

  private VariableTrackingPrecision precision;
  private final ValueAnalysisCPAStatistics statistics;
  private final StateToFormulaWriter writer;

  private final Configuration config;
  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final CFA cfa;

  private boolean refineablePrecisionSet = false;
  private ValueAnalysisConcreteErrorPathAllocator errorPathAllocator;

  private MemoryLocationValueHandler unknownValueHandler;
  private final ConstraintsStrengthenOperator constraintsStrengthenOperator;
  private final ValueTransferOptions transferOptions;
  private final PrecAdjustmentOptions precisionAdjustmentOptions;
  private final PrecAdjustmentStatistics precisionAdjustmentStatistics;

  private SymbolicStatistics symbolicStats;

  private ValueAnalysisCPA(Configuration config, LogManager logger,
      ShutdownNotifier pShutdownNotifier, CFA cfa) throws InvalidConfigurationException {
    super(DelegateAbstractDomain.<ValueAnalysisState>getInstance(), null);
    this.config           = config;
    this.logger           = logger;
    this.shutdownNotifier = pShutdownNotifier;
    this.cfa              = cfa;

    config.inject(this, ValueAnalysisCPA.class);

    precision           = initializePrecision(config, cfa);
    statistics          = new ValueAnalysisCPAStatistics(this, config);
    writer = new StateToFormulaWriter(config, logger, shutdownNotifier, cfa);
    errorPathAllocator = new ValueAnalysisConcreteErrorPathAllocator(config, logger, cfa.getMachineModel());

    unknownValueHandler = createUnknownValueHandler();

    constraintsStrengthenOperator =
        new ConstraintsStrengthenOperator(config, logger);
    transferOptions = new ValueTransferOptions(config);
    precisionAdjustmentOptions = new PrecAdjustmentOptions(config, cfa);
    precisionAdjustmentStatistics = new PrecAdjustmentStatistics();
  }

  private MemoryLocationValueHandler createUnknownValueHandler()
      throws InvalidConfigurationException {
    switch (unknownValueStrategy) {
      case DISCARD:
        return new UnknownValueAssigner();
      case INTRODUCE_SYMBOLIC:
        return new SymbolicValueAssigner(config);
      default:
        throw new AssertionError("Unhandled strategy: " + unknownValueStrategy);
    }
  }

  private VariableTrackingPrecision initializePrecision(Configuration pConfig, CFA pCfa) throws InvalidConfigurationException {
    if (initialPrecisionFile == null && initialPredicatePrecisionFile == null) {
      logger.log(Level.INFO,"both precisionfiles are null");

      return VariableTrackingPrecision.createStaticPrecision(pConfig, pCfa.getVarClassification(), getClass());
    }

    // Initialize precision
    VariableTrackingPrecision initialPrecision =
        VariableTrackingPrecision.createRefineablePrecision(
            pConfig,
            VariableTrackingPrecision.createStaticPrecision(
                pConfig, pCfa.getVarClassification(), getClass()));

    if (initialPredicatePrecisionFile != null){

      logger.log(Level.INFO,"initialPredicateprecision is not null");

      // convert the predicate precision to variable tracking precision and
      // refine precision with increment from the newly gained variable tracking precision
      // otherwise return empty precision if given predicate precision is empty

      Solver solver = Solver.create(pConfig, this.logger, this.shutdownNotifier);
      FormulaManagerView formulaManager = solver.getFormulaManager();


      RegionManager regionManager = new SymbolicRegionManager(solver);
      AbstractionManager abstractionManager = new AbstractionManager(regionManager, pConfig, logger, solver);

      //PredicatePrecision predPrec = readAndParsePredPrecFile(pConfig, pCfa, solver, formulaManager);

      PredicatePrecision predPrec = readAndParsePredPrecFile(pConfig, pCfa, solver, formulaManager, abstractionManager);


      if (!predPrec.isEmpty()) {

        logger.log(Level.INFO,"now convert precision and then refining ... ");

        return initialPrecision.withIncrement(convertPredPrecToVariableTrackingPrec(predPrec, formulaManager, abstractionManager));
      }
      else {
        return VariableTrackingPrecision.createStaticPrecision(pConfig, pCfa.getVarClassification(), getClass());
      }
    }

    else {

      logger.log(Level.INFO,"initialprecision is not null");

      // create precision with empty, refinable component precision
      // refine the refinable component precision with increment from file
      return initialPrecision.withIncrement(restoreMappingFromFile(pCfa));
    }
  }

  private PredicatePrecision readAndParsePredPrecFile(
      final Configuration pConfig, final CFA pCfa, final Solver solver,
      final FormulaManagerView pFMgr, final AbstractionManager abstractionManager) throws InvalidConfigurationException {

    // create managers for the predicate map parser for parsing the predicates from the given
    // predicate precision file
    //TODO: maybe adjust the pconfig to config for predicates
    //TODO: check if right regionmanager is being created for this
    /*
    RegionManager regionManager = new SymbolicRegionManager(solver);
    //RegionManager regionManager = new BDDManagerFactory(pConfig, this.logger).createRegionManager();

    AbstractionManager abstractionManager = new AbstractionManager(regionManager, pConfig, logger, solver);
    // get the predicate precision from given file
*/
    //TODO: check if mapParser might be wrong
    PredicateMapParser mapParser = new PredicateMapParser(pCfa, this.logger, pFMgr, abstractionManager, new InitialPredicatesOptions());
    PredicatePrecision predPrec = PredicatePrecision.empty();

    logger.log(Level.INFO, "Now trying to parse predicate precision ...");

    try {
      predPrec = mapParser.parsePredicates(initialPredicatePrecisionFile);
    } catch (IOException e) {
      logger.logUserException(Level.WARNING, e, "Could not read precision from file named " + initialPredicatePrecisionFile);
      return predPrec;
    } catch (PredicateParsingFailedException pE) {
      logger.logUserException(Level.WARNING, pE, "Could not parse predicate precision from file named " + initialPredicatePrecisionFile);
      return predPrec;
    }

    /*
    logger.log(Level.INFO, "result precision: ");
    logger.log(Level.INFO, predPrec);
    */

    solver.close();

    return predPrec;
  }

  private Multimap<CFANode, MemoryLocation> convertPredPrecToVariableTrackingPrec(
      final PredicatePrecision pPredPrec, final FormulaManagerView pFMgr, final AbstractionManager pAmgr) {
    logger.log(Level.INFO, "now starting to convert precision ...");
    Collection<AbstractionPredicate> predicates = new HashSet<>();

    predicates.addAll(pPredPrec.getLocalPredicates().values());
    predicates.addAll(pPredPrec.getGlobalPredicates());
    predicates.addAll(pPredPrec.getFunctionPredicates().values());

    SetMultimap<CFANode, MemoryLocation> trackedVariables = HashMultimap.create();
    CFANode dummyNode = new CFANode(CFunctionDeclaration.DUMMY);

    /*
    logger.log(Level.INFO, predicates);
     */

    logger.log(Level.INFO, "before for loop ...");

    // Get the variables from the predicate precision
    for (AbstractionPredicate pred : predicates) {
      for (String var : pFMgr.extractVariables(pred.getSymbolicAtom()).keySet()) {
        trackedVariables.put(dummyNode, MemoryLocation.valueOf(var));
      }
    }

    logger.log(Level.INFO, "finished converting and now starting to refine ...");

    return trackedVariables;
  }

  private Multimap<CFANode, MemoryLocation> restoreMappingFromFile(CFA pCfa) {
    Multimap<CFANode, MemoryLocation> mapping = HashMultimap.create();
    List<String> contents = null;
    try {
      contents = Files.readAllLines(initialPrecisionFile, Charset.defaultCharset());
    } catch (IOException e) {
      logger.logUserException(Level.WARNING, e, "Could not read precision from file named " + initialPrecisionFile);
      return mapping;
    }

    Map<Integer, CFANode> idToCfaNode = createMappingForCFANodes(pCfa);
    final Pattern CFA_NODE_PATTERN = Pattern.compile("N([0-9][0-9]*)");

    CFANode location = getDefaultLocation(idToCfaNode);
    for (String currentLine : contents) {
      if (currentLine.trim().isEmpty()) {
        continue;

      } else if(currentLine.endsWith(":")) {
        String scopeSelectors = currentLine.substring(0, currentLine.indexOf(":"));
        Matcher matcher = CFA_NODE_PATTERN.matcher(scopeSelectors);
        if (matcher.matches()) {
          location = idToCfaNode.get(Integer.parseInt(matcher.group(1)));
        }

      } else {
        mapping.put(location, MemoryLocation.valueOf(currentLine));
      }
    }

    return mapping;
  }

  private CFANode getDefaultLocation(Map<Integer, CFANode> idToCfaNode) {
    return idToCfaNode.values().iterator().next();
  }

  private Map<Integer, CFANode> createMappingForCFANodes(CFA pCfa) {
    Map<Integer, CFANode> idToNodeMap = new HashMap<>();
    for (CFANode n : pCfa.getAllNodes()) {
      idToNodeMap.put(n.getNodeNumber(), n);
    }
    return idToNodeMap;
  }

  public void injectRefinablePrecision() throws InvalidConfigurationException {

    // replace the full precision with an empty, refinable precision
    if (initialPrecisionFile == null && !refineablePrecisionSet) {
      precision = VariableTrackingPrecision.createRefineablePrecision(config, precision);
      refineablePrecisionSet = true;
    }
  }

  @Override
  public MergeOperator getMergeOperator() {
    return buildMergeOperator(mergeType);
  }

  @Override
  public StopOperator getStopOperator() {
    return buildStopOperator(stopType);
  }

  @Override
  public ValueAnalysisTransferRelation getTransferRelation() {
    return new ValueAnalysisTransferRelation(
        logger,
        cfa,
        transferOptions,
        unknownValueHandler,
        constraintsStrengthenOperator,
        statistics);
  }

  @Override
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition) {
    return new ValueAnalysisState(cfa.getMachineModel());
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode, StateSpacePartition pPartition) {
    return precision;
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    if (unknownValueStrategy.equals(UnknownValueStrategy.INTRODUCE_SYMBOLIC)) {
      symbolicStats = new SymbolicStatistics();
      return new SymbolicValueAnalysisPrecisionAdjustment(
          statistics,
          cfa,
          precisionAdjustmentOptions,
          precisionAdjustmentStatistics,
          Preconditions.checkNotNull(symbolicStats));
    } else {
      return new ValueAnalysisPrecisionAdjustment(
          statistics, cfa, precisionAdjustmentOptions, precisionAdjustmentStatistics);
    }
  }

  public Configuration getConfiguration() {
    return config;
  }

  public LogManager getLogger() {
    return logger;
  }

  public ShutdownNotifier getShutdownNotifier() {
    return shutdownNotifier;
  }

  public CFA getCFA() {
    return cfa;
  }

  @Override
  public Reducer getReducer() {
    return new ValueAnalysisReducer();
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(statistics);
    pStatsCollection.add(precisionAdjustmentStatistics);
    if (symbolicStats != null) {
      pStatsCollection.add(symbolicStats);
    }
    pStatsCollection.add(constraintsStrengthenOperator);
    writer.collectStatistics(pStatsCollection);
  }

  @Override
  public ConcreteStatePath createConcreteStatePath(ARGPath pPath) {
    return errorPathAllocator.allocateAssignmentsToPath(pPath);
  }
}