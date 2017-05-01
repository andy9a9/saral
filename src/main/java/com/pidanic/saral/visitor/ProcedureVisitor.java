package com.pidanic.saral.visitor;

import com.pidanic.saral.domain.*;
import com.pidanic.saral.grammar.SaralBaseVisitor;
import com.pidanic.saral.grammar.SaralParser;
import com.pidanic.saral.scope.Scope;

import java.util.List;
import java.util.stream.Collectors;

public class ProcedureVisitor extends SaralBaseVisitor<Procedure> {

    private Scope scope;

    public ProcedureVisitor(Scope scope) {
        this.scope = new Scope(scope);
    }

    @Override
    public Procedure visitProc_definition(SaralParser.Proc_definitionContext ctx) {
        String procedureName = ctx.ID().getText();

        List<Argument> arguments = ctx.arglist().arg().stream()
                .map(arg -> arg.accept(new ArgumentVisitor()))
                .peek(arg -> scope.addVariable(new LocalVariable(arg.getName(), arg.getType())))
                .collect(Collectors.toList());

        List<Statement> allStatements = ctx.block().statements().statement().stream().map(stmtCtx -> {
            SaralParser.Block_statementContext block = stmtCtx.block_statement();
            SaralParser.Simple_statementContext simpleStmt = stmtCtx.simple_statement();
            Statement val;
            if(block != null) {
                val = block.accept(new StatementVisitor(scope));
            } else {
                val = simpleStmt.accept(new StatementVisitor(scope));
            }
            return val;
        }).collect(Collectors.toList());

        List<SimpleStatement> simpleStatements = allStatements.stream()
                .filter(stmt -> stmt instanceof SimpleStatement)
                .map(statement -> (SimpleStatement) statement)
                .collect(Collectors.toList());
        Procedure procedure = new Procedure(scope, procedureName, arguments, simpleStatements);

        return procedure;
    }
}
