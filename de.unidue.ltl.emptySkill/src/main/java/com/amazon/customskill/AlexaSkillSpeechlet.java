/**
    Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.
    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at
        http://aws.amazon.com/apache2.0/
    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.amazon.customskill;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;


import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.customskill.AlexaSkillSpeechlet.UserIntent;
import com.amazon.speech.json.SpeechletRequestEnvelope;
import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.speechlet.SpeechletV2;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SsmlOutputSpeech;

import java.lang.Math;

/*
 * This class is the actual skill. Here you receive the input and have to produce the speech output. 
 */
public class AlexaSkillSpeechlet
implements SpeechletV2
{
	static Logger logger = LoggerFactory.getLogger(AlexaSkillSpeechlet.class);

	//Idee: Variablen die wir auch in Dialogos benutzt haben z.B. e1,e2,e3
	static boolean tabuwortUsed;
	static String getTabuwort = "";
	static String getNextTabuwort = "";
	static boolean knowsApplication;
	ArrayList<String> Tabulist;
	static String correctAnswer = "";
	static String answerOption1 = "";
	static String answerOption2 = "";
	static boolean jaUsed;
	static boolean neinUsed;
	

    //Was User sagt 
	public static String userRequest;

	//Muss noch angepasst werden auf unser Modell
	//Möchten Sie das Spiel anfangen oder weiterspielen? - Ja - selectTabuwort
	//Nein - Aufwiedersehen, danke fürs spielen
	static enum RecognitionState {Erklaerung, JaNein};
	RecognitionState recState;

	//Was User gerade gesagt hat - semantictags aus DialogOS - e1,e2,e3
	static enum UserIntent{UserErklaerung, UserNenntTabuwort, Straße, Ampel, Farben, Anhalten, Error, Ja, Nein}; //UserErklärung und UserNenntTabuwort = Pattern anlegen
	UserIntent ourUserIntent;

	//Was System sagen kann
	Map <String, String> utterances;


	//baut systemaeußerung zsm
	String buildString(String msg, String replacement1, String replacement2) {
		return msg.replace("{replacement}", replacement1).replace("{replacement2}", replacement2);
	}

	//ließt am Anfang alle systemaeußerungen aus datei ein
	Map<String, String> readSystemUtterances() {
		Map<String, String> utterances = new HashMap<String, String>();
		try {
			for(String line : IOUtils.readLines(this.getClass().getClassLoader().getResourceAsStream("utterances.txt"))){
				if (line.startsWith("#")) {
					continue;
				}
				String[] parts = line.split("=");
				String key = parts[0].trim();
				String utterance = parts[1].trim();
				utterances.put(key, utterance);
			}
			logger.info("Read " + utterances.keySet().size() + "utterances");
		} catch (IOException e) {
			logger.info("Could not read utterances: "+e.getMessage());
			System.err.println("Could not read utterances:"+e.getMessage());
		}
		return utterances;
	}
	

	//datenbank woraus tabuwort gezogen wird
	static String DBName = "TabuAlexa.db";
	private static Connection con = null;

	@Override
    public void onSessionStarted (SpeechletRequestEnvelope <SessionStartedRequest> requestEnvelope)
    {
		logger.info ("Alexa, ich möchte Tabu spielen.");
		tabuwortUsed = false;
		knowsApplication = false;
		utterances = readSystemUtterances();
    }

	
    //Wir starten dialog
	//hole erstes Tabuwort aus der datenbank
	//Ließ die begüßung vor und frage ob user regeln kennt oder nicht
	//wenn (if) user regeln kennt, starte spiel, (else) ansonten erkläre regeln  
	//wir wollen dann antwort erkennen vom user
	@Override
    public SpeechletResponse onLaunch(SpeechletRequestEnvelope <LaunchRequest> requestEnvelope)
    {
	   logger.info("onLaunch");

	   recState = RecognitionState.Erklaerung;
	   selectTabuwort(); //daran soll tabuwort abgerufen werden

       return askUserResponse (utterances.get("welcomeMsg") + " " + getTabuwort);
       
    }
      //Dient als Abfrage der Regeln zu Beginn
     /*  if (knowsApplication == false ) 
       { // User is new on Tabu
			resp = askUserResponse(utterances.get("regelnMsg"));
       } else { // User is familiar with Tabu
       	resp = askUserResponse(utterances.get("deinTabuwortMsg"+" "+ getTabuwort));
		}
		return res; */ 
		
    //datenbankabfrage des tabuworts
    private void selectTabuwort() {
    	logger.info("Es wird auf die Datenbank zugegriffen");
    	try {
    		con = DBConnection.getConnection();  
    		Statement stmt = con.createStatement();
    		//int randomId = (int) (Math.random() * 10); //für randomisierte Abfrage der Tabuwörter
    		ResultSet rs = stmt
    					.executeQuery("SELECT * FROM Tabu_Woerter WHERE WortID = 1" + "");
    		//SELECT Tabuwort, Synonyme FROM Tabu_Woerter JOIN Tabu_Synonyme ON WortID=WortID WHERE WortID = 1
    		//Selectabfrage, damit Tabuwort inklusive Synonyme angezeigt wird
    		getTabuwort = rs.getString("Tabuwort");
    		logger.info("Extracts random word from database " + getTabuwort);
    		con.close();
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    } 

    //hier wird gespeichert was der User sagt.
    //String wird in Userrequest gespeichert
    //je nach cognition State reagiert das system unterschiedlich
    public SpeechletResponse onIntent(SpeechletRequestEnvelope<IntentRequest> requestEnvelope)
	{
		IntentRequest request = requestEnvelope.getRequest();
		Intent intent = request.getIntent();
		userRequest = intent.getSlot("anything").getValue();
		logger.info("Received following text: [" + userRequest + "]");
		logger.info("recState is [" + recState + "]");
		logger.info("Erklaerung wird erkannt");
		SpeechletResponse resp = null;

		switch (recState) {
		case Erklaerung: 
			resp = evaluateErklaerung(userRequest); recState = RecognitionState.JaNein; break;
		case JaNein: 
			resp = evaluateJaNein(userRequest); break;
		default: 
			resp = tellUserAndFinish("Erkannter Text: " + userRequest);
		}   
		return resp;
	}


    //Alexa erhält Erklaerung des Tabuworts vom User
    // Wenn User Tabuwort nutzt, dann wird TabuwortGenanntMsg ausgegeben
    //Ansonsten wird die Erklaerung als richtig anerkannt
    private SpeechletResponse evaluateErklaerung(String userRequest) {

    	SpeechletResponse res = null;
    	logger.info("Alexa ist jetzt in evaluateErklaerung drin");
    	recognizeUserIntent(userRequest);
    	boolean containsTabuwort;
    	
    	logger.info("Alexa geht jetzt in die if schleife");
    	
    	switch (ourUserIntent) {
    	
    	case UserNenntTabuwort:
    	{
    		if (userRequest == getTabuwort) {
        		logger.info("User hat ein Tabuwort gesagt");
    			res = askUserResponse(utterances.get("TabuwortGenanntMsg"));
    			recState = RecognitionState.JaNein;
    			
    		} else if (userRequest != getTabuwort){
    			
        		logger.info("User hat kein Tabuwort beim erklären benutzt");
    			res = askUserResponse(utterances.get("rightMsg"));
    			recState = RecognitionState.JaNein;
        	}
        	
        } break;
    	default: {
    		logger.info("ALexa befindet sich im default von evaluateErklaerung");
    		res = askUserResponse(utterances.get("TabuwortGenanntMsg"));
    		recState = RecognitionState.JaNein;
    			} 
    	} return res;
    } 
    
   
//Dies ist die eigentliche Variante für den "Filter" gewesen, der die Wörter aus der ArrayList abrufen sollte
//Hier müsste dies noch ausgearbeitet werden, damit dies bei mehreren Tabuwörtern keine Probleme macht
    	/* if (userRequest.contains(<tabuwordlist>)) {
			logger.info("User hat ein Tabuwort gesagt");
			res = askUserResponse(utterances.get("tabuMsg"));
			
		} else {
			logger.info("User hat kein Tabuwort beim erklären benutzt");
			res = askUserResponse(utterances.get("rightMsg"));
			
		} 
    	
    	return res; */
    	
    
    	/*
    	containsTabuwort(ourUserIntent,);
    	switch (ourUserIntent) {
    	 
    	case UserNenntTabuwort:
    	{
    		if (containsTabuwort == true) {
    			logger.info("User hat ein Tabuwort gesagt");
    			res = askUserResponse(utterances.get("tabuMsg"));
    		}
     
    	} break;
    	
    	case UserErklaerung: {
    			if (ourUserIntent.equals(UserIntent.UserErklaerung)) {
        			logger.info("User nennt kein tabuwort beim erklaeren");
        			res = askUserResponse(utterances.get("rightMsg"));
        		}  break;
    	} default:
    	{
    		res = askUserResponse(utterances.get("errorMsg"));
    	} 
    	} return res;
    	
    } 
    	 */
   
    

    // Dein Tabuwort war richtig. Möchtest du weiterspielen?
    // Ja: Neues Tabuwort wird ausgegeben
    // Nein: GoodbyeMsg
	private SpeechletResponse evaluateJaNein(String userRequest) {

logger.info("Alexa ist in evaluateJaNein drin");
		SpeechletResponse res = null;
		logger.info("User wird gefragt, ob er weiterspielen will");
		recognizeUserIntent(userRequest);
		
		
	 switch(ourUserIntent) {
	 case Nein:{ 
			    if(jaUsed) {
			    	logger.info("case Nein in evaluateJaNein");
				res = askUserResponse(utterances.get("jaUsedMsg"));
			} else {
				jaUsed = true;
				useJa();
				res = askUserResponse(utterances.get("jaUsedMsg2") + "" + getNextTabuwort);
			} break; } 
		
		case Ja:{
			    if(jaUsed) {
			    	logger.info("case Ja in evaluateJaNein");
				jaUsed = false;
				res = askUserResponse(utterances.get("neinUsedMsg2"));
			} break;
			
		} default: {
			res = askUserResponse(utterances.get(""));
		} 
		} return res;
	}
	  
	/* 
//Ein weiterer Lösungsansatz unsererseits gewesen, um die Ja-Nein-Abfrage des weiterspielens zu optimieren
		case Ja: {
			if(knowsApplication == true) {
				logger.info("Es wird ein neues Tabuwort ausgegeben");
				res = askUserResponse (utterances.get("newroundMsg"));
				
				try {
		    		con = DBConnection.getConnection();  
		    		Statement stmt = con.createStatement();
		    		//int randomId = (int) (Math.random() * 10);
		    		ResultSet rs = stmt
		    					.executeQuery("SELECT * FROM Tabu_Woerter WHERE WortID=1" + "");
		    		getTabuwort = rs.getString("Tabuwort");
		    		logger.info("Extracts random word from database " + getTabuwort);
		    		con.close();
		    	} catch (Exception e) {
		    		e.printStackTrace();
		    	} askUserResponse (utterances.get(""));
				}
			} break;
			
		case  Nein: {
			if (knowsApplication == false) {
				logger.info("User wird verabschiedet");
				res = tellUserAndFinish(utterances.get("goodbyeMsg")); break;
			
		} 
		} default: {
			res = askUserResponse(utterances.get(""));
		
		} 
		
		} return res;
		*/
	


    //FILTER für das erkennen von Tabuwörtern
    //Useräußerung vorhanden und Liste mit Tabuwörter
    // If useräußerung contains tabuwort = true else return false
    //public, boolean, parameter (string), liste mit Tabuwörter (array)
    public boolean containsTabuwort(String utterance, List<String> tabuwords) {

    	for (String tabuword : tabuwords) {
    		if (utterance.contains(tabuword)) {
    			return true;
    		} 
    	}
    	return false;
    	//anstelle des pattern matching: matcht meine Useräußerung den Tabuwörtern?
    }
    
    
	void recognizeUserIntent(String userRequest) {
		userRequest = userRequest.toLowerCase();
		String pattern1 = "(der begriff)?(lautet )?(ampel)?usernennttabuwort (bitte)?";
		String pattern2 = "(der begriff )?(befindet sich )?verkehr( bitte)?";
		String pattern3 = "(der begriff )?(befindet sich )?(auf der)?straße( bitte)?";
		String pattern4 = "(autos)?(müssen dort )?anhalten( bitte)?";
		String pattern5 = "\\bnein\\b";
		String pattern6 = "\\bja\\b";

		Pattern p1 = Pattern.compile(pattern1);
		Matcher m1 = p1.matcher(userRequest);
		Pattern p2 = Pattern.compile(pattern2);
		Matcher m2 = p2.matcher(userRequest);
		Pattern p3 = Pattern.compile(pattern3);
		Matcher m3 = p3.matcher(userRequest);
		Pattern p4 = Pattern.compile(pattern4);
		Matcher m4 = p4.matcher(userRequest);
		Pattern p5 = Pattern.compile(pattern5);
		Matcher m5 = p5.matcher(userRequest);
		Pattern p6 = Pattern.compile(pattern6);
		Matcher m6 = p6.matcher(userRequest);
		if (m1.find()) {
			String answer = m1.group(3);
			switch (answer) {
			case "ampel": ourUserIntent = UserIntent.UserNenntTabuwort; break;
			case "straße": ourUserIntent = UserIntent.UserNenntTabuwort; break;
			case "farben": ourUserIntent = UserIntent.UserNenntTabuwort; break;
			//case "straße": ourUserIntent = UserIntent.Straße; break;
			//case "anhalten": ourUserIntent = UserIntent.Anhalten; break;
			case "ja": ourUserIntent = UserIntent.Ja; break;
			case "nein": ourUserIntent = UserIntent.Nein; break;
			}
		} else if (m2.find()) {
			ourUserIntent = UserIntent.UserNenntTabuwort;
	//	} else if (m3.find()) {
	//		ourUserIntent = UserIntent.Straße;
	//	} else if (m4.find()) {
	//		ourUserIntent = UserIntent.Anhalten;
		} else if (m5.find()) {
			ourUserIntent = UserIntent.Ja;
		} else if (m6.find()) {
			ourUserIntent = UserIntent.Nein;
		} else {
			ourUserIntent = UserIntent.Error;
		}
		logger.info("set ourUserIntent to " +ourUserIntent);
	} 
    
    private void useJa() {
    	logger.info("Wir sind jetzt im useJa, User kriegt neues Tabuwort");
    	try {
    		con = DBConnection.getConnection();  
    		Statement stmt = con.createStatement();
    		int randomId = (int) (Math.random() * 10);
    		ResultSet rs = stmt
    					.executeQuery("SELECT * FROM Tabu_Woerter WHERE WortID=2" + "");
    		getNextTabuwort = rs.getString("Tabuwort");
    		logger.info("Extracts random word from database " + getNextTabuwort);
    		con.close();
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }
   
    //Unsere Arraylist bei welcher unsere Tabuwörter abgespeichert sind, siehe zusätzlich Klasse Tabulist.
    //Unterstützt beim Filtern der Tabuwörter (containsTabuwort)
  public void tabuwordList(ArrayList<String> tabuwords) {
	
    	tabuwords = new ArrayList<String>();
    	Random random = new Random();
    	//containsTabuwort(); muss hier irgendwie eingebracht werden + Tabulist.java verknüpfen 
    	} 

	//tell the user smth or Alexa ends session after a 'tell'
    private SpeechletResponse tellUserAndFinish(String text)
	{
		// Create the plain text output.
		PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
		speech.setText(text);

		return SpeechletResponse.newTellResponse(speech);
	}
 
    private SpeechletResponse askUserResponse(String text)
	{
		SsmlOutputSpeech speech = new SsmlOutputSpeech();
		speech.setSsml("<speak>" + text + "</speak>");

		// reprompt after 8 seconds
		SsmlOutputSpeech repromptSpeech = new SsmlOutputSpeech();
		repromptSpeech.setSsml("<speak><emphasis level=\"strong\">Hey!</emphasis> Bist du noch da?</speak>");

		Reprompt rep = new Reprompt();
		rep.setOutputSpeech(repromptSpeech);

		return SpeechletResponse.newAskResponse(speech, rep);
	}


	private SpeechletResponse responseWithFlavour(String text, int i) {

		SsmlOutputSpeech speech = new SsmlOutputSpeech();
		switch(i){ 
		case 0: 
			speech.setSsml("<speak><amazon:effect name=\"whispered\">" + text + "</amazon:effect></speak>");
			break; 
		case 1: 
			speech.setSsml("<speak><emphasis level=\"strong\">" + text + "</emphasis></speak>");
			break; 
		case 2: 
			String half1=text.split(" ")[0];
			String[] rest = Arrays.copyOfRange(text.split(" "), 1, text.split(" ").length);
			speech.setSsml("<speak>"+half1+"<break time=\"3s\"/>"+ StringUtils.join(rest," ") + "</speak>");
			break; 
		case 3: 
			String firstNoun="erstes Wort buchstabiert";
			String firstN=text.split(" ")[3];
			speech.setSsml("<speak>"+firstNoun+ "<say-as interpret-as=\"spell-out\">"+firstN+"</say-as>"+"</speak>");
			break; 
		case 4: 
			speech.setSsml("<speak><audio src='soundbank://soundlibrary/transportation/amzn_sfx_airplane_takeoff_whoosh_01'/></speak>");
			break;
		default: 
			speech.setSsml("<speak><amazon:effect name=\"whispered\">" + text + "</amazon:effect></speak>");
		} 

		return SpeechletResponse.newTellResponse(speech);
	}


	@Override
	public void onSessionEnded(SpeechletRequestEnvelope<SessionEndedRequest> requestEnvelope){
		logger.info("Das Tabuspiel ist zuende. Bis zum nächsten Mal");
		try {
			con.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		logger.info("Alexa session ends now"); 
	}

	/**
	 * Tell the user something - the Alexa session ends after a 'tell'
	 */
	private SpeechletResponse response(String text)
	{
		// Create the plain text output.
		PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
		speech.setText(text);

		return SpeechletResponse.newTellResponse(speech);
	}

	/**
	 * A response to the original input - the session stays alive after an ask request was send.
	 *  have a look on https://developer.amazon.com/de/docs/custom-skills/speech-synthesis-markup-language-ssml-reference.html
	 * @param text
	 * @return
	 */
	


}
