// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

public class Main {
  public static void main(String[] args) {
    String a = "foo"+ "";
    for (int i = 0; i < 2; i++) {
      a = a+ "bar";
      }

    assert a.startsWith("foo");
    }
}
