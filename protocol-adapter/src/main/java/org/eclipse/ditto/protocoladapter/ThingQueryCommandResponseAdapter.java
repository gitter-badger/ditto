/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.protocoladapter;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;

/**
 * Adapter for mapping a {@link ThingQueryCommandResponse} to and from an {@link Adaptable}.
 */
final class ThingQueryCommandResponseAdapter extends AbstractAdapter<ThingQueryCommandResponse> {

    private ThingQueryCommandResponseAdapter(
            final Map<String, JsonifiableMapper<ThingQueryCommandResponse>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Returns a new ThingQueryCommandResponseAdapter.
     *
     * @return the adapter.
     */
    public static ThingQueryCommandResponseAdapter newInstance() {
        return new ThingQueryCommandResponseAdapter(mappingStrategies());
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    private static Map<String, JsonifiableMapper<ThingQueryCommandResponse>> mappingStrategies() {
        final Map<String, JsonifiableMapper<ThingQueryCommandResponse>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(RetrieveThingResponse.TYPE,
                adaptable -> RetrieveThingResponse.of(thingIdFrom(adaptable), thingFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveThingResponse.TYPE,
                adaptable -> RetrieveThingResponse.of(thingIdFrom(adaptable), thingFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAclResponse.TYPE,
                adaptable -> RetrieveAclResponse.of(thingIdFrom(adaptable), aclFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAclEntryResponse.TYPE,
                adaptable -> RetrieveAclEntryResponse.of(thingIdFrom(adaptable), aclEntryFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAttributesResponse.TYPE,
                adaptable -> RetrieveAttributesResponse.of(thingIdFrom(adaptable), attributesFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAttributeResponse.TYPE, adaptable -> RetrieveAttributeResponse
                .of(thingIdFrom(adaptable), attributePointerFrom(adaptable), attributeValueFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeaturesResponse.TYPE,
                adaptable -> RetrieveFeaturesResponse.of(thingIdFrom(adaptable), featuresFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatureResponse.TYPE, adaptable -> RetrieveFeatureResponse
                .of(thingIdFrom(adaptable), featureIdFrom(adaptable), featurePropertiesFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeaturePropertiesResponse.TYPE, adaptable -> RetrieveFeaturePropertiesResponse
                .of(thingIdFrom(adaptable), featureIdFrom(adaptable), featurePropertiesFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies
                .put(RetrieveFeaturePropertyResponse.TYPE,
                        adaptable -> RetrieveFeaturePropertyResponse.of(thingIdFrom(adaptable),
                                featureIdFrom(adaptable),
                                featurePropertyPointerFrom(adaptable), featurePropertyValueFrom(adaptable),
                                dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final JsonPointer path = adaptable.getPayload().getPath();
        final String commandName = topicPath.getAction().get() + upperCaseFirst(PathMatcher.match(path));
        return topicPath.getGroup() + ".responses:" + commandName;
    }

    @Override
    public Adaptable toAdaptable(final ThingQueryCommandResponse commandResponse, final TopicPath.Channel channel) {
        final String responseName = commandResponse.getClass().getSimpleName().toLowerCase();
        if (!responseName.endsWith("response")) {
            throw UnknownCommandResponseException.newBuilder(responseName).build();
        }

        final TopicPathBuilder topicPathBuilder = DittoProtocolAdapter.newTopicPathBuilder(commandResponse.getId());

        final CommandsTopicPathBuilder commandsTopicPathBuilder;
        if (channel == TopicPath.Channel.TWIN) {
            commandsTopicPathBuilder = topicPathBuilder.twin().commands();
        } else if (channel == TopicPath.Channel.LIVE) {
            commandsTopicPathBuilder = topicPathBuilder.live().commands();
        } else {
            throw new IllegalArgumentException("Unknown Channel '" + channel + "'");
        }

        final String commandName = commandResponse.getClass().getSimpleName().toLowerCase();
        if (commandName.startsWith(TopicPath.Action.RETRIEVE.toString())) {
            commandsTopicPathBuilder.retrieve();
        } else {
            throw UnknownCommandException.newBuilder(commandName).build();
        }

        final Payload payload = Payload.newBuilder(commandResponse.getResourcePath()) //
                .withStatus(commandResponse.getStatusCode()) //
                .withValue(commandResponse.getEntity(commandResponse.getImplementedSchemaVersion())) //
                .build();

        return Adaptable.newBuilder(commandsTopicPathBuilder.build()) //
                .withPayload(payload) //
                .withHeaders(DittoProtocolAdapter.newHeaders(commandResponse.getDittoHeaders())) //
                .build();
    }

}
