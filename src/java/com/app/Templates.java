package com.app;

import com.server.SimpleLogger;
import com.server.TemplatesList;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;

import com.server.Constants;

public class Templates extends XmlHttpServlet {

	private static final long serialVersionUID = -8055302843390368457L;

	@Override
    public void init(ServletConfig config) throws ServletException
    {
	   super.init(config);
	   currentClassName = Templates.class.getSimpleName();

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
    	try
    	{    		
        	float expectedAPIVersion = getExpectedVersion(request);
        	if (validateVersion(request, response, expectedAPIVersion))
        	{
        		TemplatesList templates = TemplatesList.getInstance();
            	JSONArray responseMap = templates.getTemplates();
    			if (responseMap != null)
    			{            				
    				// Send a response to caller
        			jsonResponse(request, response, responseMap);
    			}
    			else
    			{
    				errorResponse(request, response, "500", "Unable to retrieve templates");
    			}
        	}
    	}
    	catch (Exception ex)
    	{
    	    SimpleLogger.getInstance().error(currentClassName, ex.getMessage());
		}
    }
}
