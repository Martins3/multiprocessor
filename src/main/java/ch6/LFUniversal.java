package ch6;

import barrier.ThreadID;

class Response {
}

class Invoc {
}

class Consensus<T> {
  T decide(T value) {
    System.out.println("I change the interface to the generic class");
    return value;
  }
}

class SeqObject {
  public Response apply(Invoc invoc) {
    System.out.println("interface to normal : we can't new a inteface");
    return new Response();
  }
}

public class LFUniversal {
  private Node[] head;
  private Node tail;

  // n : thread 的数目 ?
  public LFUniversal() {
    tail = new Node();
    tail.seq = 1;
    int n = 10000;
    for (int i = 0; i < n; i++)
      head[i] = tail;
  }

  public Response apply(Invoc invoc) {
    int i = ThreadID.get();
    Node prefer = new Node(invoc);
    // 只要尚未添加到 list 中间
    while (prefer.seq == 0) {
      Node before = Node.max(head);
      Node after = before.decideNext.decide(prefer);
      before.next = after;
      after.seq = before.seq + 1;
      head[i] = after;// 保证共识对象仅仅访问一次
    }
    SeqObject myObject = new SeqObject();
    // The hard part about designing the concurrent lock-free universal construction
    // is that consensus objects can be used only once
    //
    // A thread tries to append its node by proposing as input to a consensus
    // protocol on the head’s decideNext field.
    Node current = tail.next;
    while (current != prefer) {
      // 递归调用 apply ? 查询到
      // 应该使用一个静态方法啊 !
      myObject.apply(current.invoc);
      current = current.next;
    }

    return myObject.apply(current.invoc);
  }
}

class Node {
  public Invoc invoc; // method name and args
  public Consensus<Node> decideNext; // decide next Node in list
  public Node next; // the next node
  public int seq; // sequence number

  // The node’s decideNext field is a consensus object used to decide which node
  // is appended next in the list,
  // 当前根本不能确定，next 需要由 decideNext 得到 !
  //
  // next is the field in which the outcome of that consensus, the reference to
  // the next node, is recorded.
  //
  // The seq field is the node’s sequence number in the list. This field is zero
  // while the node is not yet threaded onto the list, and positive otherwise.
  // 但是seq 的作用 ?

  public Node(Invoc invoc) {
    this.invoc = invoc;
    decideNext = new Consensus<Node>();
    seq = 0;
  }

  public Node() {
    System.out.println("define a default one to cheat the compiler");
    decideNext = new Consensus<Node>();
    seq = 0;
  }

  public static Node max(Node[] array) {
    Node max = array[0];
    for (int i = 1; i < array.length; i++)
      if (max.seq < array[i].seq)
        max = array[i];
    return max;
  }
}
