package com.app;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.*;

import com.db.DataAccess;
import com.db.DataAccessController;
import com.server.Constants;
import com.server.CreditChangeHistory;
import com.server.SimpleLogger;
import com.server.Utility;

public class Punches extends XmlHttpServlet 
{
	private static final long serialVersionUID = -5700319928859466433L;

	@Override
    public void init(ServletConfig config) throws ServletException
    {
	   super.init(config);
	   currentClassName = Punches.class.getSimpleName();

	   try
	   {
		   ServletContext context = config.getServletContext();
		   Constants.loadJDBCConstants(context);
	   }
	   catch(Exception e)
	   {
	       SimpleLogger.getInstance().error(currentClassName, e);
	   }
    }
	
	private boolean punchcardNotExpired(String expireDateString)
	{
		boolean valid = false;
        try
        {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            Date expireDate = dateFormat.parse(expireDateString);
            java.util.Date currentDate = new java.util.Date();

            // punch expired?
            valid = Utility.isAfterDateTime(expireDate, currentDate);
        }
        catch (ParseException ex)
        {
            SimpleLogger.getInstance().error(currentClassName, ex);
        }
        return valid;
	}
	
	private boolean insertPunchcardForUser(Connection conn, String user_id, String punch_id, String punch_num, String expirydays)
	{
		boolean success = false;
		String queryString = "INSERT INTO punchcard_download (app_user_id,punch_card_id,punch_used,download_date,download_time,payment_id,isfreepunch,exipre_date) values(?,?,?,?,?,?,?,?)";
		
		ArrayList<String> parameters = new ArrayList<String>();
		parameters.add(user_id);
		parameters.add(punch_id);
		parameters.add(punch_num);
		
		SimpleDateFormat timeFormat = new SimpleDateFormat("HHmm");
        String strTime = timeFormat.format(new Date());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String strDate = dateFormat.format(new java.util.Date().getTime());	
        parameters.add(strDate);
        parameters.add(strTime);
        
        // payment id is no longer used here
        parameters.add("0");
        
        // isfreepunch is no longer used here
        parameters.add("false");
        
        // Compute expiration date for punch
        try
        {
        	SimpleDateFormat expire_date_format = new SimpleDateFormat("MM-dd-yyyy");
            strDate = expire_date_format.format(new Date());
            java.util.Date current_date = expire_date_format.parse(strDate);
            int validdays = Integer.parseInt(expirydays);
            Date expire_time_in_long = Utility.addDays(current_date, validdays);
            String punch_expire_date = expire_date_format.format(expire_time_in_long);
            parameters.add(punch_expire_date);
            success = true;
        }
        catch (ParseException ex)
        {
            SimpleLogger.getInstance().error(currentClassName, ex);
        }
        
        if (success)
        {
        	try
        	{
            	int new_id = DataAccess.insertDatabaseWithExistingConnection(conn, queryString, parameters);
            	success = (new_id != 0);	
        	}
        	catch (SQLException ex)
            {
        		success = false;
        		SimpleLogger.getInstance().error(currentClassName, ex);
            }
        }
		
		return success;
	}
	
	private boolean updateCreditsForUser(Connection conn, String user_id, String new_credit_amount)
	{
		boolean success = false;
		String queryString = "UPDATE app_user SET credit = ? WHERE user_id = ?";
		ArrayList<String> parameters = new ArrayList<String>();
		parameters.add(new_credit_amount);
		parameters.add(user_id);
		try
		{
			success = DataAccess.updateDatabaseWithExistingConnection(conn, queryString, parameters);	
		}
		catch (SQLException ex)
        {
    		success = false;
    		SimpleLogger.getInstance().error(currentClassName, ex);
        }
		
		return success;
	}
	
	private boolean buyPunch(HttpServletRequest request, HttpServletResponse response, String user_id, String punch_id, String costString, String punch_num, String expirydays)
	{
		boolean success = false;
		Connection conn = DataAccessController.createConnection();
		try
		{
			conn.setAutoCommit(false);
			
			ArrayList<HashMap<String,String>> userResultsArray = getUserInfo(user_id, conn);
			if (userResultsArray.size() == 1)
        	{
				HashMap<String,String> userInfo = userResultsArray.get(0);
				
				// Check that user has enough credits to purchase punchcard
				float credit = Float.parseFloat(userInfo.get("credit"));
				float cost = Float.parseFloat(costString);
				float remainder = credit - cost;
				if (remainder >= 0)
				{   
					// Create new punchcard entry for current user
					success = insertPunchcardForUser(conn, user_id, punch_id, punch_num, expirydays);
					if (success)
					{
						// update number of credits remaining for current user
						boolean creditUpdateSuccess = updateCreditsForUser(conn, user_id, Float.toString(remainder));
						if (creditUpdateSuccess)
						{
							// Insert tracking row into change history table
					        CreditChangeHistory changeHistory = CreditChangeHistory.getInstance();
							changeHistory.insertCreditChange(user_id, (float)(0.0 - cost), CreditChangeHistory.PUNCHCARD, punch_id);
						}
						else
						{
							// credits update failed
							// NOTE: Don't return an error
						    SimpleLogger.getInstance().error(currentClassName, "CreditUpdateFailed|User_id:" + user_id);
						}
					}
					else
					{
						// Could not insert punch_card_download
					    SimpleLogger.getInstance().error(currentClassName, "PunchcardPurchaseFailed|Punch_id: " + punch_id + "|User_id:" + user_id);
		        		errorResponse(request, response, "500", "An error happened. Could not purchase punchcard.");
					}
				}
				else
				{
					// Not enough credits
				    SimpleLogger.getInstance().error(currentClassName, "InsufficientCredits|Credits:" + Float.toString(credit) + 
	        				"|User_id:" + user_id + "|Punchcard:" + punch_id);
	        		errorResponse(request, response, "403", "Not enough credits");
				}
        	}
			else
			{
				// Could not find user
			    SimpleLogger.getInstance().unknownUser(currentClassName, user_id);
        		errorResponse(request, response, "404", "Unknown user");
			}
			
			if (success)
			{
				conn.commit();
			}
			else
			{
				conn.rollback();
			}	
		}
		catch (SQLException ex)
		{
		    SimpleLogger.getInstance().error(currentClassName, ex.getMessage());
		}
		finally
		{
			try
			{
				conn.close();
			}
			catch (SQLException ex)
			{
			    SimpleLogger.getInstance().error(currentClassName, ex.getMessage());
			}
		}
		return success;
	}
	
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException 
    {
    	float expectedAPIVersion = getExpectedVersion(request);
    	if (validateVersion(request, response, expectedAPIVersion))
    	{
        	JSONObject requestInputs = getRequestData(request);	
        	
        	try
        	{
        		String user_id = requestInputs.getString(Constants.USERID_PARAMNAME);
            	ArrayList<HashMap<String,String>> userResultsArray = getUserInfo(user_id, null);
            	if (userResultsArray.size() == 1)
            	{
            		HashMap<String,String> userInfo = userResultsArray.get(0);
            		
            		// Validate session
            		String validSessionId = userInfo.get(Constants.SESSIONID_PARAMNAME);	
    				if (validateSessionId(validSessionId, request))
            		{
            			String punchcardid = requestInputs.getString(Constants.PUNCHCARDID_PARAMNAME);
                    	ArrayList<HashMap<String,String>> punchcardResultsArray = getPunchCardInfo(punchcardid);
                    	if (punchcardResultsArray.size() == 1)
                    	{
                    		HashMap<String,String> punchcardInfo = punchcardResultsArray.get(0);
                    		// Check if punchcard is still valid
                    		if (punchcardNotExpired(punchcardInfo.get("expiry_date")))
                    		{
                    			String business_id = punchcardInfo.get(Constants.BUSINESSID_PARAMNAME);
                    			ArrayList<HashMap<String,String>> businessResultsArray = getBusinessInfo(business_id);
                    			if (businessResultsArray.size() == 1)
                    			{
                    				HashMap<String,String> businessInfo = businessResultsArray.get(0);
                    				String busi_enabled = businessInfo.get("busi_enabled");
                    	            if (busi_enabled.equalsIgnoreCase("Y")) 
                    	            {
                    	            	String cost = punchcardInfo.get("selling_price_of_punch_card");
                    	            	String expirydays = punchcardInfo.get("expirydays");
                    	            	String punch_num = punchcardInfo.get("no_of_punches_per_card");
                    	            	if (buyPunch(request, response, user_id, punchcardid, cost, punch_num, expirydays))
                    	            	{
                    	            		// Provide successful response to caller
                    	            		JSONObject responseMap = new JSONObject();
                    	            		responseMap.put("statusCode", "00");
                    	            		responseMap.put("statusMessage", "Punch card purchased successfully");
                    	            		
                    	            		// Send a response to caller
                    	        			jsonResponse(request, response, responseMap);
                    	            	}
                    	            }
                    	            else
                    	            {
                        				// Business is not enabled
                    	                SimpleLogger.getInstance().error(currentClassName, "ExpiredBusiness|Business_id:" + business_id);
                                		errorResponse(request, response, "404", "Unknown business.");
                    	            }
                    			}
                    			else
                    			{
                    				// Could not find business
                    			    SimpleLogger.getInstance().error(currentClassName, "UnknownBusiness|Busness_id:" + business_id);
                            		errorResponse(request, response, "404", "Unknown business.");
                    			}
                    		}
                    		else
                    		{
                    			// Punchcard is expired
                    		    SimpleLogger.getInstance().error(currentClassName, "ExpiredPunchcard|Punchcard_id:" + punchcardid);
                        		errorResponse(request, response, "403", "Punchcard is expired.");
                    		}
                    	}
                    	else
                    	{
                			// Could not find punchcard
                    	    SimpleLogger.getInstance().error(currentClassName, "UnknownPunchcard|Punchcard_id:" + punchcardid);
                    		errorResponse(request, response, "404", "Unknown punchcard");
                    	}
            		}
            		else
                	{
                		// Session mismatch
            		    SimpleLogger.getInstance().sessionMismatch(currentClassName, user_id);
                		errorResponse(request, response, "400", "You have logged in from another device");
                	}
            	}
            	else
            	{
        			// Could not find user
            	    SimpleLogger.getInstance().unknownUser(currentClassName, user_id);
            		errorResponse(request, response, "404", "Unknown user");
            	}
	    	}
	    	catch (JSONException ex)
	    	{
	    	    SimpleLogger.getInstance().error(currentClassName, ex.toString());
	    		errorResponse(request, response, "500", "Unable to retrieve products");
	    	}
    	}
    }
}
