package pgo.model.tla;

public abstract class PGoTLAExpressionVisitor<Result> {

	public PGoTLAExpressionVisitor() {
		super();
	}
	
	public Result visit(PGoTLAExpression.PGoTLADefault ex) {
		throw new RuntimeException("visit(PGoTLADefault) unimplemented");
	}
	
	public Result visit(PGoTLABinOp ex) {
		throw new RuntimeException("visit(PGoTLABinOp) unimplemented");
	}
	
	public Result visit(PGoTLABool ex) {
		throw new RuntimeException("visit(PGoTLABool) unimplemented");
	}
	
	public Result visit(PGoTLAIf ex) {
		throw new RuntimeException("visit(PGoTLAIf) unimplemented");
	}
	
	public Result visit(PGoTLAMaybeAction ex) {
		throw new RuntimeException("visit(PGoTLAMaybeAction) unimplemented");
	}
	
	public Result visit(PGoTLANumber ex) {
		throw new RuntimeException("visit(PGoTLANumber) unimplemented");
	}
	
	public Result visit(PGoTLAOperatorCall ex) {
		throw new RuntimeException("visit(PGoTLAOperatorCall) unimplemented");
	}
	
	public Result visit(PGoTLARequiredAction ex) {
		throw new RuntimeException("visit(PGoTLARequiredAction) unimplemented");
	}
	
	public Result visit(PGoTLAString ex) {
		throw new RuntimeException("visit(PGoTLAString) unimplemented");
	}
	
	public Result visit(PGoTLATuple ex) {
		throw new RuntimeException("visit(PGoTLATuple) unimplemented");
	}
	
	public Result visit(PGoTLAUnary ex) {
		throw new RuntimeException("visit(PGoTLAUnary) unimplemented");
	}
	
	public Result visit(PGoTLAVariable ex) {
		throw new RuntimeException("visit(PGoTLAVariable) unimplemented");
	}

	public Result visit(PGoTLASet ex) {
		throw new RuntimeException("visit(PGoTLASet) unimplemented");
	}

	public Result visit(PGoTLASetRefinement ex) {
		throw new RuntimeException("visit(PGoTLASetRefinement) unimplemented");
	}

	public Result visit(PGoTLAExistential ex) {
		throw new RuntimeException("visit(PGoTLAExistential) unimplemented");
	}

	public Result visit(PGoTLAUniversal ex) {
		throw new RuntimeException("visit(PGoTLAUniversal) unimplemented");
	}

	public Result visit(PGoTLASetComprehension ex) {
		throw new RuntimeException("visit(PGoTLASetComprehension unimplemented");
	}

	public Result visit(PGoTLAFunction ex) {
		throw new RuntimeException("visit(PGoTLAFunction) unimplemented");
	}

	public Result visit(PGoTLARecord ex) {
		throw new RuntimeException("visit(PGoTLARecord) unimplemented");
	}

	public Result visit(PGoTLARecordSet ex) {
		throw new RuntimeException("visit(PGoTLARecordSet) unimplemented");
	}

	public Result visit(PGoTLAFunctionSet ex) {
		throw new RuntimeException("visit(PGoTLAFunctionSet) unimplemented");
	}

	public Result visit(PGoTLALet ex) {
		throw new RuntimeException("visit(PGoTLALet) unimplemented");
	}

}
