package com.amazon.customskill;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Random;
import com.amazon.customskill.DBConnection;

public class Tabulist {

	public static String tabuword;
	private static Connection con = null;
	private static Statement stmt = null;// Sql statement 
	Random random = new Random(); // fuer Randomisierte Auswahl der Woerter 
    int rand; // int mit Aktuellen rand num 
    int randOld;
    ArrayList<String> rnlist;
    
    public ArrayList<String> selectTabuword() {
    	rnlist = new ArrayList<String>();
    	
    	try {
			con = DBConnection.getConnection();
			stmt = con.createStatement();
			ResultSet rs = stmt
					.executeQuery("SELECT *  FROM Tabu_Woerter ");
			while (rs.next()) {
				tabuword = rs.getString("tabuwort");
				rnlist.add(tabuword);
			
			}
			/*
			
			for(String s : rnlist)
			{
				System.out.println(s + "\n");
			}*/
			
		} catch (Exception e) {
			e.printStackTrace();
		}

		return rnlist;
	}
    
    public String tabuword() {
    	return tabuword;
    }

    }

