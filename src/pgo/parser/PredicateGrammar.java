package pgo.parser;

import pgo.util.SourceLocatable;

import java.util.function.Predicate;

public class PredicateGrammar<Result extends SourceLocatable> extends Grammar<Result> {

	private final Grammar<Result> toFilter;
	private final Predicate<ParseInfo<Result>> predicate;

	@Override
	public String toString() {
		return "PRED";
	}

	public PredicateGrammar(Grammar<Result> toFilter, Predicate<ParseInfo<Result>> predicate) {
		this.toFilter = toFilter;
		this.predicate = predicate;
	}

	public Grammar<Result> getToFilter() { return toFilter; }
	public Predicate<ParseInfo<Result>> getPredicate() { return predicate; }

	@Override
	public <Result1, Except extends Throwable> Result1 accept(GrammarVisitor<Result1, Except> visitor) throws Except {
		return visitor.visit(this);
	}
}
