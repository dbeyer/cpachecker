// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

// Workers of ./raw_workers/ transformed into a data url to
// deal with Chrome being unable to load WebWorkers when opened using
// the file:// protocol https://stackoverflow.com/questions/21408510/chrome-cant-load-web-worker

import { argWorkerData, cfaWorkerData } from "./workerData";

const handleWorkerMessage = (msg, worker, callback) => {
  worker.busy = false;
  deleteCallbacks(worker);
  if (callback) {
    callback(msg);
  }
};

const argWorker = new Worker(argWorkerData);
argWorker.workerName = "argWorker";
argWorker.onmessage = (result) =>
  handleWorkerMessage(result.data, argWorker, argWorker.callback);
argWorker.onerror = (err) =>
  handleWorkerMessage(err.message, argWorker, argWorker.onErrorCallback);

const cfaWorker = new Worker(cfaWorkerData);
cfaWorker.workerName = "cfaWorker";
cfaWorker.onmessage = (result) =>
  handleWorkerMessage(result.data, cfaWorker, cfaWorker.callback);
cfaWorker.onerror = (err) =>
  handleWorkerMessage(err.message, cfaWorker, cfaWorker.onErrorCallback);

const workerPool = { argWorker, cfaWorker };

// FIFO queue for the jobs that will be executed by the workers
const jobQueue = [];

// Remove worker-instance specific callback functions
const deleteCallbacks = (worker) => {
  delete worker.callback;
  delete worker.onErrorCallback;
};

// Gets the first idle worker and reserves it for job dispatch in case one is available
const reserveWorker = (workerName) => {
  const worker = workerPool[workerName];
  if (!worker.busy) {
    worker.busy = true;
    return worker;
  }
};

// Executes the first job of the queue
const processQueue = () => {
  const job = jobQueue.shift();
  if (job) {
    const reservedWorker = reserveWorker(job.workerName);
    if (reservedWorker) {
      reservedWorker.callback = job.callback;
      reservedWorker.onErrorCallback = job.onErrorCallback;
      reservedWorker.postMessage(job.data);
    } else {
      jobQueue.unshift(job);
    }
    setImmediate(processQueue);
  }
};

/**
 * Registers a new job request.
 *
 * @param workerName name of the worker to use
 * @param data data that is passed to the worker
 */
const enqueue = async (workerName, data) =>
  new Promise((resolve, reject) => {
    jobQueue.push({
      workerName,
      data,
      callback: resolve,
      onErrorCallback: reject,
    });
    setImmediate(processQueue);
  });

export { enqueue };
