import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * Demonstrates that the JDK "leaks" direct ByteBuffers by writing to /dev/null. With heap
 * ByteBuffers, the number of allocated direct ByteBuffers does not decrease until the threads
 * exit, while if you use direct ByteBuffers, they decrease as usual with garbage collection.
 */
public class DirectBBLeak extends Thread {
  private final int bufferSize;
  private final WritableByteChannel output;
  private final CyclicBarrier doneExit;
  private final boolean allocateDirect;

  static private final double MB = 1024.0*1024.0;

  public DirectBBLeak(int bufferSize, WritableByteChannel output, CyclicBarrier doneExit,
      boolean allocateDirect) {
    this.bufferSize = bufferSize;
    this.output = output;
    this.doneExit = doneExit;
    this.allocateDirect = allocateDirect;
  }

  public void run() {
    try {
      ByteBuffer buffer;
      if (allocateDirect) {
        buffer = ByteBuffer.allocateDirect(bufferSize);
      } else {
        buffer = ByteBuffer.allocate(bufferSize);
      }

      int count = output.write(buffer);
      assert(count == bufferSize);
      buffer = null;
      // wait for all threads to finish writing
      doneExit.await();
      // wait for the main thread to decide we should exit
      doneExit.await();
    } catch (IOException|InterruptedException|BrokenBarrierException e) {
      throw new RuntimeException(e);
    }
  }

  /** Returns the built-in BufferPoolMXBean for direct ByteBuffers or throws IllegalStateException. */
  static private BufferPoolMXBean getDirectBean() {
    for (BufferPoolMXBean bean : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
      if (bean.getName().equals("direct")) {
        return bean;
      }
    }
    throw new IllegalStateException("could not find built-in \"direct\" BufferPoolMXBean");
  }

  private static final class TestArguments implements AutoCloseable {
    public int numThreads;
    public int bufferSize;
    public boolean allocateDirect;

    private final BufferPoolMXBean directBean;
    public final FileChannel devNullChannel;

    public TestArguments() throws IOException {
      directBean = getDirectBean();

      Path devNull = FileSystems.getDefault().getPath("/dev/null");
      devNullChannel = FileChannel.open(devNull, StandardOpenOption.WRITE);
    }

    public void close() throws IOException {
      devNullChannel.close();
    }

    public void printUsage() {
      System.out.printf("  direct ByteBuffer count: %d capacity:%.1f MB\n",
        directBean.getCount(), directBean.getTotalCapacity() / MB);
    }
  }

  static private void collectAndPrintUsage(TestArguments args) throws InterruptedException {
    args.printUsage();
    System.gc();
    // Sleep so direct ByteBuffer finalizers run. System.runFinalization is not sufficient
    Thread.sleep(100);
    System.out.println("After System.gc() and Thread.sleep(100):\n");
    args.printUsage();
  }

  static private void runTest(TestArguments args) throws InterruptedException, BrokenBarrierException {
    System.out.println("Before threads started:");
    args.printUsage();

    DirectBBLeak[] threads = new DirectBBLeak[args.numThreads];
    CyclicBarrier doneExit = new CyclicBarrier(threads.length + 1);
    for (int i = 0; i < threads.length; i++) {
      threads[i] = new DirectBBLeak(args.bufferSize, args.devNullChannel, doneExit, args.allocateDirect);
      threads[i].start();
    }

    doneExit.await();
    System.out.println("All threads have written; before System.gc():");
    collectAndPrintUsage(args);

    doneExit.await();
    for (int i = 0; i < threads.length; i++) {
      threads[i].join();
    }
    System.out.println("After threads exited:");
    collectAndPrintUsage(args);
  }

  static public void main(String[] arguments) throws BrokenBarrierException, IOException, InterruptedException {
    try (TestArguments args = new TestArguments()) {
      args.numThreads = 10;
      args.bufferSize = 100*1024*1024;

      System.out.println("=== Direct ByteBuffers ===");
      args.allocateDirect = true;
      runTest(args);

      System.out.println("\n=== Heap ByteBuffers ===");
      args.allocateDirect = false;
      runTest(args);
    }
  }
}
