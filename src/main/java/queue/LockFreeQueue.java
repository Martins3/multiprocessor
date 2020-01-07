/*
 * LockFreeQueue.java
 *
 * Created on December 29, 2005, 2:05 PM
 *
 * The Art of Multiprocessor Programming, by Maurice Herlihy and Nir Shavit.
 * by Maurice Herlihy and Nir Shavit.
 * Copyright 20065 Elsevier Inc. All rights reserved.
 */

package queue;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free queue. Based on Michael and Scott
 * http://doi.acm.org/10.1145/248052.248106
 * 
 * @param T item type
 * @author Maurice Herlihy
 */
public class LockFreeQueue<T> {
  private AtomicReference<Node> head;
  private AtomicReference<Node> tail;

  public LockFreeQueue() {
    Node sentinel = new Node(null);
    this.head = new AtomicReference<Node>(sentinel);
    this.tail = new AtomicReference<Node>(sentinel);
  }

  public class Node {
    public T value;
    public AtomicReference<Node> next;

    public Node(T value) {
      this.value = value;
      this.next = new AtomicReference<Node>(null);
    }
  }

  /**
   * enqueu 的操作分为两个部分 : 将当前节点放到next的后面，移动tail。 所有的检查都是蛇皮，可以撤销掉这些检查。其实没有考虑 :
   * dequeue 的情况。不用，在two end ! 失败必然进入循环，等待重新操作。 last 和 next 的检查顺序 :
   *
   * 所以简单的分析 : 对于两个步骤，分别讨论成功怎么办，失败怎么办！
   *
   * 不做处理: tail无法指向多个位置，但是移动总是没有问题的。
   *
   * @param value
   */
  public void enq(T value) {
    Node node = new Node(value);
    // 为什么需要while
    while (true) {
      Node last = tail.get();
      Node next = last.next.get();
      // 可能其实只是一个实现检查，毕竟 compareAndSet 的失败，代价过高!
      // 虽然是tail.get 但是不能保证就是 tail
      if (last == tail.get()) {
        // 真正的解释是失败者会将自己的节点放到next 中间，
        // 在此处的检查中间会将 tail 设置为 next !
        //
        // node is last or not
        // 此处的检查只是为了测试是不是需要助人为乐而已! 说明tail需要移动。
        // 其实可以不助人为乐的!
        if (next == null) {
          // To verify that node is indeed last, it checks whether that node has a successor
          // 为什么会失败 ? 多个人可以同时到达此处 compareAndSet
          //
          // next 是本thread 的局部变量 last.next 不是一个局部变量，而是指针指向的地方
          // 所以发生比较的时候，next 还是必定为null
          // 所以如果成功，一定在indeed tail 上添加了
          //
          // 失败 : last.next 只会指向第一个人，所以，所以失败者感觉就像什么事情都没有发生一样 !
          //
          // ? last != tail.get 可能否 也就是即使如此，依旧可以 compareAndSet 成功
          if (last.next.compareAndSet(next, node)) {
            // Even if this second compareAndSet() call fails, the thread
            // can still return successfully because, as we will see, the call fails only if
            // some other thread “helped” it by advancing tail.
            //
            // 失败的原因:　中间发生了 tail 发生了移动，或者有人提前进行了帮助。
            tail.compareAndSet(last, node);
            return;
          }
        } else {
          // ? 多个进入此处
          // ? 帮助工作，当
          // If the tail node has a successor, then the method tries to “help” other threads by advancing tail to refer
          // directly to the successor (Line 21) before trying again to insert its own
          // node.
          tail.compareAndSet(last, next);
        }
      }
    }
  }

  public T deq() throws EmptyException {
    while (true) {
      Node first = head.get();
      Node last = tail.get();
      Node next = first.next.get();
      if (first == head.get()) {
        if (first == last) {
          if (next == null) {
            throw new EmptyException();
          }
          tail.compareAndSet(last, next);
        } else {
          T value = next.value;
          if (head.compareAndSet(first, next))
            return value;
        }
      }
    }
  }
}
