package pgo.model.golang;

import pgo.model.golang.builder.GoASTBuilder;
import pgo.model.golang.builder.GoBlockBuilder;
import pgo.scope.UID;
import pgo.trans.passes.codegen.NameCleaner;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GoIfBuilder implements Closeable {

	private GoASTBuilder builder;
	private NameCleaner nameCleaner;
	private NameCleaner labelScope;
	private GoExpression condition;
	private GoBlock trueBranch;
	private GoBlock falseBranch;
	private List<GoVariableName> initialVariables;
	private GoExpression initialExpression;
	private Map<UID, GoVariableName> nameMap;

	public GoIfBuilder(GoASTBuilder builder, NameCleaner nameCleaner, Map<UID, GoVariableName> nameMap,
					   NameCleaner labelScope, GoExpression condition) {
		this.builder = builder;
		this.nameCleaner = nameCleaner;
		this.nameMap = nameMap;
		this.labelScope = labelScope;
		this.condition = condition;
		this.trueBranch = null;
		this.falseBranch = null;
		this.initialVariables = new ArrayList<>();
		this.initialExpression = null;
	}

	public List<GoVariableName> initialAssignment(List<String> nameHints, GoExpression initialExpression) {
		nameHints.forEach(hint -> {
			String actualName = nameCleaner.cleanName(hint);
			initialVariables.add(new GoVariableName(actualName));
		});

		this.initialExpression = initialExpression;
		return Collections.unmodifiableList(initialVariables);
	}

	public void setCondition(GoExpression condition) {
		this.condition = condition;
	}
	
	private void addTrue(GoBlock block) {
		trueBranch = block;
	}
	
	private void addFalse(GoBlock block) {
		falseBranch = block;
	}
	
	public GoBlockBuilder whenTrue() {
		return new GoBlockBuilder(builder, nameCleaner.child(), nameMap, labelScope, this::addTrue);
	}
	
	public GoBlockBuilder whenFalse() {
		return new GoBlockBuilder(builder, nameCleaner.child(), nameMap, labelScope, this::addFalse);
	}

	@Override
	public void close() {
		builder.addStatement(new GoIf(condition, initialVariables, initialExpression, trueBranch, falseBranch));
	}

}
