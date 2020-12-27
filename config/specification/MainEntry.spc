// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

OBSERVER AUTOMATON MainEntryAutomaton
// This automaton detects the entry point of the main function

INITIAL STATE Body;

STATE USEFIRST Body :
   MATCH [.*\\s+main\\s*\\(.*\\)\\s*;?.*] -> GOTO MainEntry;

STATE USEFIRST MainEntry :
   MATCH ENTRY -> ERROR("main entry reached");

END AUTOMATON
