package pgo.trans.intermediate;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import pgo.errors.TopLevelIssueContext;
import pgo.model.mpcal.ModularPlusCalArchetype;
import pgo.model.mpcal.ModularPlusCalBlock;
import pgo.model.pcal.*;
import pgo.trans.passes.mpcal.ModularPlusCalValidationPass;

import static pgo.model.pcal.PlusCalBuilder.*;
import static pgo.model.tla.TLABuilder.*;

@RunWith(Parameterized.class)
public class ModularPlusCalValidationTest {

    @Parameters
    public static List<Object[]> data(){
        return Arrays.asList(new Object[][] {
                {
                    // --mpcal NoIssues {
                    //     archetype MyArchetype() {
                    //         l1: print(1 + 1);
                    //     }
                    //
                    //     procedure MyProcedure() {
                    //         l2: print(3 - 3);
                    //     }
                    //
                    //     process (MyProcess = 32) {
                    //         l3: print(2 * 2);
                    //     }
                    // }
                    mpcal(
                        "NoIssues",
                        Collections.singletonList(
                            archetype(
                                    "MyArchetype",
                                    Collections.emptyList(),
                                    Collections.emptyList(),
                                    Collections.singletonList(
                                            labeled(label("l1"), printS(binop("+", num(1), num(1))))
                                    )
                            )
                        ),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.singletonList(
                                procedure(
                                        "MyProcedure",
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        labeled(label("l2"), printS(binop("-", num(3), num(3))))
                                )
                        ),
                        Collections.emptyList(),
                        process(
                                    pcalVarDecl("MyProcess", false, false, num(32)),
                                    PlusCalFairness.WEAK_FAIR,
                                    Collections.emptyList(),
                                    labeled(
                                        label("l3"),
                                        printS(binop("*", num(2), num(2)))
                                    )
                        )
                    ),
                    Collections.emptyList(),
                },

                // --mpcal ArchetypeNoFirstLabel {
                //     archetype MyArchetype() {
                //         print(1 + 1);
                //     }
                // }
                {
                        mpcal(
                                "ArchetypeNoFirstLabel",
                                Collections.singletonList(
                                        archetype(
                                                "MyArchetype",
                                                Collections.emptyList(),
                                                Collections.emptyList(),
                                                Collections.singletonList(
                                                        printS(binop("+", num(1), num(1)))
                                                )
                                        )
                                ),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.emptyList()
                        ),
                        Collections.singletonList(
                                new InvalidModularPlusCalIssue(
                                        InvalidModularPlusCalIssue.InvalidReason.MISSING_LABEL,
                                        printS(binop("+", num(1), num(1)))
                                )
                        ),
                },

                // --mpcal ProcedureNoFirstLabel {
                //     procedure MyProcess() {
                //         print(1 + 1);
                //     }
                // }
                {
                        mpcal(
                                "ProcedureNoFirstLabel",
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.singletonList(
                                        procedure(
                                                "MyProcedure",
                                                Collections.emptyList(),
                                                Collections.emptyList(),
                                                printS(binop("+", num(1), num(1)))
                                        )
                                ),
                                Collections.emptyList()
                        ),
                        Collections.singletonList(
                                new InvalidModularPlusCalIssue(
                                        InvalidModularPlusCalIssue.InvalidReason.MISSING_LABEL,
                                        printS(binop("+", num(1), num(1)))
                                )
                        ),
                },

                // --mpcal ProcessNoFirstLabel {
                //     process MyProcess() {
                //         print(1 + 1);
                //     }
                // }
                {
                        mpcal(
                                "ProcessNoFirstLabel",
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                process(
                                        pcalVarDecl("MyProcess", false, false, num(32)),
                                        PlusCalFairness.WEAK_FAIR,
                                        Collections.emptyList(),
                                        printS(binop("+", num(1), num(1)))
                                )

                        ),
                        Collections.singletonList(
                                new InvalidModularPlusCalIssue(
                                        InvalidModularPlusCalIssue.InvalidReason.MISSING_LABEL,
                                        printS(binop("+", num(1), num(1)))
                                )
                        ),
                },

                // --mpcal MoreThanOneIssue {
                //     archetype ValidArchetype() {
                //         l1: print(1 + 1);
                //     }
                //
                //     archetype InvalidArchetype() {
                //         print("invalid archetype!");
                //     }
                //
                //     procedure ValidProcedure() {
                //         l2: print(3 - 3);
                //     }
                //
                //     procedure InvalidProcedure() {
                //         print("invalid procedure!");
                //     }
                //
                //     process (ValidProcess = 32) {
                //         l3: print(2 * 2);
                //     }
                //
                //     process (InvalidProcess = 64) {
                //         print("invalid process!");
                //     }
                // }
                {
                        mpcal(
                                "MoreThanOneIssue",
                                new ArrayList<ModularPlusCalArchetype>() {{
                                    add(
                                            archetype(
                                                    "ValidArchetype",
                                                    Collections.emptyList(),
                                                    Collections.emptyList(),
                                                    Collections.singletonList(
                                                            labeled(
                                                                    label("l1"),
                                                                    printS(binop("+",num(1), num(1)))
                                                            )
                                                    )
                                            )
                                    );
                                    add(
                                            archetype(
                                                    "InvalidArchetype",
                                                    Collections.emptyList(),
                                                    Collections.emptyList(),
                                                    Collections.singletonList(
                                                            printS(str("invalid archetype!"))
                                                    )
                                            )
                                    );
                                }},
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                new ArrayList<PlusCalProcedure>() {{
                                    add(
                                            procedure(
                                                    "ValidProcedure",
                                                    Collections.emptyList(),
                                                    Collections.emptyList(),
                                                    labeled(
                                                            label("l2"),
                                                            printS(binop("-", num(3), num(3)))
                                                    )
                                            )
                                    );
                                    add(
                                            procedure(
                                                    "InvalidProcedure",
                                                    Collections.emptyList(),
                                                    Collections.emptyList(),
                                                    printS(str("invalid procedure!"))
                                            )
                                    );
                                }},
                                Collections.emptyList(),
                                process(
                                        pcalVarDecl("ValidProcess", false, false, num(32)),
                                        PlusCalFairness.WEAK_FAIR,
                                        Collections.emptyList(),
                                        labeled(
                                                label("l3"),
                                                printS(binop("*", num(2), num(2)))
                                        )
                                ),
                                process(
                                        pcalVarDecl("InvalidProcess", false, false, num(64)),
                                        PlusCalFairness.WEAK_FAIR,
                                        Collections.emptyList(),
                                        printS(str("invalid process!"))
                                )
                        ),
                        new ArrayList<InvalidModularPlusCalIssue>() {{
                            add(
                                    new InvalidModularPlusCalIssue(
                                            InvalidModularPlusCalIssue.InvalidReason.MISSING_LABEL,
                                            printS(str("invalid archetype!"))
                                    )
                            );
                            add(
                                    new InvalidModularPlusCalIssue(
                                            InvalidModularPlusCalIssue.InvalidReason.MISSING_LABEL,
                                            printS(str("invalid procedure!"))
                                    )
                            );
                            add(
                                    new InvalidModularPlusCalIssue(
                                            InvalidModularPlusCalIssue.InvalidReason.MISSING_LABEL,
                                            printS(str("invalid process!"))
                                    )
                            );
                        }},
                }
        });
    }

    private ModularPlusCalBlock spec;
    private List<InvalidModularPlusCalIssue> issues;

    public ModularPlusCalValidationTest(ModularPlusCalBlock spec, List<InvalidModularPlusCalIssue> issues) {
        this.spec = spec;
        this.issues = issues;
    }

    @Test
    public void test() {
        TopLevelIssueContext ctx = new TopLevelIssueContext();
        ModularPlusCalValidationPass.perform(ctx, spec);

        assertEquals(issues, ctx.getIssues());
    }

}
