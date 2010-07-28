package sjm.examples.robot;

import sjm.parse.*;
import sjm.parse.tokens.Token;

/*
 * Copyright (c) 1999 Steven J. Metsker. All Rights Reserved.
 * 
 * Steve Metsker makes no representations or warranties about
 * the fitness of this software for any particular purpose, 
 * including the implied warranty of merchantability.
 */
 
/**
 * Sets an assembly's target to be a <code>PickCommand
 * </code> and note its location.
 * 
 * @author Steven J. Metsker
 *
 * @version 1.0
 */
public class PickAssembler extends Assembler {
/**
 * Sets an assembly's target to be a 
 * <code>PickCommand</code> object and note its location.
 *
 * @param  Assembly  the assembly to work on
 */
public void workOn(Assembly a) {
	PickCommand pc = new PickCommand();
	Token t = (Token) a.pop();
	pc.setLocation(t.sval());
	a.setTarget(pc);	
}
}