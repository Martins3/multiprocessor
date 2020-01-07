/*
 * BoundedQueue.java
 *
 * Created on December 27, 2005, 7:14 PM
 *
 * The Art of Multiprocessor Programming, by Maurice Herlihy and Nir Shavit.
 * Copyright 2006 Elsevier Inc. All rights reserved.
 */

package queue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded blocking queue
 * 
 * @param T item type
 * @author Maurice Herlihy
 */
public class BoundedQueue<T> {

  /**
   * Lock out other enqueuers (dequeuers)
   */
  ReentrantLock enqLock, deqLock;
  /**
   * wait/signal when queue is not empty or not full
   */
  Condition notEmptyCondition, notFullCondition;
  /**
   * Number of empty slots.
   */
  AtomicInteger size;
  /**
   * First entry in queue.
   */
  Entry head;
  /**
   * Last entry in queue.
   */
  Entry tail;
  /**
   * Max number of entries allowed in queue.
   */
  int capacity;

  /**
   * Constructor.
   * 
   * @param capacity Max number of items allowed in queue.
   */
  public BoundedQueue(int capacity) {
    this.capacity = capacity;
    this.head = new Entry(null);
    this.tail = head;
    this.size = new AtomicInteger(capacity);
    this.enqLock = new ReentrantLock();
    this.notFullCondition = enqLock.newCondition(); // 注意，condition lock 和 关联性质
    this.deqLock = new ReentrantLock();
    this.notEmptyCondition = deqLock.newCondition();
  }

  /**
   * Remove and return head of queue.
   * 
   * @return remove first item in queue
   */
  public T deq() {
    T result;
    boolean mustWakeEnqueuers = true;
    deqLock.lock();
    try {
      while (size.get() == 0) {
        try {
          notEmptyCondition.await();
        } catch (InterruptedException ex) {
        }
      }
      result = head.next.value;
      head = head.next;
      if (size.getAndIncrement() == 0) {
        mustWakeEnqueuers = true;
      }
    } finally {
      deqLock.unlock();
    }

    if (mustWakeEnqueuers) {
      enqLock.lock(); // TODO 如果此时可以让其他人enqueue，何如?
      try {
        notFullCondition.signalAll();
      } finally {
        enqLock.unlock();
      }
    }
    return result;
  }

  /**
   * Append item to end of queue.
   * 
   * @param x item to append
   */
  public void enq(T x) {
    if (x == null)
      throw new NullPointerException();
    boolean mustWakeDequeuers = false;
    enqLock.lock();
    try {

      while (size.get() == capacity) {
        try {
          notFullCondition.await();
          // The enqueuer waits on the notFullCondition field (Line 22), releasing
          // the enqueue lock temporarily, and blocking until that condition is signaled
          // 醒来的时候必须首先获取到lock，虽然全部都醒来，但是只有一个可以运行其中，
          // 其余全部阻塞状态，当unlock的时候，其余的线程进入。
          // 现在第二个，如果没有while 显然超过限制。
        } catch (InterruptedException e) {
        }
      }
      Entry e = new Entry(x);
      tail.next = e;
      tail = e;
      if (size.getAndDecrement() == capacity) {
        mustWakeDequeuers = true;
      }
    } finally {
      enqLock.unlock();
    }
    if (mustWakeDequeuers) {
      deqLock.lock();
      try {
        notEmptyCondition.signalAll();
      } finally {
        deqLock.unlock();
      }
    }
  }

  /**
   * Individual queue item.
   */
  protected class Entry {
    /**
     * Actual value of queue item.
     */
    public T value;
    /**
     * next item in queue
     */
    public Entry next;

    /**
     * Constructor
     * 
     * @param x Value of item.
     */
    public Entry(T x) {
      value = x;
      next = null;
    }
  }
}
