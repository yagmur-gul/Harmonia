package ast;

import java.util.List;

public record Call(String callee, List<Expr> args,
                   int line, int column) implements Expr { }
