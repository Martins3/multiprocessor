package ch11;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class EliminationArray {
  private static final int duration = 1234;
  LockFreeExchanger[] exchanger;
  Random random;

  public EliminationArray(int capacity) {
    exchanger = (LockFreeExchanger[]) new LockFreeExchanger[capacity];
    for (int i = 0; i < capacity; i++) {
      exchanger[i] = new LockFreeExchanger();
    }
    random = new Random();
  }

  public Integer visit(Integer value, int range) throws TimeoutException {
    int slot = random.nextInt(range);
    return (exchanger[slot].exchange(value, duration, TimeUnit.MILLISECONDS));
  }
}
