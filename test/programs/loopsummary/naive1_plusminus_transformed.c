// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

void reach_error(){}
void __VERIFIER_assert(int cond) {
  if (!cond) {
    reach_error();
  }
}

int main() {
  int x = 2147483647-2000000;
  int i = 1000000;
  x+=4;
  x-=2:
  x+= 2*(1000000 - 2);
  x+=4;
  x-=2;
  __VERIFIER_assert(x%2 != 0);
  return 0;
}
