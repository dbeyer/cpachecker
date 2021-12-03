// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.composite;

import static org.sosy_lab.cpachecker.core.AnalysisDirection.BACKWARD;
import static org.sosy_lab.cpachecker.core.AnalysisDirection.FORWARD;
import static org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action.BREAK;
import static org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action.CONTINUE;

import com.google.common.base.Function;
import java.util.Optional;
import org.sosy_lab.cpachecker.cfa.blockgraph.Block;
import org.sosy_lab.cpachecker.core.AnalysisDirection;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class BlockAwarePrecisionAdjustment implements PrecisionAdjustment {
  private final PrecisionAdjustment defaultAdjustment;

  private final Block block;

  private final AnalysisDirection direction;

  BlockAwarePrecisionAdjustment(
      final PrecisionAdjustment pDefaultAdjustment,
      final Block pBlock,
      final AnalysisDirection pDirection) {
    defaultAdjustment = pDefaultAdjustment;
    block = pBlock;
    direction = pDirection;
  }

  @Override
  public Optional<PrecisionAdjustmentResult> prec(
      AbstractState state,
      Precision precision,
      UnmodifiableReachedSet states,
      Function<AbstractState, AbstractState> stateProjection,
      AbstractState fullState)
      throws CPAException, InterruptedException {
    assert state instanceof BlockAwareCompositeState;

    Optional<PrecisionAdjustmentResult> result =
        defaultAdjustment.prec(state, precision, states, stateProjection, fullState);

    if (result.isPresent()) {
      PrecisionAdjustmentResult adjustment = result.orElseThrow();

      assert adjustment.abstractState() instanceof CompositeState;
      CompositeState adjustedState = (CompositeState) adjustment.abstractState();

      BlockAwareCompositeState newState =
          BlockAwareCompositeState.create(adjustedState, block, direction);

      Action action = CONTINUE;
      if (direction == BACKWARD && newState.isTarget()) {
        action = BREAK;
      }
      else if (direction == FORWARD && newState.isLoopStart()) {
        action = BREAK;
      }
      return Optional.of(adjustment.withAbstractState(newState).withAction(action));
    }

    return Optional.empty();
  }
}
