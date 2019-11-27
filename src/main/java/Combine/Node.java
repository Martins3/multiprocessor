package Combine;
/*
 * Node.java
 *
 * Created on October 29, 2005, 8:59 AM
 *
 * From "The Art of Multiprocessor Programming",
 * by Maurice Herlihy and Nir Shavit.
 * Copyright 2006 Elsevier Inc. All rights reserved.
 */

/**
 * Node declaration for software combining tree.
 * 
 * @author Maurice Herlihy
 */
public class Node {
  enum CStatus {
    IDLE, FIRST, SECOND, RESULT, ROOT
  };

  boolean locked; // is node locked?
  CStatus cStatus; // combining status
  int firstValue, secondValue; // values to be combined
  int result; // result of combining
  Node parent; // reference to parent

  /** Creates a root Node */
  public Node() {
    cStatus = CStatus.ROOT;
    locked = false;
  }

  /** Create a non-root Node */
  public Node(Node _parent) {
    parent = _parent;
    cStatus = CStatus.IDLE;
    locked = false;
  }

  synchronized boolean precombine() throws InterruptedException {
    while (locked)
      wait();
    switch (cStatus) {
    case IDLE:
      cStatus = CStatus.FIRST;
      return true;
    case FIRST:
      locked = true;
      cStatus = CStatus.SECOND;
      return false;
    case ROOT:
      return false;
    default:
      throw new PanicException("unexpected Node state " + cStatus);
    }
  }

  synchronized int combine(int combined) throws InterruptedException {
    while (locked)
      wait();
    locked = true; // 完成了combine 那么需要等待整个工作完成才能进行下一个precombine
    // It then sets a long-term lock on the node, to ensure that late-arriving
    // threads do not expect to combine with it
    firstValue = combined;
    switch (cStatus) {
    // 该节点是precombine 的时候访问，
    case FIRST:
      return firstValue;
    // 如果该node 可以进入到 second的状态，那么说明node 从另一个路径
    // precombine 过，而且该节点lock until 其控制的所有的内容收集结束
    case SECOND:
      return firstValue + secondValue;
    default:
      throw new PanicException("unexpected Node state " + cStatus);
    }
  }

  synchronized int op(int combined) throws InterruptedException {
    switch (cStatus) {
    case ROOT:
      int oldValue = result;
      result += combined;
      return oldValue;
    case SECOND:
      // Otherwise, the thread unlocks the node,
      // notifies any blocked thread, deposits its value as the
      // SecondValue, and waits for the other thread to return a result after
      // propagating the combined operations toward the root.
      secondValue = combined;
      locked = false;
      notifyAll(); // 完成收集整个片区的内容，通知可以向上了!
      // 同时next epoch 的 precombine 和 被撞上的形成combine !
      // 不仅仅是撞上，而且可能是重入导致的
      // 只要整个操作没有完成，。。。
      //
      // 如何保证不在 notify 阻塞在precombine的位置
      // 因为每一个路径对应一个thread id 而该id 不会执行多次
      // 当一个节点出现可以op 必定没有人第三者过来 precombine
      while (cStatus != CStatus.RESULT)
        wait(); // 等待结果返回

      locked = false; // 释放active的combine 立刻上的锁
      notifyAll(); //

      cStatus = CStatus.IDLE; // next epoch
      // 上面的节点的distribute 下降的过程中间，
      // 将求和结果传递给 getAndIncrement 的发起者
      return result;
    default:
      throw new PanicException("unexpected Node state");
    }
  }

  synchronized void distribute(int prior) throws InterruptedException {
    // informing passive partners of the values they should report to
    // their own passive partners, or to the caller (at the lowest level).
    switch (cStatus) {
    case FIRST:
      cStatus = CStatus.IDLE;
      locked = false; // TODO for what ?
      break;
    case SECOND:
      result = prior + firstValue; // 向下的过程中间，将经过的路径的所有的计算为 result
      cStatus = CStatus.RESULT;
      break;
    default:
      throw new PanicException("unexpected Node state");
    }
    notifyAll();
  }

}
