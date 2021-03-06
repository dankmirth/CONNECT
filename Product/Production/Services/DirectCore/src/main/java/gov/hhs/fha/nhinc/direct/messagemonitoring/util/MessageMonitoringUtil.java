/*
 * Copyright (c) 2012, United States Government, as represented by the Secretary of Health and Human Services.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above
 *       copyright notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *     * Neither the name of the United States Government nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE UNITED STATES GOVERNMENT BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package gov.hhs.fha.nhinc.direct.messagemonitoring.util;

import static gov.hhs.fha.nhinc.direct.DirectReceiverImpl.X_DIRECT_FINAL_DESTINATION_DELIVERY_HEADER_VALUE;
import gov.hhs.fha.nhinc.direct.edge.proxy.DirectEdgeProxy;
import gov.hhs.fha.nhinc.direct.edge.proxy.DirectEdgeProxyObjectFactory;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.nhindirect.common.tx.TxDetailParser;
import org.nhindirect.common.tx.TxUtil;
import org.nhindirect.common.tx.impl.DefaultTxDetailParser;
import org.nhindirect.common.tx.model.Tx;
import org.nhindirect.common.tx.model.TxDetail;
import org.nhindirect.common.tx.model.TxDetailType;
import org.nhindirect.common.tx.model.TxMessageType;
import org.nhindirect.stagent.AddressSource;
import org.nhindirect.stagent.NHINDAddress;
import org.nhindirect.stagent.NHINDAddressCollection;

/**
 * Message Monitoring utility class
 *
 * @author Naresh Subramanyan
 */
public class MessageMonitoringUtil {

    public static final String DISPOSITION_NOTIFICATION_OPTIONS_HEADER_NAME = "Disposition-Notification-Options";
    public static final String DISPOSITION_NOTIFICATION_PROCESSED = "automatic-action/mdn-sent-automatically;processed";
    public static final String DISPOSITION_NOTIFICATION_DISPATCHED = "automatic-action/MDN-sent-automatically;dispatched";
    public static final int DEFAULT_OUTBOUND_FAILED_MESSAGE_RETRY_COUNT = 1;
    public static final int DEFAULT_INBOUND_FAILED_MESSAGE_RETRY_COUNT = 1;
    public static final boolean DEFAULT_NOTIFIY_OUTBOUND_SECURITY_FAILURE_IMMEDIATE = true;
    public static final boolean DEFAULT_MESSAGE_MONITORING_ENABLED = true;
    public static final int DEFAULT_PROCESSED_MESSAGE_RECEIVE_TIME_LIMIT = 60;
    public static final int DEFAULT_DISPATCHED_MESSAGE_RECEIVE_TIME_LIMIT = 60;
    public static final String DEFAULT_POSTMASTER_EMAIL_ID_PREFIX = "postmaster";

    /**
     * Returns Mail Recipients as a NHINDAddressCollection
     *
     * @param message
     * @return
     * @throws javax.mail.MessagingException
     */
    protected static NHINDAddressCollection getMailRecipients(MimeMessage message) throws MessagingException {
        final NHINDAddressCollection recipients = new NHINDAddressCollection();

        if (message.getAllRecipients() == null) {
            return recipients;
        }

        for (InternetAddress address : (InternetAddress[]) message.getAllRecipients()) {
            String emailId = address.getAddress();
            recipients.add(new NHINDAddress(emailId, (AddressSource) null));
        }
        return recipients;
    }

    /**
     * Returns Mail Sender from the message
     *
     * @return
     */
    private static String getMailSender(MimeMessage message) throws MessagingException {

        //get it from sender
        InternetAddress sender = (InternetAddress) message.getSender();

        if (sender == null) {
            //get it From
            InternetAddress[] fromAddress = (InternetAddress[]) message.getFrom();
            if (fromAddress != null & fromAddress.length > 1) {
                return (fromAddress[0]).getAddress();
            }
            return null;
        }
        return sender.getAddress();
    }

    protected static Tx getTxToTrack(MimeMessage msg, String sender, NHINDAddressCollection recipients) {

        final TxDetailParser txParser = new DefaultTxDetailParser();

        try {

            final Map<String, TxDetail> details = txParser.getMessageDetails(msg);

            if (sender != null) {
                details.put(TxDetailType.FROM.getType(), new TxDetail(TxDetailType.FROM, sender.toLowerCase(Locale.getDefault())));
            }
            if (recipients != null && !recipients.isEmpty()) {
                details.put(TxDetailType.RECIPIENTS.getType(), new TxDetail(TxDetailType.RECIPIENTS, recipients.toString().toLowerCase(Locale.getDefault())));
            }

            return new Tx(TxUtil.getMessageType(msg), details);
        } ///CLOVER:OFF
        catch (Exception e) {
            //LOGGER.warn("Failed to parse message to Tx object.", e);
            return null;
        }
        ///CLOVER:ON
    }

    /**
     * Returns parent message id from the message
     *
     * @param msg
     * @return
     */
    public static String getParentMessageId(MimeMessage msg) {
        final TxDetailParser txParser = new DefaultTxDetailParser();

        return getOriginalMessageId(convertMimeMessageToTx(msg, txParser));

    }

    /**
     * Returns true of the incoming message is Processed MDN
     *
     * @param msg
     * @return
     */
    public static boolean isIncomingMessageMDNProcessed(MimeMessage msg) {
        final TxDetailParser txParser = new DefaultTxDetailParser();
        Tx tx = convertMimeMessageToTx(msg, txParser);
        final TxMessageType type = tx.getMsgType();
        if (type != TxMessageType.DSN && type != TxMessageType.MDN) {
            return false;
        }

        final TxDetail detail = tx.getDetail(TxDetailType.DISPOSITION);
        return detail != null && !detail.getDetailValue().isEmpty() && detail.getDetailValue().equalsIgnoreCase(DISPOSITION_NOTIFICATION_PROCESSED);
    }

    /**
     * Returns true of the incoming message is Dispatched MDN
     *
     * @param msg
     * @return
     */
    public static boolean isIncomingMessageMDNDispatched(MimeMessage msg) {
        final TxDetailParser txParser = new DefaultTxDetailParser();
        Tx tx = convertMimeMessageToTx(msg, txParser);
        final TxMessageType type = tx.getMsgType();
        if (type != TxMessageType.DSN && type != TxMessageType.MDN) {
            return false;
        }

        final TxDetail detail = tx.getDetail(TxDetailType.DISPOSITION);
        return detail != null && !detail.getDetailValue().isEmpty() && detail.getDetailValue().equalsIgnoreCase(DISPOSITION_NOTIFICATION_DISPATCHED);
    }

    /**
     * Returns the Original Message Id of the outbound notification
     *
     * @param tx
     * @return
     */
    public static String getOriginalMessageId(Tx tx) {
        final TxMessageType type = tx.getMsgType();
        if (type != TxMessageType.DSN && type != TxMessageType.MDN) {
            return "";
        }

        final TxDetail detail = tx.getDetail(TxDetailType.PARENT_MSG_ID);
        return (detail != null && !detail.getDetailValue().isEmpty()) ? detail.getDetailValue() : "";

    }

    /**
     * Converts a MimeMessage to a Tx message
     *
     * @param msg
     * @param parser
     * @return
     */
    protected static Tx convertMimeMessageToTx(MimeMessage msg, TxDetailParser parser) {
        if (msg == null) {
            throw new IllegalArgumentException("Invalid parameter received. Message cannot be null.");
        }

        final Map<String, TxDetail> details = parser.getMessageDetails(msg);
        return new Tx(TxUtil.getMessageType(msg), details);
    }

    /**
     * Returns true if the edge requested for notification else false.
     *
     * @param message
     * @return
     */
    public static boolean isNotificationRequestedByEdge(MimeMessage message) {
        String[] headers = null;
        try {
            headers = message.getHeader(DISPOSITION_NOTIFICATION_OPTIONS_HEADER_NAME);
        } catch (MessagingException ex) {
            Logger.getLogger(MessageMonitoringUtil.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (headers != null) {
            for (String header : headers) {
                if (checkHeaderForDispatchedRequest(header)) {
                    return true;
                }

            }
        }
        return false;
    }

    /**
     * Returns true if the edge requested for notification else false.
     *
     * @return
     */
    private static boolean checkHeaderForDispatchedRequest(String header) {
        //TODO: need a beeter approcah to verify the value
        return StringUtils.contains(header, X_DIRECT_FINAL_DESTINATION_DELIVERY_HEADER_VALUE) && StringUtils.contains(header, "true");
    }

    /**
     * Returns the Outbound Failed message retry count. Reads the system property first, if not found then reads the
     * gateway properties, if not found then uses the default value.
     *
     * @return
     */
    public static int getOutboundFailedMessageRetryCount() {
        //Check if its there in the Syestem Properties
        //TODO
        //Read it from the properties file
        //TODO
        //If not found, then use the default value
        return DEFAULT_OUTBOUND_FAILED_MESSAGE_RETRY_COUNT;
    }

    /**
     * Returns the Inbound Failed message retry count. Reads the system property first, if not found then reads the
     * gateway properties, if not found then uses the default value.
     *
     * @return
     */
    public static int getInboundFailedMessageRetryCount() {
        //Check if its there in the Syestem Properties
        //TODO
        //Read it from the properties file
        //TODO
        //If not found, then use the default value
        return DEFAULT_INBOUND_FAILED_MESSAGE_RETRY_COUNT;
    }

    /**
     * Return true if the notification to edge needs to be sent immediately. Reads the system property first, if not
     * found then reads the gateway properties, if not found then uses the default value.
     *
     * @return
     */
    public static boolean isNotifyOutboundSecurityFailureImmediate() {
        //Check if its there in the Syestem Properties
        //TODO
        //Read it from the properties file
        //TODO
        //If not found, then use the default value
        return DEFAULT_NOTIFIY_OUTBOUND_SECURITY_FAILURE_IMMEDIATE;
    }

    
    /**
     * Returns the Processed message receive time limit. Reads the system property first, if not found then reads the
     * gateway properties, if not found then uses the default value.
     *
     * @return
     */
    public static int getProcessedMessageReceiveTimeLimit() {
        //Check if its there in the Syestem Properties
        //TODO
        //Read it from the properties file
        //TODO
        //If not found, then use the default value
        return DEFAULT_INBOUND_FAILED_MESSAGE_RETRY_COUNT;
    }

    /**
     * Returns the Processed message receive time limit. Reads the system property first, if not found then reads the
     * gateway properties, if not found then uses the default value.
     *
     * @return
     */
    public static String getDomainPostmasterEmailId() {
        //Check if its there in the Syestem Properties
        //TODO
        //Read it from the properties file
        //TODO
        //If not found, then use the default value
        return DEFAULT_POSTMASTER_EMAIL_ID_PREFIX;
    }
    
    /**
     * Returns the Dispatched message receive time limit. Reads the system property first, if not found then reads the
     * gateway properties, if not found then uses the default value.
     *
     * @return
     */
    public static int getDispatchedMessageReceiveTimeLimit() {
        //Check if its there in the Syestem Properties
        //TODO
        //Read it from the properties file
        //TODO
        //If not found, then use the default value
        return DEFAULT_INBOUND_FAILED_MESSAGE_RETRY_COUNT;
    }

    public static boolean isMessageMonitoringEnabled() {

        //check the system properties
        //check the property file
        //not found then return the default value
        return DEFAULT_MESSAGE_MONITORING_ENABLED;
    }

    /**
     * Return true if the message is a DSN or MDN
     *
     * @param message containing the message to be tested.
     * @return true if the envelope exists, the message exists and is an MDN Notification or a DSN notification.
     */
    public static boolean isMdnOrDsn(MimeMessage message) {
        return TxUtil.getMessageType(message).equals(TxMessageType.DSN)
            || TxUtil.getMessageType(message).equals(TxMessageType.MDN);
    }

    public static boolean isProcessedMDNReceiveTimeLapsed(Date createTime) {
        //check if the currenttime - createTime > the time limit
        long diff = (new Date()).getTime() - createTime.getTime();
        long diffHours = diff / (60 * 60 * 1000) % 24;
        long diffDays = diff / (24 * 60 * 60 * 1000);
        int totalDiffHours = (int) (diffHours + (diffDays * 24));
        return totalDiffHours > getProcessedMessageReceiveTimeLimit();
    }

    public static boolean isDispatchedMDNReceiveTimeLapsed(Date createTime) {
        //check if the currenttime - createTime > the time limit
        long diff = (new Date()).getTime() - createTime.getTime();
        long diffHours = diff / (60 * 60 * 1000) % 24;
        long diffDays = diff / (24 * 60 * 60 * 1000);
        int totalDiffHours = (int) (diffHours + (diffDays * 24));
        return totalDiffHours > getDispatchedMessageReceiveTimeLimit();
    }

    public static MimeMessage createMimeMessage(String postMasterEmailId, String subject,
        String recipient, String text) throws AddressException, MessagingException {
        MimeMessage message = new MimeMessage((Session) null);
        message.setSender(new InternetAddress(postMasterEmailId));
        message.setSubject(subject);
        message.setRecipient(RecipientType.TO, new InternetAddress(recipient));
        message.setText(text);
        message.setFrom(new InternetAddress(postMasterEmailId));
        message.setSentDate(new Date());
        return message;
    }

    /**
     * Get the Direct edge Proxy
     * 
     * @return DirectEdgeProxy implementation to handle the direct edge
     */
    public static DirectEdgeProxy getDirectEdgeProxy() {
        DirectEdgeProxyObjectFactory factory = new DirectEdgeProxyObjectFactory();
        return factory.getDirectEdgeProxy();
    }
    /**
     * Returns the domain name from an emailId.
     * 
     * @return
     */
    public static String getDomainFromEmail(String emailId){
        if (emailId!=null){
               String[] emailSplit = emailId.split("@");
               if (emailSplit.length >= 2){
                   return emailSplit[1];
               }
        }
        return null;
    }
}
