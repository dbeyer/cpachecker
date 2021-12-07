// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.ast.factories;

import org.sosy_lab.cpachecker.cfa.ast.AExpression;

public interface IExpressionFactory {

  AExpression build();

  void reset();

  IExpressionFactory negate();

  IExpressionFactory from(AExpression pAExpression);
}
