package de.codecentric.performance.agent.allocation;

/**
 * Utility class for sorting classnames and counts.
 */
public class ClassCounter implements Comparable<ClassCounter> {

  public String className;
  public long count;

  public ClassCounter(String className, long count) {
    this.className = className;
    this.count = count;
  }

  @Override
  public int compareTo(ClassCounter that) {
    long x = that.count;
    long y = this.count;
    return Double.compare(x, y);
  }

  @Override
  public String toString() {
    return className + " " + count;
  }
}
