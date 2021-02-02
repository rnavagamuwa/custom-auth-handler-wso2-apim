package com.rnavagamuwa.wso2.customAuthHandler;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axis2.Constants;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.Axis2Sender;
import org.apache.synapse.rest.AbstractHandler;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author rnavagamuwa
 */
public class CustomAuthHandler extends AbstractHandler {

    private String authUrl;

    @Override
    public boolean handleRequest(MessageContext messageContext) {

        CloseableHttpClient httpClient = HttpClientBuilder.create().build();

        try {

            HttpPost request = new HttpPost(this.authUrl);
            StringEntity params = new StringEntity(makePayload(messageContext).toString());
            request.addHeader("content-type", "application/json");
            request.setEntity(params);
            HttpResponse response = httpClient.execute(request);
            if (new JSONObject(new BasicResponseHandler().handleResponse(response)).get("validated").equals(true)) {
                return true;
            }

        } catch (Exception ex) {

            //ignored for now, but should implement this properly before moving into production
            ex.printStackTrace();
        } finally {
            //ignored for now, but should implement this properly before moving into production
        }

        org.apache.axis2.context.MessageContext axis2MC = ((Axis2MessageContext) messageContext).
                getAxis2MessageContext();
        axis2MC.setProperty(PassThroughConstants.MESSAGE_BUILDER_INVOKED, Boolean.TRUE);
        setFaultPayload(messageContext, getFaultPayload(900900, "Failed to authenticate with the backend",
                "Gateway authentication passed but the third-party authentication has failed"));
        sendFault(messageContext, HttpStatus.SC_UNAUTHORIZED);
        return false;
    }

    @Override
    public boolean handleResponse(MessageContext messageContext) {
        return true;
    }

    private JSONObject makePayload(MessageContext ctx) {
        String scopes = (String) ctx.getProperty("Scopes");
        Map headers = getTransportHeaders(ctx);
        String action = (String) headers.get("service");

        JSONObject jo = new JSONObject();
        jo.put("scope", scopes);
        jo.put("service", action);
        return jo;
    }

    private Map getTransportHeaders(MessageContext ctx) {
        return (Map) ((Axis2MessageContext) ctx).getAxis2MessageContext().
                getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
    }

    public static void sendFault(MessageContext messageContext, int status) {
        org.apache.axis2.context.MessageContext axis2MC = ((Axis2MessageContext) messageContext).
                getAxis2MessageContext();

        axis2MC.setProperty(NhttpConstants.HTTP_SC, status);
        messageContext.setResponse(true);
        messageContext.setProperty("RESPONSE", "true");
        messageContext.setTo(null);
        axis2MC.removeProperty("NO_ENTITY_BODY");

        // Always remove the ContentType - Let the formatter do its thing
        axis2MC.removeProperty(Constants.Configuration.CONTENT_TYPE);
        Map headers = (Map) axis2MC.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        if (headers != null) {
            headers.remove(HttpHeaders.AUTHORIZATION);

            headers.remove(HttpHeaders.HOST);
        }
        Axis2Sender.sendBack(messageContext);
    }

    private OMElement getFaultPayload(int AuthErrorCode, String message, String description) {
        OMFactory fac = OMAbstractFactory.getOMFactory();
        OMNamespace ns = fac.createOMNamespace("http://wso2.org/apimanager/authentication",
                "amt");
        OMElement payload = fac.createOMElement("fault", ns);

        OMElement errorCode = fac.createOMElement("code", ns);
        errorCode.setText(String.valueOf(AuthErrorCode));
        OMElement errorMessage = fac.createOMElement("message", ns);
        errorMessage.setText(message);
        OMElement errorDetail = fac.createOMElement("description", ns);
        errorDetail.setText(description);

        payload.addChild(errorCode);
        payload.addChild(errorMessage);
        payload.addChild(errorDetail);
        return payload;
    }

    public static void setFaultPayload(MessageContext messageContext, OMElement payload) {
        org.apache.axis2.context.MessageContext axis2MC = ((Axis2MessageContext) messageContext).
                getAxis2MessageContext();
        JsonUtil.removeJsonPayload(axis2MC);
        messageContext.getEnvelope().getBody().addChild(payload);
        Map headers = (Map) axis2MC.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        String acceptType = (String) headers.get(HttpHeaders.ACCEPT);
        Set<String> supportedMimes = new HashSet<String>(Arrays.asList("application/x-www-form-urlencoded",
                "multipart/form-data",
                "text/html",
                "application/xml",
                "text/xml",
                "application/soap+xml",
                "text/plain",
                "application/json",
                "application/json/badgerfish",
                "text/javascript"));

        // If an Accept header has been provided and is supported by the Gateway
        if (!StringUtils.isEmpty(acceptType) && supportedMimes.contains(acceptType)) {
            axis2MC.setProperty(Constants.Configuration.MESSAGE_TYPE, acceptType);
        } else {
            // If there isn't Accept Header in the request, will use error_message_type property
            // from _auth_failure_handler_.xml file
            if (messageContext.getProperty("error_message_type") != null) {
                axis2MC.setProperty(Constants.Configuration.MESSAGE_TYPE,
                        messageContext.getProperty("error_message_type"));
            }
        }
    }

    public String getAuthUrl() {
        return authUrl;
    }

    public void setAuthUrl(String authUrl) {
        this.authUrl = authUrl;
    }
}
