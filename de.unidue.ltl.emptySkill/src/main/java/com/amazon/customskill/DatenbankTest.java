package com.amazon.customskill;

import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatenbankTest {


	private static Connection con = null;
	private static Statement stmt = null;


	public static void main (String[] args) throws URISyntaxException {

		System.out.println(DatenbankTest.class.getClassLoader().getResource("utterances.txt").toURI());
		
		try {
			con = DBConnection.getConnection();
			stmt = con.createStatement();
			ResultSet rs = stmt
					.executeQuery("SELECT * FROM Tabu_Synonyme");
		/*	String getTabuwort = rs.getString("Tabuwort");
			System.out.println(getTabuwort); */
		} catch (Exception e){
			e.printStackTrace();
		}
	}


}