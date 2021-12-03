// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm.concurrent.task;

/*
 * Contains classes which represent tasks or manage task execution in the context of concurrent
 * analysis with block-based task partitioning.
 *
 * <p>Currently, the analysis with
 * {@link org.sosy_lab.cpachecker.core.algorithm.concurrent.ConcurrentAnalysis}
 * employs two task types which implement the common interface
 * {@link org.sosy_lab.cpachecker.core.algorithm.concurrent.task.Task}.
 * {@link org.sosy_lab.cpachecker.core.algorithm.concurrent.task.forward.ForwardAnalysisCore} implements
 * forward analysis and calculates block summaries, while
 * {@link org.sosy_lab.cpachecker.core.algorithm.concurrent.task.forward.BackwardAnalysisFull} applies
 * a backward analysis and propagates an error condition towards program entry.
 * {@link org.sosy_lab.cpachecker.core.algorithm.concurrent.message.MessageFactory} provides an easy
 * interface to create actual instances of these classes and automatically submits them to a
 * {@link org.sosy_lab.cpachecker.core.algorithm.concurrent.Scheduler} (with an instance of
 * which it has to be initialized).
 * {@link org.sosy_lab.cpachecker.core.algorithm.concurrent.message.request.RequestInvalidatedException} indicates
 * that a task has become invalid and shall not be executed.
 *
 * <p>The usual workflow to deploy the classes of this package presents itself as follows:
 * <ol>
 *   <li>Create an instance of
 *        {@link org.sosy_lab.cpachecker.core.algorithm.concurrent.Scheduler}</li>
 *   <li>Create an instance of
 *        {@link org.sosy_lab.cpachecker.core.algorithm.concurrent.message.MessageFactory} and supply it
 *        with the {@link org.sosy_lab.cpachecker.core.algorithm.concurrent.Scheduler}</li>
 *   <li>Use public methods of
 *        {@link org.sosy_lab.cpachecker.core.algorithm.concurrent.message.MessageFactory} to create and
 *        schedule initial tasks</li>
 *   <li>Start task execution with
 *        {@link org.sosy_lab.cpachecker.core.algorithm.concurrent.Scheduler#start()}</li>
 *   <li>Wait for completion of the analysis with
 *   {@link org.sosy_lab.cpachecker.core.algorithm.concurrent.Scheduler#waitForCompletion()}
 * </li>
 * </ol>
 */
