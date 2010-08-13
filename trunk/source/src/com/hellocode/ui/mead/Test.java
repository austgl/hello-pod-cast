/*
 * Test.java
 *
 * Created on __DATE__, __TIME__
 */

package com.hellocode.ui.mead;

/**
 * 
 * @author __USER__
 */
public class Test extends javax.swing.JFrame {

	/** Creates new form Test */
	public Test() {
		initComponents();
		this.init();
	}

	private void init() {
		this.mead = new MeadProgress(3, 10, 10, 20, 200, "正在进行下载");
		this.mead.setDoing(0);
		//this.add(this.slider);
		this.add(
						this.mead,
						new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0,
								-1, -1));
	}

	/**
	 * This method is called from within the constructor to initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is always
	 * regenerated by the Form Editor.
	 */
	//GEN-BEGIN:initComponents
	// <editor-fold defaultstate="collapsed" desc="Generated Code">
	private void initComponents() {

		jButton1 = new javax.swing.JButton();

		setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
		getContentPane().setLayout(
				new org.netbeans.lib.awtextra.AbsoluteLayout());

		jButton1.setText("Test");
		getContentPane().add(
				jButton1,
				new org.netbeans.lib.awtextra.AbsoluteConstraints(250, 190, -1,
						-1));

		pack();
	}// </editor-fold>
	//GEN-END:initComponents

	/**
	 * @param args
	 *            the command line arguments
	 */
	public static void main(String args[]) {
		final Test t = new Test();
		t.setVisible(true);
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		t.mead.setDone(0);
		t.mead.setDoing(1);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		t.mead.setDone(1);
		t.mead.setDoing(2);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		t.mead.setDone(2);
		t.mead.setDoing(3);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		t.mead.setDone(3);
		t.mead.setDoing(3);
		t.mead.setDone("下载任务全部完成");

	}

	public MeadProgress mead = null;
	//GEN-BEGIN:variables
	// Variables declaration - do not modify
	private javax.swing.JButton jButton1;
	// End of variables declaration//GEN-END:variables

}