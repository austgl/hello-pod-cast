package com.hellocode.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class NetWorkingUtil {

	public static void download(String http_url, String abs_file_name) {

		BufferedInputStream bis = null;
		BufferedOutputStream bos = null;
		try {
			URL url = new URL(http_url);
			URLConnection urlc = url.openConnection();
			bis = new BufferedInputStream(urlc.getInputStream());
			bos = new BufferedOutputStream(new FileOutputStream(new File(
					abs_file_name)));
			int i;
			while ((i = bis.read()) != -1) {
				bos.write(i);
			}
		} catch (Exception e) {
		} finally {
			if (bis != null)
				try {
					bis.close();
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			if (bos != null)
				try {
					bos.close();
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
		}
	}

	/**
	 * public static void getHttpFile(String http_url, String abs_file_name) {
	 * String sURL = http_url; int nStartPos = 0; int nRead = 0;
	 * 
	 * URL url = null; HttpURLConnection httpConnection = null; RandomAccessFile
	 * oSavedFile = null; try { url = new URL(sURL); // 打开连接 httpConnection =
	 * (HttpURLConnection) url.openConnection(); // 获得文件长度 long nEndPos =
	 * getFileSize(sURL); oSavedFile = new RandomAccessFile(abs_file_name,
	 * "rw"); httpConnection .setRequestProperty("User-Agent",
	 * "Internet Explorer"); String sProperty = "bytes=" + nStartPos + "-"; //
	 * 告诉服务器book.rar这个文件从nStartPos字节开始传
	 * httpConnection.setRequestProperty("RANGE", sProperty);
	 * 
	 * InputStream input = httpConnection.getInputStream(); byte[] b = new
	 * byte[1024]; // 读取网络文件,写入指定的文件中 while ((nRead = input.read(b, 0, 1024)) >
	 * 0 && nStartPos < nEndPos) { oSavedFile.write(b, 0, nRead); nStartPos +=
	 * nRead; } httpConnection.disconnect(); input.close(); oSavedFile.close();
	 * } catch (Exception e) { e.printStackTrace(); } finally { try { url =
	 * null; httpConnection.disconnect(); oSavedFile.close(); httpConnection =
	 * null; oSavedFile = null; } catch (Exception e) { e.printStackTrace(); } }
	 * // here shall use finally{}...close something...
	 * 
	 * System.out.println("save file ok =" + abs_file_name);
	 * 
	 * }
	 **/

	// 获得文件长度
	public static long getFileSize(String sURL) {
		int nFileLength = -1;
		try {
			URL url = new URL(sURL);
			HttpURLConnection httpConnection = (HttpURLConnection) url
					.openConnection();
			httpConnection
					.setRequestProperty("User-Agent", "Internet Explorer");

			int responseCode = httpConnection.getResponseCode();
			if (responseCode >= 400) {
				System.err.println("Error Code : " + responseCode);
				return -2; // -2 represent access is error
			}
			String sHeader;
			for (int i = 1;; i++) {
				sHeader = httpConnection.getHeaderFieldKey(i);
				if (sHeader != null) {
					if (sHeader.equals("Content-Length")) {
						nFileLength = Integer.parseInt(httpConnection
								.getHeaderField(sHeader));
						break;
					}
				} else
					break;
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println(nFileLength);
		return nFileLength;
	}

}
