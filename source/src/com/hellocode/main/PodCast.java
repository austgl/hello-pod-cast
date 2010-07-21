/**
 * 
 */
package com.hellocode.main;

import com.hellocode.service.RunTime;
import com.hellocode.ui.MainFrame;

/**
 * @author Administrator
 * 
 */
public final class PodCast {

	public static MainFrame main;

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		RunTime.init();

		main = new MainFrame();
		main.setVisible(true);

		System.out.println("Main...");
		try {
			Thread.sleep(500);
			RunTime.refreshAll();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}