package com.pidanic.saral.generator;

import com.pidanic.saral.domain.*;
import com.pidanic.saral.exception.ProcedureCallNotFoundException;
import com.pidanic.saral.exception.FunctionCallNotFoundException;
import com.pidanic.saral.scope.Scope;
import com.pidanic.saral.util.*;
import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class SimpleStatementGenerator extends StatementGenerator {

    private MethodVisitor methodVisitor;
    private Scope scope;

    public SimpleStatementGenerator(MethodVisitor methodVisitor, Scope scope) {
        super();
        this.methodVisitor = methodVisitor;
        this.scope = scope;
    }

    public void generate(PrintVariable instruction) {
        final LocalVariable variable = instruction.getVariable();
        final String typeName = variable.getType();
        final Type type = TypeResolver.getFromTypeName(typeName);
        final int variableId = scope.getVariableIndex(variable.getName());
        String descriptor = "(" + type.getDescriptor() + ")V";
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        if (type == BuiltInType.INT) {
            methodVisitor.visitVarInsn(Opcodes.ILOAD, variableId);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "java/io/PrintStream", "println", descriptor, false);
        } else if (type == BuiltInType.STRING) {
            methodVisitor.visitVarInsn(Opcodes.ALOAD, variableId);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "java/io/PrintStream", "println", descriptor, false);
        }
    }

    public void generate(VariableDeclaration variableDeclaration) {
        final String variableName = variableDeclaration.getName();
        final Type type = TypeResolver.getFromValue(variableDeclaration.getValue());
        final int variableId = scope.getVariableIndex(variableName);
        if(type == BuiltInType.INT) {
            int val = Integer.valueOf(variableDeclaration.getValue());
            methodVisitor.visitIntInsn(Opcodes.BIPUSH, val);
            methodVisitor.visitVarInsn(Opcodes.ISTORE, variableId);
        } else if(type == BuiltInType.STRING) {
            String stringValue = variableDeclaration.getValue();
            stringValue = StringUtils.removeStart(stringValue, "\"");
            stringValue = StringUtils.removeEnd(stringValue, "\"");
            methodVisitor.visitLdcInsn(stringValue);
            methodVisitor.visitVarInsn(Opcodes.ASTORE, variableId);
        }
    }

    public void generate(ProcedureCall procedureCall) {
        List<Argument> parameters = procedureCall.getProcedure().getArguments();
        List<CalledArgument> calledParameter = procedureCall.getCalledArguments();
        for(int i = 0; i < parameters.size(); i++) {
            Argument param = parameters.get(i);
            CalledArgument callArg = calledParameter.get(i);
            String realLocalVariableName = callArg.getName();
            param.accept(this, realLocalVariableName);
        }
        //Type owner = procedureCall.getProcedure().getReturnType().orElse(new ClassType(scope.getClassName()));
        Type owner = procedureCall.getProcedure().getReturnType().orElse(new ClassType(scope.getClassName()));
        String ownerDescription = owner.getInternalName();
        String procedureName = procedureCall.getProcedure().getName();
        String methodDescriptor = getFunctionDescriptor(procedureCall);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, ownerDescription, procedureName, methodDescriptor, false);
    }

    public void generate(FunctionCall functionCall) {
        List<Argument> parameters = functionCall.getFunction().getArguments();
        List<CalledArgument> calledParameter = functionCall.getCalledArguments();
        for(int i = 0; i < parameters.size(); i++) {
            Argument param = parameters.get(i);
            CalledArgument callArg = calledParameter.get(i);
            String realLocalVariableName = callArg.getName();
            param.accept(this, realLocalVariableName);
        }
        //Type owner = functionCall.getFunction().getReturnType().orElse(new ClassType(scope.getClassName()));
        Type owner = new ClassType(scope.getClassName());
        String ownerDescription = owner.getInternalName();
        String functionName = functionCall.getFunction().getName();
        String methodDescriptor = getFunctionDescriptor(functionCall);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, ownerDescription, functionName, methodDescriptor, false);
    }

    public void generate(Argument parameter, String localVariableName) {
        Type type = TypeResolver.getFromTypeName(parameter.getType());
        int index = scope.getVariableIndex(localVariableName);
        if (type == BuiltInType.INT) {
            methodVisitor.visitVarInsn(Opcodes.ILOAD, index);
        } else {
            methodVisitor.visitVarInsn(Opcodes.ALOAD, index);
        }
    }

    private String getFunctionDescriptor(ProcedureCall procedureCall) {
        return Optional.of(getDescriptorForFunctionInScope(procedureCall))
                .orElse(getDescriptorForFunctionOnClasspath(procedureCall))
                .orElseThrow(() -> new ProcedureCallNotFoundException(procedureCall));
    }
    private String getFunctionDescriptor(FunctionCall functionCall) {
        return Optional.of(getDescriptorForFunctionInScope(functionCall))
                .orElse(getDescriptorForFunctionOnClasspath(functionCall))
                .orElseThrow(() -> new FunctionCallNotFoundException(functionCall));
    }

    private Optional<String> getDescriptorForFunctionInScope(ProcedureCall functionCall) {
        return Optional.ofNullable(DescriptorFactory.getMethodDescriptor(functionCall.getProcedure()));
    }

    private Optional<String> getDescriptorForFunctionInScope(FunctionCall functionCall) {
        return Optional.ofNullable(DescriptorFactory.getMethodDescriptor(functionCall.getFunction()));
    }

    private Optional<String> getDescriptorForFunctionOnClasspath(ProcedureCall procedureCall) {
        try {
            String functionName = procedureCall.getProcedure().getName();
            Collection<CalledArgument> parameters = procedureCall.getCalledArguments();
            Optional<Type> owner = procedureCall.getProcedure().getReturnType();
            //String className = owner.isPresent() ? owner.get().getName() : scope.getClassName();
            String className = scope.getClassName();
            Class<?> aClass = Class.forName(className);
            Method method = aClass.getMethod(functionName);
            String methodDescriptor = org.objectweb.asm.Type.getMethodDescriptor(method);
            return Optional.of(methodDescriptor);
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        }
    }

    private Optional<String> getDescriptorForFunctionOnClasspath(FunctionCall functionCall) {
        try {
            String functionName = functionCall.getFunction().getName();
            Collection<CalledArgument> parameters = functionCall.getCalledArguments();
            Optional<Type> owner = functionCall.getFunction().getReturnType();
            //String className = owner.isPresent() ? owner.get().getName() : scope.getClassName();
            String className = scope.getClassName();
            Class<?> aClass = Class.forName(className);
            Method method = aClass.getMethod(functionName);
            String methodDescriptor = org.objectweb.asm.Type.getMethodDescriptor(method);
            return Optional.of(methodDescriptor);
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        }
    }
}
