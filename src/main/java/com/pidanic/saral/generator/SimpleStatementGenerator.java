package com.pidanic.saral.generator;

import com.pidanic.saral.domain.*;
import com.pidanic.saral.domain.block.Argument;
import com.pidanic.saral.domain.expression.Expression;
import com.pidanic.saral.exception.FunctionCallNotFoundException;
import com.pidanic.saral.exception.VariableNotInitializedException;
import com.pidanic.saral.scope.Scope;
import com.pidanic.saral.util.*;
import jdk.nashorn.internal.runtime.regexp.joni.constants.OPCode;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class SimpleStatementGenerator extends StatementGenerator {

    private MethodVisitor methodVisitor;
    private Scope scope;
    private ExpressionGenerator expressionGenerator;

    public SimpleStatementGenerator(MethodVisitor methodVisitor, Scope scope) {
        super();
        this.methodVisitor = methodVisitor;
        this.scope = scope;
        this.expressionGenerator = new ExpressionGenerator(methodVisitor, scope);
    }

    public void generate(PrintVariable instruction) {
        final LocalVariable variable = instruction.getVariable();
        if(!variable.isInitialized()) {
            throw new VariableNotInitializedException(scope, variable.getName());
        }
        final Type type = variable.getType();
        final int variableId = scope.getVariableIndex(variable.getName());
        methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        //methodVisitor.visitVarInsn(type.getTypeSpecificOpcode().getLoad(), variableId);
        String descriptor;
        if(variable instanceof LocalVariableArrayIndex) {
            methodVisitor.visitVarInsn(Opcodes.ALOAD, variableId);
            LocalVariableArrayIndex localArrayIndex = (LocalVariableArrayIndex) variable;
            Expression index = localArrayIndex.getIndex();
            index.accept(expressionGenerator);
            methodVisitor.visitInsn(type.getTypeSpecificOpcode().getLoad());
            if(type == BuiltInType.BOOLEAN_ARR) {
                descriptor = "(" + BuiltInType.BOOLEAN.getDescriptor() + ")V";
            } else if(type == BuiltInType.LONG_ARR) {
                descriptor = "(" + BuiltInType.LONG.getDescriptor() + ")V";
            } else if(type == BuiltInType.DOUBLE_ARR) {
                descriptor = "(" + BuiltInType.DOUBLE.getDescriptor() + ")V";
            } else if(type == BuiltInType.CHAR_ARR) {
                descriptor = "(" + BuiltInType.CHAR.getDescriptor() + ")V";
            } else {
                descriptor = "(" + BuiltInType.STRING.getDescriptor() + ")V";
            }
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "Ljava/io/PrintStream;", "println", descriptor, false);
        } else {
            methodVisitor.visitVarInsn(type.getTypeSpecificOpcode().getLoad(), variableId);
            if (type == BuiltInType.BOOLEAN) {
                descriptor = "(" + BuiltInType.STRING.getDescriptor() + ")V";

                Label endLabel = new Label();
                Label pravdaLabel = new Label();
                Label osalLabel = new Label();
                Label skoroosalLabel = new Label();

                methodVisitor.visitIntInsn(Opcodes.BIPUSH, Logic.PRAVDA.getIntValue());
                methodVisitor.visitJumpInsn(Opcodes.IF_ICMPNE, osalLabel);
                methodVisitor.visitLdcInsn(Logic.PRAVDA.getStringValue());
                methodVisitor.visitJumpInsn(Opcodes.GOTO, endLabel);
                methodVisitor.visitLabel(osalLabel);

                // comparing value of the left expression if "false"
                methodVisitor.visitVarInsn(type.getTypeSpecificOpcode().getLoad(), variableId);
                methodVisitor.visitIntInsn(Opcodes.BIPUSH, Logic.OSAL.getIntValue());
                methodVisitor.visitJumpInsn(Opcodes.IF_ICMPNE, skoroosalLabel);
                methodVisitor.visitLdcInsn(Logic.OSAL.getStringValue());
                methodVisitor.visitJumpInsn(Opcodes.GOTO, endLabel);

                methodVisitor.visitLabel(skoroosalLabel);
                methodVisitor.visitLdcInsn(Logic.SKOROOSAL.getStringValue());

                methodVisitor.visitLabel(endLabel);
            } else {
                descriptor = "(" + type.getDescriptor() + ")V";
            }
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "Ljava/io/PrintStream;", "println", descriptor, false);
        }
    }

    public void generate(VariableDeclaration variableDeclaration) {
        final String variableName = variableDeclaration.getName();
        final int variableId = scope.getVariableIndex(variableName);
        final Optional<Expression> expressionOption = variableDeclaration.getExpression();
        if(expressionOption.isPresent()) {
            Expression expression = expressionOption.get();
            expression.accept(expressionGenerator);
            final Type type = expression.getType();
            methodVisitor.visitVarInsn(type.getTypeSpecificOpcode().getStore(), variableId);
        }
    }

     public void generate(ProcedureCall functionCall) {
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
        methodVisitor.visitVarInsn(type.getTypeSpecificOpcode().getLoad(), index);
    }

    private String getFunctionDescriptor(ProcedureCall functionCall) {
        return Optional.of(getDescriptorForFunctionInScope(functionCall))
                .orElse(getDescriptorForFunctionOnClasspath(functionCall))
                .orElseThrow(() -> new FunctionCallNotFoundException(functionCall));
    }

    private Optional<String> getDescriptorForFunctionInScope(ProcedureCall functionCall) {
        return Optional.ofNullable(DescriptorFactory.getMethodDescriptor(functionCall.getFunction()));
    }

    private Optional<String> getDescriptorForFunctionOnClasspath(ProcedureCall functionCall) {
        try {
            String functionName = functionCall.getFunction().getName();
            Collection<CalledArgument> parameters = functionCall.getCalledArguments();
            Type owner = functionCall.getFunction().getReturnType();
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

    public void generate(Assignment assignment) {
        final String variableName = assignment.getName();
        final int variableId = scope.getVariableIndex(variableName);
        final Optional<Expression> expressionOption = assignment.getExpression();
        if(assignment instanceof ArrayAssignment) {
            if(expressionOption.isPresent()) {
                Expression expression = expressionOption.get();
                final Type type = expression.getType();
                methodVisitor.visitVarInsn(Opcodes.ALOAD, variableId);

                ((ArrayAssignment) assignment).getIndex().accept(expressionGenerator);
                expression.accept(expressionGenerator);

                methodVisitor.visitInsn(type.getTypeSpecificOpcode().getStore());
            }
        } else {
            if(expressionOption.isPresent()) {
                Expression expression = expressionOption.get();
                expression.accept(expressionGenerator);
                final Type type = expression.getType();
                methodVisitor.visitVarInsn(type.getTypeSpecificOpcode().getStore(), variableId);
            }
        }
    }

    public void generate(ArrayDeclaration array) {
        Type arrayType = array.getType();
        Expression arrayLength = array.getLength();
        arrayLength.accept(expressionGenerator);
        if(arrayType == BuiltInType.STRING_ARR) {
            methodVisitor.visitTypeInsn(arrayType.getTypeSpecificOpcode().getNew(), "Ljava/lang/String;");
        } else {
            methodVisitor.visitIntInsn(arrayType.getTypeSpecificOpcode().getNew(), arrayType.getTypeSpecificOpcode().getAsmType());
        }
        String name = array.getName();
        int variableIndex = scope.getVariableIndex(name);
        methodVisitor.visitVarInsn(Opcodes.ASTORE, variableIndex);
    }
}
