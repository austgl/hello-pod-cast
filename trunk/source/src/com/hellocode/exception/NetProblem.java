package com.hellocode.exception;

public class NetProblem extends Exception {

	@Override
	public String getMessage() {
		
		return "�����쳣";
	}

	@Override
	public void printStackTrace() {
		super.printStackTrace();
	}

	@Override
	public String toString() {
		return super.toString()+"�����쳣";
	}
	
}
