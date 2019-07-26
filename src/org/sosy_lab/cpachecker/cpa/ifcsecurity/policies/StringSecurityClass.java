/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.ifcsecurity.policies;




/**
 * Generic Security Class, that generates a Security Class from a String
 */
public class StringSecurityClass extends SecurityClasses{

  /**
   * Internal Variable containing the String-Identifier of the Security class
   */
  private String sc;


  /**
   * Constructor for the Security-Class
    * @param pSC the representive SecurityClass-Element
   */
  public StringSecurityClass(String pSC){
    this.sc=pSC;
  }

  @Override
  public String toString(){
    return this.sc.toString();
  }

  @Override
  public int hashCode() {
    return this.sc.hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    if (this==pObj) {
      return true;
   }
   if (pObj==null) {
      return false;
   }
   if (!(pObj instanceof StringSecurityClass )) {
      return false; // different class
   }

   StringSecurityClass pOther = (StringSecurityClass) pObj;
   if (this.sc==pOther.sc) {
      return true;
   }
   if (this.sc!=null && pOther.sc==null) {
      return false;
   }
   // this.content can't be null
   if(this.sc!=null && pOther.sc!= null){
     return this.sc.equals(pOther.sc);
   }
   return true;
 }

  @Override
  public int compareTo(SecurityClasses pObj) {
     if (this==pObj) {
        return 0;
     }
     if (pObj==null) {
        return 1;
     }
     if (!(pObj instanceof StringSecurityClass )) {
       return (getClass().getName().compareTo(pObj.getClass().getName()));
     }
     StringSecurityClass other=(StringSecurityClass) pObj;
     return ((this.sc).compareTo(other.sc));
  }

}
