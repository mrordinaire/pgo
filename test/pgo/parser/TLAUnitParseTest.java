package pgo.parser;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import pgo.model.tla.TLAUnit;
import pgo.model.tla.TLAFairness;

import static pgo.model.tla.TLABuilder.*;

@RunWith(Parameterized.class)
public class TLAUnitParseTest {

	@Parameters
	public static List<Object[]> data(){
		return Arrays.asList(new Object[][] {
				{"IsSolution(queens) ==\n" +
						"  \\A i \\in 1 .. Len(queens)-1 : \\A j \\in i+1 .. Len(queens) :\n" +
						"       ~ Attacks(queens,i,j)",
						opdef(false, id("IsSolution"), opdecls(opdecl(id("queens"))),
								universal(bounds(qbIds(ids(id("i")), binop("..", num(1), binop("-", opcall("Len", idexp("queens")), num(1))))),
										universal(bounds(qbIds(ids(id("j")), binop("..", binop("+", idexp("i"), num(1)), opcall("Len", idexp("queens"))))),
												unary("~", opcall("Attacks", idexp("queens"), idexp("i"), idexp("j"))))))
				},
				{"Solutions == { queens \\in [1..N -> 1..N] : IsSolution(queens) }",
					opdef(false, id("Solutions"), opdecls(),
							setRefinement("queens", functionSet(binop("..", num(1), idexp("N")), binop("..", num(1), idexp("N"))), opcall("IsSolution", idexp("queens"))))
				},
				{"-----------------------------------------------------------------------------\n" +
						"MutualExclusion == \\A i, j \\in Proc :\n" +
						"                     (i # j) => ~ /\\ pc[i] = \"cs\"\n" +
						"                                  /\\ pc[j] = \"cs\"\n",
						opdef(false, id("MutualExclusion"), opdecls(),
								universal(bounds(qbIds(ids(id("i"), id("j")), idexp("Proc"))),
										binop("=>", binop("#", idexp("i"), idexp("j")),
												unary("~", conjunct(
														binop("=",
																fncall(idexp("pc"), idexp("i")),
																str("cs")),
														binop("=",
																fncall(idexp("pc"), idexp("j")),
																str("cs")))))))
				},
				{"DeadlockFreedom ==\n" +
						"    \\A i \\in Proc :\n" +
						"      (pc[i] \\notin {\"Li5\", \"Li6\", \"ncs\"}) ~> (\\E j \\in Proc : pc[j] = \"cs\")\n",
						opdef(false, id("DeadlockFreedom"), opdecls(),
								universal(bounds(qbIds(ids(id("i")), idexp("Proc"))),
										binop("~>",
												binop("\\notin",
														fncall(idexp("pc"), idexp("i")),
														set(str("Li5"), str("Li6"), str("ncs"))),
												existential(bounds(qbIds(ids(id("j")), idexp("Proc"))),
														binop("=",
																fncall(idexp("pc"), idexp("j")),
																str("cs"))))))
				},
				{"Termination == <>(pc = \"Done\")",
						opdef(false, id("Termination"), opdecls(),
								unary("<>", binop("=", idexp("pc"), str("Done"))))
				},
				{"Spec == /\\ Init /\\ []4\n" +
						"        /\\ \\A self \\in 0..procs-1 : WF_vars(P(self))",
						opdef(false, id("Spec"), opdecls(),
								binop("/\\",
										binop("/\\",
											idexp("Init"),
											unary("[]",
													num(4))),
										universal(
												bounds(
														qbIds(
																ids(id("self")),
																binop("..",
																		num(0),
																		binop("-", idexp("procs"), num(1))))
												),
												fairness(TLAFairness.Type.WEAK, idexp("vars"),
														opcall("P", idexp("self"))))
										))
				},
				{"c1(self) == /\\ pc[self] = \"c1\"\n" +
						"            /\\ (restaurant_stage[self] = \"commit\") \\/\n" +
						"               (restaurant_stage[self] = \"abort\")\n" +
						"            /\\ IF restaurant_stage[self] = \"commit\"\n" +
						"                  THEN /\\ restaurant_stage' = [restaurant_stage EXCEPT ![self] = \"committed\"]\n" +
						"                  ELSE /\\ restaurant_stage' = [restaurant_stage EXCEPT ![self] = \"aborted\"]\n" +
						"            /\\ pc' = [pc EXCEPT ![self] = \"Done\"]\n" +
						"            /\\ UNCHANGED << managers, rstMgrs, aborted >>",
						opdef(false, id("c1"), opdecls(opdecl(id("self"))),
								binop("/\\",
										binop("/\\",
												binop("/\\",
														binop("/\\",
																binop("=", fncall(idexp("pc"), idexp("self")), str("c1")),
																binop("\\/",
																		binop("=",
																				fncall(idexp("restaurant_stage"), idexp("self")),
																				str("commit")),
																		binop("=",
																				fncall(idexp("restaurant_stage"), idexp("self")),
																				str("abort")))),
														ifexp(
																binop("=",
																		fncall(idexp("restaurant_stage"), idexp("self")),
																		str("commit")),
																binop("=",
																		unary("'", idexp("restaurant_stage")),
																		except(
																				idexp("restaurant_stage"),
																				sub(keys(idexp("self")), str("committed")))),
																binop("=",
																		unary("'", idexp("restaurant_stage")),
																		except(
																				idexp("restaurant_stage"),
																				sub(keys(idexp("self")), str("aborted")))))),
												binop("=",
														unary("'", idexp("pc")),
														except(
																idexp("pc"),
																sub(keys(idexp("self")), str("Done"))))),
										unary("UNCHANGED",
												tuple(idexp("managers"), idexp("rstMgrs"), idexp("aborted")))))
				},

				{"----- MODULE Test ---- \n" +
						"EXTENDS Sequences, Integers\n" +
						"(* --algorithm Test {\n" +
						"    variables a = 2; \n" +
						"          b = 2; \n" +
						"          c = 3; \n" +
						"    {    \n" +
						"        print (a)*((b)+(c))\n" +
						"    }    \n" +
						"}\n" +
						"*)\n" +
						// FIXME: adding the following will make the parser throw an exception
						// "B == FALSE\n" +
						// "----\n" +
						// "C == TRUE\n" +
						"====\n",
						module("Test",
								Arrays.asList(id("Sequences"), id("Integers")),
								Collections.emptyList(), Collections.emptyList(), Collections.emptyList())
				},

				{"DeadlockFree == \\A i \\in Proc :\n                     (pc[i] = \"Li0\") ~> (\\E j \\in Proc : pc[j] = \"cs\")",
						opdef(false, id("DeadlockFree"), opdecls(),
								universal(
								bounds(qbIds(ids(id("i")), idexp("Proc"))),
								binop("~>",
										binop("=",
												fncall(idexp("pc"), idexp("i")),
												str("Li0")),
										existential(
												bounds(qbIds(ids(id("j")), idexp("Proc"))),
												binop("=",
														fncall(idexp("pc"), idexp("j")),
														str("cs"))))))
				}
		});
	}
	
	private String unitString;
	private TLAUnit unitExpected;
	public TLAUnitParseTest(String unitString, TLAUnit unitExpected) {
		this.unitString = unitString;
		this.unitExpected = unitExpected;
	}
	
	private static final Path testFile = Paths.get("TEST");

	@Test
	public void test() throws ParseFailureException {
		LexicalContext ctx = new LexicalContext(testFile, String.join(System.lineSeparator(), unitString.split("\n")));

		System.out.println(unitString);

		TLAUnit unit = TLAParser.readUnit(ctx);
		
		assertThat(unit, is(unitExpected));
	}

}
