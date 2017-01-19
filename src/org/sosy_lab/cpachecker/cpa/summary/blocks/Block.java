/*
 * CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cpa.summary.blocks;

import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.sosy_lab.cpachecker.cfa.ast.ASimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;

/**
 * Value class representing grouping for summarization.
 * A single {@link org.sosy_lab.cpachecker.cpa.summary.interfaces.Summary}
 * summarizes all possible computations done inside the block
 * (including all nested blocks).
 */
public class Block {

  private final ImmutableSet<CFANode> innerNodes;
  private final boolean hasRecursion;

  private final ImmutableSet<Wrapper<ASimpleDeclaration>> modifiedVariables;
  private final ImmutableSet<Wrapper<ASimpleDeclaration>> readVariables;

  private final CFANode startNode;
  private final CFANode exitNode;
  private final ImmutableSet<CFAEdge> incomingTransitions;
  private final ImmutableSet<CFANode> ownNodes;

  public Block(
      Set<CFANode> pInnerNodes,
      Set<CFANode> pOwnNodes,
      Set<Wrapper<ASimpleDeclaration>> pModifiedVariables,
      Set<Wrapper<ASimpleDeclaration>> pReadVariables,
      CFANode pStartNode,
      CFANode pExitNode,
      boolean pHasRecursion,
      Set<CFAEdge> pIncomingTransitions) {
    innerNodes = ImmutableSet.copyOf(pInnerNodes);
    ownNodes = ImmutableSet.copyOf(pOwnNodes);
    readVariables = ImmutableSet.copyOf(pReadVariables);
    hasRecursion = pHasRecursion;
    modifiedVariables = ImmutableSet.copyOf(pModifiedVariables);
    startNode = pStartNode;
    exitNode = pExitNode;
    incomingTransitions = ImmutableSet.copyOf(pIncomingTransitions);
  }

  /**
   * @return all incoming edges
   */
  public Set<CFAEdge> getCallEdges() {
    return incomingTransitions;
  }

  /**
   * @return set of nodes composing the interior of the node.
   * <b>Does</b> include called blocks.
   */
  public Set<CFANode> getInnerNodes() {
    return innerNodes;
  }

  /**
   * @return set of nodes inside the block, <b>not</b> including called blocks.
   */
  public Set<CFANode> getOwnNodes() {
    return ownNodes;
  }

  /**
   * @return Whether this block is involved in a
   * (mutual) recursion.
   */
  public boolean hasRecursion() {
    return hasRecursion;
  }

  /**
   * @return unique function entry node.
   */
  public CFANode getStartNode() {
    return startNode;
  }

  /**
   * @return unique function exit node.
   */
  public CFANode getExitNode() {
    return exitNode;
  }

  /**
   * @return function name associated with the block.
   */
  public String getName() {
    return startNode.getFunctionName();
  }

  /**
   * @return Set of variables at block entry node which
   * were read inside the block.
   * Includes modifications by inner blocks.
   */
  public Set<Wrapper<ASimpleDeclaration>> getReadVariables() {
    return readVariables;
  }

  /**
   * @return All variables modified inside the block.
   * Includes modifications by the called blocks.
   */
  public Set<Wrapper<ASimpleDeclaration>> getModifiedVariables() {
    return modifiedVariables;
  }

  public Set<String> getModifiedVariableNames() {
    return modifiedVariables.stream().map(s -> s.get().getQualifiedName()).collect(Collectors.toSet());
  }

  public Set<String> getReadVariableNames() {
    return readVariables.stream().map(s -> s.get().getQualifiedName()).collect(Collectors.toSet());
  }

  @Override
  public String toString() {
    return "Block{" +
        "functionName=" + getName() +
        ", startNode=" + startNode +
        ", exitNode=" + exitNode + '}';
  }
}
