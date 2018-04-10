/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2018  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.algorithm.tiger.util;

import org.sosy_lab.cpachecker.core.algorithm.tiger.goals.Goal;
import org.sosy_lab.cpachecker.util.predicates.regions.Region;

public class GoalCondition {

  private Goal goal;
  private Region simplifiedPresenceCondition;
  private BDDUtils bddUtils;

  public GoalCondition(Goal pGoal, Region pSimplifiedPresenceCondition, BDDUtils pBddUtils) {
    goal = pGoal;
    simplifiedPresenceCondition = pSimplifiedPresenceCondition;
    bddUtils = pBddUtils;
  }

  public Goal getGoal() {
    return goal;
  }

  public Region getSimplifiedPresenceCondition() {
    return simplifiedPresenceCondition;
  }

  @Override
  public String toString() {
    return goal.toString() + " : " + bddUtils.dumpRegion(simplifiedPresenceCondition);
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof GoalCondition)) {
      return false;
    }
    GoalCondition otherGC = (GoalCondition) other;
    if (this.goal != otherGC.goal) {
      return false;
    }
    if (this.simplifiedPresenceCondition != otherGC.simplifiedPresenceCondition) {
      return false;
    }
    return true;
  }

}
