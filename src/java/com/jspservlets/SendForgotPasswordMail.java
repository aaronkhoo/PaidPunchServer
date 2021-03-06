package com.jspservlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.Security;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.mysql.jdbc.Statement;

/**
 * @author admin
 */
public class SendForgotPasswordMail extends HttpServlet {

    private static final long serialVersionUID = 3924747300296488756L;
    ServletConfig config = null;
    ServletContext context;

    final String SMTP_HOST_NAME = "smtp.gmail.com";
    // final String SMTP_AUTH_USER = "mobimedia.mm@gmail.com";
    // final String SMTP_AUTH_PWD = "mobimedia";

    // final String SMTP_HOST_NAME = "mail.paidpunch.com";
    final String SMTP_AUTH_USER = "noreply@paidpunch.com";
    // final String SMTP_AUTH_PWD = "nor3ply";
    final String SMTP_AUTH_PWD = "P@idpunch";

    String emailMsgTxt = "";
    final String emailSubjectTxt = "PaidPunch Credentials";
    final String emailFromAddress = "noreply@paidpunch.com";
    // private static final String SMTP_PORT = "25";
    final String SMTP_PORT = "465";
    // final String SMTP_PORT = "25";
    final String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";
    String recipient_email_id = "";

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
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, AddressException, MessagingException {
        System.out.println("Processing request");
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        try {

            try {
                recipient_email_id = request.getParameter("email");
            } catch (Exception e) {

            }

            Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());

            boolean debug = false;

            String password = getPassword(recipient_email_id);

            // Set the host smtp address
            Properties props = new Properties();
            props.put("mail.smtp.host", SMTP_HOST_NAME);
            props.put("mail.smtp.auth", "true");
            props.put("mail.debug", "true");
            props.put("mail.smtp.port", SMTP_PORT);
            props.put("mail.smtp.socketFactory.port", SMTP_PORT);
            props.put("mail.smtp.socketFactory.class", SSL_FACTORY);
            props.put("mail.smtp.socketFactory.fallback", "false");

            // url = new URLName(protocol,SMTP_HOST_NAME,-1, mbox,SMTP_AUTH_USER,SMTP_AUTH_PWD);
            // Authenticator auth = getPasswordAuthentication(SMTP_AUTH_USER,SMTP_AUTH_PWD);
            Authenticator auth = new SMTPAuthenticator();
            Session session1 = Session.getDefaultInstance(props, auth);
            // Session session = Session.getDefaultInstance(props, new javax.mail.Authenticator() {});
            session1.setDebug(debug);

            // create a message
            Message msg = new MimeMessage(session1);

            // set the from and to address
            InternetAddress addressFrom = new InternetAddress(emailFromAddress);
            msg.setFrom(addressFrom);

            InternetAddress addressTo = new InternetAddress(recipient_email_id);

            msg.setRecipient(Message.RecipientType.TO, addressTo);

            // Setting the Subject and Content Type
            msg.setSubject(emailSubjectTxt);
            emailMsgTxt = "The login credentials for your PaidPunch Account are as follows";
            emailMsgTxt = emailMsgTxt + "\n" + "Username : " + recipient_email_id + "\n" + "Password : " + password;
            msg.setContent(emailMsgTxt, "text/plain");
            Transport.send(msg);

            // messageSending(emailSubjectTxt, recipient_email_id , emailMsgTxt);

            response.sendRedirect("login.jsp");

            /*
             * TODO output your page here out.println("<html>"); out.println("<head>");
             * out.println("<title>Servlet forgot_pass_mail_send</title>"); out.println("</head>");
             * out.println("<body>"); out.println("<h1>Servlet forgot_pass_mail_send at " + request.getContextPath () +
             * "</h1>"); out.println("</body>"); out.println("</html>");
             */
        } finally {
            out.close();

        }
    }

    /*
     * private void messageSending(String mail_subject, String mail_to, String mail_body) throws MessagingException {
     * Message message = new MimeMessage(getSession());
     * 
     * message.addRecipient(RecipientType.TO, new InternetAddress(mail_to)); message.addFrom(new InternetAddress[] { new
     * InternetAddress(SMTP_AUTH_USER) });
     * 
     * message.setSubject(mail_subject); message.setContent(mail_body, "text/plain"); try{ Transport.send(message);
     * }catch(Exception e){ e.printStackTrace(); } System.out.println("Hi"); }
     */
    /*
     * public Session getSession() { SMTPAuthenticator authenticator = new SMTPAuthenticator();
     * 
     * Properties properties = new Properties(); properties.setProperty("mail.smtp.submitter",
     * authenticator.getPasswordAuthentication().getUserName()); properties.setProperty("mail.smtp.auth", "true");
     * 
     * properties.setProperty("mail.smtp.host", SMTP_HOST_NAME); properties.setProperty("mail.smtp.port", SMTP_PORT);
     * 
     * return Session.getInstance(properties, authenticator); }
     */

    public class SMTPAuthenticator extends javax.mail.Authenticator {
        private PasswordAuthentication authentication;

        public SMTPAuthenticator() {
            String username = SMTP_AUTH_USER;
            String password = SMTP_AUTH_PWD;
            authentication = new PasswordAuthentication(username, password);
        }

        public PasswordAuthentication getPasswordAuthentication() {
            return authentication;
        }
    }

    // <editor-fold defaultstate="collapsed"
    // desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
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
        try {
            // processRequest(request, response);

            try {
                config = getServletConfig();

                context = config.getServletContext();
            } catch (Exception e) {

            }
            com.server.Constants.loadJDBCConstants(context);
            String emailid = null;
            PrintWriter out = response.getWriter();
            if (request.getParameter("email") != null) {
                emailid = request.getParameter("email");

                boolean emailflag = getEmailId(emailid);
                if (emailflag) {
                    emailid = "00";
                }
                else {
                    emailid = "01";
                }
            }
            else {

            }
            out.println(emailid);

        } catch (Exception ex) {
            // Logger.getLogger(forgot_pass_mail_send.class.getName()).log(Level.SEVERE, null, ex);
        }
        // } catch (MessagingException ex) {
        // Logger.getLogger(forgot_pass_mail_send.class.getName()).log(Level.SEVERE, null, ex);
        // }
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
        try {
            System.out.println("request has come for post of form");
            processRequest(request, response);

        } catch (AddressException ex) {
            Logger.getLogger(SendForgotPasswordMail.class.getName()).log(Level.SEVERE, null, ex);
        } catch (MessagingException ex) {
            Logger.getLogger(SendForgotPasswordMail.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Returns a short description of the servlet.
     * 
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

    public boolean getEmailId(String emailid) throws ServletException {
        DBConnection db = null;
        Statement stmt = null;
        ResultSet rs = null;
        boolean codeFlag = false;

        try {
            db = new DBConnection();
            stmt = db.stmt;
            String query = "SELECT email_id from business_users where email_id='" + emailid + "'";
            rs = stmt.executeQuery(query);
            com.server.Constants.logger.info("The select query is " + query);
            // displaying records

            if (rs.next()) {
                codeFlag = true;
            }
            else {
                codeFlag = false;
            }

        } catch (SQLException e) {
            com.server.Constants.logger
                    .error("Error in Sql in checksecretcode.java in getsecretcode " + e.getMessage());
            throw new ServletException("SQL Exception.", e);
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    // Constants.logger.info("Closing rs Statement ");
                    rs = null;
                }
                db.closeConnection();

            } catch (SQLException e) {
                com.server.Constants.logger.error("Error in closing SQL in checksecretcode.java" + e.getMessage());
            }
        }
        return codeFlag;
    }

    public String getPassword(String emailid) throws ServletException {
        DBConnection db = null;
        Statement stmt = null;
        ResultSet rs = null;
        boolean codeFlag = false;
        String password = "";
        try {
            db = new DBConnection();
            stmt = db.stmt;
            String query = "SELECT email_id,password from business_users where email_id='" + emailid + "'";
            rs = stmt.executeQuery(query);
            com.server.Constants.logger.info("The select query is " + query);
            // displaying records

            if (rs.next()) {
                password = rs.getObject(2).toString();
            }

        } catch (SQLException e) {
            com.server.Constants.logger
                    .error("Error in Sql in checksecretcode.java in getsecretcode " + e.getMessage());
            throw new ServletException("SQL Exception.", e);
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                    // Constants.logger.info("Closing rs Statement ");
                    rs = null;
                }
                db.closeConnection();

            } catch (SQLException e) {
                com.server.Constants.logger.error("Error in closing SQL in checksecretcode.java" + e.getMessage());
            }
        }
        return password;
    }

}