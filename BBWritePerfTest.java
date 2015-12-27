import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class BBWritePerfTest {
  static private final int MB = 1024*1024;
  static private final int BUFFER_SIZE = 10 * MB;
  static private final int NUM_WRITES = 1000;
  static private final double NS_IN_S = 1e9;

  static private final Path DEV_NULL = FileSystems.getDefault().getPath("/dev/null");

  static private void doWrites() throws IOException {
    try (FileChannel c = FileChannel.open(DEV_NULL, StandardOpenOption.WRITE)) {
      final int bufferSizeMB = BUFFER_SIZE / MB;

      ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
      final long start = System.nanoTime();
      for (int i = 0; i < NUM_WRITES; i++) {
        int count = c.write(buffer);
        assert(BUFFER_SIZE == count);
        assert(buffer.position() == BUFFER_SIZE);
        buffer.position(0);
      }
      final long end = System.nanoTime();
      System.out.printf("%d writes of %d MB in %f seconds\n",
        NUM_WRITES, bufferSizeMB, (end-start)/NS_IN_S);
    }
  }

  static public void main(String[] arguments) throws IOException {
    for (int i = 0; i < 10; i++) {
      doWrites();
    }
  }
}
