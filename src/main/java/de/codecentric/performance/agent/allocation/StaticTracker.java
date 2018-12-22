package de.codecentric.performance.agent.allocation;


public class StaticTracker {

  private static Tracker trackerHeap = new Tracker(TrackerType.HEAP);
  private static Tracker trackerCount = new Tracker(TrackerType.COUNT);

  public static void constructed(Object obj) {
    trackerHeap.constructed(obj);
    trackerCount.constructed(obj);
  }

  public static void startHeap() {
    trackerHeap.start();
  }

  public static void startCount() {
    trackerCount.start();
  }

  public static void stopHeap() {
    trackerHeap.stop();
  }

  public static void stopCount() {
    trackerCount.stop();
  }

  public static String buildTopHeapList(final int amount) {
    return trackerHeap.buildTopList(amount);
  }

  public static String buildTopCountList(final int amount) {
    return trackerCount.buildTopList(amount);
  }
}
