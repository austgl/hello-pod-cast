/**
 * @(#)Flow.java	1.64 03/04/16
 *
 * Copyright 2003 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.sun.tools.javac.v8.comp;

import com.sun.tools.javac.v8.code.Flags;
import com.sun.tools.javac.v8.code.Kinds;
import com.sun.tools.javac.v8.code.Symbol;
import com.sun.tools.javac.v8.code.Symtab;
import com.sun.tools.javac.v8.code.Type;
import com.sun.tools.javac.v8.code.TypeTags;
import com.sun.tools.javac.v8.code.Symbol.ClassSymbol;
import com.sun.tools.javac.v8.code.Symbol.VarSymbol;
import com.sun.tools.javac.v8.code.Type.MethodType;
import com.sun.tools.javac.v8.tree.Tree;
import com.sun.tools.javac.v8.tree.TreeInfo;
import com.sun.tools.javac.v8.tree.TreeMaker;
import com.sun.tools.javac.v8.tree.TreeScanner;
import com.sun.tools.javac.v8.tree.Tree.Apply;
import com.sun.tools.javac.v8.tree.Tree.Assert;
import com.sun.tools.javac.v8.tree.Tree.Assign;
import com.sun.tools.javac.v8.tree.Tree.Assignop;
import com.sun.tools.javac.v8.tree.Tree.Binary;
import com.sun.tools.javac.v8.tree.Tree.Block;
import com.sun.tools.javac.v8.tree.Tree.Break;
import com.sun.tools.javac.v8.tree.Tree.Case;
import com.sun.tools.javac.v8.tree.Tree.ClassDef;
import com.sun.tools.javac.v8.tree.Tree.Conditional;
import com.sun.tools.javac.v8.tree.Tree.Continue;
import com.sun.tools.javac.v8.tree.Tree.DoLoop;
import com.sun.tools.javac.v8.tree.Tree.ForLoop;
import com.sun.tools.javac.v8.tree.Tree.Ident;
import com.sun.tools.javac.v8.tree.Tree.If;
import com.sun.tools.javac.v8.tree.Tree.Labelled;
import com.sun.tools.javac.v8.tree.Tree.MethodDef;
import com.sun.tools.javac.v8.tree.Tree.NewArray;
import com.sun.tools.javac.v8.tree.Tree.NewClass;
import com.sun.tools.javac.v8.tree.Tree.Return;
import com.sun.tools.javac.v8.tree.Tree.Switch;
import com.sun.tools.javac.v8.tree.Tree.Throw;
import com.sun.tools.javac.v8.tree.Tree.Try;
import com.sun.tools.javac.v8.tree.Tree.Unary;
import com.sun.tools.javac.v8.tree.Tree.VarDef;
import com.sun.tools.javac.v8.tree.Tree.WhileLoop;
import com.sun.tools.javac.v8.util.Bits;
import com.sun.tools.javac.v8.util.Context;
import com.sun.tools.javac.v8.util.List;
import com.sun.tools.javac.v8.util.ListBuffer;
import com.sun.tools.javac.v8.util.Log;
import com.sun.tools.javac.v8.util.Name;
import com.sun.tools.javac.v8.util.Options;

/**
 * This pass implements dataflow analysis for Java programs. Liveness analysis
 * checks that every statement is reachable. Exception analysis ensures that
 * every checked exception that is thrown is declared or caught. Definite
 * assignment analysis ensures that each variable is assigned when used.
 * Definite unassignment analysis ensures that no final variable is assigned
 * more than once.
 * 
 * <p>
 * The second edition of the JLS has a number of problems in the specification
 * of these flow analysis problems. This implementation attempts to address
 * those issues.
 * 
 * <p>
 * First, there is no accommodation for a finally clause that cannot complete
 * normally. For liveness analysis, an intervening finally clause can cause a
 * break, continue, or return not to reach its target. For exception analysis,
 * an intervening finally clause can cause any exception to be "caught". For
 * DA/DU analysis, the finally clause can prevent a transfer of control from
 * propagating DA/DU state to the target. In addition, code in the finally
 * clause can affect the DA/DU status of variables.
 * 
 * <p>
 * For try statements, we introduce the idea of a variable being definitely
 * unassigned "everywhere" in a block. A variable V is "unassigned everywhere"
 * in a block iff it is unassigned at the beginning of the block and there is no
 * reachable assignment to V in the block. An assignment V=e is reachable iff V
 * is not DA after e. Then we can say that V is DU at the beginning of the catch
 * block iff V is DU everywhere in the try block. Similarly, V is DU at the
 * beginning of the finally block iff V is DU everywhere in the try block and in
 * every catch block. Specifically, the following bullet is added to 16.2.2
 * 
 * V is <em>unassigned everywhere</em> in a block if it is unassigned before the
 * block and there is no reachable assignment to V within the block.
 * 
 * In 16.2.15, the third bullet (and all of its sub-bullets) for all try blocks
 * is changed to
 * 
 * V is definitely unassigned before a catch block iff V is definitely
 * unassigned everywhere in the try block.
 * 
 * The last bullet (and all of its sub-bullets) for try blocks that have a
 * finally block is changed to
 * 
 * V is definitely unassigned before the finally block iff V is definitely
 * unassigned everywhere in the try block and everywhere in each catch block of
 * the try statement.
 * 
 * In addition,
 * 
 * V is definitely assigned at the end of a constructor iff V is definitely
 * assigned after the block that is the body of the constructor and V is
 * definitely assigned at every return that can return from the constructor.
 * </pre>
 * 
 * <p>
 * In addition, each continue statement with the loop as its target is treated
 * as a jump to the end of the loop body, and "intervening" finally clauses are
 * treated as follows: V is DA "due to the continue" iff V is DA before the
 * continue statement or V is DA at the end of any intervening finally block. V
 * is DU "due to the continue" iff any intervening finally cannot complete
 * normally or V is DU at the end of every intervening finally block. This "due
 * to the continue" concept is then used in the spec for the loops.
 * 
 * <p>
 * Similarly, break statements must consider intervening finally blocks. For
 * liveness analysis, a break statement for which any intervening finally cannot
 * complete normally is not considered to cause the target statement to be able
 * to complete normally. Then we say V is DA "due to the break" iff V is DA
 * before the break or V is DA at the end of any intervening finally block. V is
 * DU "due to the break" iff any intervening finally cannot complete normally or
 * V is DU at the break and at the end of every intervening finally block. (I
 * suspect this latter condition can be simplified.) This "due to the break" is
 * then used in the spec for all statements that can be "broken".
 * 
 * <p>
 * The return statement is treated similarly. V is DA "due to a return
 * statement" iff V is DA before the return statement or V is DA at the end of
 * any intervening finally block. Note that we don't have to worry about the
 * return expression because this concept is only used for construcrors.
 * 
 * <p>
 * There is no spec in JLS2 for when a variable is definitely assigned at the
 * end of a constructor, which is needed for final fields (8.3.1.2). We
 * implement the rule that V is DA at the end of the constructor iff it is DA
 * and the end of the body of the constructor and V is DA "due to" every return
 * of the constructor.
 * 
 * <p>
 * Intervening finally blocks similarly affect exception analysis. An
 * intervening finally that cannot complete normally allows us to ignore an
 * otherwise uncaught exception.
 * 
 * <p>
 * To implement the semantics of intervening finally clauses, all nonlocal
 * transfers (break, continue, return, throw, method call that can throw a
 * checked exception, and a constructor invocation that can thrown a checked
 * exception) are recorded in a queue, and removed from the queue when we
 * complete processing the target of the nonlocal transfer. This allows us to
 * modify the queue in accordance with the above rules when we encounter a
 * finally clause. The only exception to this [no pun intended] is that checked
 * exceptions that are known to be caught or declared to be caught in the
 * enclosing method are not recorded in the queue, but instead are recorded in a
 * global variable "Set<Type> thrown" that records the type of all exceptions
 * that can be thrown.
 * 
 * <p>
 * Other minor issues the treatment of members of other classes (always
 * considered DA except that within an anonymous class constructor, where DA
 * status from the enclosing scope is preserved), treatment of the case
 * expression (V is DA before the case expression iff V is DA after the switch
 * expression), treatment of variables declared in a switch block (the implied
 * DA/DU status after the switch expression is DU and not DA for variables
 * defined in a switch block), the treatment of boolean ?: expressions (The JLS
 * rules only handle b and c non-boolean; the new rule is that if b and c are
 * boolean valued, then V is (un)assigned after a?b:c when true/false iff V is
 * (un)assigned after b when true/false and V is (un)assigned after c when
 * true/false).
 * 
 * There is the remaining question of what syntactic forms constitute a
 * reference to a variable. It is conventional to allow this.x on the
 * left-hand-side to initialize a final instance field named x, yet this.x isn't
 * considered a "use" when appearing on a right-hand-side in most
 * implementations. Should parentheses affect what is considered a variable
 * reference? The simplest rule would be to allow unqualified forms only,
 * parentheses optional, and phase out support for assigning to a final field
 * via this.x.
 */
public class Flow extends TreeScanner implements Flags, Kinds, TypeTags {
	private static final Context.Key flowKey = new Context.Key();
	private Name.Table names;
	private Log log;
	private Symtab syms;
	private Check chk;
	private TreeMaker make;
	private boolean switchCheck;

	public static Flow instance(Context context) {
		Flow instance = (Flow) context.get(flowKey);
		if (instance == null)
			instance = new Flow(context);
		return instance;
	}

	private Flow(Context context) {
		super();
		context.put(flowKey, this);
		names = Name.Table.instance(context);
		log = Log.instance(context);
		syms = Symtab.instance(context);
		chk = Check.instance(context);
		Options options = Options.instance(context);
		switchCheck = options.get("-Xswitchcheck") != null;
	}

	/**
	 * A flag that indicates whether the last statement could complete normally.
	 */
	private boolean alive;

	/**
	 * The set of definitely assigned variables.
	 */
	Bits inits;

	/**
	 * The set of definitely unassigned variables.
	 */
	Bits uninits;

	/**
	 * The set of variables that are definitely unassigned everywhere in current
	 * try block. This variable is maintained lazily; it is updated only when
	 * something gets removed from uninits, typically by being assigned in
	 * reachable code. To obtain the correct set of variables which are
	 * definitely unassigned anywhere in current try block, intersect uninitsTry
	 * and uninits.
	 */
	Bits uninitsTry;

	/**
	 * When analyzing a condition, inits and uninits are null. Instead we have:
	 */
	Bits initsWhenTrue;
	Bits initsWhenFalse;
	Bits uninitsWhenTrue;
	Bits uninitsWhenFalse;

	/**
	 * A mapping from addresses to variable symbols.
	 */
	VarSymbol[] vars;

	/**
	 * The current class being defined.
	 */
	ClassDef classDef;

	/**
	 * The first variable sequence number in this class definition.
	 */
	int firstadr;

	/**
	 * The next available variable sequence number.
	 */
	int nextadr;

	/**
	 * The list of possibly thrown declarable exceptions.
	 */
	List thrown;

	/**
	 * The list of exceptions that are either caught or declared to be thrown.
	 */
	List caught;

	/**
	 * Set when processing a loop body the second time for DU analysis.
	 */
	boolean loopPassTwo = false;

	/**
	 * A pending exit. These are the statements return, break, and continue. In
	 * addition, exception-throwing expressions or statements are put here when
	 * not known to be caught. This will typically result in an error unless it
	 * is within a try-finally whose finally block cannot complete normally.
	 */
	static class PendingExit {
		Tree tree;
		Bits inits;
		Bits uninits;
		Type thrown;

		PendingExit(Tree tree, Bits inits, Bits uninits) {
			super();
			this.tree = tree;
			this.inits = inits.dup();
			this.uninits = uninits.dup();
		}

		PendingExit(Tree tree, Type thrown) {
			super();
			this.tree = tree;
			this.thrown = thrown;
		}
	}

	/**
	 * The currently pending exits that go from current inner blocks to an
	 * enclosing block, in source order.
	 */
	ListBuffer pendingExits;

	/**
	 * Complain that pending exceptions are not caught.
	 */
	void errorUncaught() {
		for (PendingExit exit = (Flow.PendingExit) pendingExits.next(); exit != null; exit = (Flow.PendingExit) pendingExits
				.next()) {
			boolean synthetic = classDef != null
					&& classDef.pos == exit.tree.pos;
			log.error(exit.tree.pos,
					synthetic ? "unreported.exception.default.constructor"
							: "unreported.exception.need.to.catch.or.throw",
					exit.thrown.toJava());
		}
	}

	/**
	 * Record that exception is potentially thrown and check that it is caught.
	 */
	void markThrown(Tree tree, Type exc) {
		if (!chk.isUnchecked(tree.pos, exc)) {
			if (!chk.isHandled(exc, caught))
				pendingExits.append(new PendingExit(tree, exc));
			thrown = chk.incl(exc, thrown);
		}
	}

	/**
	 * Do we need to track init/uninit state of this symbol? I.e. is symbol
	 * either a local or a blank final variable?
	 */
	boolean trackable(VarSymbol sym) {
		return (sym.owner.kind == MTH || ((sym.flags() & (FINAL | HASINIT | PARAMETER)) == FINAL && classDef.sym
				.isEnclosedBy((ClassSymbol) sym.owner)));
	}

	/**
	 * Initialize new trackable variable by setting its address field to the
	 * next available sequence number and entering it under that index into the
	 * vars array.
	 */
	void newVar(VarSymbol sym) {
		if (nextadr == vars.length) {
			VarSymbol[] newvars = new VarSymbol[nextadr * 2];
			System.arraycopy(vars, 0, newvars, 0, nextadr);
			vars = newvars;
		}
		sym.adr = nextadr;
		vars[nextadr] = sym;
		inits.excl(nextadr);
		uninits.incl(nextadr);
		nextadr++;
	}

	/**
	 * Record an initialization of a trackable variable.
	 */
	void letInit(int pos, VarSymbol sym) {
		if (sym.adr >= firstadr && trackable(sym)) {
			if ((sym.flags() & FINAL) != 0) {
				if ((sym.flags() & PARAMETER) != 0) {
					log.error(pos, "final.parameter.may.not.be.assigned", sym
							.toJava());
				} else if (!uninits.isMember(sym.adr)) {
					log.error(pos,
							loopPassTwo ? "var.might.be.assigned.in.loop"
									: "var.might.already.be.assigned", sym
									.toJava());
				} else if (!inits.isMember(sym.adr)) {
					uninits.excl(sym.adr);
					uninitsTry.excl(sym.adr);
				} else {
					uninits.excl(sym.adr);
				}
			}
			inits.incl(sym.adr);
		} else if ((sym.flags() & FINAL) != 0) {
			log.error(pos, "var.might.already.be.assigned", sym.toJava());
		}
	}

	/**
	 * If tree is either a simple name or of the form this.name or C.this.name,
	 * and tree represents a trackable variable, record an initialization of the
	 * variable.
	 */
	void letInit(Tree tree) {
		tree = TreeInfo.skipParens(tree);
		if (tree.tag == Tree.IDENT || tree.tag == Tree.SELECT) {
			Symbol sym = TreeInfo.symbol(tree);
			letInit(tree.pos, (VarSymbol) sym);
		}
	}

	/**
	 * Check that trackable variable is initialized.
	 */
	void checkInit(int pos, VarSymbol sym) {
		if ((sym.adr >= firstadr || sym.owner.kind != TYP) && trackable(sym)
				&& !inits.isMember(sym.adr)) {
			log.error(pos, "var.might.not.have.been.initialized", sym.toJava());
			inits.incl(sym.adr);
		}
	}

	/**
	 * Record an outward transfer of control.
	 */
	void recordExit(Tree tree) {
		pendingExits.append(new PendingExit(tree, inits, uninits));
		markDead();
	}

	/**
	 * Resolve all breaks of this statement.
	 */
	boolean resolveBreaks(Tree tree, ListBuffer oldPendingExits) {
		boolean result = false;
		List exits = pendingExits.toList();
		pendingExits = oldPendingExits;
		for (; exits.nonEmpty(); exits = exits.tail) {
			PendingExit exit = (Flow.PendingExit) exits.head;
			if (exit.tree.tag == Tree.BREAK
					&& ((Break) exit.tree).target == tree) {
				inits.andSet(exit.inits);
				uninits.andSet(exit.uninits);
				result = true;
			} else {
				pendingExits.append(exit);
			}
		}
		return result;
	}

	/**
	 * Resolve all continues of this statement.
	 */
	boolean resolveContinues(Tree tree) {
		boolean result = false;
		List exits = pendingExits.toList();
		pendingExits = new ListBuffer();
		for (; exits.nonEmpty(); exits = exits.tail) {
			PendingExit exit = (Flow.PendingExit) exits.head;
			if (exit.tree.tag == Tree.CONTINUE
					&& ((Continue) exit.tree).target == tree) {
				inits.andSet(exit.inits);
				uninits.andSet(exit.uninits);
				result = true;
			} else {
				pendingExits.append(exit);
			}
		}
		return result;
	}

	/**
	 * Record that statement is unreachable.
	 */
	void markDead() {
		inits.inclRange(firstadr, nextadr);
		uninits.inclRange(firstadr, nextadr);
		alive = false;
	}

	/**
	 * Split (duplicate) inits/uninits into WhenTrue/WhenFalse sets
	 */
	void split() {
		initsWhenFalse = inits.dup();
		uninitsWhenFalse = uninits.dup();
		initsWhenTrue = inits;
		uninitsWhenTrue = uninits;
		inits = uninits = null;
	}

	/**
	 * Merge (intersect) inits/uninits from WhenTrue/WhenFalse sets.
	 */
	void merge() {
		inits = initsWhenFalse.andSet(initsWhenTrue);
		uninits = uninitsWhenFalse.andSet(uninitsWhenTrue);
	}

	/**
	 * Analyze a definition.
	 */
	void scanDef(Tree tree) {
		scanStat(tree);
		if (tree != null && tree.tag == Tree.BLOCK && !alive) {
			log
					.error(tree.pos,
							"initializer.must.be.able.to.complete.normally");
		}
	}

	/**
	 * Analyze a statement. Check that statement is reachable.
	 */
	void scanStat(Tree tree) {
		if (!alive && tree != null) {
			log.error(tree.pos, "unreachable.stmt");
			if (tree.tag != Tree.SKIP)
				alive = true;
		}
		scan(tree);
	}

	/**
	 * Analyze list of statements.
	 */
	void scanStats(List trees) {
		if (trees != null)
			for (List l = trees; l.nonEmpty(); l = l.tail)
				scanStat((Tree) l.head);
	}

	/**
	 * Analyze an expression. Make sure to set (un)inits rather than
	 * (un)initsWhenTrue(WhenFalse) on exit.
	 */
	void scanExpr(Tree tree) {
		if (tree != null) {
			scan(tree);
			if (inits == null)
				merge();
		}
	}

	/**
	 * Analyze a list of expressions.
	 */
	void scanExprs(List trees) {
		if (trees != null)
			for (List l = trees; l.nonEmpty(); l = l.tail)
				scanExpr((Tree) l.head);
	}

	/**
	 * Analyze a condition. Make sure to set (un)initsWhenTrue(WhenFalse) rather
	 * than (un)inits on exit.
	 */
	void scanCond(Tree tree) {
		if (tree.type.isFalse()) {
			if (inits == null)
				merge();
			initsWhenTrue = inits.dup();
			initsWhenTrue.inclRange(firstadr, nextadr);
			uninitsWhenTrue = uninits.dup();
			uninitsWhenTrue.inclRange(firstadr, nextadr);
			initsWhenFalse = inits;
			uninitsWhenFalse = uninits;
		} else if (tree.type.isTrue()) {
			if (inits == null)
				merge();
			initsWhenFalse = inits.dup();
			initsWhenFalse.inclRange(firstadr, nextadr);
			uninitsWhenFalse = uninits.dup();
			uninitsWhenFalse.inclRange(firstadr, nextadr);
			initsWhenTrue = inits;
			uninitsWhenTrue = uninits;
		} else {
			scan(tree);
			if (inits != null)
				split();
		}
		inits = uninits = null;
	}

	public void visitClassDef(ClassDef tree) {
		if (tree.sym == null)
			return;
		ClassDef classDefPrev = classDef;
		List thrownPrev = thrown;
		List caughtPrev = caught;
		boolean alivePrev = alive;
		int firstadrPrev = firstadr;
		int nextadrPrev = nextadr;
		ListBuffer pendingExitsPrev = pendingExits;
		pendingExits = new ListBuffer();
		if (tree.name != names.empty) {
			caught = Type.emptyList;
			firstadr = nextadr;
		}
		classDef = tree;
		thrown = Type.emptyList;
		try {
			for (List l = tree.defs; l.nonEmpty(); l = l.tail) {
				if (((Tree) l.head).tag == Tree.VARDEF) {
					VarDef def = (VarDef) l.head;
					if ((def.flags & STATIC) != 0) {
						VarSymbol sym = def.sym;
						if (trackable(sym))
							newVar(sym);
					}
				}
			}
			for (List l = tree.defs; l.nonEmpty(); l = l.tail) {
				if (((Tree) l.head).tag != Tree.METHODDEF
						&& (TreeInfo.flags((Tree) l.head) & STATIC) != 0) {
					scanDef((Tree) l.head);
					errorUncaught();
				}
			}
			if (tree.name != names.empty) {
				boolean firstConstructor = true;
				for (List l = tree.defs; l.nonEmpty(); l = l.tail) {
					if (TreeInfo.isInitialConstructor((Tree) l.head)) {
						List mthrown = ((MethodDef) l.head).sym.type.thrown();
						if (firstConstructor) {
							caught = mthrown;
							firstConstructor = false;
						} else {
							caught = chk.intersect(mthrown, caught);
						}
					}
				}
			}
			for (List l = tree.defs; l.nonEmpty(); l = l.tail) {
				if (((Tree) l.head).tag == Tree.VARDEF) {
					VarDef def = (VarDef) l.head;
					if ((def.flags & STATIC) == 0) {
						VarSymbol sym = def.sym;
						if (trackable(sym))
							newVar(sym);
					}
				}
			}
			for (List l = tree.defs; l.nonEmpty(); l = l.tail) {
				if (((Tree) l.head).tag != Tree.METHODDEF
						&& (TreeInfo.flags((Tree) l.head) & STATIC) == 0) {
					scanDef((Tree) l.head);
					errorUncaught();
				}
			}
			for (List l = tree.defs; l.nonEmpty(); l = l.tail) {
				if (((Tree) l.head).tag == Tree.METHODDEF) {
					scan((Tree) l.head);
					errorUncaught();
				}
			}
			if (tree.name == names.empty) {
				for (List l = tree.defs; l.nonEmpty(); l = l.tail) {
					if (TreeInfo.isInitialConstructor((Tree) l.head)) {
						MethodDef mdef = (MethodDef) l.head;
						mdef.thrown = make.Types(thrown);
						((MethodType) mdef.sym.type).thrown = thrown;
					}
				}
				thrown = chk.union(thrown, thrownPrev);
			} else {
				thrown = thrownPrev;
			}
		} finally {
			pendingExits = pendingExitsPrev;
			alive = alivePrev;
			nextadr = nextadrPrev;
			firstadr = firstadrPrev;
			caught = caughtPrev;
			classDef = classDefPrev;
		}
	}

	public void visitMethodDef(MethodDef tree) {
		if (tree.body == null)
			return;
		List caughtPrev = caught;
		List mthrown = tree.sym.type.thrown();
		Bits initsPrev = inits.dup();
		Bits uninitsPrev = uninits.dup();
		int nextadrPrev = nextadr;
		int firstadrPrev = firstadr;
		assert pendingExits.isEmpty();
		try {
			boolean isInitialConstructor = TreeInfo.isInitialConstructor(tree);
			if (!isInitialConstructor)
				firstadr = nextadr;
			for (List l = tree.params; l.nonEmpty(); l = l.tail) {
				VarDef def = (Tree.VarDef) l.head;
				scan(def);
				inits.incl(def.sym.adr);
				uninits.excl(def.sym.adr);
			}
			if (isInitialConstructor)
				caught = chk.union(caught, mthrown);
			else if ((tree.sym.flags() & (BLOCK | STATIC)) != BLOCK)
				caught = mthrown;
			alive = true;
			scanStat(tree.body);
			int endPos = TreeInfo.endPos(tree.body);
			if (alive && tree.sym.type.restype().tag != VOID)
				log.error(endPos, "missing.ret.stmt");
			if (isInitialConstructor) {
				for (int i = firstadr; i < nextadr; i++)
					if (vars[i].owner == classDef.sym)
						checkInit(endPos, vars[i]);
			}
			List exits = pendingExits.toList();
			pendingExits = new ListBuffer();
			while (exits.nonEmpty()) {
				PendingExit exit = (Flow.PendingExit) exits.head;
				exits = exits.tail;
				if (exit.thrown == null) {
					assert exit.tree.tag == Tree.RETURN;
					if (isInitialConstructor) {
						inits = exit.inits;
						for (int i = firstadr; i < nextadr; i++)
							checkInit(exit.tree.pos, vars[i]);
					}
				} else {
					pendingExits.append(exit);
				}
			}
		} finally {
			inits = initsPrev;
			uninits = uninitsPrev;
			nextadr = nextadrPrev;
			firstadr = firstadrPrev;
			caught = caughtPrev;
		}
	}

	public void visitVarDef(VarDef tree) {
		boolean track = trackable(tree.sym);
		if (track && tree.sym.owner.kind == MTH)
			newVar(tree.sym);
		if (tree.init != null) {
			scanExpr(tree.init);
			if (track)
				letInit(tree.pos, tree.sym);
		}
	}

	public void visitBlock(Block tree) {
		int nextadrPrev = nextadr;
		scanStats(tree.stats);
		nextadr = nextadrPrev;
	}

	public void visitDoLoop(DoLoop tree) {
		ListBuffer prevPendingExits = pendingExits;
		boolean prevLoopPassTwo = loopPassTwo;
		do {
			pendingExits = new ListBuffer();
			Bits uninitsEntry = uninits.dup();
			scanStat(tree.body);
			alive |= resolveContinues(tree);
			scanCond(tree.cond);
			if (log.nerrors != 0
					|| loopPassTwo
					|| uninitsEntry.diffSet(uninitsWhenTrue).nextBit(firstadr) == -1)
				break;
			inits = initsWhenTrue;
			uninits = uninitsEntry.andSet(uninitsWhenTrue);
			loopPassTwo = true;
			alive = true;
		} while (true);
		loopPassTwo = prevLoopPassTwo;
		inits = initsWhenFalse;
		uninits = uninitsWhenFalse;
		alive = alive && !tree.cond.type.isTrue();
		alive |= resolveBreaks(tree, prevPendingExits);
	}

	public void visitWhileLoop(WhileLoop tree) {
		ListBuffer prevPendingExits = pendingExits;
		boolean prevLoopPassTwo = loopPassTwo;
		Bits initsCond;
		Bits uninitsCond;
		do {
			pendingExits = new ListBuffer();
			Bits uninitsEntry = uninits.dup();
			scanCond(tree.cond);
			initsCond = initsWhenFalse;
			uninitsCond = uninitsWhenFalse;
			inits = initsWhenTrue;
			uninits = uninitsWhenTrue;
			alive = !tree.cond.type.isFalse();
			scanStat(tree.body);
			alive |= resolveContinues(tree);
			if (log.nerrors != 0 || loopPassTwo
					|| uninitsEntry.diffSet(uninits).nextBit(firstadr) == -1)
				break;
			uninits = uninitsEntry.andSet(uninits);
			loopPassTwo = true;
			alive = true;
		} while (true);
		loopPassTwo = prevLoopPassTwo;
		inits = initsCond;
		uninits = uninitsCond;
		alive = resolveBreaks(tree, prevPendingExits)
				|| !tree.cond.type.isTrue();
	}

	public void visitForLoop(ForLoop tree) {
		ListBuffer prevPendingExits = pendingExits;
		boolean prevLoopPassTwo = loopPassTwo;
		int nextadrPrev = nextadr;
		scanStats(tree.init);
		Bits initsCond;
		Bits uninitsCond;
		do {
			pendingExits = new ListBuffer();
			Bits uninitsEntry = uninits.dup();
			if (tree.cond != null) {
				scanCond(tree.cond);
				initsCond = initsWhenFalse;
				uninitsCond = uninitsWhenFalse;
				inits = initsWhenTrue;
				uninits = uninitsWhenTrue;
				alive = !tree.cond.type.isFalse();
			} else {
				initsCond = inits.dup();
				initsCond.inclRange(firstadr, nextadr);
				uninitsCond = uninits.dup();
				uninitsCond.inclRange(firstadr, nextadr);
				alive = true;
			}
			scanStat(tree.body);
			alive |= resolveContinues(tree);
			scan(tree.step);
			if (log.nerrors != 0 || loopPassTwo
					|| uninitsEntry.diffSet(uninits).nextBit(firstadr) == -1)
				break;
			uninits = uninitsEntry.andSet(uninits);
			loopPassTwo = true;
			alive = true;
		} while (true);
		loopPassTwo = prevLoopPassTwo;
		inits = initsCond;
		uninits = uninitsCond;
		alive = resolveBreaks(tree, prevPendingExits) || tree.cond != null
				&& !tree.cond.type.isTrue();
		nextadr = nextadrPrev;
	}

	public void visitLabelled(Labelled tree) {
		ListBuffer prevPendingExits = pendingExits;
		pendingExits = new ListBuffer();
		scanStat(tree.body);
		alive |= resolveBreaks(tree, prevPendingExits);
	}

	public void visitSwitch(Switch tree) {
		ListBuffer prevPendingExits = pendingExits;
		pendingExits = new ListBuffer();
		int nextadrPrev = nextadr;
		scanExpr(tree.selector);
		Bits initsSwitch = inits;
		Bits uninitsSwitch = uninits.dup();
		boolean hasDefault = false;
		for (List l = tree.cases; l.nonEmpty(); l = l.tail) {
			alive = true;
			inits = initsSwitch.dup();
			uninits = uninits.andSet(uninitsSwitch);
			Case c = (Tree.Case) l.head;
			if (c.pat == null)
				hasDefault = true;
			else
				scanExpr(c.pat);
			scanStats(c.stats);
			addVars(c.stats, initsSwitch, uninitsSwitch);
			if (!loopPassTwo && switchCheck && alive && c.stats.nonEmpty()
					&& l.tail.nonEmpty())
				log.warning(((Tree.Case) l.tail.head).pos,
						"possible.fall-through.into.case");
		}
		if (!hasDefault) {
			inits.andSet(initsSwitch);
			alive = true;
		}
		alive |= resolveBreaks(tree, prevPendingExits);
		nextadr = nextadrPrev;
	}

	/**
	 * Add any variables defined in stats to inits and uninits.
	 */
	private static void addVars(List stats, Bits inits, Bits uninits) {
		for (; stats.nonEmpty(); stats = stats.tail) {
			Tree stat = (Tree) stats.head;
			if (stat.tag == Tree.VARDEF) {
				int adr = ((VarDef) stat).sym.adr;
				inits.excl(adr);
				uninits.incl(adr);
			}
		}
	}

	public void visitTry(Try tree) {
		List caughtPrev = caught;
		List thrownPrev = thrown;
		thrown = Type.emptyList;
		for (List l = tree.catchers; l.nonEmpty(); l = l.tail)
			caught = chk.incl(((Tree.Catch) l.head).param.type, caught);
		Bits uninitsTryPrev = uninitsTry;
		ListBuffer prevPendingExits = pendingExits;
		pendingExits = new ListBuffer();
		Bits initsTry = inits.dup();
		uninitsTry = uninits.dup();
		scanStat(tree.body);
		List thrownInTry = thrown;
		thrown = thrownPrev;
		caught = caughtPrev;
		boolean aliveEnd = alive;
		uninitsTry.andSet(uninits);
		Bits initsEnd = inits;
		Bits uninitsEnd = uninits;
		int nextadrCatch = nextadr;
		List caughtInTry = Type.emptyList;
		for (List l = tree.catchers; l.nonEmpty(); l = l.tail) {
			alive = true;
			VarDef param = ((Tree.Catch) l.head).param;
			Type exc = param.type;
			if (chk.subset(exc, caughtInTry)) {
				log.error(((Tree.Catch) l.head).pos, "except.already.caught",
						exc.toJava());
			} else if (!chk.isUnchecked(((Tree.Catch) l.head).pos, exc)
					&& exc != syms.throwableType && exc != syms.exceptionType
					&& !chk.intersects(exc, thrownInTry)) {
				log.error(((Tree.Catch) l.head).pos,
						"except.never.thrown.in.try", exc.toJava());
			}
			caughtInTry = chk.incl(exc, caughtInTry);
			inits = initsTry.dup();
			uninits = uninitsTry.dup();
			scan(param);
			inits.incl(param.sym.adr);
			uninits.excl(param.sym.adr);
			scanStat(((Tree.Catch) l.head).body);
			initsEnd.andSet(inits);
			uninitsEnd.andSet(uninits);
			nextadr = nextadrCatch;
			aliveEnd |= alive;
		}
		if (tree.finalizer != null) {
			List savedThrown = thrown;
			thrown = Type.emptyList;
			inits = initsTry.dup();
			uninits = uninitsTry.dup();
			ListBuffer exits = pendingExits;
			pendingExits = prevPendingExits;
			alive = true;
			scanStat(tree.finalizer);
			if (!alive) {
				thrown = chk.union(thrown, thrownPrev);
				if (!loopPassTwo)
					log.warning(TreeInfo.endPos(tree.finalizer),
							"finally.cannot.complete");
			} else {
				thrown = chk.union(thrown, chk.diff(thrownInTry, caughtInTry));
				thrown = chk.union(thrown, savedThrown);
				uninits.andSet(uninitsEnd);
				while (exits.nonEmpty()) {
					PendingExit exit = (Flow.PendingExit) exits.next();
					if (exit.inits != null) {
						exit.inits.orSet(inits);
						exit.uninits.andSet(uninits);
					}
					pendingExits.append(exit);
				}
				inits.orSet(initsEnd);
				alive = aliveEnd;
			}
		} else {
			thrown = chk.union(thrown, chk.diff(thrownInTry, caughtInTry));
			inits = initsEnd;
			uninits = uninitsEnd;
			alive = aliveEnd;
			ListBuffer exits = pendingExits;
			pendingExits = prevPendingExits;
			while (exits.nonEmpty())
				pendingExits.append(exits.next());
		}
		uninitsTry.andSet(uninitsTryPrev).andSet(uninits);
	}

	public void visitConditional(Conditional tree) {
		scanCond(tree.cond);
		Bits initsBeforeElse = initsWhenFalse;
		Bits uninitsBeforeElse = uninitsWhenFalse;
		inits = initsWhenTrue;
		uninits = uninitsWhenTrue;
		if (tree.truepart.type.tag == BOOLEAN
				&& tree.falsepart.type.tag == BOOLEAN) {
			scanCond(tree.truepart);
			Bits initsAfterThenWhenTrue = initsWhenTrue.dup();
			Bits initsAfterThenWhenFalse = initsWhenFalse.dup();
			Bits uninitsAfterThenWhenTrue = uninitsWhenTrue.dup();
			Bits uninitsAfterThenWhenFalse = uninitsWhenFalse.dup();
			inits = initsBeforeElse;
			uninits = uninitsBeforeElse;
			scanCond(tree.falsepart);
			initsWhenTrue.andSet(initsAfterThenWhenTrue);
			initsWhenFalse.andSet(initsAfterThenWhenFalse);
			uninitsWhenTrue.andSet(uninitsAfterThenWhenTrue);
			uninitsWhenFalse.andSet(uninitsAfterThenWhenFalse);
		} else {
			scanExpr(tree.truepart);
			Bits initsAfterThen = inits.dup();
			Bits uninitsAfterThen = uninits.dup();
			inits = initsBeforeElse;
			uninits = uninitsBeforeElse;
			scanExpr(tree.falsepart);
			inits.andSet(initsAfterThen);
			uninits.andSet(uninitsAfterThen);
		}
	}

	public void visitIf(If tree) {
		scanCond(tree.cond);
		Bits initsBeforeElse = initsWhenFalse;
		Bits uninitsBeforeElse = uninitsWhenFalse;
		inits = initsWhenTrue;
		uninits = uninitsWhenTrue;
		scanStat(tree.thenpart);
		if (tree.elsepart != null) {
			boolean aliveAfterThen = alive;
			alive = true;
			Bits initsAfterThen = inits.dup();
			Bits uninitsAfterThen = uninits.dup();
			inits = initsBeforeElse;
			uninits = uninitsBeforeElse;
			scanStat(tree.elsepart);
			inits.andSet(initsAfterThen);
			uninits.andSet(uninitsAfterThen);
			alive = alive | aliveAfterThen;
		} else {
			inits.andSet(initsBeforeElse);
			uninits.andSet(uninitsBeforeElse);
			alive = true;
		}
	}

	public void visitBreak(Break tree) {
		recordExit(tree);
	}

	public void visitContinue(Continue tree) {
		recordExit(tree);
	}

	public void visitReturn(Return tree) {
		scanExpr(tree.expr);
		recordExit(tree);
	}

	public void visitThrow(Throw tree) {
		scanExpr(tree.expr);
		markThrown(tree, tree.expr.type);
		markDead();
	}

	public void visitApply(Apply tree) {
		scanExpr(tree.meth);
		scanExprs(tree.args);
		for (List l = tree.meth.type.thrown(); l.nonEmpty(); l = l.tail)
			markThrown(tree, (Type) l.head);
	}

	public void visitNewClass(NewClass tree) {
		scanExpr(tree.encl);
		scanExprs(tree.args);
		for (List l = tree.constructor.type.thrown(); l.nonEmpty(); l = l.tail)
			markThrown(tree, (Type) l.head);
		scan(tree.def);
	}

	public void visitNewArray(NewArray tree) {
		scanExprs(tree.dims);
		scanExprs(tree.elems);
	}

	public void visitAssert(Assert tree) {
		Bits initsExit = inits.dup();
		Bits uninitsExit = uninits.dup();
		scanCond(tree.cond);
		uninitsExit.andSet(uninitsWhenTrue);
		if (tree.detail != null) {
			inits = initsWhenFalse;
			uninits = uninitsWhenFalse;
			scanExpr(tree.detail);
		}
		inits = initsExit;
		uninits = uninitsExit;
	}

	public void visitAssign(Assign tree) {
		Tree lhs = TreeInfo.skipParens(tree.lhs);
		if (!(lhs instanceof Ident))
			scanExpr(lhs);
		scanExpr(tree.rhs);
		letInit(lhs);
	}

	public void visitAssignop(Assignop tree) {
		scanExpr(tree.lhs);
		scanExpr(tree.rhs);
		letInit(tree.lhs);
	}

	public void visitUnary(Unary tree) {
		switch (tree.tag) {
		case Tree.NOT:
			scanCond(tree.arg);
			Bits t = initsWhenFalse;
			initsWhenFalse = initsWhenTrue;
			initsWhenTrue = t;
			t = uninitsWhenFalse;
			uninitsWhenFalse = uninitsWhenTrue;
			uninitsWhenTrue = t;
			break;

		case Tree.PREINC:

		case Tree.POSTINC:

		case Tree.PREDEC:

		case Tree.POSTDEC:
			scanExpr(tree.arg);
			letInit(tree.arg);
			break;

		default:
			scanExpr(tree.arg);

		}
	}

	public void visitBinary(Binary tree) {
		switch (tree.tag) {
		case Tree.AND:
			scanCond(tree.lhs);
			Bits initsWhenFalseLeft = initsWhenFalse;
			Bits uninitsWhenFalseLeft = uninitsWhenFalse;
			inits = initsWhenTrue;
			uninits = uninitsWhenTrue;
			scanCond(tree.rhs);
			initsWhenFalse.andSet(initsWhenFalseLeft);
			uninitsWhenFalse.andSet(uninitsWhenFalseLeft);
			break;

		case Tree.OR:
			scanCond(tree.lhs);
			Bits initsWhenTrueLeft = initsWhenTrue;
			Bits uninitsWhenTrueLeft = uninitsWhenTrue;
			inits = initsWhenFalse;
			uninits = uninitsWhenFalse;
			scanCond(tree.rhs);
			initsWhenTrue.andSet(initsWhenTrueLeft);
			uninitsWhenTrue.andSet(uninitsWhenTrueLeft);
			break;

		default:
			scanExpr(tree.lhs);
			scanExpr(tree.rhs);

		}
	}

	public void visitIdent(Ident tree) {
		if (tree.sym.kind == VAR)
			checkInit(tree.pos, (VarSymbol) tree.sym);
	}

	/**
	 * Perform definite assignment/unassignment analysis on a tree.
	 */
	public void analyzeTree(Tree tree, TreeMaker make) {
		try {
			this.make = make;
			inits = new Bits();
			uninits = new Bits();
			uninitsTry = new Bits();
			initsWhenTrue = initsWhenFalse = uninitsWhenTrue = uninitsWhenFalse = null;
			if (vars == null)
				vars = new VarSymbol[32];
			else
				for (int i = 0; i < vars.length; i++)
					vars[i] = null;
			firstadr = 0;
			nextadr = 0;
			pendingExits = new ListBuffer();
			alive = true;
			this.thrown = this.caught = null;
			this.classDef = null;
			scan(tree);
		} finally {
			inits = uninits = uninitsTry = null;
			initsWhenTrue = initsWhenFalse = uninitsWhenTrue = uninitsWhenFalse = null;
			if (vars != null)
				for (int i = 0; i < vars.length; i++)
					vars[i] = null;
			firstadr = 0;
			nextadr = 0;
			pendingExits = null;
			this.make = null;
			this.thrown = this.caught = null;
			this.classDef = null;
		}
	}
}
