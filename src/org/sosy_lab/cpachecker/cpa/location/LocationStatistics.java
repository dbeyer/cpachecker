/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2019  Dirk Beyer
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
 */
package org.sosy_lab.cpachecker.cpa.location;

import java.io.PrintStream;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public class LocationStatistics implements Statistics {

  StatTimer getSuccessorsForEdgeTimer = new StatTimer("Total time for getSuccessorsForEdge");
  StatTimer createStateTimer = new StatTimer("Total time for state creation");
  StatTimer getSuccessorsTimer = new StatTimer("Total time for getSuccessors");

  @Override
  public void printStatistics(PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {
    StatisticsWriter w = StatisticsWriter.writingStatisticsTo(pOut);
    w.beginLevel()
        .put(getSuccessorsForEdgeTimer)
        .beginLevel()
        .put(createStateTimer)
        .endLevel()
        .put(getSuccessorsTimer)
        .endLevel();
  }

  @Override
  public @Nullable String getName() {
    return "LocationCPA";
  }

}
