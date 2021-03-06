package com.app;

import com.db.DataAccess;
import com.db.DataAccessController;
import com.server.Constants;
import com.server.CreditChangeHistory;
import com.server.ProductsList;
import com.server.SimpleLogger;

import java.io.IOException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.authorize.CustomerProfileCommunication;
import net.authorize.TransactionCommunication;

import org.json.*;

public class Products extends XmlHttpServlet  {

	private static final long serialVersionUID = 4628293433991773440L;
	
	@Override
    public void init(ServletConfig config) throws ServletException
    {
	   super.init(config);
	   currentClassName = Products.class.getSimpleName();

	   try
	   {
		   ServletContext context = config.getServletContext();
		   Constants.loadJDBCConstants(context);
	   }
	   catch(Exception e)
	   {
	       SimpleLogger.getInstance().error(currentClassName, e.getMessage());
	   }
    }
	
    private HashMap<String,String> getPaymentInfo(String user_id, String profile_id)
    {
    	HashMap<String,String> results = null;
    	CustomerProfileCommunication profile = new CustomerProfileCommunication();
        
    	try
    	{
    		// getCustomerProfileAuth actually returns a Vector, but all items
        	// within that vector are Strings.
        	@SuppressWarnings("unchecked")
        	Vector<String> profile_data = profile.getCustomerProfileAuth(profile_id);
        	
        	// code represents success or failure. On failure, just return null.
            String code = profile_data.elementAt(0);
            if (code.equalsIgnoreCase("00"))
            {
            	results = new HashMap<String,String>();
            	results.put("code", code);
            	results.put("message", profile_data.elementAt(1));
            	results.put("payment_id", profile_data.elementAt(2));
            	results.put("maskno", profile_data.elementAt(3));
            }	
            else
            {
                SimpleLogger.getInstance().error(currentClassName, "Status:Payment info retrieval failed|User_id:" + user_id +
            			"|ErrorCode:" + code + "|Message:" + profile_data.elementAt(1));
            }
    	}
    	catch (Exception ex)
		{
    	    SimpleLogger.getInstance().error(currentClassName, ex.getMessage());
		}
    	
        return results;
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
    		SimpleLogger.getInstance().error(currentClassName, ex.getMessage());
        }
		
		return success;
	}
    
    // This function call is for record keeping purposes. Do not fail overall transaction if this fails
    private void insertPaymentDetails(String user_id, String product_id, String transaction_id, String gatewaymsg)
    {
		String queryString = "INSERT INTO payment_details (punchcard_id,appuser_id,token,response,product_id) values(?,?,?,?,?)";
		
		ArrayList<String> parameters = new ArrayList<String>();
		parameters.add("0");
		parameters.add(user_id);
		parameters.add(transaction_id);
		parameters.add(gatewaymsg);
		parameters.add(product_id);
		
		try
    	{
        	int new_id = DataAccess.insertDatabase(queryString, parameters);
        	if (new_id == 0)
        	{
        	    SimpleLogger.getInstance().error(currentClassName, "InsertPaymentDetails failed|User_id:" +
        				user_id +
        				"|Product_id:" +
        				product_id);
        	}
    	}
    	catch (SQLException ex)
        {
    	    SimpleLogger.getInstance().error(currentClassName, ex.getMessage());
        }
    }
    
    private boolean updateUserCredits(String user_id, String product_id, float increaseAmount)
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
				
				float credit = Float.parseFloat(userInfo.get("credit"));
				float newAmount = credit + increaseAmount;
				
				// update number of credits remaining for current user
				success = updateCreditsForUser(conn, user_id, Float.toString(newAmount));
				if (success)
				{
					// Insert tracking row into change history table
					// If this fails, don't return an error
			        CreditChangeHistory changeHistory = CreditChangeHistory.getInstance();
					changeHistory.insertCreditChange(user_id, increaseAmount, CreditChangeHistory.PURCHASE, product_id);
				}
				else
				{
					// credit update failed
				    SimpleLogger.getInstance().error(currentClassName, "CreditUpdateFailed|User_id:" + user_id);
				}
        	}
			else
			{
				// Could not find user
			    SimpleLogger.getInstance().error(currentClassName, "NoSuchUser|User_id:" + user_id);
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
    
    private boolean purchaseProduct(String user_id, String product_id, String profile_id, String payment_id, float cost, float credit)
    {
    	TransactionCommunication tc = new TransactionCommunication();
    	boolean success = false;
    	
    	try
    	{
        	// getCustomerProfileAuth actually returns a Vector, but all items
        	// within that vector are Strings.
        	@SuppressWarnings("unchecked")
            Vector<String> paymentdata = tc.makePaymentAuthCapture(profile_id, payment_id, Float.toString(cost));
            String code = paymentdata.elementAt(0);
            String servermsg = paymentdata.elementAt(1);
            String gatewaymsg = paymentdata.elementAt(2);
            String invoiceno = paymentdata.elementAt(3);
            String authcode = paymentdata.elementAt(4);
            String transaction_id = paymentdata.elementAt(5);
            Constants.logger.info("Payment details:" +
            	" userid: " + user_id +
            	", code: " + code +
            	", servermsg: " + servermsg +
            	", gatewaymsg: " + gatewaymsg +
            	", invoiceno: " + invoiceno +
            	", authcode: " + authcode +
            	", transactionid: " + transaction_id);
            
            // Insert row into payment_details table for tracking purposes
            insertPaymentDetails(user_id, product_id, transaction_id, gatewaymsg);

            // Update credits for user
            if (code.equalsIgnoreCase("00")) 
            {
            	success = updateUserCredits(user_id, product_id, credit);	
            }
            else
            {
                SimpleLogger.getInstance().error(currentClassName, "UnableToPurchaseCredits|User_id:" + user_id);
            }
    	}
    	catch (Exception ex)
		{
    	    SimpleLogger.getInstance().error(currentClassName, ex.getMessage());
		}
    	return success;
    }
	
	/**
     * Handles the HTTP <code>GET</code> method.
     * 
     * @param request
     *            servlet request
     * @param response
     *            servlet response
     * @throws ServletException
     *             if a servlet-specific error occurs
     * @throws IOException
     *             if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException 
    {    	
    	float expectedAPIVersion = getExpectedVersion(request);
    	if (validateVersion(request, response, expectedAPIVersion))
    	{
	    	ProductsList products = ProductsList.getInstance();
	    	String currentProducts = products.getMapOfProducts();
	    	stringResponse(response, currentProducts);
    	}
    }
    
	/**
     * Handles the HTTP <code>PUT</code> method.
     * 
     * @param request
     *            servlet request
     * @param response
     *            servlet response
     * @throws ServletException
     *             if a servlet-specific error occurs
     * @throws IOException
     *             if an I/O error occurs
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
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
            			String product_id = requestInputs.getString(Constants.PRODUCTID_PARAMNAME);
                    	ArrayList<HashMap<String,String>> productResultsArray = getProductInfo(product_id);
                    	if (productResultsArray.size() == 1)
                    	{
                    		HashMap<String,String> productInfo = productResultsArray.get(0);
                    		// Check if punchcard is still valid
                    		if (!Boolean.getBoolean(productInfo.get("disabled")))
                    		{
                    			boolean success = false;
                    			String profile_id = userInfo.get("profile_id");
                    			HashMap<String,String> paymentInfo = getPaymentInfo(user_id, profile_id);
                    			if (paymentInfo != null)
                    			{
                    				success = purchaseProduct(
                    								user_id, 
                    								product_id, 
                    								profile_id, 
                    								paymentInfo.get("payment_id"), 
                    								Float.parseFloat(productInfo.get("cost")),
                    								Float.parseFloat(productInfo.get("credits")));
                    			}
                    			
                    			if (success)
                    			{
                    				try
                    				{
                    					// Provide successful response to caller
                        				JSONObject responseMap = new JSONObject();
                	            		responseMap.put("statusCode", "00");
                	            		responseMap.put("statusMessage", "Credit product purchased successfully");
                	            		
                	            		// Send a response to caller
                	        			jsonResponse(request, response, responseMap);
                    				}
                    				catch (JSONException ex)
                    				{
                    				    SimpleLogger.getInstance().error(currentClassName, ex.getMessage());
                    				}
                    			}
                    			else
                        		{
                        			// Could not retrieve payment information
                    			    SimpleLogger.getInstance().error(currentClassName, "UnableToPurchaseCredits|Product_id:" + product_id);
                            		errorResponse(request, response, "400", "Unable to purchase credits");
                        		}
                    		}
                    		else
                    		{
                    			// Product is disabled
                    		    SimpleLogger.getInstance().error(currentClassName, "DisabledProduct|Product_id:" + product_id);
                        		errorResponse(request, response, "403", "This product is no longer available for purchase");
                    		}
                    	}
                    	else
                    	{
                    		// Could not find product
                    	    SimpleLogger.getInstance().error(currentClassName, "ProductMissing|Product_id:" + product_id);
                    		errorResponse(request, response, "404", "Unknown credit product");
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
        		errorResponse(request, response, "500", "An unknown error occurred");
        	}
    	}
    }
}
