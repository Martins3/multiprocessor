package ch11;

import java.util.concurrent.TimeoutException;

import queue.EmptyException;

public class EliminationBackoffStack extends LockFreeStack {

  private static class RangePolicy {
    int maxRange;
    int currentRange = 1;

    RangePolicy(int maxRange) {
      this.maxRange = maxRange;
    }

    public void recordEliminationSuccess() {
      if (currentRange < maxRange)
        currentRange++;
    }

    public void recordEliminationTimeout() {
      if (currentRange > 1)
        currentRange--;
    }

    public int getRange() {
      return currentRange;
    }
  }

  static final int capacity = 100;
  EliminationArray eliminationArray = new EliminationArray(capacity);
  static ThreadLocal<RangePolicy> policy = new ThreadLocal<RangePolicy>() {
    protected synchronized RangePolicy initialValue() {

      return new RangePolicy(capacity);
    }
  };

  public void push(Integer value) {
    RangePolicy rangePolicy = policy.get();
    Node node = new Node(value);
    while (true) {
      if (tryPush(node)) {
        // 成功直接处理，失败试图使用elimination的方法
        return;
      } else
        try {
          Integer otherValue = eliminationArray.visit(value, rangePolicy.getRange());
          // 匹配到是的null而不是 另一个push
          if (otherValue == null) {
            rangePolicy.recordEliminationSuccess();
            return; // exchanged with pop
          }
        } catch (TimeoutException ex) {
          // 失败了反而需要收缩 ?
          rangePolicy.recordEliminationTimeout();
        }
    }
  }

  public Integer pop() throws EmptyException, InterruptedException {
    RangePolicy rangePolicy = policy.get();
    while (true) {
      Node returnNode = tryPop();
      if (returnNode != null) {
        return returnNode.value;
      } else
        try {
          Integer otherValue = eliminationArray.visit(null, rangePolicy.getRange());
          if (otherValue != null) {
            rangePolicy.recordEliminationSuccess();
            return otherValue;
          }
        } catch (TimeoutException ex) {
          rangePolicy.recordEliminationTimeout();
        }
    }
  }
}
