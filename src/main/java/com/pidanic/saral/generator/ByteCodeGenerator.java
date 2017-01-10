package com.pidanic.saral.generator;

import com.pidanic.saral.domain.Statements;
import com.pidanic.saral.domain.Instruction;
import com.pidanic.saral.domain.VariableDeclaration;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;

public class ByteCodeGenerator implements Opcodes {
    public byte[] generateByteCode(Statements compilationUnit, String name) {
        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mw;
        List<Instruction> instructionQueue = compilationUnit.getInstructions();
        // version, access, name, signature, base class, interfaces
        cw.visit(52, ACC_PUBLIC + ACC_SUPER, name, null, "java/lang/Object", null);
        {
            // declare static void main
            mw = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
            long localVariablesCount = instructionQueue.stream()
                    .filter(instruction -> instruction instanceof VariableDeclaration).count();
            int maxStack = 100; //
            for(Instruction ins : instructionQueue) {
                ins.apply(mw);
            }
            mw.visitInsn(RETURN);
            mw.visitMaxs(maxStack, (int) localVariablesCount);
            mw.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }
}