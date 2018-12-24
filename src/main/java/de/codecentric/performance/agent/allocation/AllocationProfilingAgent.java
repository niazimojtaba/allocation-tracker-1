package de.codecentric.performance.agent.allocation;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import de.codecentric.performance.agent.allocation.mbean.Agent;
import de.codecentric.performance.agent.allocation.mbean.AgentHeap;

/**
 * Class registered as premain hook, will add a ClassFileTransformer and register an MBean for controlling the agent.
 */
public class AllocationProfilingAgent {
  private static Instrumentation instrumentation;
  private static Map<String, Long>catchSize = new HashMap<>();
  public static void premain(String agentArgs, Instrumentation inst) {
    AllocationProfilingAgent.instrumentation = inst;
    String prefix = agentArgs;
    if (prefix == null || prefix.length() == 0) {
      AgentLogger.log("Agent failed to start: Please provide a package prefix to filter.");
      return;
    }
    // accepts both . and / notation, but will convert dots to slashes
    prefix = prefix.replace(".", "/");
    if (!prefix.contains("/")) {
      AgentLogger.log("Agent failed to start: Please provide at least one package level prefix to filter.");
      return;
    }
    registerMBean();
    inst.addTransformer(new AllocationTrackerClassFileTransformer(prefix));
  }

  /** Returns object size. */
  public static long sizeOf(Object obj) {
    if (instrumentation == null) {
      throw new IllegalStateException(
              "Instrumentation environment not initialised.");
    }
    if (isSharedFlyweight(obj)) {
      return 0;
    }
    return instrumentation.getObjectSize(obj);
  }

  /**
   * Returns deep size of object, recursively iterating over
   * its fields and superclasses.
   */
  public static long deepSizeOf(Object obj) {
    Map visited = new IdentityHashMap();
    Stack stack = new Stack();
    stack.push(obj);

    long result = 0;
    do {
      result += internalSizeOf(stack.pop(), stack, visited);
    } while (!stack.isEmpty());
    return result;
  }

  public static long deepSizeOfRecursive(Object obj) throws IllegalAccessException {
    Map visited = new IdentityHashMap();
    long len = internalSizeOfRecursive(obj, visited);
    return len;
  }
  /**
   * Returns true if this is a well-known shared flyweight.
   * For example, interned Strings, Booleans and Number objects
   */
  private static boolean isSharedFlyweight(Object obj) {
    // optimization - all of our flyweights are Comparable
    if (obj instanceof Comparable) {
      if (obj instanceof Enum) {
        return true;
      } else if (obj instanceof String) {
        return (obj == ((String) obj).intern());
      } else if (obj instanceof Boolean) {
        return (obj == Boolean.TRUE || obj == Boolean.FALSE);
      } else if (obj instanceof Integer) {
        return (obj == Integer.valueOf((Integer) obj));
      } else if (obj instanceof Short) {
        return (obj == Short.valueOf((Short) obj));
      } else if (obj instanceof Byte) {
        return (obj == Byte.valueOf((Byte) obj));
      } else if (obj instanceof Long) {
        return (obj == Long.valueOf((Long) obj));
      } else if (obj instanceof Character) {
        return (obj == Character.valueOf((Character) obj));
      }
    }
    return false;
  }

  private static boolean skipObject(Object obj, Map visited) {
    return obj == null
            || visited.containsKey(obj)
            || isSharedFlyweight(obj);
  }

  private static long internalSizeOfRecursive(Object obj, Map visited) throws IllegalAccessException {
    long length = 0;
    if (skipObject(obj, visited)) {
      return 0;
    }
    String hashObj = obj.getClass().getName();
    if (catchSize.containsKey(hashObj)) return catchSize.get(hashObj);
    visited.put(obj, null);
    Class clazz = obj.getClass();
    if (clazz.isArray()) {
      if (!clazz.getComponentType().isPrimitive()) {
        length = length + Array.getLength(obj);
        for (int i = 0; i < Array.getLength(obj); i++) {
          if(Array.get(obj, i) == null) continue;
          long len = internalSizeOfRecursive(Array.get(obj, i), visited);
          length = length + len;
        }
      }
    } else {
      // add all non-primitive fields to the stack
      while (clazz != null) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
          if (!Modifier.isStatic(field.getModifiers())
                  && !field.getType().isPrimitive()) {
            field.setAccessible(true);
            if(field.get(obj) == null) continue;
            Long len = internalSizeOfRecursive(field.get(obj), visited);
            length = length + len;
          }
        }
        clazz = clazz.getSuperclass();
      }
    }
    catchSize.put(obj.getClass().getName(), length + sizeOf(obj));
    return length + sizeOf(obj);
  }

  private static long internalSizeOf(
          Object obj, Stack stack, Map visited) {
    if (skipObject(obj, visited)) {
      return 0;
    }

    Class clazz = obj.getClass();
    if (clazz.isArray()) {
      addArrayElementsToStack(clazz, obj, stack);
    } else {
      // add all non-primitive fields to the stack
      while (clazz != null) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
          if (!Modifier.isStatic(field.getModifiers())
                  && !field.getType().isPrimitive()) {
            field.setAccessible(true);
            try {
              stack.add(field.get(obj));
            } catch (IllegalAccessException ex) {
              throw new RuntimeException(ex);
            }
          }
        }
        clazz = clazz.getSuperclass();
      }
    }
    visited.put(obj, null);
    return sizeOf(obj);
  }

  private static void addArrayElementsToStack(
          Class clazz, Object obj, Stack stack) {
    if (!clazz.getComponentType().isPrimitive()) {
      int length = Array.getLength(obj);
      for (int i = 0; i < length; i++) {
        stack.add(Array.get(obj, i));
      }
    }
  }


  /*
   * Starts a new thread which will try to connect to the Platform Mbean Server.
   */
  private static void registerMBean() {
    Thread thread = new Thread() {
      @Override
      public void run() {
        try {
          // retry up to a maximum of 10 minutes
          int retryLimit = 60;
          MBeanServer mbs = null;
          while (mbs == null) {
            if (retryLimit-- == 0) {
              AgentLogger.log("Could not register Agent MBean in 10 minutes.");
              return;
            }
            TimeUnit.SECONDS.sleep(10);
            mbs = ManagementFactory.getPlatformMBeanServer();
          }
          mbs.registerMBean(new Agent(), new ObjectName("de.codecentric:type=AgentCount"));
          mbs.registerMBean(new AgentHeap(), new ObjectName("de.codecentric:type=AgentHeap"));
          AgentLogger.log("Registered Agent MBean.");
        } catch (Exception e) {
          AgentLogger.log("Could not register Agent MBean. Exception:");
          StringWriter sw = new StringWriter();
          e.printStackTrace(new PrintWriter(sw));
          AgentLogger.log(sw.toString());
        }
      }
    };
    thread.start();
  }
}
