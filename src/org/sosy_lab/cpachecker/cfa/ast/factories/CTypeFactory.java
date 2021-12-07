// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.ast.factories;

import org.sosy_lab.cpachecker.cfa.types.c.CBasicType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;

public class CTypeFactory {

  @SuppressWarnings("unused")
  public static CType getMostGeneralType(CType type1, CType type2) {
    return type1;
  }

  public static CType getBiggestType(CType pType) {
    if (pType instanceof CSimpleType) {
      switch (((CSimpleType) pType).getType()) {
        case BOOL:
        case CHAR:
        case UNSPECIFIED:
        case DOUBLE:
          return pType;
        case FLOAT:
        case FLOAT128:
          return new CSimpleType(
              false, false, CBasicType.DOUBLE, false, false, false, false, false, false, false);
        case INT:
        case INT128:
          return new CSimpleType(
              false, false, CBasicType.INT, false, false, false, false, false, false, true);
        default:
          return pType;
      }
    } else {
      return null;
    }
  }
}
