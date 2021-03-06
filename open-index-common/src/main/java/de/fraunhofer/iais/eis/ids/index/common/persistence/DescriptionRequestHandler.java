package de.fraunhofer.iais.eis.ids.index.common.persistence;

import de.fraunhofer.iais.eis.DescriptionRequestMessage;
import de.fraunhofer.iais.eis.DescriptionResponseMessageBuilder;
import de.fraunhofer.iais.eis.Message;
import de.fraunhofer.iais.eis.RejectionReason;
import de.fraunhofer.iais.eis.ids.component.core.MessageHandler;
import de.fraunhofer.iais.eis.ids.component.core.RejectMessageException;
import de.fraunhofer.iais.eis.ids.component.core.SecurityTokenProvider;
import de.fraunhofer.iais.eis.ids.component.core.TokenRetrievalException;
import de.fraunhofer.iais.eis.ids.component.core.map.DescriptionRequestMAP;
import de.fraunhofer.iais.eis.ids.component.core.util.CalendarUtil;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;

/**
 * Message handler class, forwarding the request to the DescriptionProvider and responding with a DescriptionResponseMessage
 */
public class DescriptionRequestHandler implements MessageHandler<DescriptionRequestMAP, DescriptionResultMAP> {

    private final DescriptionProvider descriptionProvider;
    private final SecurityTokenProvider securityTokenProvider;
    private final URI responseSenderAgentUri;
    public static int maxDepth = 10;

    /**
     * Constructor
     * @param descriptionProvider Instance of DescriptionProvider class to retrieve descriptions of the requested objects from triple store
     * @param securityTokenProvider Instance of SecurityTokenProvider to retrieve a DAT for the response messages
     * @param responseSenderAgentUri The ids:senderAgent which should be provided in response messages
     */
    public DescriptionRequestHandler(DescriptionProvider descriptionProvider, SecurityTokenProvider securityTokenProvider, URI responseSenderAgentUri) {
        this.descriptionProvider = descriptionProvider;
        this.securityTokenProvider = securityTokenProvider;
        this.responseSenderAgentUri = responseSenderAgentUri;
    }

    /**
     * The actual handle function which is called from the components, if the incoming request can be handled by this class
     * @param messageAndPayload The incoming request
     * @return A DescriptionResponseMessage, if the request could be handled successfully
     * @throws RejectMessageException thrown if an error occurs during the retrieval process, e.g. if the requested object could not be found
     */
    @Override
    public DescriptionResultMAP handle(DescriptionRequestMAP messageAndPayload) throws RejectMessageException {
        String payload;
        /*if(messageAndPayload.getMessage().getProperties() == null)
        {
            System.out.println("No properties in message");
        }
        else
        {
            System.out.println("Properties not null");
            for(Map.Entry<String, Object> entry :  messageAndPayload.getMessage().getProperties().entrySet())
            {
                System.out.println("Key: " + entry.getKey());
            }
        }
         */
        //TODO: No hardcoded URI should be used
        if(messageAndPayload.getMessage().getProperties() != null && messageAndPayload.getMessage().getProperties().containsKey("https://w3id.org/idsa/core/depth"))
        {
            //0 is the default value
            int depth = 0;
            try {
                String propertyValue = messageAndPayload.getMessage().getProperties().get("https://w3id.org/idsa/core/depth").toString();
                if(propertyValue.contains("^^"))
                {
                    propertyValue = propertyValue.substring(0, propertyValue.indexOf("^^"));
                }
                propertyValue = propertyValue.replace("\"", "");
                depth = Integer.parseInt(propertyValue);
                if(depth > maxDepth)
                {
                    depth = maxDepth;
                }
            }
            catch (NumberFormatException e)
            {
                e.printStackTrace();
                //come up with default value
            }
            payload = descriptionProvider.getElementAsJsonLd(messageAndPayload.getMessage().getRequestedElement(), depth);
        }
        else {
            payload = descriptionProvider.getElementAsJsonLd(messageAndPayload.getMessage().getRequestedElement());
        }
        try {
            return new DescriptionResultMAP(new DescriptionResponseMessageBuilder()
                    ._correlationMessage_(messageAndPayload.getMessage().getId())
                    ._issued_(CalendarUtil.now())
                    ._issuerConnector_(descriptionProvider.selfDescription.getId())
                    ._modelVersion_(descriptionProvider.selfDescription.getOutboundModelVersion())
                    ._securityToken_(securityTokenProvider.getSecurityTokenAsDAT())
                    ._senderAgent_(responseSenderAgentUri)
                    .build(),
                    payload
            );
        }
        catch (TokenRetrievalException e)
        {
            throw new RejectMessageException(RejectionReason.INTERNAL_RECIPIENT_ERROR, e);
        }
    }

    /**
     * Determines whether an incoming request can be handled by this class
     * @return true, if the message is of an applicable type (DescriptionRequestMessage only)
     */
    @Override
    public Collection<Class<? extends Message>> getSupportedMessageTypes() {
        return Collections.singletonList(DescriptionRequestMessage.class);
    }

}
