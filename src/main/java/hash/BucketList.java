/*
 * BucketList.java
 *
 * Created on December 30, 2005, 3:24 PM
 *
 * From "Multiprocessor Synchronization and Concurrent Data Structures",
 * by Maurice Herlihy and Nir Shavit.
 * Copyright 2006 Elsevier Inc. All rights reserved.
 */

package hash;

import java.util.concurrent.atomic.*;
import java.util.Iterator;

/**
 * @param T item type
 * @author Maurice Herlihy
 */
public class BucketList<T> implements Set<T> {
  static final int WORD_SIZE = 24;
  static final int LO_MASK = 0x00000001;
  static final int HI_MASK = 0x00800000;
  static final int MASK = 0x00FFFFFF;
  Node head;

  /**
   * Constructor
   */
  public BucketList() {
    this.head = new Node(0);
    this.head.next = new AtomicMarkableReference<Node>(new Node(Integer.MAX_VALUE), false);
  }

  private BucketList(Node e) {
    this.head = e;
  }

  /**
   * Restricted-size hash code
   * 
   * @param x object to hash
   * @return hash code
   */
  public static int hashCode(Object x) {
    return x.hashCode() & MASK;
  }

  public boolean add(T x) {
    int key = makeRegularKey(x);
    boolean splice;
    while (true) {
      // find predecessor and current entries
      Window window = find(head, key);
      Node pred = window.pred;
      Node curr = window.curr;
      // is the key present?
      if (curr.key == key) {
        return false;
      } else {
        // splice in new entry
        Node entry = new Node(key, x);
        // 右端总是没有问题的，左端同时添加 ? 没有tail 的维护，所以变得很简单了!
        // 不希望左侧节点此时被删除
        // 也不想含有节点正在splice在左侧上!
        entry.next.set(curr, false);
        splice = pred.next.compareAndSet(curr, entry, false, false);
        if (splice)
          return true;
        else
          continue;
      }
    }
  }

  public boolean remove(T x) {
    int key = makeRegularKey(x);
    boolean snip;
    while (true) {
      // find predecessor and current entries
      Window window = find(head, key);
      Node pred = window.pred;
      Node curr = window.curr;
      // is the key present?
      if (curr.key != key) {
        return false;
      } else {
        // snip out matching entry
        // 标记为true 即可 !
        snip = pred.next.attemptMark(curr, true);
        if (snip)
          return true;
        else
          continue;
      }
    }
  }

  public boolean contains(T x) {
    int key = makeRegularKey(x);
    Window window = find(head, key);
    Node pred = window.pred;
    Node curr = window.curr;
    return (curr.key == key);
  }

  // 和 add 几乎完全相同的算法
  // sentinel 从 parent 处添加
  public BucketList<T> getSentinel(int index) {
    int key = makeSentinelKey(index);
    boolean splice;
    while (true) {
      // find predecessor and current entries
      Window window = find(head, key);
      Node pred = window.pred;
      Node curr = window.curr;
      // is the key present?
      if (curr.key == key) {
        return new BucketList<T>(curr);
      } else {
        // splice in new entry
        Node entry = new Node(key);
        entry.next.set(pred.next.getReference(), false);
        splice = pred.next.compareAndSet(curr, entry, false, false);
        if (splice)
          return new BucketList<T>(entry);
        else
          continue;
      }
    }
  }

  // TODO  为什么需要进行 reverse ?
  // 为了辅助合并，形成 reverse order !
  // 将倍数的连接在一起了 !
  public static int reverse(int key) {
    int loMask = LO_MASK;
    int hiMask = HI_MASK;
    int result = 0;
    for (int i = 0; i < WORD_SIZE; i++) {
      if ((key & loMask) != 0) { // bit set
        result |= hiMask;
      }
      loMask <<= 1;
      hiMask >>>= 1; // fill with 0 from left
    }
    return result;
  }

  public int makeRegularKey(T x) {
    int code = x.hashCode() & MASK; // take 3 lowest bytes
    return reverse(code | HI_MASK);
  }

  // key 是index ，所在的位置就是最小值
  // 优雅的性质
  private int makeSentinelKey(int key) {
    return reverse(key & MASK);
  }

  // iterate over Set elements
  public Iterator<T> iterator() {
    throw new UnsupportedOperationException();
  }

  private class Node {
    int key;
    T value;
    AtomicMarkableReference<Node> next;

    Node(int key, T object) { // usual constructor
      this.key = key;
      this.value = object;
      this.next = new AtomicMarkableReference<Node>(null, false);
    }

    Node(int key) { // sentinel constructor
      this.key = key;
      this.next = new AtomicMarkableReference<Node>(null, false);
    }

    Node getNext() {
      boolean[] cMarked = { false }; // is curr marked?
      boolean[] sMarked = { false }; // is succ marked?
      Node entry = this.next.get(cMarked);
      // 在getNext 的过程中间，将标记的过的去除!
      while (cMarked[0]) {
        Node succ = entry.next.get(sMarked);
        this.next.compareAndSet(entry, succ, true, sMarked[0]);
        entry = this.next.get(cMarked);
      }
      return entry;
    }
  }

  class Window {
    public Node pred;
    public Node curr;

    Window(Node pred, Node curr) {
      this.pred = pred;
      this.curr = curr;
    }
  }

  public Window find(Node head, int key) {
    Node pred = head;
    Node curr = head.getNext();
    while (curr.key < key) {
      pred = curr;
      curr = pred.getNext();
    }
    return new Window(pred, curr);
  }
}
