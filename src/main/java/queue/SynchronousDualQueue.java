/*
 * SynchronousQueueDual.java
 *
 * Created on March 12, 2006, 5:56 PM
 *
 * From "The Art of Multiprocessor Programming"
 * by Maurice Herlihy and Nir Shavit.
 * Copyright 2006 Elsevier Inc. All rights reserved.
 */

package queue;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Dual Synchronous Queue. Based on "Scalable Synchronous Queues", by W. N.
 * Scherer III, Doug Lea, and M. L. Scott. Symposium on Principles and Practice
 * of Parallel Programming, March 2006.
 * 
 * @author Maurice Herlihy
 */
public class SynchronousDualQueue<T> {
  AtomicReference<Node> head;
  AtomicReference<Node> tail;

  /**
   * Constructor: creates an empty synchronous queue.
   */
  public SynchronousDualQueue() {
    Node sentinel = new Node(null, NodeType.ITEM);
    head = new AtomicReference<Node>(sentinel);
    tail = new AtomicReference<Node>(sentinel);
  }

  /**
   * Append item to end of queue.
   * 
   * @param e item to append
   */
  public void enq(T e) {
    Node offer = new Node(e, NodeType.ITEM);
    while (true) {
      Node t = tail.get();
      Node h = head.get();
      if (h == t || t.type == NodeType.ITEM) {
        Node n = t.next.get();
        if (t == tail.get()) {
          if (n != null) {
            tail.compareAndSet(t, n);
          } else if (t.next.compareAndSet(n, offer)) {
            tail.compareAndSet(t, offer);
            // enq 需要一直有人来修改过!
            while (offer.item.get() == e)
              ; // spin
            // If it succeeds, it tries to advance the tail to the newly appended node, and
            // then spins, waiting for a dequeuer to announce that it has dequeued
            // the item by setting the node’s item field to null.

            // Once the item is dequeued, the method tries to clean up by making its node
            // the new sentinel. This last step serves only to enhance performance, because
            // the implementation remains correct, whether or not the method advances the
            // head reference
            //
            //
            // 如果head 下一个节点就是 offer，此时offer 被清空了
            h = head.get();
            if (offer == h.next.get()) {
              // 将 head 设置为 offer，哨兵进行移动
              head.compareAndSet(h, offer);
            }
            return;
          }
        }
      } else {
        // If, however, the enq() method discovers that the queue contains dequeuers’
        // reservations waiting to be fulfilled, then it tries to find a reservation to
        // fulfill. Since the queue’s head node is a sentinel with no meaningful value,
        // enq() reads the head’s successor (Line 25), checks that the values it has
        // read are consistent (Lines 26–28), and tries to switch that node’s item field
        // from null to the item being enqueued. Whether or not this step succeeds, the
        // method tries to advance
        //
        // 进行滑动到下一个，但是，但是enqueue 的内容，现在谁知道啊!
        Node n = h.next.get();
        if (t != tail.get() || h != head.get() || n == null) {
          continue; // inconsistent snapshot
        }
        boolean success = n.item.compareAndSet(null, e);
        head.compareAndSet(h, n);
        if (success) {
          return;
        }
      }
    }
  }

  /**
   * remove item from front of queue
   * 
   * @return item removed
   */
  public T deq() {
    Node offer = new Node(null, NodeType.RESERVATION);
    while (true) {
      Node t = tail.get();
      Node h = head.get();
      // The enq() method first checks whether the queue is empty or whether it
      // contains enqueued items waiting to be dequeued
      if (h == t || t.type == NodeType.RESERVATION) {
        Node n = t.next.get();
        if (t == tail.get()) {
          if (n != null) {
            tail.compareAndSet(t, n);
          } else if (t.next.compareAndSet(n, offer)) {
            tail.compareAndSet(t, offer);

            h = head.get();
            if (offer == h.next.get()) {
              head.compareAndSet(h, offer);
            }
            return offer.item.get();
          }
        }
      } else {
        Node n = h.next.get();
        if (t != tail.get() || h != head.get() || n == null) {
          continue; // inconsistent snapshot
        }
        T item = n.item.get();
        boolean success = n.item.compareAndSet(item, null);
        head.compareAndSet(h, n);
        if (success) {
          return item;
        }
      }
    }
  }

  private enum NodeType {
    ITEM, RESERVATION
  };

  private class Node {
    volatile NodeType type;
    volatile AtomicReference<T> item;
    volatile AtomicReference<Node> next;

    Node(T item, NodeType type) {
      this.item = new AtomicReference<T>(item);
      this.next = new AtomicReference<Node>(null);
      this.type = type;
    }

    public String toString() {
      return "Node[" + type + ", item: " + item + ", next: " + next + "]";
    }
  }
}
