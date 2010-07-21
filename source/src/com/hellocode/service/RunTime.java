package com.hellocode.service;

import java.io.FileNotFoundException;

import com.hellocode.main.PodCast;
import com.hellocode.model.JDomPodCastURL;
import com.hellocode.util.FileUtil;
import com.hellocode.util.XML2JavaUtil;

public final class RunTime {

	public static String selectedFileName = "";
	private static final String CONFIG_XML = "mead.xml";
	public static boolean synchronizing = false;
	public static Config CONFIG = new Config();
	private static boolean refreshing = false;
	static {
		init();
	}

	public static void destroy() {
		try {
			XML2JavaUtil.java2XML(CONFIG_XML, RunTime.CONFIG);
		} catch (Exception e) {
			FileUtil.deleteFile(CONFIG_XML);
		}
		System.out.println("DESTROY");
	}

	public static void init() {

		try {
			CONFIG = XML2JavaUtil.XML2Java(CONFIG_XML);
		} catch (FileNotFoundException e) {
			FileUtil.deleteFile(CONFIG_XML);
			CONFIG = new Config();
		}
		// feed_tv.add(url);
	}

	public static JDomPodCastURL findFeedByName(String name) {
		for (JDomPodCastURL pod : RunTime.CONFIG.feed_au) {
			if (pod.getName().equalsIgnoreCase(name)) {
				return pod;
			}
		}
		// throw new RuntimeException();
		return null;
	}

	public static void refreshAll() {
		PodCast.main.lb_info.setText("���ڸ���feed,���Ժ�...");
		if (RunTime.CONFIG.feed_au.size() == 0) {
			PodCast.main.lb_info.setText("��û�ж����κ�feed,����Add Feed. ллʹ��");
		}
		if (refreshing) {
			PodCast.main.lb_info.setText("�̳߳�ͻ��������ˢ��Refresh");
			return;
		}
		
		new Thread(new Runnable() {

			@Override
			public void run() {
				
				synchronized (RunTime.CONFIG.feed_au) {
					refreshing = true;
					try {
						System.out.println("runing...");
						for (JDomPodCastURL pod : RunTime.CONFIG.feed_au) {
							System.out.println("runing...");
							PodCast.main.lb_info.setText(pod.getName() + "���ڸ���"
									+ pod.getURL());

							pod.reFreshURL();
						}
						PodCast.main.lb_info.setText("��ɸ���!");
						System.out.println("runing...");
					} catch (Exception e) {
						refreshing = false;
					} finally {
						refreshing = false;
					}
					refreshing = false;
				}
			}
		}).start();
		System.out.println("runing...");
	}

	public static void main(String[] args) {
		// RunTime run = new RunTime();
		System.out.println(RunTime.CONFIG.disk_main);

	}
}
