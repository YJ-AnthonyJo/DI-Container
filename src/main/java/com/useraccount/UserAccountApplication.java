package com.useraccount;

import org.di.framework.Injector;

public class UserAccountApplication {

	public static void main(String[] args) {
		long startTime = System.currentTimeMillis();
		Injector.startApplication(UserAccountApplication.class);
		Injector.getService(UserAccountClientComponent.class).displayUserAccount(); //get UserAccountClientComponent's instance from Injector(DI Container)

		// The code of the below two lines which is commented out is the same as the code of the above line.
//		UserAccountClientComponent userAccountClientComponent = Injector.getService(UserAccountClientComponent.class);
//		userAccountClientComponent.displayUserAccount();

		long endTime = System.currentTimeMillis();
		System.out.println("\tElapsed time: " + getFormattedDifferenceOfMillis(endTime, startTime) + " seconds\n");
	}
	
	static String getFormattedDifferenceOfMillis(long value1, long value2) {
		String valueFormatted = String.format("%04d", (value1 - value2));
		return valueFormatted.substring(0, valueFormatted.length() - 3) + "," + valueFormatted.substring(valueFormatted.length() -3);
	}
}
