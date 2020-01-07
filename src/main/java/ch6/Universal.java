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

public class Universal {
  private Node[] announce; // array added to coordinate helping 表示 thread i 试图将其节点添加到log上
  private Node[] head;
  private Node tail = new Node();
  int n = 1000;

  public Universal() {
    tail.seq = 1;
    for (int j = 0; j < n; j++) {
      head[j] = tail;
      announce[j] = tail;
    }
  }

  public Response apply(Invoc invoc) {
    int i = ThreadID.get();
    announce[i] = new Node(invoc); // 希望放上去的节点，这是私有的 !
    head[i] = Node.max(head); // 找到头节点，但是如果多个进程访问，看到的max不是同一个，何如 ?

    Node prefer = new Node(invoc); // 似乎此处只是需要申明，而不是new 一个

    while (announce[i].seq == 0) {
      Node before = head[i]; 
      Node help = announce[(before.seq + 1) % n]; // 选择to help or help me !

      // 如果一个人的 apply 始终没有办法相应
      // 如果存在一个人apply 无穷，那么帮助的人也始终无法理解，随着log 的增长，
      // 那么将会所有的节点都来帮忙的情况下依旧无法通过，所以不可能 !
      if (help.seq == 0)
        prefer = help;
      else
        prefer = announce[i];

      Node after = before.decideNext.decide(prefer);
      before.next = after;
      after.seq = before.seq + 1;
      head[i] = after;
    }

    SeqObject MyObject = new SeqObject();
    Node current = tail.next;
    while (current != announce[i]) {
      MyObject.apply(current.invoc);
      current = current.next;
    }
    head[i] = announce[i];
    // 前面的操作只是保证decide 的被放到head 中间了，但是无法确定是help 的还是自己的
    return MyObject.apply(current.invoc);
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
