// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/** 有界遥测队列：控制面故障时丢新事件并告警，绝不阻塞游戏线程。 */
public final class TelemetryQueue implements AutoCloseable {

  private final BlockingQueue<ReportEvent> queue;
  private final ControlGateway gateway;
  private final Logger logger;
  private final AtomicLong dropped = new AtomicLong();
  private final Thread worker;
  private volatile boolean running = true;

  public TelemetryQueue(ControlGateway gateway, int capacity, Logger logger) {
    this.gateway = gateway;
    this.logger = logger;
    this.queue = new ArrayBlockingQueue<>(capacity);
    this.worker = new Thread(this::run, "ccnr-telemetry");
    this.worker.setDaemon(true);
  }

  public void start() {
    worker.start();
  }

  public boolean submit(ReportEvent event) {
    if (!running) {
      return false;
    }
    if (queue.offer(event)) {
      return true;
    }
    long total = dropped.incrementAndGet();
    if (total % 100 == 1) {
      logger.warn("遥测队列已满，累计丢弃 {} 条；检查控制面是否可达", total);
    }
    return false;
  }

  public long dropped() {
    return dropped.get();
  }

  private void run() {
    while (running) {
      try {
        ReportEvent event = queue.poll(1, TimeUnit.SECONDS);
        if (event == null) {
          continue;
        }
        try {
          gateway.report(event);
        } catch (ControlUnavailableException error) {
          logger.warn("遥测上报失败（{}）：{}", event.kind(), error.getMessage());
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  @Override
  public void close() {
    running = false;
    worker.interrupt();
    try {
      worker.join(2000);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
