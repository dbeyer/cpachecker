/*
 *  CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.util.predicates.smt;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaType;

public class TaggedFormulaManager extends FormulaManagerView{

  private int tag=0;


  protected int getTag() {
    return tag;
  }


  public void setTag(int pTag) {
    tag = pTag;
  }

  public TaggedFormulaManager(FormulaManagerView pFormulaManager, Configuration pConfig, LogManager pLogger)
      throws InvalidConfigurationException {
    super(pFormulaManager.getRawFormulaManager(), pConfig, pLogger);
    this.tag=0;
  }

  private static final String INDEX_SEPARATOR = "@";

  @Override
  public <T extends Formula> T makeVariable(FormulaType<T> formulaType, String name, int idx) {
    return makeVariable(formulaType, makeName(name, tag, idx));
  }

  public static String makeName(String name, int tag, int idx) {
    if (idx < 0) {
      return name;
    }
    return name + INDEX_SEPARATOR + tag + INDEX_SEPARATOR + idx;
  }

}
