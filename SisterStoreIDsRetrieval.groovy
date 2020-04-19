package com.genRocket

import com.genRocket.GenRocketException
import com.genRocket.engine.EngineAPI
import com.genRocket.engine.EngineManual
import groovyx.net.http.HTTPBuilder
import java.sql.*;
import java.io.*;
import java.util.Properties;
import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.*
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

class SisterStoreIDsRetrieval {
	private static final String STORE_SCENARIO = "StoreIDsUpdToZeroQoHcheckScenario.grs"
	//private static final String INSTANCE_URL = "http://dv-sebring-xl33.autozone.com:22443/commercial/stores/v1/proximity-request"

	static JSONObject makeRequestAndRunScenario(String inputMilesRange, String storeAddress, File storeScenarioFile, String environment1, String environment2, String itemID) {
		//println (inputMilesRange+" "+storeScenarioFile+" "+ storeAddress)
		JSONObject secondScenario = new JSONObject();
		Map storeNumbers = makeRequestAndReceiveResponse([custAddress: storeAddress, maxMilesRange: inputMilesRange])
		//println ("Store number in Map"+storeNumbers)
		List storeNumberList = storeNumbers.stores*.storeId
		//println ("Store Number in List : "+storeNumberList)
		EngineAPI api = new EngineManual()
		api.scenarioLoad(storeScenarioFile.absolutePath)
		if (storeNumberList) {
			api.generatorParameterSet("StoreIDsUpdToZeroQoHcheck.environmentTRICKLE", 0, "value", environment1)
			api.generatorParameterSet("StoreIDsUpdToZeroQoHcheck.environmentINVENTORY", 0, "value", environment2)
			api.generatorParameterSet("StoreIDsUpdToZeroQoHcheck.itemID", 0, "value", itemID)
			api.generatorParameterSet("StoreIDsUpdToZeroQoHcheck.storeID", 0, "list", storeNumberList)
			
			//api.domainSetLoopCount("StoreIDsUpdToZero",storeNumberList.size())
			List<Object> apiResp = api.scenarioRunInMemory("StoreIDsUpdToZeroQoHcheck");
			secondScenario.put("success","true");
			secondScenario.put("data",apiResp);
			for(Object each:apiResp){
			 println "each >>> "+each.toString();
			}  
		} else {
			secondScenario.put("success","false");
			secondScenario.put("message","No Stores to Update in DB");
		}
		return secondScenario;
	}
	
	private static Map makeRequestAndReceiveResponse(Map parameters) {
		HTTPBuilder httpBuilder = new HTTPBuilder()
		httpBuilder.ignoreSSLIssues()
		def jsonResponse = null
		//println ("Param values to the API:"+parameters)
		try {
			InputStream input = new FileInputStream("D://GenRocket//Output//FILE//ProximityServiceURL.properties")
			Properties prop = new Properties();
			prop.load(input);
			System.out.println("URL: "+prop.getProperty("url"));
			//httpBuilder.request(INSTANCE_URL, POST, JSON) { req ->
			httpBuilder.request(prop.getProperty("url"), POST, JSON) { req ->
			body = parameters
			response.success = { resp, reader ->
			jsonResponse = reader
        }
        response.failure = { resp ->
          def outputStream = new ByteArrayOutputStream()
          resp.entity.writeTo(outputStream)
          def errorMsg = outputStream.toString('utf8')

          println "Unexpected error: ${errorMsg}"
          println "Unexpected error: ${resp.statusLine.statusCode} : ${resp.statusLine.reasonPhrase}"
        }
      }
    } catch (Exception e) {
      println "Unexpected error occurred : ${e.getMessage()}"
    }
    return jsonResponse
  }

	private static JSONObject returnFullAddress(String[] args){
		String fullAddress = "";
		JSONObject returnAddress = new JSONObject();
		try {
			JSONParser parser = new JSONParser();
			Object obj = parser.parse(new FileReader("D://GenRocket//Output//FILE//DBConfigFile.json"));
			JSONObject jsonObject = (JSONObject) obj;
			String environment = args?.size() > 0 ? args[0] : null
			//System.out.println("Env: " + environment);
			JSONObject jsonObject1 = (JSONObject) jsonObject.get(environment);	
			String driver = (String) jsonObject1.get("driver");
			String url = (String) jsonObject1.get("url");
			String username = (String) jsonObject1.get("username");
			String password = (String) jsonObject1.get("password");
		
			//System.out.println("driver: " + driver);
			//System.out.println("url: " + url);
			//System.out.println("username: " + username);
			//System.out.println("password: " + password);
        	//DB connection check	
			//Class.forName(driver)
			//Connection con = DriverManager.getConnection(url,username,password);
			String dbCheck = "";		
			Connection con = null;
			try{
				Class.forName(driver)
				con = DriverManager.getConnection(url,username,password);
				dbCheck = con.getMetaData();
			}catch (Exception e) {
				dbCheck = null;
			}
			if (dbCheck != null){
				println("DB Connected")
			} else {				
				returnAddress.put("success","false");
				returnAddress.put("message","DB connection failed. Customer Address could not be retrieved for sending to Proximity service.");
				return returnAddress;	
			}
			
			Statement st = con.createStatement();
			Statement st2 = con.createStatement();
			String custID = args?.size() > 0 ? args[1] : null
			//System.out.println("Customer_PIN: " + custID)
			
			ResultSet rs2 = st2.executeQuery("Select cad_customer_id from com_cus_address where cad_customer_id = "+custID+" AND cad_address_typ_cd ='01' AND cad_add_end_date is null");
			int custCheck = 0;
			while(rs2.next()) {
			custCheck = rs2.getInt(1);
			}
			if (custCheck == 0){
				JSONObject objNew3 = new JSONObject();
				returnAddress.put("success","false");
				returnAddress.put("message","Invalid CustomerPIN");
				return returnAddress;
			}
		
			ResultSet rs1 = st.executeQuery("Select ccu_pri_store_id from com_customer where ccu_customer_id = "+custID);
			int storeID = 0;
			while(rs1.next()) {
				storeID = rs1.getInt(1);
				//System.out.println("Store_ID: " + storeID)
			}
			if (storeID == 0){
				returnAddress.put("success","false");
				returnAddress.put("message","Primary Store not mapped to customer. Hence address could not be retrieved");
				return returnAddress;
			}
			ResultSet rs = st.executeQuery("Select Cust.ccu_pri_store_id,Addr.cad_add_line_1,Addr.cad_city_name,Addr.cad_ste_prv_cd,Addr.cad_postal_code,Addr.cad_country_code from com_cus_address Addr,com_customer Cust where Cust.ccu_customer_id = Addr.cad_customer_id and Cust.ccu_pri_store_id = "+storeID+" and Cust.ccu_customer_id = "+custID+" and Addr.cad_address_typ_cd='01' and Addr.cad_add_end_date is null");			
			while( rs.next()) {
				int str1 = rs.getInt(1);
				//System.out.println("Store ID:" + str1)
				String str2 = rs.getString(2);
				//System.out.println("Address_line_1:" + str2)
				String str3 = rs.getString(3);
				//System.out.println("City:" + str3)
				String str4 = rs.getString(4);
				//System.out.println("State:" + str4)
				String str5 = rs.getString(5);
				//System.out.println("PostalCode:" + str5)
				String str6 = rs.getString(6);
				//System.out.println("Country:" + str6)
				fullAddress = str2.trim()+" "+str3.trim()+" "+str4.trim()+" "+str5.trim()+" "+str6.trim();
				
			}
		returnAddress.put("success","true");
		returnAddress.put("message",fullAddress);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return returnAddress;
	}
	
	//public static void main(String[] args) {
	public static JSONObject main(String[] args) {
		JSONObject mainReturn  =  new JSONObject();
		String milesRange = 10
		JSONObject storeAddress = returnFullAddress(args)
		
		if(storeAddress.get("success").equals("false")){
			mainReturn =  storeAddress;
		}else{
		println storeAddress.toJSONString();
		println storeAddress.get("message");
			String storeScenarioPath = "D:\\GenRocket\\Scenarios"
			String environment1 = args?.size() > 0 ? args[2] : null
			String environment2 = args?.size() > 0 ? args[3] : null
			String itemID = args?.size() > 0 ? args[4] : null	
			if (!storeScenarioPath) {
				throw new GenRocketException("Please specify Store scenario path in command as first parameter")
			}
			File storeScenarioFile = new File(storeScenarioPath + File.separator + STORE_SCENARIO)
			//println("scenario file path:"+ storeScenarioFile)
			if (!storeScenarioFile.exists()) {
			throw new GenRocketException("Store Scenario to Update is not found!")
			}
			mainReturn = makeRequestAndRunScenario(milesRange, storeAddress.get("message"), storeScenarioFile, environment1, environment2, itemID)
			println "main"+mainReturn.toJSONString();
			}

		return mainReturn;
		
	}
	
}
