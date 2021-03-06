package pgo.trans.passes.codegen.go;

import pgo.InternalCompilerError;
import pgo.model.golang.GoLabelName;
import pgo.model.golang.builder.GoBlockBuilder;
import pgo.scope.UID;
import pgo.trans.intermediate.DefinitionRegistry;

import java.util.function.Consumer;

public class CriticalSectionTracker {
	private DefinitionRegistry registry;
	private UID processUID;
	private CriticalSection criticalSection;
	private int currentLockGroup;
	private UID currentLabelUID;
	private GoLabelName currentLabelName;

	private CriticalSectionTracker(DefinitionRegistry registry, UID processUID, CriticalSection criticalSection,
	                               int currentLockGroup, UID currentLabelUID, GoLabelName currentLabelName) {
		this.registry = registry;
		this.processUID = processUID;
		this.criticalSection = criticalSection;
		this.currentLockGroup = currentLockGroup;
		this.currentLabelUID = currentLabelUID;
		this.currentLabelName = currentLabelName;
	}

	public CriticalSectionTracker(DefinitionRegistry registry, UID processUID, CriticalSection criticalSection) {
		this(registry, processUID, criticalSection, -1, null, null);
	}

	public GoLabelName getCurrentLabelName() {
		return currentLabelName;
	}

	public int getCurrentLockGroup() {
		return currentLockGroup;
	}

	public void start(GoBlockBuilder builder, UID labelUID, GoLabelName labelName) {
		start(builder, labelUID, labelName, true);
	}

	private void start(GoBlockBuilder builder, UID labelUID, GoLabelName labelName, boolean createLabel) {
		// end the current lock group
		end(builder);
		currentLockGroup = registry.getLockGroupOrDefault(labelUID, -1);
		currentLabelUID = labelUID;
		currentLabelName = labelName;

		if (createLabel) {
			builder.labelIsUnique(labelName.getName());
		}

		if (currentLockGroup < 0) {
			// nothing to do
			return;
		}
		// start the new (now current) lock group
		criticalSection.startCriticalSection(builder, processUID, currentLockGroup, currentLabelUID, currentLabelName);
	}

	public void abort(GoBlockBuilder builder, GoLabelName optionalLabelName) {
		if (currentLockGroup >= 0) {
			criticalSection.abortCriticalSection(
					builder, processUID, currentLockGroup, currentLabelUID, currentLabelName);
		}
		builder.goTo(optionalLabelName == null ? currentLabelName : optionalLabelName);
		currentLockGroup = -1;
		currentLabelUID = null;
		currentLabelName = null;
	}

	public void end(GoBlockBuilder builder) {
		if (currentLockGroup < 0) {
			// nothing to do
			return;
		}
		criticalSection.endCriticalSection(builder, processUID, currentLockGroup, currentLabelUID, currentLabelName);
		currentLockGroup = -1;
		currentLabelUID = null;
		currentLabelName = null;
	}

	public void restore(GoBlockBuilder builder) {
		if (currentLockGroup < 0) {
			// nothing to do
			return;
		}
		criticalSection.startCriticalSection(builder, processUID, currentLockGroup, currentLabelUID, currentLabelName);
	}

	public void checkCompatibility(CriticalSectionTracker other) {
		if (registry != other.registry || !criticalSection.equals(other.criticalSection) || currentLockGroup != other.currentLockGroup) {
			throw new InternalCompilerError();
		}
	}

	public CriticalSectionTracker copy() {
		return new CriticalSectionTracker(
				registry, processUID, criticalSection, currentLockGroup, currentLabelUID, currentLabelName);
	}

	public Consumer<GoBlockBuilder> actionAtLoopEnd() {
		// since we're compiling while loops to infinite loops with a conditional break, we have to reacquire
		// the critical section at loop end
		int lockGroup = currentLockGroup;
		UID labelUID = currentLabelUID;
		GoLabelName labelName = currentLabelName;
		if (lockGroup < 0) {
			return ignored -> {};
		}

		// a new label is not required since we are reacquiring the critical section
		return builder -> start(builder, labelUID, labelName, false);
	}
}
