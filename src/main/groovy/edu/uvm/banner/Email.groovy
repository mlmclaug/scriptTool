/*
Auth: mlm - 06/27/2016

Email is a helper class to send emails.

03/24/2023 mlm - technology debt - update to jakarta.mail 2.0.1 
*/
package edu.uvm.banner;
import java.util.Properties;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.activation.*;

public class Email {

	String from = System.getenv('USER') + '@' + 
		(System.getenv('HOSTNAME') ?  System.getenv('HOSTNAME') : 'uvm.edu')

	String username //user name and password to authenticate to email system
	String password // authenticate only if these are set.

	String host = "smtp.uvm.edu";
	String port = "25";
	boolean starttls = true

	String to 
	String cc 
	String bcc
	String subject 
	String body 
	String bodymimetype 
	List attachments // array of file names or File objects to attach

	Session session

	void openSession(){
		// Get the Session object.
		Properties props = new Properties();
		props.put("mail.smtp.starttls.enable", starttls);
		props.put("mail.smtp.host", host);
		props.put("mail.smtp.port", port);

		def authenticator = null
		if (username) {
			props.put("mail.smtp.auth", "true")
	     	authenticator = Session.getInstance(props,      
			new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
			   return new PasswordAuthentication(username, password);
			}
			});
	    }
    	session = Session.getInstance(props,authenticator);
	}


	boolean send(){
		// send the email returns true on success/ false on failure.

		if (!session) {openSession()}

		try {
		// Create a default MimeMessage object.
		Message message = new MimeMessage(session);

 		message.setFrom(new InternetAddress(from ? from : ''));
		message.setRecipients(Message.RecipientType.TO,
	    	InternetAddress.parse(to ? to : ''));
		message.setRecipients(Message.RecipientType.CC,
		    InternetAddress.parse(cc ? cc : ''));
		message.setRecipients(Message.RecipientType.BCC,
		    InternetAddress.parse(bcc ? bcc : ''));
		message.setSubject(subject ? subject : '');


		// Create the message part
		BodyPart messageBodyPart = new MimeBodyPart();

		if (bodymimetype) {
			//set the content of the email message (as html)
			messageBodyPart.setContent(body ? body : '', bodymimetype);
		}else{
			// Now set the actual message (as text email)
			messageBodyPart.setText(body ? body : '');
		}

		// Create a multipart message
		Multipart multipart = new MimeMultipart();
		// Set text message part
		multipart.addBodyPart(messageBodyPart);

		// Add any attachments here
		attachments.each { f ->
			String filename 
			DataSource source

			if ( f instanceof File ){
			    filename = f.getName()
			    source = new FileDataSource(f);
			}else{ 
			    File f2 = new File(f)
			    filename = f2.getName()
			    source = new FileDataSource(f2);
			}

			messageBodyPart = new MimeBodyPart();
			messageBodyPart.setDataHandler(new DataHandler(source));
			messageBodyPart.setFileName(filename);
			multipart.addBodyPart(messageBodyPart);
		}
		// Send the complete message parts
		message.setContent(multipart);
		// Send message
		Transport.send(message);
		return true
		} catch (MessagingException e) {
		 throw new RuntimeException(e);
		}
	}
}

