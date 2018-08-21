package pgo.trans.passes.codegen;

import pgo.model.golang.*;
import pgo.model.golang.builder.GoBlockBuilder;
import pgo.model.golang.builder.GoForRangeBuilder;
import pgo.model.golang.builder.GoModuleBuilder;
import pgo.model.golang.type.GoChanType;
import pgo.model.golang.type.GoSliceType;
import pgo.model.golang.type.GoType;
import pgo.model.golang.type.GoTypeName;
import pgo.model.pcal.PlusCalAlgorithm;
import pgo.model.pcal.PlusCalMultiProcess;
import pgo.model.pcal.PlusCalProcess;
import pgo.model.type.PGoType;
import pgo.scope.UID;
import pgo.trans.intermediate.DefinitionRegistry;
import pgo.trans.intermediate.GlobalVariableStrategy;
import pgo.trans.intermediate.TLAExpressionCodeGenVisitor;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

// FIXME this strategy, for efficiency reasons, does not implement abortCriticalSection correctly
public class MultithreadedProcessGlobalVariableStrategy extends GlobalVariableStrategy {
	private DefinitionRegistry registry;
	private Map<UID, PGoType> typeMap;
	private PlusCalAlgorithm plusCalAlgorithm;
	private UID pGoLockUID;
	private UID pGoWaitUID;
	private UID pGoStartUID;

	private static final GoType PGO_LOCK_TYPE = new GoSliceType(new GoTypeName("sync.RWMutex"));

	public MultithreadedProcessGlobalVariableStrategy(DefinitionRegistry registry, Map<UID, PGoType> typeMap,
	                                                  PlusCalAlgorithm plusCalAlgorithm) {
		this.registry = registry;
		this.typeMap = typeMap;
		this.plusCalAlgorithm = plusCalAlgorithm;
		this.pGoLockUID = new UID();
		this.pGoWaitUID = new UID();
		this.pGoStartUID = new UID();
	}

	@Override
	public void initPostlude(GoModuleBuilder moduleBuilder, GoBlockBuilder initBuilder) {
		int nLock = registry.getNumberOfLockGroups();
		if (nLock <= 0) {
			// nothing to do
			return;
		}
		moduleBuilder.addImport("sync");
		GoVariableName pGoLock = moduleBuilder.defineGlobal(pGoLockUID, "pGoLock", PGO_LOCK_TYPE);
		addVariable(pGoLockUID, pGoLock);
		initBuilder.assign(pGoLock, new GoMakeExpression(PGO_LOCK_TYPE, new GoIntLiteral(nLock), null));
		GoVariableName pGoStart = moduleBuilder.defineGlobal(pGoStartUID, "pGoStart", new GoChanType(GoBuiltins.Bool));
		addVariable(pGoStartUID, pGoStart);
		initBuilder.assign(pGoStart, new GoMakeExpression(new GoChanType(GoBuiltins.Bool), null, null));
		GoVariableName pGoWait = moduleBuilder.defineGlobal(pGoWaitUID, "pGoWait", new GoTypeName("sync.WaitGroup"));
		addVariable(pGoWaitUID, pGoWait);
	}

	@Override
	public void processPrelude(GoBlockBuilder processBody, PlusCalProcess process, String processName, GoVariableName self,
							   GoType selfType) {
		processBody.deferStmt(new GoCall(
				new GoSelectorExpression(findVariable(pGoWaitUID), "Done"),
				Collections.emptyList()));
		processBody.addStatement(new GoUnary(GoUnary.Operation.RECV, findVariable(pGoStartUID)));
	}

	@Override
	public void mainPrelude(GoBlockBuilder builder) {
		for (PlusCalProcess process : ((PlusCalMultiProcess) plusCalAlgorithm.getProcesses()).getProcesses()) {
			String processName = process.getName().getName().getValue();
			GoExpression value = process.getName().getValue().accept(
					new TLAExpressionCodeGenVisitor(builder, registry, typeMap, this));
			if (process.getName().isSet()) {
				GoForRangeBuilder forRangeBuilder = builder.forRange(value);
				GoVariableName v = forRangeBuilder.initVariables(Arrays.asList("_", "v")).get(1);
				try (GoBlockBuilder forBody = forRangeBuilder.getBlockBuilder()) {
					forBody.addStatement(new GoCall(
							new GoSelectorExpression(findVariable(pGoWaitUID), "Add"),
							Collections.singletonList(new GoIntLiteral(1))));
					forBody.goStmt(new GoCall(new GoVariableName(processName), Collections.singletonList(v)));
				}
				continue;
			}
			builder.addStatement(new GoCall(
					new GoSelectorExpression(findVariable(pGoWaitUID), "Add"),
					Collections.singletonList(new GoIntLiteral(1))));
			builder.goStmt(new GoCall(new GoVariableName(processName), Collections.singletonList(value)));
		}
		builder.addStatement(new GoCall(
				new GoVariableName("close"),
				Collections.singletonList(findVariable(pGoStartUID))));
		GoVariableName pGoWait = findVariable(pGoWaitUID);
		builder.addStatement(new GoCall(new GoSelectorExpression(pGoWait, "Wait"), Collections.emptyList()));
	}

	@Override
	public void startCriticalSection(GoBlockBuilder builder, UID processUID, int lockGroup, UID labelUID, GoLabelName labelName) {
		String functionName = "Lock";
		if (registry.getVariableWritesInLockGroup(lockGroup).isEmpty()) {
			functionName = "RLock";
		}
		builder.addStatement(new GoCall(
				new GoSelectorExpression(new GoIndexExpression(findVariable(pGoLockUID), new GoIntLiteral(lockGroup)), functionName),
				Collections.emptyList()));
	}

	@Override
	public void abortCriticalSection(GoBlockBuilder builder, UID processUID, int lockGroup, UID labelUID, GoLabelName labelName) {
		// FIXME
		endCriticalSection(builder, processUID, lockGroup, labelUID, labelName);
	}

	@Override
	public void endCriticalSection(GoBlockBuilder builder, UID processUID, int lockGroup, UID labelUID, GoLabelName labelName) {
		String functionName = "Unlock";
		if (registry.getVariableWritesInLockGroup(lockGroup).isEmpty()) {
			functionName = "RUnlock";
		}
		builder.addStatement(new GoCall(
				new GoSelectorExpression(new GoIndexExpression(findVariable(pGoLockUID), new GoIntLiteral(lockGroup)), functionName),
				Collections.emptyList()));
	}

	@Override
	public GoExpression readGlobalVariable(GoBlockBuilder builder, UID uid) {
		return builder.findUID(uid);
	}

	@Override
	public GlobalVariableWrite writeGlobalVariable(UID uid) {
		return new GlobalVariableWrite() {
			@Override
			public GoExpression getValueSink(GoBlockBuilder builder) {
				return builder.findUID(uid);
			}

			@Override
			public void writeAfter(GoBlockBuilder builder) {
				// pass
			}
		};
	}
}
