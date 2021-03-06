package com.journear.app.core.services;

import android.util.Log;

import com.journear.app.core.LocalFunctions;
import com.journear.app.core.entities.NearbyDevice;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CommunicationHub {

    public static final Integer MAX_RETRY_COUNT = 3;
    private static final String LOGTAG = "CommunicationHubLogs";
    // Subject is the ID of NearbyDevice (all messages will be contained in an array as a Thread
    // <Subject, Log>
    private Map<String, ConversationLog> conversationLogMap = new HashMap<>();
    private ArrayList<RequestTriesResponse> pendingMessages = new ArrayList<>();
    private NearbyDevice ndOwnJourneyPlan = null;
    private CommunicationListener commonListener = null;

    public void setCommonListener(CommunicationListener commonListener) {
        this.commonListener = commonListener;
    }

    public void setOwnJourneyPlan(NearbyDevice ndOwnJourneyPlan) {
        this.ndOwnJourneyPlan = ndOwnJourneyPlan;
    }


    public ArrayList<RequestTriesResponse> getPendingMessages() {
        return pendingMessages;
    }

    public void expire(RequestTriesResponse rtr) {
        try {
            pendingMessages.remove(rtr);
            rtr.responseListener.onExpire(rtr.message, conversationLogMap.get(rtr.message.getSubject()).nearbyDevice);
        } catch (Exception ex) {
            Log.e(LOGTAG, "Error in receiveResponse", ex);
        }
    }

    public void receiveResponse(JnMessage message) {
        /* 1. remove the message from @pendingMessages list
         * 2. add it to the appropriate entry in the @conversationLogMap
         * 3. raise the listener */
        try {
            if (ndOwnJourneyPlan != null) {
                if (message.getSubject().equals(ndOwnJourneyPlan.getTravelPlanId())) {
                    if (commonListener != null) {
                        commonListener.onResponse(message, ndOwnJourneyPlan);
                    }
                }
            }


            for (int i = 0; i < pendingMessages.size(); i++) {
                RequestTriesResponse rtr = pendingMessages.get(i);
                // match subject
                if (rtr.message.getSubject().equals(message.getSubject())) {

                    ConversationLog logObj = null;
                    if (conversationLogMap.containsKey(message.getSubject())) {

                        logObj = conversationLogMap.get(message.getSubject());
                        logObj.messages.add(message);

                        if (rtr != null) {
                            rtr.responseListener.onResponse(message, logObj.nearbyDevice);
                        }

                        if (message.getMessageFlag() == JnMessageSet.Accept ||
                                message.getMessageFlag() == JnMessageSet.Reject) {

                            String newMessageId = createMessageId(logObj.nearbyDevice, message);
                            JnMessage message1 = new JnMessage(newMessageId, JnMessageSet.Okay, LocalFunctions.getCurrentUser().phoneValue,
                                    logObj.nearbyDevice.getTravelPlanId(), LocalFunctions.getCurrentUser());
                            pendingMessages.add(new RequestTriesResponse(message1, MAX_RETRY_COUNT, null));
                        }
                    }

                    pendingMessages.remove(i);
                    break;
                }
            }
        } catch (Exception ex) {
            Log.e(LOGTAG, "Error in receiveResponse", ex);
        }
    }

    // returns message Id
    public String sendMessage(NearbyDevice nearbyDevice, JnMessageSet message, CommunicationListener responseListener) {
        String newMessageId = "";
        String phoneNumber = "";
        JnMessage lastMessageInTheConversation = null;
        JnMessage newMessage;

        // if there is a conversation log, get the last message so that this one can have a sequence
        if (conversationLogMap.containsKey(nearbyDevice.getTravelPlanId())) {
            lastMessageInTheConversation =
                    conversationLogMap.get(nearbyDevice.getTravelPlanId()).getLastMessageInTheConversation();
        } else {
            conversationLogMap.put(nearbyDevice.getTravelPlanId(), new ConversationLog(nearbyDevice));
            lastMessageInTheConversation = null;
        }

        newMessageId = createMessageId(nearbyDevice, lastMessageInTheConversation);
        newMessage = new JnMessage(newMessageId, message,
                phoneNumber, nearbyDevice.getTravelPlanId(), LocalFunctions.getCurrentUser());

        pendingMessages.add(new RequestTriesResponse(newMessage, MAX_RETRY_COUNT, responseListener));
        return newMessageId;
    }

    private String createMessageId(NearbyDevice nearbyDevice, JnMessage lastMessage) {
        if (nearbyDevice == null)
            return null;

        if (lastMessage == null) {
            return nearbyDevice.getTravelPlanId() + ":" + LocalFunctions.getCurrentUser().getUserId() + ":1";
        } else {
            int lastId = Integer.parseInt(StringUtils.split(lastMessage.getMessageId(), ":")[1]);
            return nearbyDevice.getTravelPlanId() + ":" + (lastId + 1);
        }
    }

    class RequestTriesResponse {
        JnMessage message;
        Integer triesLeft;
        CommunicationListener responseListener;

        public RequestTriesResponse(JnMessage message, Integer triesLeft, CommunicationListener responseListener) {
            this.message = message;
            this.triesLeft = triesLeft;
            this.responseListener = responseListener;
        }
    }
}

class ConversationLog {
    public NearbyDevice nearbyDevice;
    public ArrayList<JnMessage> messages;

    public ConversationLog(NearbyDevice nd) {
        nearbyDevice = nd;
        messages = new ArrayList<>();
    }

    public JnMessage getLastMessageInTheConversation() {
        try {
            return messages.get(messages.size() - 1);
        } catch (Exception ex) {
            return null;
        }
    }
}

