package org.scriptable.util;

import java.util.Map;
import java.util.Properties;
import javax.mail.Session;
import javax.mail.Message;
import javax.mail.Transport;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.InternetAddress;
import org.scriptable.ScriptableHttpRequest;

import javax.mail.MessagingException;
import java.io.UnsupportedEncodingException;

public final class Emailer
{
    static Properties mailProperties = null;

    public Emailer(Map props) {
        this(Files.toProperties(props));
    }

    public Emailer(Properties props) {
        mailProperties = props;
    }

    /**
     * Send an email.
     * @param from optional, if null, mail_from property value will be used
     * @param textBody optional
     */
    public void sendTextEmail(String to, String subject, String textBody)
        throws MessagingException, UnsupportedEncodingException {
        sendEmail(null, to, subject, null, textBody);
    }

    public void sendHtmlEmail(String to, String subject, String htmlBody)
        throws MessagingException, UnsupportedEncodingException {
        sendEmail(null, to, subject, htmlBody, null);
    }

    public void sendEmail(String from, String to, String subject, String htmlBody, String textBody)
        throws MessagingException, UnsupportedEncodingException {
        Session session = Session.getInstance(mailProperties, null);
        MimeMessage message = new MimeMessage(session);

        if (from != null)
            message.setFrom(new InternetAddress(from));
        else if (mailProperties.get("mail_from") != null)
            message.setFrom(new InternetAddress(mailProperties.getProperty("mail_from")));

        for (String toAddr: to.split("[,;] *"))
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(toAddr));

        message.setSubject(subject);
        MimeMultipart multi = new MimeMultipart();
        MimeBodyPart bodyPart;

        if (textBody != null && htmlBody != null && !htmlBody.equals("")) {
            // attach this one first, since some clients just use the last part dy default
            bodyPart = new MimeBodyPart();
            bodyPart.setText(textBody);
            multi.addBodyPart(bodyPart);
            multi.setSubType("alternative"); // let the reader choose between html and plain text version
        }

        bodyPart = new MimeBodyPart();

        if (htmlBody != null && !htmlBody.equals(""))
            bodyPart.setContent(htmlBody, ScriptableHttpRequest.CONTENT_HTML);
        else
            bodyPart.setContent(textBody, ScriptableHttpRequest.CONTENT_PLAIN);

        multi.addBodyPart(bodyPart);

        message.setContent(multi);
        message.saveChanges(); // update headers before send
        Transport.send(message);
    }
}

