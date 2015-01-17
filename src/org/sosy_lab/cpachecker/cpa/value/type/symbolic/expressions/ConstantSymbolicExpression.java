/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.value.type.symbolic.expressions;

import org.sosy_lab.cpachecker.cfa.types.Type;
import org.sosy_lab.cpachecker.cpa.value.type.Value;
import org.sosy_lab.cpachecker.cpa.value.type.symbolic.SymbolicIdentifier;
import org.sosy_lab.cpachecker.cpa.value.type.symbolic.SymbolicValue;
import org.sosy_lab.cpachecker.cpa.value.type.symbolic.SymbolicValueVisitor;

/**
 * {@link SymbolicExpression} that represents a single constant value of a specific type.
 */
public class ConstantSymbolicExpression extends SymbolicExpression {

  private final Value value;
  private final Type type;

  /**
   * Create a new <code>ConstantSymbolicExpression</code> object with the given value and type.
   *
   * @param pValue the value of the new object
   * @param pType the type of the value of the new object
   */
  public ConstantSymbolicExpression(Value pValue, Type pType) {
    value = pValue;
    type = pType;
  }

  @Override
  public <VisitorReturnT> VisitorReturnT accept(SymbolicValueVisitor<VisitorReturnT> pVisitor) {
    return pVisitor.visit(this);
  }

  public Value getValue() {
    return value;
  }

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public boolean isTrivial() {
    return !(value instanceof SymbolicValue);
  }

  @Override
  public ConstantSymbolicExpression copyWithType(Type pExpressionType) {
    Value newValue = value;

    if (value instanceof SymbolicIdentifier) {
      newValue = ((SymbolicIdentifier) value).copyWithType(pExpressionType);
    }

    return new ConstantSymbolicExpression(newValue, pExpressionType);
  }

  @Override
  public String toString() {
    return "SymEx[" + value.toString() + "]";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ConstantSymbolicExpression that = (ConstantSymbolicExpression)o;

    return type.equals(that.type) && value.equals(that.value);

  }

  @Override
  public int hashCode() {
    int result = value.hashCode();
    result = 31 * result + type.hashCode();
    return result;
  }
}
