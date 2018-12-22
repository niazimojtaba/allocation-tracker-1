package de.codecentric.performance.agent.allocation.mbean;

import de.codecentric.performance.agent.allocation.AgentLogger;
import de.codecentric.performance.agent.allocation.StaticTracker;

public class AgentHeap implements AgentHeapMBean {

  @Override
  public void start() {
    AgentLogger.log("Agent heap is now tracking.");
    StaticTracker.startHeap();
  }

  @Override
  public void stop() {
    AgentLogger.log("Agent heap is no longer tracking.");
    StaticTracker.stopHeap();
  }

  @Override
  public String printTop(int amount) {
    String topList = StaticTracker.buildTopHeapList(amount);
    if (AgentLogger.LOG_TOP_LIST) {
      AgentLogger.log("Agent heap saw these allocations:");
      AgentLogger.log(topList);
    }
    return topList;
  }

}
