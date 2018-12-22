package de.codecentric.performance.agent.allocation;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.UnmodifiableClassException;

/**
 * Changes the bytecode of the visited constructor to call the static tracker. technically could be added to any method.
 */
public class ConstructorVisitor extends MethodVisitor {

  /*
   * reference to the class name of the visited class.
   */
  private String className;

  public ConstructorVisitor(String className, MethodVisitor mv) {
    super(Opcodes.ASM5, mv);
    this.className = className;
  }

  /** Inserts the appropriate INVOKESTATIC call */
  @Override
  public void visitInsn(int opcode) {
    if ((opcode == Opcodes.ARETURN)
            || (opcode == Opcodes.IRETURN)
            || (opcode == Opcodes.LRETURN)
            || (opcode == Opcodes.FRETURN)
            || (opcode == Opcodes.DRETURN)) {
      throw new RuntimeException(
              new UnmodifiableClassException("Constructors are supposed to return void"));
    }
    if (opcode == Opcodes.RETURN) {
      super.visitVarInsn(Opcodes.ALOAD, 0);
      super.visitMethodInsn(Opcodes.INVOKESTATIC, TrackerConfig.TRACKER_CLASS, TrackerConfig.TRACKER_CALLBACK,
              TrackerConfig.TRACKER_CALLBACK_SIGNATURE, false);
    }
    super.visitInsn(opcode);
  }

}
