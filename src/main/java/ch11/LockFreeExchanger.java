package ch11;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicStampedReference;

public class LockFreeExchanger {
  static final int EMPTY = 0, WAITING = 1, BUSY = 2; // 枚举常量而已
  AtomicStampedReference<Integer> slot = new AtomicStampedReference<Integer>(null, 0);

  public Integer exchange(Integer myItem, long timeout, TimeUnit unit) throws TimeoutException {
    long nanos = unit.toNanos(timeout);
    long timeBound = System.nanoTime() + nanos;
    int[] stampHolder = { EMPTY };
    while (true) {
      if (System.nanoTime() > timeBound)
        throw new TimeoutException();
      int yrItem = slot.get(stampHolder); // 同时获取item 和 stamp
      int stamp = stampHolder[0];

      // 对于slot中间的stamp进行分析
      switch (stamp) {
      case EMPTY:
        if (slot.compareAndSet(yrItem, myItem, EMPTY, WAITING)) {
          // 等待第二者的到来!
          while (System.nanoTime() < timeBound) {
            yrItem = slot.get(stampHolder);
            if (stampHolder[0] == BUSY) {
              slot.set(null, EMPTY);
              return yrItem;
            }
          }
          // 检查是因为compareAndSet失败还是因为超时跑出来的
          if (slot.compareAndSet(myItem, null, WAITING, EMPTY)) {
            throw new TimeoutException();
          } else {
            yrItem = slot.get(stampHolder);
            slot.set(null, EMPTY);
            return yrItem;
          }
        }
        break;
      case WAITING:
        // 其中已经存在一个value 了
        if (slot.compareAndSet(yrItem, myItem, WAITING, BUSY))
          return yrItem;
        break;
      case BUSY:
        // 该slot 被占用，由于外层while loop 马上可以进入到 empty 
        break;
      default: // impossible
        System.out.println("impossible");
      }
    }
  }
}
