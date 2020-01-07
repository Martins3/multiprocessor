/*
 * Bitonic.java
 *
 * Created on June 9, 2006, 9:28 PM
 *
 * From "The Art of Multiprocessor Programming",
 * by Maurice Herlihy and Nir Shavit.
 * Copyright 2006 Elsevier Inc. All rights reserved.
 */

package counting;

/**
 * Bitonic counting network.
 * 
 * @author Maurice Herlihy
 */
public class Bitonic implements Network {
  // two half-size bitonic networks
  Bitonic[] half;
  // output i from each half-size mergers goes to layer[i]
  Merger layer;
  final int size;

  public Bitonic(int _size) {
    size = _size;
    layer = new Merger(size);
    if (size > 2) {
      half = new Bitonic[] { new Bitonic(size / 2), new Bitonic(size / 2) };
    }
  }

  // 为什么output需要一直累加。
  // 如果output 的内容表示为，output成为了下一个阶段的input
  //
  //
  // input 的含义是什么 : 进入的端口
  // The class provides a traverse(i) method, where i is the wire on
  // which the token enters.
  public int traverse(int input) {
    int output = 0;
    if (size > 2) {
      // 此时两个 half bitonic 映射到是同一个空间
      // 虽然layer 初始化的大小为size 但是其实
      // 每一层的大小都是size/2
      //
      // 为什么体现了将输出交叉的设计 ?
      // 握草，和书上根本不一致
      output = half[input % 2].traverse(input / 2);
    }
    return output + layer.traverse(output);
  }

}
