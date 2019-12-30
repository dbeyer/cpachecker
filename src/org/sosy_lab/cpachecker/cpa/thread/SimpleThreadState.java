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
package org.sosy_lab.cpachecker.cpa.thread;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleState;

public class SimpleThreadState extends ThreadState {

  private final Optional<ThreadLabel> mainThread;

  private SimpleThreadState(
      Map<String, ThreadStatus> Tset,
      ImmutableMap<ThreadLabel, ThreadStatus> Rset,
      List<ThreadLabel> pOrder,
      Optional<ThreadLabel> pMainThread) {
    super(Tset, Rset, pOrder);
    mainThread = pMainThread;
  }

  public SimpleThreadState(
      Map<String, ThreadStatus> Tset,
      ImmutableMap<ThreadLabel, ThreadStatus> Rset,
      List<ThreadLabel> pOrder) {
    super(
        Tset,
        Rset,
        pOrder);
    mainThread = getMainThread();
  }

  @Override
  public boolean isCompatibleWith(CompatibleState state) {
    // Consider compatibility as if we knows only the last state
    Preconditions.checkArgument(state instanceof SimpleThreadState);
    SimpleThreadState other = (SimpleThreadState) state;

    Optional<ThreadLabel> currentLabel = this.mainThread;
    Optional<ThreadLabel> otherLabel = other.mainThread;

    if (!currentLabel.isPresent() && !otherLabel.isPresent()) {
      return false;
    } else if (!currentLabel.isPresent() || !otherLabel.isPresent()) {
      return true;
    } else {
      String currentVar = currentLabel.get().getVarName();
      String otherVar = otherLabel.get().getVarName();

      boolean res = Objects.equals(currentVar, otherVar);

      if (!res) {
        return true;
      }

      ThreadStatus currentStatus = threadSet.get(currentVar);
      ThreadStatus status = other.threadSet.get(otherVar);

      return currentStatus == ThreadStatus.SELF_PARALLEL_THREAD
          || status == ThreadStatus.SELF_PARALLEL_THREAD;
    }
  }

  public static ThreadState emptyState() {
    return new SimpleThreadState(ImmutableMap.of(), ImmutableMap.of(), ImmutableList.of());
  }

  @Override
  public ThreadState copyWith(Map<String, ThreadStatus> tSet, List<ThreadLabel> pOrder) {
    return new SimpleThreadState(tSet, this.removedSet, ImmutableList.of());
  }

  @Override
  public ThreadState prepareToStore() {
    return new SimpleThreadState(this.threadSet, ImmutableMap.of(), ImmutableList.of(), mainThread);
  }

  @Override
  public boolean isLessOrEqual(ThreadState pOther) {
    Optional<ThreadLabel> currentLabel = this.mainThread;
    Optional<ThreadLabel> otherLabel = ((SimpleThreadState) pOther).mainThread;
    boolean b = currentLabel.equals(otherLabel);
    if (b && currentLabel.isPresent()) {
      String currentVar = currentLabel.get().getVarName();
      String otherVar = otherLabel.get().getVarName();

      ThreadStatus currentStatus = threadSet.get(currentVar);
      ThreadStatus status = ((SimpleThreadState) pOther).threadSet.get(otherVar);
      return currentStatus.equals(status);
    } else {
      return false;
    }
  }
}
