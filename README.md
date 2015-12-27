# Java NIO direct ByteBuffer leak

Java's NIO API caches temporary direct ByteBuffers, which looks like a native memory leak. See [my blog post for details](http://www.evanjones.ca/java-bytebuffer-leak.html). This repository contains a program to demonstrate the leak, a quick-and-dirty benchmark to test the performance impact, and a patch against OpenJDK 8 to remove the cache.


## DirectBBLeak

This program demonstrates that the JDK "leaks" direct ByteBuffers by writing to `/dev/null` with multiple threads. When using heap ByteBuffers, the number of allocated direct ByteBuffers does not decrease until the threads exit, while if you use direct ByteBuffers, they decrease as usual with garbage collection. Run it with `javac DirectBBLeak.java; java DirectBBLeak`.


### Output from Java 1.8.0_66 with the leak

```
=== Direct ByteBuffers ===
Before threads started:
  direct ByteBuffer count: 0 capacity:0.0 MB
All threads have written; before System.GC():
  direct ByteBuffer count: 10 capacity:1000.0 MB
After System.GC() and Thread.sleep(100):
  direct ByteBuffer count: 0 capacity:0.0 MB
After threads exited:
  direct ByteBuffer count: 0 capacity:0.0 MB
After System.GC() and Thread.sleep(100):
  direct ByteBuffer count: 0 capacity:0.0 MB

=== Heap ByteBuffers ===
Before threads started:
  direct ByteBuffer count: 0 capacity:0.0 MB
All threads have written; before System.GC():
  direct ByteBuffer count: 10 capacity:1000.0 MB
After System.GC() and Thread.sleep(100):
  direct ByteBuffer count: 10 capacity:1000.0 MB
After threads exited:
  direct ByteBuffer count: 10 capacity:1000.0 MB
After System.GC() and Thread.sleep(100):
  direct ByteBuffer count: 0 capacity:0.0 MB
```


### Output from Java 1.8.0_66 with patched Util class (no leak)

```
=== Direct ByteBuffers ===
Before threads started:
  direct ByteBuffer count: 0 capacity:0.0 MB
All threads have written; before System.GC():
  direct ByteBuffer count: 10 capacity:1000.0 MB
After System.GC() and Thread.sleep(100):
  direct ByteBuffer count: 0 capacity:0.0 MB
After threads exited:
  direct ByteBuffer count: 0 capacity:0.0 MB
After System.GC() and Thread.sleep(100):
  direct ByteBuffer count: 0 capacity:0.0 MB

=== Heap ByteBuffers ===
Before threads started:
  direct ByteBuffer count: 0 capacity:0.0 MB
All threads have written; before System.GC():
  direct ByteBuffer count: 0 capacity:0.0 MB
After System.GC() and Thread.sleep(100):
  direct ByteBuffer count: 0 capacity:0.0 MB
After threads exited:
  direct ByteBuffer count: 0 capacity:0.0 MB
After System.GC() and Thread.sleep(100):
  direct ByteBuffer count: 0 capacity:0.0 MB
```


## BBWritePerfTest

Measures the performance of writing to `/dev/null` in a loop. This can be used to measure the performance impact of changes to this cache. Removing the cache completely does hurt performance, particularly for small I/O calls. Run it with `javac BBWritePerfTest.java; java BBWritePerfTest`.


## remove-bb-cache.diff

This patch removes the direct ByteBuffer cache completely. See [my blog post for how to compile and replace the affected class](http://www.evanjones.ca/java-bytebuffer-leak.html).
