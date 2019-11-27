package ch11;

import java.util.concurrent.atomic.AtomicReference;

import queue.EmptyException;
import spin.Backoff;

public class LockFreeStack {
  AtomicReference<Node> top = new AtomicReference<Node>(null);
  static final int MIN_DELAY = 100;
  static final int MAX_DELAY = 1000;
  Backoff backoff = new Backoff(MIN_DELAY, MAX_DELAY);

  protected boolean tryPush(Node node) {
    Node oldTop = top.get();
    node.next = oldTop;
    // 从设置node.next 和 将当前节点设置为top 
    // 并没有去掉lock，将循环放到不断失败的重复之中
    // so, what's the essence of lock free ?
    return (top.compareAndSet(oldTop, node));
  }

  public void push(int value) throws InterruptedException {
    Node node = new Node(value);
    while (true) {
      if (tryPush(node)) {
        return;
      } else {
        backoff.backoff();
      }
    }
  }

  protected Node tryPop() throws EmptyException {
    Node oldTop = top.get();
    if (oldTop == null) {
      throw new EmptyException();
    }
    // 获取到next 和 将next设置为top 两者之间不是top 的
    // 导致需要采用了类似于lock 的轮训
    Node newTop = oldTop.next;
    if (top.compareAndSet(oldTop, newTop)) {
      return oldTop;
    } else {
      return null;
    }
  }

  public Integer pop() throws EmptyException, InterruptedException {
    while (true) {
      Node returnNode = tryPop();
      if (returnNode != null) {
        return returnNode.value;
      } else {
        backoff.backoff();
      }
    }
  }
}
