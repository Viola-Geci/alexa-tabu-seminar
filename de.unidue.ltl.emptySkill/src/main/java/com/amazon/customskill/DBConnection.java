package com.amazon.customskill;

import java.sql.DriverManager;
import java.sql.*;

	public class DBConnection {

		static String DBName = "TabuAlexa.db";
		private static Connection con = null;
	/*
	 * establishing the connection with the SQLite database 
	 * */
		public static Connection getConnection() {
			try {
				Class.forName("org.sqlite.JDBC");
				try {
					System.out.println("DB started");
					con = DriverManager.getConnection("jdbc:sqlite::resource:" + 
					           DBConnection.class.getClassLoader().getResource(DBName));
					} catch (SQLException ex) {
					System.out.println("Failed to create the database connection.");
					ex.printStackTrace();
				}
			} catch (ClassNotFoundException ex) {
				ex.printStackTrace();
			}
			return con;
		}

	}

