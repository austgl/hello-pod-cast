package sjm.examples.tests;

import sjm.examples.coffee.CoffeeParser;
import sjm.parse.tokens.TokenTester;

/*
 * Copyright (c) 2000 Steven J. Metsker. All Rights Reserved.
 * 
 * Steve Metsker makes no representations or warranties about
 * the fitness of this software for any particular purpose, 
 * including the implied warranty of merchantability.
 */

/**
 * Test the <code>CoffeeParser</code> class.
 * 
 * @author Steven J. Metsker
 * 
 * @version 1.0 
 */
public class ShowCoffeeTest {
/**
 * Test the <code>CoffeeParser</code> class.
 */
public static void main(String[] args) {
	new TokenTester(CoffeeParser.start()).test();
}
}