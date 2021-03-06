package com.app;

import com.db.DataAccess;
import com.db.DataAccessController;
import com.jspservlets.SignupAddPunch;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.server.Constants;
import com.server.FeedBean;
import com.server.SAXParserExample;
import com.server.AccessRequest;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Vector;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletInputStream;
import org.xml.sax.InputSource;

/**
 * @author qube26
 */
public class FacebookLogin extends HttpServlet {

    private static final long serialVersionUID = 7750955681431092195L;
    ServletConfig config = null;
    private Vector userdata, userinfo;
    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
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
    ServletContext context;

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        try {
            response.setContentType("text/html;charset=UTF-8");
            List list;
            try {
                config = getServletConfig();
                context = config.getServletContext();
                Constants.loadJDBCConstants(context);
            } catch (Exception e) {
                Constants.logger.error(e);
            }
            try {
                ServletInputStream in = request.getInputStream();

                SAXParserExample example = new SAXParserExample();

                int info;
                StringBuffer sb = new StringBuffer();
                while ((info = in.read()) != -1) {
                    char temp;
                    temp = (char) info;
                    sb.append(temp);
                }
                Constants.logger.info("-------------paid_punch---------------");
                Constants.logger.info("XML File" + sb);
                String xmldata = new String(sb);
                xmldata = xmldata.trim();
                InputSource iSource = new InputSource(new StringReader(xmldata));

                example.parseDocument(iSource);
                list = example.getData();
                AccessRequest arz = (AccessRequest) list.get(0);
                String reqtype = arz.getTxType();

                if (reqtype.equalsIgnoreCase("FB-LOGIN-REQ")) {
                    String name, email, fbid, sessionId;
                    DataAccess da = new DataAccess();
                    name = arz.getName();
                    email = arz.getEmail();
                    fbid = arz.getFbid();
                    sessionId = arz.getSessionId();
                    boolean res = da.check_fbid(fbid);
                    // res true means already reg OR false means first time login
                    Vector appUser;
                    String appId;
                    String isProfiled = "false";
                    if (res) {
                        da.fb_login(fbid, email, name, sessionId);
                        Vector userinfo = DataAccessController.getDataFromTable("app_user", "FBid", fbid);
                        appUser = (Vector) userinfo.get(0);
                        appId = "" + appUser.get(0);
                        isProfiled = "" + appUser.get(13);

                    } else {
                        da.fb_Registration(fbid, email, name, sessionId);
                        Vector userinfo = DataAccessController.getDataFromTable("app_user", "FBid", fbid);
                        String temp = name;
                        String namearray[] = temp.split(" ");
                        String lastName = "";
                        if (namearray.length > 1)
                        {
                            lastName = namearray[namearray.length - 1];
                            char firstChar = lastName.charAt(0);
                            // lastName=""+firstChar;
                            // namearray[namearray.length-1]=lastName;
                        }
                        temp = "";
                        for (int index = 0; index < namearray.length - 1; index++)
                        {
                            temp = temp + namearray[index];
                            temp = temp + " ";
                        }
                        temp = temp.trim();
                        // email function call
                        appUser = (Vector) userinfo.get(0);
                        appId = "" + appUser.get(0);
                        SignupAddPunch emailsender = new SignupAddPunch();
                        emailsender.sendConfirmationEmail(email, temp);
                    }
                    xmlLogin(response, "Login Successful", appId, sessionId, isProfiled);
                }
            } catch (Exception ex) {
                Constants.logger.error(ex);
            }
        } catch (Exception e) {
            Constants.logger.error(e);
        }
    }

    private void xmlLogin(HttpServletResponse p_response, String message, String user_id, String session,
            String iscreated) {

        String statusCode = "00";
        String statusMessage = message;

        try {
            PrintWriter out = p_response.getWriter();
            Constants.logger.info("Respones userid   " + user_id);
            Constants.logger.info("statuscode" + statusCode);
            Constants.logger.info("statusmessage" + statusMessage);

            p_response.setHeader("Content-Disposition", "attachement; filename= response.xml");
            out.print("<?xml version='1.0' ?>"
                    + "<paidpunch-resp>"
                    + "<statusCode>" + statusCode + "</statusCode>");
            String respons = "<userid>" + user_id + "</userid>"
                    + "<sessionid>" + session + "</sessionid>"
                    + "<is_profileid_created>" + iscreated + "</is_profileid_created>";

            out.print(respons);
            Constants.logger.info("respons" + respons);
            out.print("<statusMessage>" + statusMessage + "</statusMessage>");
            out.print("</paidpunch-resp>");
        } catch (Exception e) {
            Constants.logger.error(e);
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
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
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
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     * 
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }

    private void feeds(List list, HttpServletResponse response) {
        ArrayList<FeedBean> feedlist = new ArrayList<FeedBean>();
        DataAccess da = new DataAccess();
        feedlist = da.getDownloadFeed();
        ArrayList<String> fbid = new ArrayList<String>();
        fbid.add("100002527741735");
        fbid.add("538761849");
        fbid.add("520588660");
        fbid.add("500431977");
        fbid.add("100003516975847");
        fbid.add("100002952444313");
        fbid.add("100000923580850");
        fbid.add("9890");
        fbid.add("1234");
        fbid.add("100002527741735");
        ArrayList<FeedBean> frendlist = new ArrayList<FeedBean>();
        for (int i = 0; i < feedlist.size(); i++) {
            String temp = feedlist.get(i).getFbid();
            for (int j = 0; j < fbid.size(); j++) {
                if (temp.equalsIgnoreCase(fbid.get(j))) {
                    feedlist.get(i).setIsMyFriend(true);
                    frendlist.add(feedlist.get(i));
                    feedlist.remove(i);
                }
            }
        }
    }
}