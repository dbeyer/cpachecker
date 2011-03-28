/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
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
package org.sosy_lab.cpachecker.cfa.ast;

import static com.google.common.base.Preconditions.checkNotNull;

public class IASTDeclaration extends IASTNode {

  private final StorageClass         storageClass;
  private final IASTSimpleDeclaration declaration;
  private final IASTInitializer      initializer;
  
  public IASTDeclaration(String pRawSignature,
      IASTFileLocation pFileLocation,
      StorageClass pStorageClass,
      IASTSimpleDeclaration pDeclaration,
      IASTInitializer pInitializer) {
    super(pRawSignature, pFileLocation);
    storageClass = checkNotNull(pStorageClass);
    declaration = checkNotNull(pDeclaration);
    initializer = pInitializer;
  }

  public StorageClass getStorageClass() {
    return storageClass;
  }
  
  /**
   * Get the declaration of the variable.
   * The result may be null if the variable was not declared.
   */
  public IASTSimpleDeclaration getDeclaration() {
    return declaration;
  }
  
  public IType getDeclSpecifier() {
    return declaration.getDeclSpecifier();
  }

  public IASTName getName() {
    return declaration.getName();
  }

  public IASTInitializer getInitializer() {
    return initializer;
  }
  
  @Override
  public IASTNode[] getChildren() {
    return new IASTNode[] {getName(), getInitializer()};
  }

}
